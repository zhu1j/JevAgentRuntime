package com.jev.runtime.mcp;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory MCP registry. In production this mirrors a Nacos MCP registry so
 * MCP servers can register/discover dynamically across the fleet.
 */
@Component
public class InMemoryMcpRegistry implements McpRegistry {

    private final Map<String, McpTool> tools = new ConcurrentHashMap<>();

    public InMemoryMcpRegistry() {
        // seed a few representative enterprise tools
        register(new McpTool("order.query", "Query an order by id",
                Map.of("orderId", "string"), "platform", List.of("*")));
        register(new McpTool("kb.search", "Search the internal knowledge base",
                Map.of("query", "string", "topK", "int"), "platform", List.of("*")));
        register(new McpTool("billing.refund", "Issue a refund for an order",
                Map.of("orderId", "string", "amount", "number"), "finance", List.of("finance", "support")));
        register(new McpTool("repo.grep", "Search a code repository",
                Map.of("repo", "string", "pattern", "string"), "dev", List.of("dev")));
    }

    @Override
    public void register(McpTool tool) {
        tools.put(tool.name(), tool);
    }

    @Override
    public List<McpTool> discover(String tenantId) {
        return tools.values().stream().filter(t -> t.allows(tenantId)).toList();
    }

    @Override
    public McpTool find(String name) {
        return tools.get(name);
    }
}
