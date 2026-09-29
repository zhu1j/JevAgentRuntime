package com.jev.runtime.decision;

import com.jev.runtime.common.JevClient;
import com.jev.runtime.common.JevDecision;
import com.jev.runtime.common.JevPrimitive;
import com.jev.runtime.common.JevRequest;
import com.jev.runtime.observability.AgentMetrics;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Jev usage #5 — result grading (an LLM-as-judge replacement).
 *
 * <p>Rather than spending an expensive LLM call to grade every output on a discrete
 * scale, Jev returns a continuous SCORE plus a calibrated confidence. This makes
 * "eval as a release gate" cheap enough to run on every request, not just in CI.</p>
 */
@Component
public class JevGrader {

    private final JevClient jev;
    private final AgentMetrics metrics;
    private final double passThreshold;

    @Autowired
    public JevGrader(JevClient jev, AgentMetrics metrics) {
        this(jev, metrics, 0.75);
    }

    public JevGrader(JevClient jev, AgentMetrics metrics, double passThreshold) {
        this.jev = jev;
        this.metrics = metrics;
        this.passThreshold = passThreshold;
    }

    public GradingResult grade(String goal, String answer) {
        JevRequest req = JevRequest.score(
                "Grade how well the answer satisfies the goal, from 0 (fails) to 1 (excellent), "
                        + "considering correctness, completeness and relevance.",
                "Goal: " + goal + "\n\nAnswer:\n" + answer);
        JevDecision d = jev.decide(req);
        metrics.recordJevDecision(JevPrimitive.SCORE.name(), d.source(), d.latencyMs());

        double score = d.score() == null ? 0.0 : d.score();
        return new GradingResult(score, d.confidence(), score >= passThreshold);
    }
}
