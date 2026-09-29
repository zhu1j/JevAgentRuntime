package com.jev.runtime.observability;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * Central metric registry for the agent runtime.
 * Exposed to Prometheus via the actuator endpoint; all gateway/decision layers
 * record into it so the runtime has fleet-wide, tenant-scoped observability.
 *
 * Key metrics:
 *  - jev_decision_latency   (timer, by primitive + source)
 *  - jev_decision_total     (counter, by primitive + source)
 *  - llm_request_total      (counter, by tenant + provider + model + tier + status)
 *  - llm_request_latency    (timer, by provider + model)
 *  - llm_cost_usd_total     (counter, by tenant + provider + model)
 *  - llm_tokens_total       (counter, by tenant + direction)
 *  - guardrail_block_total  (counter, by tenant + reason)
 *  - context_compaction_saved_tokens (counter)
 */
@Component
public class AgentMetrics {

    private final MeterRegistry registry;

    public AgentMetrics(MeterRegistry registry) {
        this.registry = registry;
    }

    public void recordJevDecision(String primitive, String source, long latencyMs) {
        Counter.builder("jev_decision_total")
                .tag("primitive", primitive).tag("source", source)
                .register(registry).increment();
        Timer.builder("jev_decision_latency")
                .tag("primitive", primitive).tag("source", source)
                .register(registry).record(latencyMs, TimeUnit.MILLISECONDS);
    }

    public void recordLlmRequest(String tenant, String provider, String model, String tier, String status, long latencyMs) {
        Counter.builder("llm_request_total")
                .tag("tenant", tenant).tag("provider", provider).tag("model", model)
                .tag("tier", tier).tag("status", status)
                .register(registry).increment();
        Timer.builder("llm_request_latency")
                .tag("provider", provider).tag("model", model)
                .register(registry).record(latencyMs, TimeUnit.MILLISECONDS);
    }

    public void recordCost(String tenant, String provider, String model, double costUsd) {
        Counter.builder("llm_cost_usd_total")
                .tag("tenant", tenant).tag("provider", provider).tag("model", model)
                .register(registry).increment(costUsd);
    }

    public void recordTokens(String tenant, String direction, long tokens) {
        Counter.builder("llm_tokens_total")
                .tag("tenant", tenant).tag("direction", direction)
                .register(registry).increment(tokens);
    }

    public void recordGuardrailBlock(String tenant, String reason) {
        Counter.builder("guardrail_block_total")
                .tag("tenant", tenant).tag("reason", reason)
                .register(registry).increment();
    }

    public void recordCompactionSaved(long tokens) {
        Counter.builder("context_compaction_saved_tokens")
                .register(registry).increment(tokens);
    }
}
