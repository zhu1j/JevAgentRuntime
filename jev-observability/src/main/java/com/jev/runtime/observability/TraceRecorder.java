package com.jev.runtime.observability;

import com.jev.runtime.common.AgentResponse;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Collects an ordered decision trace for a single agent run.
 * In a production deployment these spans would also be exported through
 * OpenTelemetry; here the trace is retained as the audit/replay record that
 * the gateway surfaces to callers and feeds into evaluation.
 */
@Component
public class TraceRecorder {

    /** A per-run, thread-confined-style recorder (one instance per agent execution). */
    public static class RunTrace {
        private final List<AgentResponse.StepTrace> steps = new ArrayList<>();
        private int counter = 0;

        public void add(String kind, String summary, String primitive, Double value, long latencyMs) {
            steps.add(new AgentResponse.StepTrace(counter++, kind, summary, primitive, value, latencyMs));
        }

        public List<AgentResponse.StepTrace> steps() {
            return List.copyOf(steps);
        }

        public int size() {
            return steps.size();
        }
    }

    public RunTrace newTrace() {
        return new RunTrace();
    }
}
