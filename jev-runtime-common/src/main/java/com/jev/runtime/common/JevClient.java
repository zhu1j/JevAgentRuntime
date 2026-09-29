package com.jev.runtime.common;

/**
 * Abstraction over the Jev "System One" decision backend.
 * Implementations must be non-blocking and safe for concurrent use.
 */
public interface JevClient {

    /** Evaluate a single (single-primitive) decision request. */
    JevDecision decide(JevRequest request);

    /** Backend identifier, e.g. {@code remote} or {@code local-fallback}. */
    String backend();

    /** Whether the backend is currently reachable/healthy. */
    default boolean healthy() {
        return true;
    }
}
