package com.openframe.client.service.agentregistration;

import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class MachineIdGenerator {

    // TODO: add orgId prefix
    public String generate() {
        return UUID.randomUUID().toString();
    }

}
