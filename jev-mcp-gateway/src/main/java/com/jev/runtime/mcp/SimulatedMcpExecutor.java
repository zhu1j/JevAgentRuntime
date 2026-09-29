package com.jev.runtime.mcp;

import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Deterministic offline executor. Echoes the tool + arguments so the whole
 * runtime can be exercised without a live MCP server fleet.
 */
@Component
public class SimulatedMcpExecutor implements McpExecutor {

    @Override
    public McpToolResult execute(McpTool tool, Map<String, Object> arguments) {
        long start = System.nanoTime();
        String output = "[%s] executed with %s".formatted(tool.name(), arguments);
        long latencyMs = (System.nanoTime() - start) / 1_000_000L;
        return new McpToolResult(tool.name(), true, output, latencyMs, null);
    }
}
