package com.jev.runtime.eval;

/**
 * A single evaluation case: an input goal and the criteria a good answer must meet.
 */
public record EvalCase(
        String id,
        String goal,
        String expectedHint,
        double passThreshold
) {
    public EvalCase {
        if (passThreshold <= 0) passThreshold = 0.75;
    }
}
