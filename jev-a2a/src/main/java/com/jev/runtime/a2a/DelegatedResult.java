package com.jev.runtime.a2a;

/**
 * Result returned by a delegated remote agent.
 */
public record DelegatedResult(
        String taskId,
        String status,
        String output,
        long latencyMs
) {}
