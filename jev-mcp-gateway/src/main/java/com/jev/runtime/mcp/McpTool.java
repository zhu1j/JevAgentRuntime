package com.jev.runtime.mcp;

import java.util.List;
import java.util.Map;

/**
 * A tool exposed through the MCP gateway.
 *
 * @param name        tool name (globally unique within the registry)
 * @param description natural-language description used by the planner
 * @param inputSchema JSON-schema-like description of arguments
 * @param owner       the tenant/team that publishes the tool
 * @param allowedTenants tenants permitted to invoke it ("*" = public)
 * @param permission  execution risk tier; DANGEROUS tools require human confirmation
 */
public record McpTool(
        String name,
        String description,
        Map<String, Object> inputSchema,
        String owner,
        List<String> allowedTenants,
        PermissionTier permission
) {
    public boolean allows(String tenantId) {
        return allowedTenants.contains("*") || allowedTenants.contains(tenantId);
    }
}
