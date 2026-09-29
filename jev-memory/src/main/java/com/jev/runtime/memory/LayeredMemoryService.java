package com.jev.runtime.memory;

import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

/**
 * Facade over the memory tiers. Provides the "context assembly" primitive:
 * working + recent episodic + top semantic memories are merged into a single
 * context block that the agent loop feeds to the model / compactor.
 */
@Service
public class LayeredMemoryService {

    private final MemoryStore store;

    public LayeredMemoryService(MemoryStore store) {
        this.store = store;
    }

    public void recordWorking(String tenant, String session, String content, double salience) {
        store.put(new MemoryRecord(tenant, session, MemoryTier.WORKING, content, salience, Instant.now()));
    }

    public void recordEpisode(String tenant, String session, String content, double salience) {
        store.put(new MemoryRecord(tenant, session, MemoryTier.EPISODIC, content, salience, Instant.now()));
    }

    public void recordFact(String tenant, String content, double salience) {
        store.put(new MemoryRecord(tenant, "_shared", MemoryTier.SEMANTIC, content, salience, Instant.now()));
    }

    /** Merge tiers into an assembled context string (most salient first). */
    public List<MemoryRecord> assemble(String tenant, String session, int workingN, int episodicN, int semanticN) {
        var merged = new java.util.ArrayList<MemoryRecord>();
        merged.addAll(store.top(tenant, session, MemoryTier.WORKING, workingN));
        merged.addAll(store.top(tenant, session, MemoryTier.EPISODIC, episodicN));
        merged.addAll(store.topSemantic(tenant, semanticN));
        return merged;
    }
}
