package com.jev.runtime.decision;

import com.jev.runtime.common.JevClient;
import com.jev.runtime.common.JevDecision;
import com.jev.runtime.common.JevPrimitive;
import com.jev.runtime.common.JevRequest;
import com.jev.runtime.observability.AgentMetrics;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Jev usage #3 — execution-side trajectory verification ("are we done / did it work?").
 *
 * <p>Every agent step is verified before continuing: a calibrated NOUL probability
 * answers "has the current goal been satisfied?" If not, the loop retries or
 * re-plans. This replaces an expensive LLM self-check on every iteration.</p>
 */
@Component
public class JevTrajectoryVerifier {

    private final JevClient jev;
    private final AgentMetrics metrics;
    private final double doneThreshold;

    @Autowired
    public JevTrajectoryVerifier(JevClient jev, AgentMetrics metrics) {
        this(jev, metrics, 0.7);
    }

    public JevTrajectoryVerifier(JevClient jev, AgentMetrics metrics, double doneThreshold) {
        this.jev = jev;
        this.metrics = metrics;
        this.doneThreshold = doneThreshold;
    }

    public TrajectoryVerdict verify(String goal, String latestObservation) {
        JevRequest req = JevRequest.noul(
                "Given the goal and the latest observation, is the goal now satisfied? "
                        + "Answer with a calibrated probability.",
                "Goal: " + goal + "\n\nLatest observation:\n" + latestObservation);
        JevDecision d = jev.decide(req);
        metrics.recordJevDecision(JevPrimitive.NOUL.name(), d.source(), d.latencyMs());

        double p = d.probability() == null ? 0.0 : d.probability();
        boolean completed = p >= doneThreshold;
        return new TrajectoryVerdict(completed, p, d.confidence(),
                completed ? "goal satisfied (p=%.3f)".formatted(p)
                          : "not yet satisfied (p=%.3f), continue".formatted(p));
    }
}
