package com.jev.runtime.app.runtime;

import com.jev.runtime.app.AgentOrchestrator;
import com.jev.runtime.common.AgentRequest;
import com.jev.runtime.common.AgentResponse;

/**
 * Default engine: the built-in Jev-driven single-agent loop
 * (self-contained, runs fully offline).
 */
public class LocalAgentRuntime implements AgentRuntime {

    private final AgentOrchestrator orchestrator;

    public LocalAgentRuntime(AgentOrchestrator orchestrator) {
        this.orchestrator = orchestrator;
    }

    @Override
    public AgentResponse run(AgentRequest request) {
        return orchestrator.run(request);
    }

    @Override
    public String engine() {
        return "local";
    }
}
