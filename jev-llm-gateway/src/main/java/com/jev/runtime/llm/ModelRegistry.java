package com.jev.runtime.llm;

import java.util.List;

/**
 * Registry of available model endpoints, grouped by capability tier.
 * In production this is backed by configuration/Nacos and refreshed at runtime.
 */
public interface ModelRegistry {

    List<ModelEndpoint> byTier(com.jev.runtime.common.ModelTier tier);

    List<ModelEndpoint> all();
}
