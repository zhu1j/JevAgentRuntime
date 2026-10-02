package com.jev.runtime.app.runtime;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration for the AgentScope HarnessAgent execution engine
 * (active when {@code runtime.engine=agentscope}).
 */
@ConfigurationProperties(prefix = "runtime.agentscope")
public class AgentScopeProperties {

    /** Agent name inside the AgentScope workspace. */
    private String agentName = "jev-agent";
    /** System prompt / persona for the harness agent. */
    private String sysPrompt =
            "You are an enterprise agent running behind the Jev governance gateway. Be concise.";
    /** Model id resolved by AgentScope's ModelRegistry, e.g. dashscope:qwen-plus
     *  (requires the matching provider API key, e.g. DASHSCOPE_API_KEY). */
    private String model = "dashscope:qwen-plus";
    /** Workspace directory used by HarnessAgent for persona / memory / session state. */
    private String workspace = ".agentscope/workspace";
    /** Max seconds to wait for one agent call before degrading to the local engine. */
    private long timeoutSeconds = 120;

    public String getAgentName() {
        return agentName;
    }

    public void setAgentName(String agentName) {
        this.agentName = agentName;
    }

    public String getSysPrompt() {
        return sysPrompt;
    }

    public void setSysPrompt(String sysPrompt) {
        this.sysPrompt = sysPrompt;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public String getWorkspace() {
        return workspace;
    }

    public void setWorkspace(String workspace) {
        this.workspace = workspace;
    }

    public long getTimeoutSeconds() {
        return timeoutSeconds;
    }

    public void setTimeoutSeconds(long timeoutSeconds) {
        this.timeoutSeconds = timeoutSeconds;
    }
}
