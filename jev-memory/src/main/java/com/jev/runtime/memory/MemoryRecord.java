package com.jev.runtime.memory;

import java.time.Instant;

/**
 * A single memory item. {@code tier} distinguishes working / episodic / semantic memory.
 */
public record MemoryRecord(
        String tenantId,
        String sessionId,
        MemoryTier tier,
        String content,
        double salience,
        Instant createdAt
) {}
