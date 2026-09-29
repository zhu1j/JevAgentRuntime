package com.jev.runtime.llm;

import com.jev.runtime.common.ModelTier;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Picks a primary endpoint for a tier and an ordered fallback chain.
 * Weight-aware: the highest-weight healthy endpoint leads, the rest follow as
 * fallbacks so a circuit-breaker trip degrades gracefully.
 */
@Component
public class EndpointSelector {

    private final ModelRegistry registry;

    public EndpointSelector(ModelRegistry registry) {
        this.registry = registry;
    }

    public EndpointSelection select(ModelTier tier) {
        List<ModelEndpoint> candidates = new ArrayList<>(registry.byTier(tier));
        if (candidates.isEmpty()) {
            throw new GatewayException("no model endpoint registered for tier " + tier);
        }
        ModelEndpoint primary = candidates.get(0);
        List<ModelEndpoint> fallbacks = candidates.size() > 1
                ? List.copyOf(candidates.subList(1, candidates.size()))
                : List.of();
        return new EndpointSelection(primary, fallbacks, tier);
    }
}
