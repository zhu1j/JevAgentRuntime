package com.jev.runtime.app.runtime;

import com.jev.runtime.app.AgentOrchestrator;
import com.jev.runtime.common.AgentRequest;
import com.jev.runtime.common.AgentResponse;
import com.jev.runtime.decision.GuardrailResult;
import com.jev.runtime.decision.GradingResult;
import com.jev.runtime.decision.JevGrader;
import com.jev.runtime.decision.JevGuardrail;
import com.jev.runtime.observability.TraceRecorder;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.message.Msg;
import io.agentscope.harness.agent.HarnessAgent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Paths;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

/**
 * AgentScope execution engine.
 *
 * <p>The agent's lifecycle (ReAct loop, session persistence, workspace, memory,
 * tool calling) is delegated to AgentScope's {@link HarnessAgent}, while this
 * project deliberately keeps the differentiating layers in-house:</p>
 *
 * <ul>
 *   <li><b>Jev guardrail</b> screens every request before it reaches the agent;</li>
 *   <li><b>Jev grading</b> scores every response (eval-as-gate);</li>
 *   <li>the <b>trace + decision audit</b> keep the whole run observable;</li>
 *   <li>any AgentScope failure (missing credentials, model error, timeout)
 *       degrades transparently to the local engine — the gateway never dies
 *       together with its runtime.</li>
 * </ul>
 */
public class AgentScopeAgentRuntime implements AgentRuntime {

    private static final Logger log = LoggerFactory.getLogger(AgentScopeAgentRuntime.class);

    private final AgentScopeProperties properties;
    private final JevGuardrail guardrail;
    private final JevGrader grader;
    private final TraceRecorder traceRecorder;
    private final AgentOrchestrator localFallback;
    private volatile HarnessAgent agent;

    public AgentScopeAgentRuntime(AgentScopeProperties properties,
                                  JevGuardrail guardrail,
                                  JevGrader grader,
                                  TraceRecorder traceRecorder,
                                  AgentOrchestrator localFallback) {
        this.properties = properties;
        this.guardrail = guardrail;
        this.grader = grader;
        this.traceRecorder = traceRecorder;
        this.localFallback = localFallback;
    }

    @Override
    public AgentResponse run(AgentRequest request) {
        String tenant = request.tenantId();
        String session = request.sessionId() == null ? "default" : request.sessionId();
        var trace = traceRecorder.newTrace();
        Map<String, Object> metrics = new HashMap<>();

        // the Jev governance layer stays in front of ANY execution engine
        GuardrailResult guard = guardrail.check(tenant, request.goal());
        trace.add("guardrail", guard.reason(), "NOUL", guard.riskScore(), 0L);
        if (!guard.allowed()) {
            metrics.put("blocked", true);
            metrics.put("riskScore", guard.riskScore());
            metrics.put("engine", engine());
            return new AgentResponse(session, "BLOCKED", null, null, trace.steps(), metrics);
        }

        try {
            long start = System.nanoTime();
            // NOTE: RuntimeContext only exposes builder(RuntimeContext) — start from empty()
            RuntimeContext ctx = RuntimeContext.builder(RuntimeContext.empty())
                    .sessionId(session)
                    .userId(tenant)
                    .build();
            Msg reply = agent().call(request.goal(), ctx)
                    .block(Duration.ofSeconds(properties.getTimeoutSeconds()));
            long latencyMs = (System.nanoTime() - start) / 1_000_000L;

            String answer = reply == null ? null : reply.getTextContent();
            trace.add("agentscope.call", "model=" + properties.getModel(), null, null, latencyMs);

            GradingResult grading = grader.grade(request.goal(), answer);
            trace.add("grade", "score=%.3f pass=%s".formatted(grading.score(), grading.pass()),
                    "SCORE", grading.score(), 0L);

            metrics.put("engine", engine());
            metrics.put("gradingScore", grading.score());
            metrics.put("gradingConfidence", grading.confidence());
            return new AgentResponse(session, "COMPLETED", answer, null, trace.steps(), metrics);
        } catch (RuntimeException ex) {
            // the runtime adapter must never let an engine failure kill the request
            log.warn("AgentScope engine failed ({}), degrading to the local engine", ex.getMessage());
            AgentResponse degraded = localFallback.run(request);
            degraded.metrics().put("engine", engine() + "->local-fallback");
            degraded.metrics().put("fallbackReason", ex.getClass().getSimpleName());
            return degraded;
        }
    }

    /** Lazily build the harness agent (double-checked locking for the heavy builder). */
    private HarnessAgent agent() {
        HarnessAgent a = agent;
        if (a == null) {
            synchronized (this) {
                a = agent;
                if (a == null) {
                    a = HarnessAgent.builder()
                            .name(properties.getAgentName())
                            .sysPrompt(properties.getSysPrompt())
                            .model(properties.getModel())
                            .workspace(Paths.get(properties.getWorkspace()))
                            .build();
                    agent = a;
                }
            }
        }
        return a;
    }

    @Override
    public String engine() {
        return "agentscope";
    }
}
