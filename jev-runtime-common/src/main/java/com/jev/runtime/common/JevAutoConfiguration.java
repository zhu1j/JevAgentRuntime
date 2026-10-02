package com.jev.runtime.common;

import com.jev.runtime.common.audit.DecisionAuditLog;
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
    public DecisionAuditLog decisionAuditLog() {
        return new DecisionAuditLog();
    }

    @Bean
    @ConditionalOnMissingBean
    public JevClient jevClient(JevProperties properties, DecisionAuditLog auditLog) {
        JevClient primary;
        if (properties.isFallbackLocal() || properties.getApiKey() == null || properties.getApiKey().isBlank()) {
            primary = LocalJevClient.INSTANCE;
        } else {
            primary = new RemoteJevClient(properties);
        }
        // every decision flows through the resilience wrapper: fallback on failure,
        // chaos injection for game-days, and a full decision audit trail
        return new ResilientJevClient(primary, LocalJevClient.INSTANCE, auditLog);
    }
}
