package com.jev.runtime.eval;

import com.jev.runtime.decision.GradingResult;
import com.jev.runtime.decision.JevGrader;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * Evaluation harness.
 *
 * <p>Implements "eval as a release gate": a golden dataset is replayed through the
 * agent, each answer is graded by Jev, and the aggregate pass-rate decides whether
 * a change is safe to ship. Because Jev grading is fast and cheap, the same gate
 * can run per-PR and (sampled) in production.</p>
 */
@Service
public class EvalRunner {

    private final JevGrader grader;

    public EvalRunner(JevGrader grader) {
        this.grader = grader;
    }

    public EvalReport run(EvalDataset dataset) {
        List<EvalCaseResult> results = new ArrayList<>();
        long totalLatency = 0;
        int passed = 0;
        double scoreSum = 0;

        for (EvalCase c : dataset.cases()) {
            long start = System.nanoTime();
            String answer;
            try {
                answer = dataset.agent().apply(c.goal());
            } catch (RuntimeException ex) {
                answer = "";
            }
            GradingResult g = grader.grade(c.goal(), answer + "\n(expected hint: " + c.expectedHint() + ")");
            long latency = (System.nanoTime() - start) / 1_000_000L;
            totalLatency += latency;
            boolean pass = g.score() >= c.passThreshold();
            if (pass) passed++;
            scoreSum += g.score();
            results.add(new EvalCaseResult(c.id(), pass, g.score(), g.confidence(), latency));
        }

        int total = results.size();
        double passRate = total == 0 ? 0.0 : (double) passed / total;
        double meanScore = total == 0 ? 0.0 : scoreSum / total;
        return new EvalReport(total, passed, passRate, meanScore, totalLatency, results);
    }
}
