package com.jev.runtime.common;

/**
 * The typed, calibrated answer returned by Jev.
 * Exactly one of {@code choiceIndex} / {@code probability} / {@code score} is meaningful,
 * depending on {@link #primitive()}.
 *
 * @param value       chosen option text (CHOICE) or null
 * @param choiceIndex 0-based index of the chosen candidate (CHOICE) or null
 * @param probability calibrated probability in [0,1] (NOUL) or null
 * @param score       continuous score in [0,1] (SCORE) or null
 * @param confidence  Jev's calibrated confidence in this decision
 * @param latencyMs   observed end-to-end latency
 * @param costUsd     observed cost (Jev output is free; usually only input cost)
 * @param source      which backend produced the decision (remote / local-fallback)
 */
public record JevDecision(
        JevPrimitive primitive,
        String value,
        Integer choiceIndex,
        Double probability,
        Double score,
        double confidence,
        long latencyMs,
        double costUsd,
        String source
) {
    public boolean isTrue(double threshold) {
        return probability != null && probability >= threshold;
    }
}
