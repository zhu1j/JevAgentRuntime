package com.jev.runtime.eval;

import java.util.List;

/**
 * Aggregate outcome of an evaluation run — the "release gate" verdict.
 */
public record EvalReport(
        int total,
        int passed,
        double passRate,
        double meanScore,
        long totalLatencyMs,
        List<EvalCaseResult> cases
) {
    public boolean gatePassed(double minPassRate) {
        return passRate >= minPassRate;
    }
}
