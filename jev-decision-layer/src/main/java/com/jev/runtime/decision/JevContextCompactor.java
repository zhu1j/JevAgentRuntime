package com.jev.runtime.decision;

import com.jev.runtime.common.JevClient;
import com.jev.runtime.common.JevDecision;
import com.jev.runtime.common.JevPrimitive;
import com.jev.runtime.common.JevRequest;
import com.jev.runtime.observability.AgentMetrics;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Jev usage #4 — context compaction ("which history do we keep?").
 *
 * <p>Long agent runs accumulate tool calls and observations. Rather than dropping
 * the oldest items blindly or sending everything, each item is scored by Jev for
 * relevance to the goal; the top-K are kept and the rest dropped. This directly
 * controls context size (and therefore cost) while preserving the items that matter.</p>
 */
@Component
public class JevContextCompactor {

    private final JevClient jev;
    private final AgentMetrics metrics;
    private final int keepTopK;
    /** Rough chars-per-token ratio used to estimate savings without a tokenizer. */
    private static final int CHARS_PER_TOKEN = 4;

    @Autowired
    public JevContextCompactor(JevClient jev, AgentMetrics metrics) {
        this(jev, metrics, 4);
    }

    public JevContextCompactor(JevClient jev, AgentMetrics metrics, int keepTopK) {
        this.jev = jev;
        this.metrics = metrics;
        this.keepTopK = keepTopK;
    }

    public CompactionResult compact(String goal, List<String> history) {
        if (history == null || history.isEmpty()) {
            return new CompactionResult(List.of(), List.of(), 0);
        }
        Map<String, Double> relevance = new LinkedHashMap<>();
        for (String item : history) {
            JevRequest req = JevRequest.score(
                    "Score how relevant this context item is to achieving the goal, from 0 to 1.",
                    "Goal: " + goal + "\n\nContext item:\n" + item);
            JevDecision d = jev.decide(req);
            metrics.recordJevDecision(JevPrimitive.SCORE.name(), d.source(), d.latencyMs());
            relevance.put(item, d.score() == null ? 0.0 : d.score());
        }

        List<String> sorted = new ArrayList<>(history);
        sorted.sort(Comparator.comparingDouble((String s) -> relevance.getOrDefault(s, 0.0)).reversed());

        List<String> kept = new ArrayList<>(sorted.subList(0, Math.min(keepTopK, sorted.size())));
        List<String> dropped = new ArrayList<>(sorted.subList(kept.size(), sorted.size()));

        long droppedChars = dropped.stream().mapToLong(String::length).sum();
        long savedTokens = droppedChars / CHARS_PER_TOKEN;
        metrics.recordCompactionSaved(savedTokens);

        return new CompactionResult(kept, dropped, savedTokens);
    }
}
