package com.jev.runtime.common.audit;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Bounded, thread-safe in-memory decision audit log (ring buffer, most recent
 * 1000 decisions retained).
 *
 * <p>In a production deployment these records would stream to Kafka/OLAP for
 * offline eval replay; the bounded in-memory buffer keeps the platform
 * self-contained while still powering the {@code /api/audit} endpoints.</p>
 */
public class DecisionAuditLog {

    private static final int CAPACITY = 1_000;

    private final ConcurrentLinkedDeque<DecisionAuditRecord> records = new ConcurrentLinkedDeque<>();
    private final AtomicLong total = new AtomicLong();
    private final AtomicLong fallbacks = new AtomicLong();
    private final AtomicLong chaosEvents = new AtomicLong();

    public void record(DecisionAuditRecord record) {
        records.addLast(record);
        total.incrementAndGet();
        if (record.fallback()) {
            fallbacks.incrementAndGet();
        }
        if (record.chaos()) {
            chaosEvents.incrementAndGet();
        }
        while (records.size() > CAPACITY) {
            records.pollFirst();
        }
    }

    /** Most recent first, capped at {@code limit}. */
    public List<DecisionAuditRecord> recent(int limit) {
        List<DecisionAuditRecord> out = new ArrayList<>(Math.min(limit, records.size()));
        var it = records.descendingIterator();
        while (it.hasNext() && out.size() < limit) {
            out.add(it.next());
        }
        return out;
    }

    public Map<String, Object> stats() {
        long t = total.get();
        return Map.of(
                "total", t,
                "fallbacks", fallbacks.get(),
                "chaosEvents", chaosEvents.get(),
                "fallbackRate", t == 0 ? 0.0 : (double) fallbacks.get() / t,
                "retained", records.size());
    }
}
