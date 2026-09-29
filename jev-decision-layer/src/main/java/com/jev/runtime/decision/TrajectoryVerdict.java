package com.jev.runtime.decision;

/**
 * Outcome of the execution-side trajectory verification.
 *
 * @param completed    whether the goal is judged complete
 * @param successProb  calibrated probability that the step/goal succeeded
 * @param confidence   Jev's calibrated confidence
 * @param reason       rationale for the verdict
 */
public record TrajectoryVerdict(
        boolean completed,
        double successProb,
        double confidence,
        String reason
) {}
