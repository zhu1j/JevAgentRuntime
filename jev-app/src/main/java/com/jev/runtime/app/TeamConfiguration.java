package com.jev.runtime.app;

import com.jev.runtime.common.AgentRequest;
import com.jev.runtime.common.AgentResponse;
import com.jev.runtime.common.JevClient;
import com.jev.runtime.common.JevTeamProperties;
import com.jev.runtime.decision.JevGrader;
import com.jev.runtime.decision.team.TeamMemberRunner;
import com.jev.runtime.decision.team.TeamPipeline;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Wires the elastic multi-agent team pipeline.
 *
 * <p>The single-agent {@link AgentOrchestrator} stays the backbone: here it is
 * adapted to the {@link TeamMemberRunner} SPI so the leader pipeline can delegate
 * sub-tasks to it as a team member. The whole wiring is conditional on
 * {@code jev.team.enabled} (default true), so the runtime can fall back to a
 * pure single-agent deployment by configuration only.</p>
 */
@Configuration
@ConditionalOnProperty(prefix = "jev.team", name = "enabled", havingValue = "true", matchIfMissing = true)
public class TeamConfiguration {

    /**
     * Adapt the single-agent orchestrator to the team-member SPI. Each member
     * runs in a session namespaced by member id ({code sessionId:memberId}) so
     * the layered memory stays partitioned per member within a team run.
     */
    @Bean
    public TeamMemberRunner teamMemberRunner(AgentOrchestrator orchestrator) {
        return (tenantId, sessionId, memberId, subGoal) -> {
            String memberSession = (sessionId == null ? "default" : sessionId) + ":" + memberId;
            AgentResponse resp = orchestrator.run(
                    new AgentRequest(tenantId, memberSession, subGoal, null, null));
            return resp.answer() != null
                    ? resp.answer()
                    : "[" + resp.status() + "] member produced no answer";
        };
    }

    /** The leader pipeline that selects members, delegates, verifies and grades. */
    @Bean
    public TeamPipeline teamPipeline(JevClient jev,
                                     JevGrader grader,
                                     TeamMemberRunner runner,
                                     JevTeamProperties props) {
        return new TeamPipeline(jev, grader, runner, props);
    }
}
