package com.jev.runtime.memory;

import java.util.List;

/**
 * Tenant- and session-scoped memory store.
 * Implementations must never leak records across tenants.
 */
public interface MemoryStore {

    void put(MemoryRecord record);

    /** Retrieve the most salient N records for (tenant, session, tier). */
    List<MemoryRecord> top(String tenantId, String sessionId, MemoryTier tier, int n);

    /** Retrieve the most salient N semantic facts for a tenant. */
    List<MemoryRecord> topSemantic(String tenantId, int n);

    int size(String tenantId, String sessionId, MemoryTier tier);
}
