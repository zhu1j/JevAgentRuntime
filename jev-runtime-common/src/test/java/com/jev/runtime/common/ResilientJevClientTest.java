package com.jev.runtime.common;

import com.jev.runtime.common.audit.DecisionAuditLog;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The resilience contract: fallback on primary failure, chaos injection, full audit trail. */
class ResilientJevClientTest {

    private JevRequest request() {
        return JevRequest.choice("query the stock of activity 10001",
                "select the most appropriate tool", List.of("seckill.queryStock", "kb.search"));
    }

    @Test
    void healthyPrimaryDecidesNormally() {
        DecisionAuditLog audit = new DecisionAuditLog();
        ResilientJevClient client = new ResilientJevClient(LocalJevClient.INSTANCE,
                LocalJevClient.INSTANCE, audit);

        JevDecision d = client.decide(request());

        assertEquals("seckill.queryStock", d.value());
        assertFalse(client.isChaos());
        assertEquals(1L, audit.stats().get("total"));
        assertEquals(0L, audit.stats().get("fallbacks"));
    }

    @Test
    void primaryFailureFallsBackAndIsAudited() {
        JevClient broken = new JevClient() {
            @Override
            public JevDecision decide(JevRequest request) {
                throw new IllegalStateException("jev unavailable");
            }

            @Override
            public String backend() {
                return "broken";
            }
        };
        DecisionAuditLog audit = new DecisionAuditLog();
        ResilientJevClient client = new ResilientJevClient(broken, LocalJevClient.INSTANCE, audit);

        JevDecision d = client.decide(request());

        // decision still succeeds via the local calibrated heuristic
        assertEquals("seckill.queryStock", d.value());
        assertEquals("local-fallback", d.source());
        assertEquals(1L, audit.stats().get("total"));
        assertEquals(1L, audit.stats().get("fallbacks"));
        assertEquals(1.0, (double) audit.stats().get("fallbackRate"), 1e-9);
    }

    @Test
    void chaosForcesFallbackUntilTurnedOff() {
        DecisionAuditLog audit = new DecisionAuditLog();
        ResilientJevClient client = new ResilientJevClient(LocalJevClient.INSTANCE,
                LocalJevClient.INSTANCE, audit);

        client.setChaos(true);
        assertTrue(client.isChaos());
        assertFalse(client.healthy());

        JevDecision duringChaos = client.decide(request());
        assertEquals("local-fallback", duringChaos.source());
        assertEquals(1L, audit.stats().get("chaosEvents"));

        client.setChaos(false);
        assertFalse(client.isChaos());
        assertTrue(client.healthy());
    }
}
