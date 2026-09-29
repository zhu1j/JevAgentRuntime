package com.jev.runtime.llm;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Fixed-window per-tenant rate limiter.
 * Keeps a noisy tenant from exhausting shared model capacity. Resilience4j's
 * RateLimiter is used in Spring-managed deployments; this lightweight guard is
 * dependency-free and adequate for the reference implementation.
 */
public class TenantRateLimiter {

    private final int permitsPerWindow;
    private final long windowMillis;
    private final ConcurrentHashMap<String, Window> windows = new ConcurrentHashMap<>();

    public TenantRateLimiter(int permitsPerWindow, long windowMillis) {
        this.permitsPerWindow = permitsPerWindow;
        this.windowMillis = windowMillis;
    }

    public boolean tryAcquire(String tenantId) {
        Window w = windows.compute(tenantId, (k, existing) -> {
            long now = System.currentTimeMillis();
            if (existing == null || now - existing.start >= windowMillis) {
                return new Window(now);
            }
            return existing;
        });
        return w.count.getAndIncrement() < permitsPerWindow;
    }

    private static final class Window {
        final long start;
        final AtomicInteger count = new AtomicInteger(0);
        Window(long start) { this.start = start; }
    }
}
