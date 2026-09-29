package com.jev.runtime.llm;

import com.jev.runtime.common.ModelTier;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Default in-memory model registry seeded with representative endpoints per tier.
 * Replace the seed data from configuration for real deployments.
 */
@Component
public class InMemoryModelRegistry implements ModelRegistry {

    private final Map<ModelTier, List<ModelEndpoint>> byTier = new ConcurrentHashMap<>();

    public InMemoryModelRegistry() {
        register(new ModelEndpoint("qwen", "qwen-turbo", ModelTier.SMALL, 0.0000003, 0.0000006, 10));
        register(new ModelEndpoint("deepseek", "deepseek-chat", ModelTier.MEDIUM, 0.0000005, 0.0000015, 8));
        register(new ModelEndpoint("openai", "gpt-4o-mini", ModelTier.MEDIUM, 0.00000015, 0.0000006, 9));
        register(new ModelEndpoint("openai", "gpt-4o", ModelTier.LARGE, 0.0000025, 0.00001, 7));
        register(new ModelEndpoint("anthropic", "claude-3-5-sonnet", ModelTier.LARGE, 0.000003, 0.000015, 8));
    }

    private void register(ModelEndpoint e) {
        byTier.compute(e.tier(), (k, list) -> {
            var l = list == null ? new java.util.ArrayList<ModelEndpoint>() : new java.util.ArrayList<>(list);
            l.add(e);
            l.sort(Comparator.comparingInt(ModelEndpoint::weight).reversed());
            return l;
        });
    }

    @Override
    public List<ModelEndpoint> byTier(ModelTier tier) {
        return List.copyOf(byTier.getOrDefault(tier, List.of()));
    }

    @Override
    public List<ModelEndpoint> all() {
        return byTier.values().stream().flatMap(List::stream).toList();
    }
}
