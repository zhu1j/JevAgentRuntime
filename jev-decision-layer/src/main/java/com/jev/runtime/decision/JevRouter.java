package com.jev.runtime.decision;

import com.jev.runtime.common.JevClient;
import com.jev.runtime.common.JevDecision;
import com.jev.runtime.common.JevPrimitive;
import com.jev.runtime.common.JevRequest;
import com.jev.runtime.common.ModelTier;
import com.jev.runtime.observability.AgentMetrics;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Jev usage #1 — task difficulty to model routing.
 *
 * <p>Instead of always calling the most capable (and expensive) model, the runtime
 * asks Jev for a continuous difficulty SCORE and maps it onto a capability tier.
 * This is the "fast/slow division of labor": cheap decisions first, expensive
 * generation only when the difficulty justifies it.</p>
 */
@Component
public class JevRouter {

    private final JevClient jev;
    private final AgentMetrics metrics;

    /** Difficulty thresholds for tier promotion. Configurable in production. */
    private final double mediumThreshold;
    private final double largeThreshold;

    @Autowired
    public JevRouter(JevClient jev, AgentMetrics metrics) {
        this(jev, metrics, 0.35, 0.7);
    }

    public JevRouter(JevClient jev, AgentMetrics metrics, double mediumThreshold, double largeThreshold) {
        this.jev = jev;
        this.metrics = metrics;
        this.mediumThreshold = mediumThreshold;
        this.largeThreshold = largeThreshold;
    }

    public TaskRoutingDecision route(String goal, String stepContext) {
        JevRequest req = JevRequest.score(
                "Score the reasoning difficulty of this agent step from 0 (trivial) to 1 (very hard), "
                        + "considering length, multi-step planning and domain complexity.",
                goal + "\n\nStep context:\n" + stepContext);
        JevDecision d = jev.decide(req);
        metrics.recordJevDecision(JevPrimitive.SCORE.name(), d.source(), d.latencyMs());

        double difficulty = d.score() == null ? 0.5 : d.score();
        ModelTier tier;
        if (difficulty >= largeThreshold) {
            tier = ModelTier.LARGE;
        } else if (difficulty >= mediumThreshold) {
            tier = ModelTier.MEDIUM;
        } else {
            tier = ModelTier.SMALL;
        }
        return new TaskRoutingDecision(tier, difficulty, d.confidence(),
                "difficulty=%.3f -> tier=%s".formatted(difficulty, tier));
    }
}
