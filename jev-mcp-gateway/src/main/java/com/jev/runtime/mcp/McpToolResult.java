package com.jev.runtime.mcp;

import java.util.Map;

/**
 * Result of an MCP tool invocation.
 */
public record McpToolResult(
        String tool,
        boolean success,
        String output,
        long latencyMs,
        String error
) {}
