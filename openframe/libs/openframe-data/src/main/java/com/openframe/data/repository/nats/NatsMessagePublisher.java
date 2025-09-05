package com.openframe.data.repository.nats;

import com.openframe.core.exception.NatsException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cloud.stream.function.StreamBridge;
import org.springframework.integration.support.MessageBuilder;
import org.springframework.messaging.Message;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty("spring.cloud.stream.enabled")
public class NatsMessagePublisher {

    private final StreamBridge streamBridge;
    
    public <T> void publish(String subject, T payload) {
        try {
            Message<T> message = MessageBuilder
                    .withPayload(payload)
                    .build();
            
            boolean result = streamBridge.send(subject, message);
            if (result) {
                log.info("Successfully published message to subject: {}", subject);
            } else {
                throw new NatsException("Failed to publish message to subject: " + subject);
            }
        } catch (Exception e) {
            throw new NatsException("Error publishing message to subject: " + subject, e);
        }
    }
} 