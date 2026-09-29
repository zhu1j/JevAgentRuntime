package com.jev.runtime.memory;

/** The three memory tiers of the agent runtime. */
public enum MemoryTier {
    /** Short-lived scratch state for the current run. */
    WORKING,
    /** Per-session event history (what happened). */
    EPISODIC,
    /** Long-lived distilled facts (what we know). */
    SEMANTIC
}
