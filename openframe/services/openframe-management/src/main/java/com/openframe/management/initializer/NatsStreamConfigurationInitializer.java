package com.openframe.management.initializer;

import com.openframe.management.service.NatsStreamManagementService;
import io.nats.client.api.RetentionPolicy;
import io.nats.client.api.StorageType;
import io.nats.client.api.StreamConfiguration;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class NatsStreamConfigurationInitializer {

    // TODO: use json file
    private static final List<StreamConfiguration> CONFIGURATIONS = List.of(
            // tool installation stream
            StreamConfiguration.builder()
                    .name("TOOL_INSTALLATION")
                    .subjects(List.of("machine.*.tool-installation"))
                    .storageType(StorageType.File)
                    .retentionPolicy(RetentionPolicy.Limits)
                    .build()
    );

    private final NatsStreamManagementService natsStreamManagementService;

    @PostConstruct
    public void run() {
        CONFIGURATIONS
                .forEach(natsStreamManagementService::save);
    }

}
