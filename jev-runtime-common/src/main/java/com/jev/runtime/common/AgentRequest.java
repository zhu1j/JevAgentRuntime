package com.jev.runtime.common;

import jakarta.validation.constraints.NotBlank;
import java.util.List;
import java.util.Map;

/**
 * Inbound request to the agent runtime.
 *
 * @param tenantId   tenant identifier (multi-tenant isolation key)
 * @param sessionId  conversation/session id, used for memory scoping
 * @param goal       the user goal in natural language
 * @param tools      optional tool/MCP names to make available (empty = all allowed)
 * @param context    free-form metadata
 */
public record AgentRequest(
        @NotBlank String tenantId,
        String sessionId,
        @NotBlank String goal,
        List<String> tools,
        Map<String, Object> context
) {
    public AgentRequest {
        if (tools == null) tools = List.of();
        if (context == null) context = Map.of();
    }
}
