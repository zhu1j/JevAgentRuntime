package com.jev.runtime.common;

import java.util.List;
import java.util.Map;

/**
 * Outbound response from the agent runtime, carrying the answer plus the decision
 * trace so the gateway's routing, guardrail and evaluation decisions are auditable.
 */
public record AgentResponse(
        String sessionId,
        String status,
        String answer,
        String modelTier,
        List<StepTrace> steps,
        Map<String, Object> metrics
) {
    public record StepTrace(
            int index,
            String kind,
            String summary,
            String jevPrimitive,
            Double jevValue,
            long latencyMs
    ) {}
}
