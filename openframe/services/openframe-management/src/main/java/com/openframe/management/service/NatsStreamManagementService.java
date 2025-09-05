package com.openframe.management.service;

import com.openframe.core.exception.NatsException;
import io.nats.client.Connection;
import io.nats.client.JetStreamApiException;
import io.nats.client.JetStreamManagement;
import io.nats.client.api.StreamConfiguration;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class NatsStreamManagementService {

    private final Connection natsConnection;

    public void save(StreamConfiguration streamConfiguration) {
        try {
            JetStreamManagement jetStreamManagement = natsConnection.jetStreamManagement();
            String streamName = streamConfiguration.getName();

            if (existsByName(streamName)) {
                log.info("Update existing stream {}", streamName);
                jetStreamManagement.updateStream(streamConfiguration);
            } else {
                log.info("Add new stream {}: {}", streamName, streamConfiguration);
                jetStreamManagement.addStream(streamConfiguration);
            }
        } catch (Exception e) {
            throw new NatsException("Error during stream creation with configuration: " + streamConfiguration, e);
        }
    }

    private boolean existsByName(String streamName) {
        try {
            JetStreamManagement jetStreamManagement = natsConnection.jetStreamManagement();
            jetStreamManagement.getStreamInfo(streamName);
            log.info("Stream {} exists", streamName);
            return true;
        } catch (JetStreamApiException e) {
            if (e.getErrorCode() == 404) {
                log.info("Stream {} doesn't exist", streamName);
                return false;
            }
            throw new NatsException("Api error during stream " + streamName + " retrieve", e);
        } catch (Exception e) {
            throw new NatsException("Error during stream " + streamName + " retrieve", e);
        }
    }
}
