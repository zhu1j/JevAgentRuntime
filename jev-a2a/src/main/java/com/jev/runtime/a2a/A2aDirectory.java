package com.jev.runtime.a2a;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Service;

/**
 * Agent Card registry + delegation façade.
 *
 * <p>This is the elastic multi-agent seam: the core runtime is single-agent, but
 * when a task is better served by a specialist it can discover a peer agent's
 * card and delegate over A2A. In production the transport is A2A JSON-RPC; here
 * a deterministic in-process delegate stands in.</p>
 */
@Service
public class A2aDirectory {

    private final Map<String, AgentCard> cards = new ConcurrentHashMap<>();

    public void register(AgentCard card) {
        cards.put(card.name(), card);
    }

    public Optional<AgentCard> find(String name) {
        return Optional.ofNullable(cards.get(name));
    }

    public List<AgentCard> list() {
        return List.copyOf(cards.values());
    }

    /**
     * Delegate a task to a peer agent. Only attempted when a specialist is
     * registered; otherwise the core agent handles it locally.
     */
    public DelegatedResult delegate(String targetAgent, DelegatedTask task) {
        long start = System.nanoTime();
        AgentCard card = cards.get(targetAgent);
        long latency = (System.nanoTime() - start) / 1_000_000L;
        if (card == null) {
            return new DelegatedResult(task.taskId(), "not_found", null, latency);
        }
        String output = "[A2A -> %s] handled goal: %s".formatted(targetAgent, task.goal());
        return new DelegatedResult(task.taskId(), "completed", output, latency);
    }
}
