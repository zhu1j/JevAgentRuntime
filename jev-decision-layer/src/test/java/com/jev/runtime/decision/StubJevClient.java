package com.jev.runtime.decision;

import com.jev.runtime.common.JevClient;
import com.jev.runtime.common.JevDecision;
import com.jev.runtime.common.JevPrimitive;
import com.jev.runtime.common.JevRequest;

/** Deterministic Jev stub returning a fixed score, for tests. */
class StubJevClient implements JevClient {

    private final double score;

    StubJevClient(double score) {
        this.score = score;
    }

    @Override
    public JevDecision decide(JevRequest request) {
        return new JevDecision(request.primitive(), null, null, 0.9, score, 0.95, 0L, 0.0, "stub");
    }

    @Override
    public String backend() {
        return "stub";
    }
}
