package com.openframe.api;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.kafka.annotation.EnableKafka;

@SpringBootApplication(exclude = {
        org.springframework.cloud.stream.config.BindingServiceConfiguration.class,
        org.springframework.cloud.stream.function.FunctionConfiguration.class,
})
@EnableKafka
@ComponentScan(basePackages = {
    "com.openframe.api",
    "com.openframe.data",
        "com.openframe.core"
})
@Slf4j
public class ApiApplication {

    public static void main(String[] args) {
        log.info("Starting OpenFrame API");
        SpringApplication.run(ApiApplication.class, args);
    }
}