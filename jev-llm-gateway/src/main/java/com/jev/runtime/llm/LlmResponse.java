package com.jev.runtime.llm;

/**
 * A model completion response with usage + cost accounting.
 */
public record LlmResponse(
        String provider,
        String model,
        String tier,
        String content,
        long inputTokens,
        long outputTokens,
        double costUsd,
        long latencyMs,
        boolean fallback
) {}
