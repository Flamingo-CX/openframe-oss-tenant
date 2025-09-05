package com.openframe.client.config;

import com.openframe.sdk.tacticalrmm.TacticalRmmClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ToolSdkConfig {

    @Bean
    public TacticalRmmClient tacticalRmmClient() {
        return new TacticalRmmClient();
    }

}
