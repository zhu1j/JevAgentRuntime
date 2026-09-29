package com.jev.runtime.llm;

/**
 * Provider abstraction. A real implementation calls the provider SDK/HTTP API;
 * the reference {@link SimulatedProvider} keeps the runtime runnable offline.
 */
public interface ModelProvider {

    /** Provider id, matching {@link ModelEndpoint#provider()}. */
    String id();

    /** Perform a completion. Implementations should be resilient and idempotent. */
    RawCompletion complete(String apiKey, ModelEndpoint endpoint, LlmRequest request);

    /**
     * Raw provider output prior to cost/usage normalization.
     */
    record RawCompletion(String content, long inputTokens, long outputTokens) {}
}
