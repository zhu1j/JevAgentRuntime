package com.jev.runtime.eval;

import java.util.List;

/**
 * Result of scoring a case.
 */
public record EvalCaseResult(
        String id,
        boolean pass,
        double score,
        double confidence,
        long latencyMs
) {}
