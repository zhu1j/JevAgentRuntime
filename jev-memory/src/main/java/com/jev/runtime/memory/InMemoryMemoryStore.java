package com.jev.runtime.memory;

import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Concurrent, in-process memory store. Suitable for single-node deployments and
 * tests. A production deployment swaps this for Redis (working/episodic) plus a
 * vector store (semantic) behind the same {@link MemoryStore} contract.
 */
@Component
public class InMemoryMemoryStore implements MemoryStore {

    // key = tenant|session|tier ; value = copy-on-write list (lock-free reads, O(1) amortized append)
    private final Map<String, java.util.concurrent.CopyOnWriteArrayList<MemoryRecord>> store = new ConcurrentHashMap<>();

    private static String key(String tenant, String session, MemoryTier tier) {
        return tenant + "|" + (session == null ? "_" : session) + "|" + tier;
    }

    @Override
    public void put(MemoryRecord record) {
        String k = key(record.tenantId(), record.sessionId(), record.tier());
        store.computeIfAbsent(k, kk -> new java.util.concurrent.CopyOnWriteArrayList<>()).add(record);
    }

    @Override
    public List<MemoryRecord> top(String tenantId, String sessionId, MemoryTier tier, int n) {
        List<MemoryRecord> list = store.getOrDefault(key(tenantId, sessionId, tier), new java.util.concurrent.CopyOnWriteArrayList<>());
        return list.stream()
                .sorted(Comparator.comparingDouble(MemoryRecord::salience).reversed())
                .limit(n)
                .collect(Collectors.toList());
    }

    @Override
    public List<MemoryRecord> topSemantic(String tenantId, int n) {
        // semantic facts are shared across sessions of a tenant
        return store.entrySet().stream()
                .filter(e -> e.getKey().startsWith(tenantId + "|") && e.getKey().endsWith("|" + MemoryTier.SEMANTIC))
                .flatMap(e -> e.getValue().stream())
                .sorted(Comparator.comparingDouble(MemoryRecord::salience).reversed())
                .limit(n)
                .collect(Collectors.toList());
    }

    @Override
    public int size(String tenantId, String sessionId, MemoryTier tier) {
        var list = store.get(key(tenantId, sessionId, tier));
        return list == null ? 0 : list.size();
    }
}
