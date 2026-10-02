package com.jev.runtime.common;

import com.jev.runtime.common.audit.DecisionAuditLog;
import com.jev.runtime.common.audit.DecisionAuditRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Resilience wrapper around the configured Jev backend — the concrete answer to
 * "what happens when the decision model is down or slow?":
 *
 * <ul>
 *   <li><b>Fallback</b>: any primary failure (timeout / error) transparently
 *       degrades to the local calibrated heuristic, so an agent run never dies
 *       together with its decision model;</li>
 *   <li><b>Chaos switch</b>: deliberately injects primary failures to demonstrate
 *       the degradation path live (game-day / interview demo);</li>
 *   <li><b>Audit</b>: every decision is written to the {@link DecisionAuditLog}
 *       with its source, confidence and whether the fallback path was taken.</li>
 * </ul>
 */
public class ResilientJevClient implements JevClient {

    private static final Logger log = LoggerFactory.getLogger(ResilientJevClient.class);

    private final JevClient primary;
    private final JevClient fallback;
    private final DecisionAuditLog auditLog;
    private final AtomicBoolean chaos = new AtomicBoolean(false);

    public ResilientJevClient(JevClient primary, JevClient fallback, DecisionAuditLog auditLog) {
        this.primary = primary;
        this.fallback = fallback;
        this.auditLog = auditLog;
    }

    @Override
    public JevDecision decide(JevRequest request) {
        if (chaos.get()) {
            log.warn("chaos: injecting Jev primary failure, engaging fallback");
            JevDecision d = fallback.decide(request);
            auditLog.record(DecisionAuditRecord.of(request, d, true, true));
            return d;
        }
        try {
            JevDecision d = primary.decide(request);
            auditLog.record(DecisionAuditRecord.of(request, d, false, false));
            return d;
        } catch (RuntimeException ex) {
            log.warn("Jev primary '{}' failed ({}), falling back to '{}'",
                    primary.backend(), ex.getMessage(), fallback.backend());
            JevDecision d = fallback.decide(request);
            auditLog.record(DecisionAuditRecord.of(request, d, true, false));
            return d;
        }
    }

    @Override
    public String backend() {
        return primary.backend() + (chaos.get() ? "+chaos" : "");
    }

    @Override
    public boolean healthy() {
        return !chaos.get() && primary.healthy();
    }

    /** Game-day switch: inject primary failures to exercise the degradation path. */
    public void setChaos(boolean enabled) {
        chaos.set(enabled);
    }

    public boolean isChaos() {
        return chaos.get();
    }
}
