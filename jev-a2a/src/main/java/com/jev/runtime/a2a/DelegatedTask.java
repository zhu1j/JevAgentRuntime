package com.jev.runtime.a2a;

import java.util.List;
import java.util.Map;

/**
 * A task delegated to a remote agent over A2A.
 */
public record DelegatedTask(
        String taskId,
        String fromAgent,
        String toAgent,
        String goal,
        Map<String, Object> payload
) {
    public DelegatedTask {
        if (payload == null) payload = Map.of();
    }
}
