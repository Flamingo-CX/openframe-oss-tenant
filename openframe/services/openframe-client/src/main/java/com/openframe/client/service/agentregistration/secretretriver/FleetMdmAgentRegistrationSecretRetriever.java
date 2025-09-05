package com.openframe.client.service.agentregistration.secretretriver;

import com.openframe.data.document.tool.IntegratedTool;
import com.openframe.data.document.tool.ToolUrl;
import com.openframe.data.document.tool.ToolUrlType;
import com.openframe.data.service.IntegratedToolService;
import com.openframe.data.service.ToolUrlService;
import com.openframe.sdk.fleetmdm.FleetMdmClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
@RequiredArgsConstructor
@Slf4j
public class FleetMdmAgentRegistrationSecretRetriever implements ToolAgentRegistrationSecretRetriever {

    private static final String TOOL_ID = "fleetmdm-server";

    private final IntegratedToolService integratedToolService;
    private final ToolUrlService toolUrlService;

    @Override
    public String getToolId() {
        return TOOL_ID;
    }

    @Override
    public String getSecret() {
        try {
            // Get the integrated tool configuration
            IntegratedTool integratedTool = integratedToolService.getToolById(TOOL_ID)
                    .orElseThrow(() -> new IllegalStateException("Found no tool with id " + TOOL_ID));
            
            ToolUrl toolUrl = toolUrlService.getUrlByToolType(integratedTool, ToolUrlType.API)
                    .orElseThrow(() -> new IllegalStateException("Found no api url for tool with id" + TOOL_ID));

            String apiUrl = toolUrl.getUrl() + ":" + toolUrl.getPort();
            String apiToken = integratedTool.getCredentials().getApiKey().getKey();

            // Create Fleet MDM client and get enroll secret
            FleetMdmClient client = new FleetMdmClient(apiUrl, apiToken);
            String enrollSecret = client.getEnrollSecret();
            
            log.info("Successfully retrieved enroll secret from Fleet MDM");
            return enrollSecret;
        } catch (Exception e) {
            log.error("Unexpected error while retrieving Fleet MDM enroll secret", e);
            throw new IllegalStateException("Failed to retrieve Fleet MDM enroll secret", e);
        }
    }
}
