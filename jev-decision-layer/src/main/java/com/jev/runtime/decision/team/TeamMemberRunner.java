package com.jev.runtime.decision.team;

/**
 * SPI that executes one sub-task for a chosen team member and returns its text
 * answer. Implemented by the application layer (single-agent orchestrator as a
 * member) and/or by A2A adapters (elastic remote peers). The leader pipeline
 * depends only on this interface, keeping the decision layer free of any
 * dependency on the orchestration / A2A modules (no circular Maven deps).
 */
@FunctionalInterface
public interface TeamMemberRunner {

    /**
     * Execute {@code subGoal} as {@code memberId} and return the member's answer text.
     */
    String run(String tenantId, String sessionId, String memberId, String subGoal);
}
