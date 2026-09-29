package com.jev.runtime.decision;

import java.util.List;

/**
 * Result of context compaction.
 *
 * @param kept       retained context items (most relevant first)
 * @param dropped    dropped context items
 * @param savedTokens estimated tokens saved
 */
public record CompactionResult(
        List<String> kept,
        List<String> dropped,
        long savedTokens
) {}
