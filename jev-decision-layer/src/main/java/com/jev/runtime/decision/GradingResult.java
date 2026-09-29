package com.jev.runtime.decision;

/**
 * Outcome of result grading.
 *
 * @param score       continuous quality score in [0,1]
 * @param confidence  Jev's calibrated confidence
 * @param pass        whether the score clears the acceptance bar
 */
public record GradingResult(
        double score,
        double confidence,
        boolean pass
) {}
