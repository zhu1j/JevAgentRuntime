package com.jev.runtime.llm;

import java.util.List;
import java.util.Map;

/**
 * A model completion request after routing.
 */
public record LlmRequest(
        String tenantId,
        String sessionId,
        String prompt,
        String system,
        int maxTokens,
        Map<String, Object> metadata
) {
    public LlmRequest {
        if (metadata == null) metadata = Map.of();
    }
}
