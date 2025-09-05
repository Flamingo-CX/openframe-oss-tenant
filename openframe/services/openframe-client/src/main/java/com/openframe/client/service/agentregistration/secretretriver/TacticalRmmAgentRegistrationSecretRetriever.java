package com.openframe.client.service.agentregistration.secretretriver;

import com.openframe.data.document.tool.IntegratedTool;
import com.openframe.data.document.tool.ToolUrl;
import com.openframe.data.document.tool.ToolUrlType;
import com.openframe.data.service.IntegratedToolService;
import com.openframe.data.service.ToolUrlService;
import com.openframe.sdk.tacticalrmm.TacticalRmmClient;
import com.openframe.sdk.tacticalrmm.model.AgentRegistrationSecretRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class TacticalRmmAgentRegistrationSecretRetriever implements ToolAgentRegistrationSecretRetriever{

    private static final String TOOL_ID = "tactical-rmm";

    private final IntegratedToolService integratedToolService;
    private final ToolUrlService toolUrlService;
    private final TacticalRmmClient client;

    @Override
    public String getToolId() {
        return TOOL_ID;
    }

    @Override
    public String getSecret() {
        // Get the integrated tool configuration
        IntegratedTool integratedTool = integratedToolService.getToolById(TOOL_ID)
                .orElseThrow(() -> new IllegalStateException("Found no tool with id " + TOOL_ID));

        ToolUrl toolUrl = toolUrlService.getUrlByToolType(integratedTool, ToolUrlType.API)
                .orElseThrow(() -> new IllegalStateException("Found no api url for tool with id" + TOOL_ID));

        String apiUrl = toolUrl.getUrl() + ":" + toolUrl.getPort();
        String apiKey = integratedTool.getCredentials().getApiKey().getKey();

        // Get secret
        AgentRegistrationSecretRequest request = buildRequest(apiUrl);
        String secret = client.getInstallationSecret(apiUrl, apiKey, request);

        log.info("Successfully retrieved enroll secret from TacticalRmm");
        return secret;
    }

    // Default request for any OS
    private AgentRegistrationSecretRequest buildRequest(String apiUrl) {
        AgentRegistrationSecretRequest request = new AgentRegistrationSecretRequest();
        request.setInstallMethod("manual");
        request.setClient(1);
        request.setSite(1);
        request.setExpires(2400);
        request.setAgentType("server");
        request.setPower(0);
        request.setRdp(0);
        request.setPing(0);
        request.setGoarch("amd64");
        request.setApi(apiUrl);
        request.setFileName("trmm-defaultorganization-defaultsite-server-amd64.exe");
        request.setPlatform("windows");
        return request;
    }

}
