package com.jev.runtime.decision;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Upgrades the remote Jev dependency to a deterministic stub so decision-layer
 * behaviour can be asserted without the external endpoint.
 */
class JevGraderTest {

    @Test
    void gradesHighWhenStubReturnsHighScore() {
        JevGrader grader = new JevGrader(new StubJevClient(0.9), new NoopMetrics(), 0.75);
        GradingResult r = grader.grade("summarize the doc", "a good summary");
        assertTrue(r.pass());
        assertEquals(0.9, r.score(), 1e-6);
    }
}
