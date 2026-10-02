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
                Map.of("orderId", "string"), "platform", List.of("*"), PermissionTier.READ));
        register(new McpTool("kb.search", "Search the internal knowledge base",
                Map.of("query", "string", "topK", "int"), "platform", List.of("*"), PermissionTier.READ));
        register(new McpTool("billing.refund", "Issue a refund for an order",
                Map.of("orderId", "string", "amount", "number"), "finance", List.of("finance", "support"),
                PermissionTier.DANGEROUS));
        register(new McpTool("repo.grep", "Search a code repository",
                Map.of("repo", "string", "pattern", "string"), "dev", List.of("dev"), PermissionTier.READ));
        // business-line tools: the "shop" tenant is the seckill/order backend
        // (e.g. zhu-seckill) exposed to agents through this gateway
        register(new McpTool("seckill.queryStock", "Query real-time seckill stock (Redis vs DB)",
                Map.of("activityId", "string"), "shop", List.of("shop"), PermissionTier.READ));
        register(new McpTool("seckill.queryOrders", "Query recent seckill orders for an activity",
                Map.of("activityId", "string", "limit", "int"), "shop", List.of("shop"), PermissionTier.READ));
        register(new McpTool("shop.cancelOrder", "Cancel an unpaid shop order",
                Map.of("orderId", "string"), "shop", List.of("shop"), PermissionTier.WRITE));
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
