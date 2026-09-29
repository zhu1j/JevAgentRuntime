package com.jev.runtime.decision;

import com.jev.runtime.common.ModelTier;

/**
 * Result of the Jev-driven routing decision: which model tier should serve this step,
 * plus the difficulty score and confidence that produced it.
 */
public record TaskRoutingDecision(
        ModelTier tier,
        double difficulty,
        double confidence,
        String reason
) {}
