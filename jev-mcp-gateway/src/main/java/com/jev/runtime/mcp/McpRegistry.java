package com.jev.runtime.mcp;

import java.util.List;

/**
 * MCP tool registry with tenant-scoped discovery.
 */
public interface McpRegistry {

    void register(McpTool tool);

    /** All tools visible to a tenant. */
    List<McpTool> discover(String tenantId);

    McpTool find(String name);
}
