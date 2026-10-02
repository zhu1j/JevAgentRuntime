package com.jev.runtime.app.runtime;

import com.jev.runtime.app.AgentOrchestrator;
import com.jev.runtime.decision.JevGrader;
import com.jev.runtime.decision.JevGuardrail;
import com.jev.runtime.observability.TraceRecorder;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Selects the agent execution engine via {@code runtime.engine}:
 * {@code local} (default, fully self-contained) or {@code agentscope}
 * (delegates the agent lifecycle to AgentScope HarnessAgent; requires a model
 * provider API key). Exactly one {@link AgentRuntime} bean exists per profile,
 * so the controller layer never knows which engine is serving it.
 */
@Configuration
@EnableConfigurationProperties(AgentScopeProperties.class)
public class RuntimeConfiguration {

    @Bean
    @ConditionalOnProperty(name = "runtime.engine", havingValue = "local", matchIfMissing = true)
    public AgentRuntime localAgentRuntime(AgentOrchestrator orchestrator) {
        return new LocalAgentRuntime(orchestrator);
    }

    @Bean
    @ConditionalOnProperty(name = "runtime.engine", havingValue = "agentscope")
    public AgentRuntime agentScopeAgentRuntime(AgentScopeProperties properties,
                                               JevGuardrail guardrail,
                                               JevGrader grader,
                                               TraceRecorder traceRecorder,
                                               AgentOrchestrator orchestrator) {
        return new AgentScopeAgentRuntime(properties, guardrail, grader, traceRecorder, orchestrator);
    }
}
