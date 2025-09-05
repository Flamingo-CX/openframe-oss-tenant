package com.openframe.sdk.tacticalrmm;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.openframe.sdk.tacticalrmm.exception.TacticalRmmApiException;
import com.openframe.sdk.tacticalrmm.exception.TacticalRmmException;
import com.openframe.sdk.tacticalrmm.model.AgentRegistrationSecretRequest;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

public class TacticalRmmClient {

    private static final String GET_INSTALLER_URL = "/agents/installer/";

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public TacticalRmmClient() {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
        this.objectMapper = new ObjectMapper();
    }

    public String getInstallationSecret(
            String tacticalServerUrl,
            String apiKey,
            AgentRegistrationSecretRequest request
    ) {
        try {
            String requestBody = objectMapper.writeValueAsString(request);

            HttpRequest httpRequest = HttpRequest.newBuilder()
                    .uri(URI.create(tacticalServerUrl + GET_INSTALLER_URL))
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json")
                    .header("X-API-KEY", apiKey)
                    .timeout(Duration.ofSeconds(3))
                    .build();

            HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                throw new TacticalRmmApiException("Failed to fetch agent registration secret", response.statusCode(), response.body());
            }

            String body = response.body();
            return RegistrationSecretParser.parse(body);
        } catch (Exception e) {
            throw new TacticalRmmException("Failed to process get agent registration secret request", e);
        }
    }


    // get list of devices

    // run script

    // etc

}


