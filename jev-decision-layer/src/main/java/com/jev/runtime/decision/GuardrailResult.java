package com.jev.runtime.decision;

/**
 * Outcome of the request-side guardrail check.
 *
 * @param allowed     whether the request may proceed
 * @param riskScore   calibrated injection/unsafe probability in [0,1]
 * @param confidence  Jev's calibrated confidence in this decision
 * @param reason      human-readable reason when blocked
 */
public record GuardrailResult(
        boolean allowed,
        double riskScore,
        double confidence,
        String reason
) {}
