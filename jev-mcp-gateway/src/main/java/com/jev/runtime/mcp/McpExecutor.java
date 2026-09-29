package com.jev.runtime.mcp;

import java.util.Map;

/**
 * Executes a tool. Real implementations translate to MCP JSON-RPC calls to the
 * owning MCP server; the reference implementation returns a synthetic result.
 */
public interface McpExecutor {

    McpToolResult execute(McpTool tool, Map<String, Object> arguments);
}
