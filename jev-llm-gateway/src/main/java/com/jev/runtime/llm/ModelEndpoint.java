package com.jev.runtime.llm;

import com.jev.runtime.common.ModelTier;

/**
 * A concrete model endpoint registered with the gateway.
 *
 * @param provider   e.g. openai / anthropic / qwen / deepseek
 * @param model      provider model id
 * @param tier       capability tier this model serves
 * @param costPerInputTokenUsd  input price
 * @param costPerOutputTokenUsd output price
 * @param weight     relative preference within a tier (higher = preferred)
 */
public record ModelEndpoint(
        String provider,
        String model,
        ModelTier tier,
        double costPerInputTokenUsd,
        double costPerOutputTokenUsd,
        int weight
) {}
