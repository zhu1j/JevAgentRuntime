package com.jev.runtime.common;

/**
 * LLM capability tiers used by the Jev-driven router.
 * The router maps an inferred task difficulty (Jev SCORE) onto a tier.
 */
public enum ModelTier {
    /** Cheapest, fastest model for trivial / retrieval-only steps. */
    SMALL,
    /** Balanced default for the majority of agent steps. */
    MEDIUM,
    /** Most capable (and most expensive) model for long-horizon planning / hard reasoning. */
    LARGE
}
