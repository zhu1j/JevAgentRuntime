package com.jev.runtime.mcp;

import com.jev.runtime.observability.AgentMetrics;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Tool permission tiers: READ auto, WRITE executes, DANGEROUS needs human confirmation. */
class McpGatewayPermissionTest {

    private final McpRegistry registry = new InMemoryMcpRegistry();
    private final McpGateway gateway = new McpGateway(registry, new SimulatedMcpExecutor(),
            new AgentMetrics(new SimpleMeterRegistry()));

    private McpTool tool(String name) {
        McpTool tool = registry.find(name);
        assertNotNull(tool, "seeded tool should exist: " + name);
        return tool;
    }

    @Test
    void readToolExecutesAutomatically() {
        McpToolResult result = gateway.invoke("shop", tool("seckill.queryStock"), Map.of("activityId", "10001"));
        assertTrue(result.success(), "READ tool should auto-execute");
    }

    @Test
    void writeToolExecutesForPermittedTenant() {
        McpToolResult result = gateway.invoke("shop", tool("shop.cancelOrder"), Map.of("orderId", "9527"));
        assertTrue(result.success(), "WRITE tool should execute for the owning tenant");
    }

    @Test
    void dangerousToolIsBlockedWithoutHumanConfirmation() {
        McpToolResult result = gateway.invoke("finance", tool("billing.refund"),
                Map.of("orderId", "9527", "amount", 10.0));
        assertFalse(result.success(), "DANGEROUS tool must not run on the agent's word alone");
        assertTrue(result.error().contains("confirm"), "error should explain the confirmation gate");
    }

    @Test
    void dangerousToolExecutesAfterHumanConfirmation() {
        Map<String, Object> args = new HashMap<>();
        args.put("orderId", "9527");
        args.put("amount", 10.0);
        args.put("confirmDangerous", true);
        McpToolResult result = gateway.invoke("finance", tool("billing.refund"), args);
        assertTrue(result.success(), "DANGEROUS tool executes after explicit confirmation");
    }

    @Test
    void tenantIsolationStillAppliesBeforeTierGate() {
        McpToolResult result = gateway.invoke("dev", tool("billing.refund"), Map.of("confirmDangerous", true));
        assertFalse(result.success(), "a non-permitted tenant is denied regardless of confirmation");
    }
}
