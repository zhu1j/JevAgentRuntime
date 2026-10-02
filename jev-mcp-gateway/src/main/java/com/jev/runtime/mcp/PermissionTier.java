package com.jev.runtime.mcp;

/**
 * Execution risk tier of a tool. The MCP gateway enforces an execution policy
 * per tier so that a prompt-driven agent can never silently perform write or
 * dangerous operations on real business backends.
 */
public enum PermissionTier {
    /** Read-only and side-effect free: executed automatically. */
    READ,
    /** State-changing but business-safe / reversible: executed and audited. */
    WRITE,
    /** Irreversible or high-impact (refund, delete, cancel-paid): requires explicit human confirmation. */
    DANGEROUS
}
