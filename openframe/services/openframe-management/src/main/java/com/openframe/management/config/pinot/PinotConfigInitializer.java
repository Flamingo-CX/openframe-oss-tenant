package com.openframe.management.config.pinot;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;
import javax.annotation.PostConstruct;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

@Slf4j
//@Configuration
// TODO: make work again
public class PinotConfigInitializer {

    private final ResourceLoader resourceLoader;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    @Value("${pinot.controller.url}")
    private String pinotControllerUrl;

    @Value("${pinot.config.enabled:true}")
    private boolean pinotConfigEnabled;

    @Value("${pinot.config.retry.max-attempts:5}")
    private int maxRetries;

    @Value("${pinot.config.retry.delay-ms:5000}")
    private long retryDelayMs;

    private static final List<PinotConfig> PINOT_CONFIGS = Arrays.asList(
            new PinotConfig("devices", "schema-devices.json", "table-config-devices.json"),
            new PinotConfig("logs","schema-logs.json","table-config-logs.json")
    );

    public PinotConfigInitializer(ResourceLoader resourceLoader) {
        this.resourceLoader = resourceLoader;
        this.restTemplate = new RestTemplate();
        this.objectMapper = new ObjectMapper();
    }

    @PostConstruct
    public void init() {
        if (!pinotConfigEnabled) {
            log.info("Pinot configuration deployment is disabled");
            return;
        }

        log.info("Starting Pinot configuration deployment to {}", pinotControllerUrl);

        for (PinotConfig config : PINOT_CONFIGS) {
            try {
                deployPinotConfig(config);
            } catch (Exception e) {
                log.error("Failed to deploy Pinot configuration for {}", config.getName(), e);
            }
        }

        log.info("Pinot configuration deployment completed");
    }

    private void deployPinotConfig(PinotConfig config) {
        log.info("Deploying Pinot configuration for: {}", config.getName());

        try {
            String schemaConfig = loadResource(config.getSchemaFile());
            String tableConfig = loadResource(config.getTableConfigFile());

            deployWithRetry(() -> deploySchema(schemaConfig), "schema for " + config.getName());
            deployWithRetry(() -> deployTableConfig(tableConfig, config.getName()), "table config for " + config.getName());

            log.info("Successfully deployed Pinot configuration for: {}", config.getName());

        } catch (Exception e) {
            log.error("Failed to load Pinot configuration files for {}", config.getName(), e);
            throw new RuntimeException("Failed to load Pinot configuration for " + config.getName(), e);
        }
    }

    private String loadResource(String resourcePath) throws IOException {
        Resource resource = resourceLoader.getResource("classpath:pinot/config/" + resourcePath);
        if (!resource.exists()) {
            throw new IOException("Resource not found: " + resourcePath);
        }
        try (var inputStream = resource.getInputStream()) {
            return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private void deployWithRetry(Runnable deployment, String configType) {
        int retryCount = 0;
        Exception lastException = null;

        while (retryCount < maxRetries) {
            try {
                deployment.run();
                return;
            } catch (ResourceAccessException e) {
                lastException = e;
                retryCount++;

                if (retryCount >= maxRetries) {
                    log.error("Failed to deploy {} after {} retries", configType, maxRetries, e);
                    throw new RuntimeException("Failed to deploy " + configType + " after " + maxRetries + " retries", e);
                }

                log.warn("Failed to deploy {}, retrying in {} ms (attempt {}/{})",
                        configType, retryDelayMs, retryCount, maxRetries);

                try {
                    TimeUnit.MILLISECONDS.sleep(retryDelayMs);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException("Interrupted while waiting to retry deployment", ie);
                }
            } catch (Exception e) {
                log.error("Non-retryable error deploying {}", configType, e);
                throw new RuntimeException("Failed to deploy " + configType, e);
            }
        }

        if (lastException != null) {
            throw new RuntimeException("Failed to deploy " + configType + " after " + maxRetries + " retries", lastException);
        }
    }

    private void deploySchema(String schemaConfig) {
        String url = String.format("http://%s/schemas", pinotControllerUrl);

        try {
            objectMapper.readTree(schemaConfig);
            HttpHeaders headers = createHeaders();
            HttpEntity<String> request = new HttpEntity<>(schemaConfig, headers);
            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.POST, request, String.class);

            if (response.getStatusCode() == HttpStatus.OK) {
                log.info("Successfully deployed schema configuration");
            } else {
                log.error("Failed to deploy schema configuration. Status: {}", response.getStatusCode());
                throw new RuntimeException("Failed to deploy schema configuration. Status: " + response.getStatusCode());
            }

        } catch (Exception e) {
            log.error("Error deploying schema configuration", e);
            throw new RuntimeException("Failed to deploy schema configuration", e);
        }
    }

    private void deployTableConfig(String tableConfig, String configName) {
        try {
            JsonNode tableConfigJson = objectMapper.readTree(tableConfig);
            String tableName = tableConfigJson.get("tableName").asText() + "_REALTIME";

            String updateUrl = String.format("http://%s/tables/%s", pinotControllerUrl, tableName);
            String createUrl = String.format("http://%s/tables", pinotControllerUrl);

            HttpHeaders headers = createHeaders();
            HttpEntity<String> request = new HttpEntity<>(tableConfig, headers);

            try {
                ResponseEntity<String> response = restTemplate.exchange(updateUrl, HttpMethod.PUT, request, String.class);

                if (response.getStatusCode() == HttpStatus.OK) {
                    log.info("Successfully updated table configuration for {} ({})", tableName, configName);
                } else {
                    log.error("Failed to update table configuration for {}. Status: {}", tableName, response.getStatusCode());
                    throw new RuntimeException("Failed to update table configuration. Status: " + response.getStatusCode());
                }

            } catch (HttpClientErrorException.NotFound e) {
                ResponseEntity<String> response = restTemplate.exchange(createUrl, HttpMethod.POST, request, String.class);

                if (response.getStatusCode() == HttpStatus.OK) {
                    log.info("Successfully created table configuration for {} ({})", tableName, configName);
                } else {
                    log.error("Failed to create table configuration for {}. Status: {}", tableName, response.getStatusCode());
                    throw new RuntimeException("Failed to create table configuration. Status: " + response.getStatusCode());
                }
            }

        } catch (Exception e) {
            log.error("Error deploying table configuration for {}", configName, e);
            throw new RuntimeException("Failed to deploy table configuration for " + configName, e);
        }
    }

    private HttpHeaders createHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));
        return headers;
    }
}
