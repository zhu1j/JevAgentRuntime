package com.jev.runtime.common.audit;

import com.jev.runtime.common.JevDecision;
import com.jev.runtime.common.JevRequest;

import java.time.Instant;

/**
 * One audited Jev decision: the input/output snapshot needed to replay, debug
 * and evaluate decision quality offline. This record is the unit of the
 * runtime's decision audit log — it feeds observability, evaluation and
 * postmortem analysis (e.g. "tool-selection confidence dropped below 0.6 in
 * scenario X, so the routing threshold was re-calibrated").
 */
public record DecisionAuditRecord(
        Instant timestamp,
        String primitive,
        String instruction,
        String inputPreview,
        String value,
        Double probability,
        Double score,
        double confidence,
        long latencyMs,
        String source,
        boolean fallback,
        boolean chaos
) {
    public static DecisionAuditRecord of(JevRequest request, JevDecision decision,
                                         boolean fallback, boolean chaos) {
        return new DecisionAuditRecord(
                Instant.now(),
                request.primitive().name(),
                truncate(request.instruction(), 120),
                truncate(request.input(), 200),
                decision.value(),
                decision.probability(),
                decision.score(),
                decision.confidence(),
                decision.latencyMs(),
                decision.source(),
                fallback,
                chaos);
    }

    private static String truncate(String s, int max) {
        if (s == null) {
            return null;
        }
        return s.length() <= max ? s : s.substring(0, max) + "...";
    }
}
