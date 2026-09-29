package com.jev.runtime.app;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * Entry point of the Jev-driven Enterprise Agent Runtime / Intelligent Agent Gateway.
 *
 * <p>Start with {@code mvn -pl jev-app -am spring-boot:run} (defaults to the offline
 * local Jev fallback, so no external credentials are required to run end-to-end).
 * Swagger UI: {@code /swagger-ui.html}; metrics: {@code /actuator/prometheus}.</p>
 */
@SpringBootApplication(scanBasePackages = "com.jev.runtime")
@ConfigurationPropertiesScan("com.jev.runtime")
public class JevAgentRuntimeApplication {

    public static void main(String[] args) {
        SpringApplication.run(JevAgentRuntimeApplication.class, args);
    }
}
