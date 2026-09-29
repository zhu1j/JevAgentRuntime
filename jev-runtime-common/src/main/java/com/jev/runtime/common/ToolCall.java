package com.jev.runtime.common;

import java.util.Map;

/**
 * A normalized tool invocation. Tool names are resolved through the MCP gateway,
 * which enforces tenant scoping and permission boundaries.
 */
public record ToolCall(
        String name,
        Map<String, Object> arguments
) {}
