package com.openframe.client.listener;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.openframe.client.service.ToolConnectionService;
import com.openframe.core.exception.NatsException;
import com.openframe.data.model.nats.ToolConnectionMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.Message;
import org.springframework.stereotype.Component;

import java.util.function.Consumer;

import static org.apache.commons.lang3.StringUtils.isEmpty;

@Component
@RequiredArgsConstructor
@Slf4j
public class ToolConnectionListener {

    private final ObjectMapper objectMapper;
    private final ToolConnectionService toolConnectionService;

    @Bean
    public Consumer<Message<String>> toolConnectionConsumer() {
        return message -> {
            String messagePayload = message.getPayload();
            try {
                ToolConnectionMessage toolConnectionMessage = objectMapper.readValue(messagePayload, ToolConnectionMessage.class);

                String machineId = getMachineId(message);
                String toolId = toolConnectionMessage.getToolId();
                String agentToolId = toolConnectionMessage.getAgentToolId();

                log.info("Received tool connection message with machineId {} toolId {} agentToolId {}", machineId, toolId, agentToolId);

                toolConnectionService.addToolConnection(machineId, toolId, agentToolId);
            } catch (Exception e) {
                log.error("Failed to process tool connection event: {}", messagePayload, e);
                throw new NatsException("Failed to process tool connection", e);
            }
        };
    }

    private String getMachineId(Message<String> message) {
        String topicName = message.getHeaders().get("NATS_RECEIVED_TOPIC", String.class);
        if (isEmpty(topicName)) {
            throw new IllegalStateException("Tool connection topic name is empty");
        }
        return topicName.split("\\.")[1];
    }

}
