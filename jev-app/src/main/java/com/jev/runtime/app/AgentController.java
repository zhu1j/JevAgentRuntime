package com.jev.runtime.app;

import com.jev.runtime.a2a.A2aDirectory;
import com.jev.runtime.a2a.AgentCard;
import com.jev.runtime.app.runtime.AgentRuntime;
import com.jev.runtime.common.AgentRequest;
import com.jev.runtime.common.AgentResponse;
import com.jev.runtime.common.audit.DecisionAuditLog;
import com.jev.runtime.common.audit.DecisionAuditRecord;
import com.jev.runtime.eval.EvalCase;
import com.jev.runtime.eval.EvalDataset;
import com.jev.runtime.eval.EvalReport;
import com.jev.runtime.eval.EvalRunner;
import com.jev.runtime.llm.LlmGateway;
import com.jev.runtime.llm.ModelEndpoint;
import com.jev.runtime.mcp.McpGateway;
import com.jev.runtime.mcp.McpTool;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * Public HTTP surface of the agent runtime: run an agent (through the pluggable
 * {@link AgentRuntime} engine), inspect the model fleet, discover tenant tools,
 * register A2A peers, read the decision audit log, run the benchmark and the
 * evaluation gate.
 */
@RestController
@RequestMapping("/api")
public class AgentController {

    private final AgentRuntime agentRuntime;
    private final LlmGateway llmGateway;
    private final McpGateway mcpGateway;
    private final A2aDirectory a2aDirectory;
    private final EvalRunner evalRunner;
    private final BenchmarkService benchmarkService;
    private final DecisionAuditLog auditLog;

    public AgentController(AgentRuntime agentRuntime,
                           LlmGateway llmGateway,
                           McpGateway mcpGateway,
                           A2aDirectory a2aDirectory,
                           EvalRunner evalRunner,
                           BenchmarkService benchmarkService,
                           DecisionAuditLog auditLog) {
        this.agentRuntime = agentRuntime;
        this.llmGateway = llmGateway;
        this.mcpGateway = mcpGateway;
        this.a2aDirectory = a2aDirectory;
        this.evalRunner = evalRunner;
        this.benchmarkService = benchmarkService;
        this.auditLog = auditLog;
    }

    /** Run an agent task through the configured execution engine. */
    @PostMapping("/agent/run")
    public AgentResponse run(@Valid @RequestBody AgentRequest request) {
        return agentRuntime.run(request);
    }

    /** The currently active execution engine (local | agentscope). */
    @GetMapping("/runtime")
    public Map<String, String> runtime() {
        return Map.of("engine", agentRuntime.engine());
    }

    /** List the registered model endpoints (the routing fleet). */
    @GetMapping("/models")
    public List<ModelEndpoint> models() {
        return llmGateway.registry().all();
    }

    /** Discover the MCP tools visible to a tenant. */
    @GetMapping("/tools")
    public List<McpTool> tools(String tenantId) {
        return mcpGateway.discover(tenantId == null ? "public" : tenantId, List.of());
    }

    /** Register an A2A peer agent card. */
    @PostMapping("/a2a/agents")
    public ResponseEntity<AgentCard> registerAgent(@RequestBody AgentCard card) {
        a2aDirectory.register(card);
        return ResponseEntity.ok(card);
    }

    /** List known A2A peers. */
    @GetMapping("/a2a/agents")
    public List<AgentCard> agents() {
        return a2aDirectory.list();
    }

    /** Decision audit log: most recent Jev decisions (for replay / debugging / eval). */
    @GetMapping("/audit/decisions")
    public List<DecisionAuditRecord> auditDecisions(@RequestParam(defaultValue = "50") int limit) {
        return auditLog.recent(Math.max(1, Math.min(limit, 500)));
    }

    /** Decision audit statistics: totals, fallback rate, chaos events. */
    @GetMapping("/audit/stats")
    public Map<String, Object> auditStats() {
        return auditLog.stats();
    }

    /**
     * A/B benchmark: Jev structured decision vs. LLM text round-trip for the
     * same bounded task (tool selection). Returns P50/P95, speed-up and cost.
     */
    @PostMapping("/benchmark/tool-selection")
    public Map<String, Object> benchmark(@RequestParam(defaultValue = "20") int iterations) {
        return benchmarkService.runToolSelectionBenchmark(iterations);
    }

    /**
     * Run the evaluation gate against the live agent.
     * Returns the pass-rate that decides whether a change is safe to ship.
     */
    @PostMapping("/eval/run")
    public EvalReport eval() {
        EvalDataset dataset = new EvalDataset() {
            @Override
            public String name() {
                return "smoke";
            }

            @Override
            public List<EvalCase> cases() {
                // Smoke thresholds are calibrated to the OFFLINE grader baseline
                // (the local heuristic scores competent answers ~0.5-0.7). When the
                // real Jev model is wired in, re-calibrate these to its scale.
                return List.of(
                        new EvalCase("c1", "Summarize the onboarding policy", "concise summary", 0.45),
                        new EvalCase("c2", "Query order 123", "order details", 0.45),
                        new EvalCase("c3", "Refund order 123", "refund confirmation", 0.45));
            }

            @Override
            public java.util.function.Function<String, String> agent() {
                return goal -> agentRuntime.run(
                        new AgentRequest("eval-tenant", "eval-session", goal, List.of(), Map.of())).answer();
            }
        };
        return evalRunner.run(dataset);
    }
}
