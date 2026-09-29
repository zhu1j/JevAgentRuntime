package com.jev.runtime.a2a;

import java.util.List;

/**
 * An A2A "Agent Card" — the self-describing document another agent reads to
 * discover capabilities and the endpoint to delegate to. Mirrors the A2A spec
 * subset the runtime needs.
 */
public record AgentCard(
        String name,
        String description,
        String endpoint,
        String version,
        List<String> skills,
        boolean streaming
) {}
