package com.jev.runtime.llm;

import org.springframework.stereotype.Component;

import java.util.Locale;

/**
 * Deterministic offline provider used when no real credentials are configured.
 * It returns a synthetic completion whose length scales with the prompt, so the
 * whole gateway (routing, cost, fallback, metrics) can be exercised end-to-end.
 */
@Component
public class SimulatedProvider implements ModelProvider {

    @Override
    public String id() {
        return "*";
    }

    @Override
    public RawCompletion complete(String apiKey, ModelEndpoint endpoint, LlmRequest request) {
        long inputTokens = Math.max(1, request.prompt().length() / 4L);
        // simulate a model reply
        long outputTokens = Math.min(request.maxTokens(), 64 + inputTokens % 128);
        String content = "[%s/%s] simulated answer for: %s"
                .formatted(endpoint.provider(), endpoint.model(),
                        request.prompt().toLowerCase(Locale.ROOT).length() > 80
                                ? request.prompt().substring(0, 80) + "..."
                                : request.prompt());
        return new RawCompletion(content, inputTokens, outputTokens);
    }
}
