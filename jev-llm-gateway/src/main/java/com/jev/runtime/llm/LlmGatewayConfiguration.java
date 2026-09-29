package com.jev.runtime.llm;

import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * Wires the LLM gateway beans: key pool, tenant rate limiter, and a circuit-breaker
 * registry used to short-circuit failing model endpoints.
 */
@Configuration
@EnableConfigurationProperties(GatewayProperties.class)
public class LlmGatewayConfiguration {

    @Bean
    public KeyPool keyPool() {
        return new KeyPool();
    }

    @Bean
    public TenantRateLimiter tenantRateLimiter(GatewayProperties props) {
        return new TenantRateLimiter(props.getRateLimitPerWindow(), props.getRateWindowMillis());
    }

    /**
     * One circuit breaker per endpoint, created lazily on first use.
     * A window of 10 calls with a 50% failure threshold trips the breaker open;
     * it then waits 10s before allowing trial calls again.
     */
    @Bean
    public CircuitBreakerRegistry circuitBreakerRegistry() {
        CircuitBreakerConfig config = CircuitBreakerConfig.custom()
                .slidingWindowSize(10)
                .minimumNumberOfCalls(5)
                .failureRateThreshold(50.0f)
                .waitDurationInOpenState(Duration.ofSeconds(10))
                .permittedNumberOfCallsInHalfOpenState(3)
                .build();
        return CircuitBreakerRegistry.of(config);
    }
}
