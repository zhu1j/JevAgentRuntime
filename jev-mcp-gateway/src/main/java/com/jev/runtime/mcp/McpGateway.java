package com.jev.runtime.mcp;

import com.jev.runtime.mcp.McpTool;
import com.jev.runtime.observability.AgentMetrics;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * The MCP Gateway — the only path through which agents reach tools.
 *
 * <p>It enforces two invariants that matter for enterprise adoption:</p>
 * <ul>
 *   <li><b>Discovery scoping</b>: a tenant only ever sees tools it is allowed to call.</li>
 *   <li><b>Permission boundary</b>: invocation is re-checked even if a name leaks,
 *       so a prompt-injected tool name cannot cross tenants.</li>
 * </ul>
 */
@Service
public class McpGateway {

    private final McpRegistry registry;
    private final McpExecutor executor;
    private final AgentMetrics metrics;

    public McpGateway(McpRegistry registry, McpExecutor executor, AgentMetrics metrics) {
        this.registry = registry;
        this.executor = executor;
        this.metrics = metrics;
    }

    /** Tools visible to the tenant, respecting the request's allow-list. */
    public List<McpTool> discover(String tenantId, List<String> requested) {
        List<McpTool> visible = registry.discover(tenantId);
        if (requested == null || requested.isEmpty()) {
            return visible;
        }
        return visible.stream().filter(t -> requested.contains(t.name())).toList();
    }

    /** Invoke a tool with tenant permission enforcement. */
    public McpToolResult invoke(String tenantId, McpTool tool, Map<String, Object> arguments) {
        if (tool == null) {
            return new McpToolResult(null, false, null, 0, "tool not found");
        }
        if (!tool.allows(tenantId)) {
            metrics.recordGuardrailBlock(tenantId, "mcp_permission_denied");
            return new McpToolResult(tool.name(), false, null, 0,
                    "tenant '" + tenantId + "' is not permitted to call " + tool.name());
        }
        return executor.execute(tool, arguments);
    }
}
