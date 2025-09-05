package com.openframe.client.listener;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.openframe.client.service.MachineStatusService;
import com.openframe.core.exception.NatsException;
import com.openframe.data.model.nats.ClientConnectionEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.messaging.Message;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.function.Consumer;

@Component
@RequiredArgsConstructor
@Slf4j
public class ClientConnectionListener {

    private final ObjectMapper objectMapper;
    private final MachineStatusService machineStatusService;

    // TODO: nats is down - heartbeat fallback
    @Bean
    public Consumer<String> machineConnectedConsumer() {
        return message -> {
            try {
                ClientConnectionEvent event = objectMapper.readValue(message, ClientConnectionEvent.class);

                String machineId = event.getClient().getName();
                Instant eventTimestamp = Instant.parse(event.getTimestamp());
                machineStatusService.updateToOnline(machineId, eventTimestamp);
            } catch (Exception e) {
                log.error("Failed to process tool connection event", e);
                throw new NatsException("Failed to process client connected event", e);
            }
        };
    }

    @Bean
    public Consumer<String> machineDisconnectionConsumer() {
        return message -> {
            try {
                ClientConnectionEvent event = objectMapper.readValue(message, ClientConnectionEvent.class);

                String machineId = event.getClient().getName();
                Instant eventTimestamp = Instant.parse(event.getTimestamp());
                machineStatusService.updateToOffline(machineId, eventTimestamp);
            } catch (Exception e) {
                throw new NatsException("Failed to process disconnected connect event", e);
            }
        };
    }
}
