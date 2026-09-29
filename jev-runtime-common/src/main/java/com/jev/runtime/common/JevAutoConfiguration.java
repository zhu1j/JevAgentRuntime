package com.jev.runtime.common;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * Wires the Jev decision backend. When {@code jev.fallback-local=true} (default),
 * or no API key is set, an offline local implementation is used so the platform
 * runs end-to-end without the remote endpoint.
 */
@AutoConfiguration
@EnableConfigurationProperties(JevProperties.class)
public class JevAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public JevClient jevClient(JevProperties properties) {
        if (properties.isFallbackLocal() || properties.getApiKey() == null || properties.getApiKey().isBlank()) {
            return LocalJevClient.INSTANCE;
        }
        return new RemoteJevClient(properties);
    }
}
