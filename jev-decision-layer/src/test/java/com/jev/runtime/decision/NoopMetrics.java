package com.jev.runtime.decision;

import com.jev.runtime.observability.AgentMetrics;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

/** Metrics backed by an ephemeral registry, for tests. */
class NoopMetrics extends AgentMetrics {

    NoopMetrics() {
        super(new SimpleMeterRegistry());
    }
}
