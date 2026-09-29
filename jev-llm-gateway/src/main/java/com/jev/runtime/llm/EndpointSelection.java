package com.jev.runtime.llm;

import com.jev.runtime.common.ModelTier;

/**
 * Internal selection result: chosen endpoint plus the fallback chain.
 */
public record EndpointSelection(
        ModelEndpoint primary,
        java.util.List<ModelEndpoint> fallbacks,
        ModelTier tier
) {}
