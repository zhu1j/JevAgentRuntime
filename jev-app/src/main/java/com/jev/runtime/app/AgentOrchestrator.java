package com.jev.runtime.app;

import com.jev.runtime.common.AgentRequest;
import com.jev.runtime.common.AgentResponse;
import com.jev.runtime.decision.CompactionResult;
import com.jev.runtime.decision.GuardrailResult;
import com.jev.runtime.decision.GradingResult;
import com.jev.runtime.decision.JevContextCompactor;
import com.jev.runtime.decision.JevGrader;
import com.jev.runtime.decision.JevGuardrail;
import com.jev.runtime.decision.JevRouter;
import com.jev.runtime.decision.JevTrajectoryVerifier;
import com.jev.runtime.decision.TaskRoutingDecision;
import com.jev.runtime.decision.TrajectoryVerdict;
import com.jev.runtime.llm.LlmGateway;
import com.jev.runtime.llm.LlmRequest;
import com.jev.runtime.llm.LlmResponse;
import com.jev.runtime.mcp.McpGateway;
import com.jev.runtime.mcp.McpTool;
import com.jev.runtime.mcp.McpToolResult;
import com.jev.runtime.memory.LayeredMemoryService;
import com.jev.runtime.observability.TraceRecorder;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The core single-agent orchestrator (the "single agent as the backbone" design).
 *
 * <p>Each run is a bounded ReAct-style loop in which every high-frequency decision
 * is delegated to the Jev decision layer, while only the actual generation is sent
 * to an LLM through the gateway:</p>
 *
 * <pre>
 *   goal
 *     ├─ 1. guardrail      (Jev NOUL  : unsafe / injection?)
 *     ├─ 2. route          (Jev SCORE : difficulty -> model tier)
 *     ├─ 3. act            (LLM       : generate next step, via gateway)
 *     ├─ 4. tool           (MCP       : tenant-scoped tool call)
 *     ├─ 5. verify         (Jev NOUL  : goal satisfied?)
 *     ├─ 6. compact        (Jev SCORE : keep the relevant context)
 *     └─ 7. grade          (Jev SCORE : final quality, eval-as-gate)
 * </pre>
 */
@Service
public class AgentOrchestrator {

    private static final int MAX_STEPS = 6;

    private final JevGuardrail guardrail;
    private final JevRouter router;
    private final LlmGateway llmGateway;
    private final McpGateway mcpGateway;
    private final JevTrajectoryVerifier verifier;
    private final JevContextCompactor compactor;
    private final JevGrader grader;
    private final LayeredMemoryService memory;
    private final TraceRecorder traceRecorder;

    public AgentOrchestrator(JevGuardrail guardrail,
                             JevRouter router,
                             LlmGateway llmGateway,
                             McpGateway mcpGateway,
                             JevTrajectoryVerifier verifier,
                             JevContextCompactor compactor,
                             JevGrader grader,
                             LayeredMemoryService memory,
                             TraceRecorder traceRecorder) {
        this.guardrail = guardrail;
        this.router = router;
        this.llmGateway = llmGateway;
        this.mcpGateway = mcpGateway;
        this.verifier = verifier;
        this.compactor = compactor;
        this.grader = grader;
        this.memory = memory;
        this.traceRecorder = traceRecorder;
    }

    public AgentResponse run(AgentRequest request) {
        String tenant = request.tenantId();
        String session = request.sessionId() == null ? "default" : request.sessionId();
        var trace = traceRecorder.newTrace();
        Map<String, Object> metrics = new HashMap<>();

        // 1. request-side guardrail
        GuardrailResult guard = guardrail.check(tenant, request.goal());
        trace.add("guardrail", guard.reason(), "NOUL", guard.riskScore(), 0L);
        if (!guard.allowed()) {
            metrics.put("blocked", true);
            metrics.put("riskScore", guard.riskScore());
            return new AgentResponse(session, "BLOCKED", null, null, trace.steps(), metrics);
        }

        memory.recordEpisode(tenant, session, "goal: " + request.goal(), 0.9);

        List<String> history = new ArrayList<>();
        String answer = null;
        String tier = null;
        int steps = 0;

        for (int i = 0; i < MAX_STEPS; i++) {
            steps = i + 1;

            // 2. route by difficulty
            TaskRoutingDecision routing = router.route(request.goal(), String.join("\n", history));
            tier = routing.tier().name();
            trace.add("route", routing.reason(), "SCORE", routing.difficulty(), 0L);

            // 3. act: generate the next step through the LLM gateway
            String prompt = buildPrompt(request, history);
            LlmResponse llm = llmGateway.complete(
                    new LlmRequest(tenant, session, prompt, "You are a helpful agent.", 512, Map.of()));
            answer = llm.content();
            trace.add("act", "model=" + llm.model() + (llm.fallback() ? " (fallback)" : ""),
                    null, null, llm.latencyMs());

            // 4. tool: invoke a tenant-visible MCP tool (deterministic reference behaviour)
            List<McpTool> tools = mcpGateway.discover(tenant, request.tools());
            if (!tools.isEmpty()) {
                McpTool tool = tools.get(i % tools.size());
                McpToolResult toolResult = mcpGateway.invoke(tenant, tool,
                        Map.of("goal", request.goal(), "step", i));
                trace.add("tool", tool.name() + " -> " + toolResult.success(),
                        null, null, toolResult.latencyMs());
                history.add("tool:" + tool.name() + "=" + toolResult.output());
            } else {
                history.add("step-" + i + ":" + answer);
            }

            // 5. verify: are we done? The "progress i/N" marker lets even the offline
            //    fallback (and any context-compacted observation) judge convergence.
            String observation = "progress " + (i + 1) + "/" + MAX_STEPS + "\n" + String.join("\n", history);
            TrajectoryVerdict verdict = verifier.verify(request.goal(), observation);
            trace.add("verify", verdict.reason(), "NOUL", verdict.successProb(), 0L);
            if (verdict.completed()) {
                break;
            }

            // 6. compact: keep only the relevant context for the next iteration
            CompactionResult compaction = compactor.compact(request.goal(), history);
            history = new ArrayList<>(compaction.kept());
            metrics.put("compactionSavedTokens",
                    ((Number) metrics.getOrDefault("compactionSavedTokens", 0L)).longValue() + compaction.savedTokens());
        }

        // 7. grade: eval as a release gate, per request
        GradingResult grading = grader.grade(request.goal(), answer);
        trace.add("grade", "score=%.3f pass=%s".formatted(grading.score(), grading.pass()),
                "SCORE", grading.score(), 0L);

        metrics.put("steps", steps);
        metrics.put("gradingScore", grading.score());
        metrics.put("gradingConfidence", grading.confidence());

        return new AgentResponse(session, "COMPLETED", answer, tier, trace.steps(), metrics);
    }

    private String buildPrompt(AgentRequest request, List<String> history) {
        StringBuilder sb = new StringBuilder();
        sb.append("Goal: ").append(request.goal()).append('\n');
        if (!history.isEmpty()) {
            sb.append("Progress so far:\n");
            for (String h : history) {
                sb.append("- ").append(h).append('\n');
            }
        }
        sb.append("Produce the next best response or final answer. Be concise.");
        return sb.toString();
    }
}
