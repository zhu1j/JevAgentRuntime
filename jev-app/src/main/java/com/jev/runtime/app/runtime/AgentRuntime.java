package com.jev.runtime.app.runtime;

import com.jev.runtime.common.AgentRequest;
import com.jev.runtime.common.AgentResponse;

/**
 * Pluggable agent execution engine behind the gateway.
 *
 * <p>The runtime is deliberately an interface so the execution engine stays
 * replaceable: {@code local} runs the built-in Jev-driven ReAct loop, while
 * {@code agentscope} delegates the agent lifecycle (session, memory, workspace,
 * tool calling) to AgentScope's HarnessAgent. In both modes this project keeps
 * owning the differentiating layers: the Jev decision layer, gateway governance
 * and observability.</p>
 */
public interface AgentRuntime {

    AgentResponse run(AgentRequest request);

    /** Engine identifier surfaced in responses and metrics, e.g. local / agentscope. */
    String engine();
}
