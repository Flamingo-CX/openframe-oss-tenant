package com.openframe.client.service.agentregistration;

import com.openframe.client.service.agentregistration.secretretriver.ToolAgentRegistrationSecretRetriever;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class ToolCommandParamsResolver {

    private static final String REGISTRATION_SECRET_PLACEHOLDER = "${server.registrationSecret}";

    private final List<ToolAgentRegistrationSecretRetriever> toolAgentRegistrationSecretRetrievers;

    public List<String> process(String toolId, List<String> commandArgs) {
        if (commandArgs == null) {
            return null;
        }

        // Process each argument and replace placeholders where found
        return commandArgs.stream()
                .map(arg -> processArgument(toolId, arg))
                .toList();
    }

    private String processArgument(String toolId, String argument) {
        if (argument == null) {
            return null;
        }

        // Retrieve and inject the registration secret only when the placeholder is present.
        if (StringUtils.contains(argument, REGISTRATION_SECRET_PLACEHOLDER)) {
            return argument.replace(REGISTRATION_SECRET_PLACEHOLDER, getRegistrationSecret(toolId));
        }

        // If no placeholder is found, return the argument unchanged.
        return argument;
    }

    private String getRegistrationSecret(String toolId) {
        return toolAgentRegistrationSecretRetrievers.stream()
                .filter(retriever -> isSuitable(toolId, retriever))
                .findFirst()
                .map(ToolAgentRegistrationSecretRetriever::getSecret)
                .orElseThrow(() -> new IllegalStateException("No tool agent registration secret retriver found for " + toolId));
    }

    private boolean isSuitable(String toolId, ToolAgentRegistrationSecretRetriever retriever) {
        String retrieverToolId = retriever.getToolId();
        return StringUtils.equals(retrieverToolId, toolId);
    }

}
