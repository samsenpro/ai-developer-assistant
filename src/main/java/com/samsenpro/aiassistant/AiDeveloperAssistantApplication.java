package com.samsenpro.aiassistant;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class AiDeveloperAssistantApplication {

    public static void main(String[] args) {
        SpringApplication.run(AiDeveloperAssistantApplication.class, args);
    }
}
