package com.jev.runtime.decision.team;

import com.jev.runtime.common.AgentRequest;
import com.jev.runtime.common.JevClient;
import com.jev.runtime.common.JevDecision;
import com.jev.runtime.common.JevPrimitive;
import com.jev.runtime.common.JevRequest;
import com.jev.runtime.common.JevTeamProperties;
import com.jev.runtime.decision.JevGrader;
import com.jev.runtime.observability.AgentMetrics;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for {@link TeamPipeline} with a scripted in-memory {@link JevClient}
 * (no network) and a stub {@link TeamMemberRunner}. Verifies member selection,
 * per-member verification, synthesis, grading and graceful degradation.
 */
class TeamPipelineTest {

    /** Deterministic scripted Jev client: CHOICE returns queued picks, NOUL returns p, SCORE returns s. */
    static class ScriptedJev implements JevClient {
        final List<String> picks;
        final double noulP;
        final double scoreS;
        int pickIdx = 0;

        ScriptedJev(List<String> picks, double noulP, double scoreS) {
            this.picks = new ArrayList<>(picks);
            this.noulP = noulP;
            this.scoreS = scoreS;
        }

        @Override
        public JevDecision decide(JevRequest request) {
            return switch (request.primitive()) {
                case CHOICE -> {
                    String pick = pickIdx < picks.size() ? picks.get(pickIdx++) : null;
                    Integer idx = pick == null ? null : request.candidates().indexOf(pick);
                    yield new JevDecision(JevPrimitive.CHOICE,
                            pick, idx != null && idx >= 0 ? idx : null, null, null, 0.9, 1L, 0.0, "test");
                }
                case NOUL -> new JevDecision(JevPrimitive.NOUL,
                        null, null, noulP, null, 0.9, 1L, 0.0, "test");
                case SCORE -> new JevDecision(JevPrimitive.SCORE,
                        null, null, null, scoreS, 0.9, 1L, 0.0, "test");
            };
        }

        @Override
        public String backend() { return "test"; }

        @Override
        public boolean healthy() { return true; }
    }

    private static JevGrader newGrader(JevClient jev, double threshold) {
        return new JevGrader(jev, new AgentMetrics(new SimpleMeterRegistry()), threshold);
    }

    private static JevTeamProperties props(int maxMembers, String... ids) {
        JevTeamProperties p = new JevTeamProperties();
        p.setMaxMembers(maxMembers);
        List<JevTeamProperties.Member> ms = new ArrayList<>();
        for (String id : ids) {
            JevTeamProperties.Member m = new JevTeamProperties.Member();
            m.setId(id);
            m.setDescription(id + " specialist");
            m.setSkills(List.of(id));
            ms.add(m);
        }
        p.setMembers(ms);
        return p;
    }

    private static TeamMemberRunner stubRunner(Map<String, String> answers) {
        return (tenant, session, memberId, subGoal) ->
                answers.getOrDefault(memberId, "answer from " + memberId + " for: " + subGoal);
    }

    @Test
    void delegatesToSelectedMembersAndGradesSynthesis() {
        ScriptedJev jev = new ScriptedJev(List.of("research", "code"), 0.9, 0.8);
        TeamPipeline pipeline = new TeamPipeline(jev, newGrader(jev, 0.5),
                stubRunner(Map.of()), props(2, "research", "code", "support"));

        TeamRunResult result = pipeline.run(
                new AgentRequest("t1", "s1", "research then implement feature", List.of(), Map.of()));

        assertEquals(2, result.steps().size());
        assertEquals("research", result.steps().get(0).memberId());
        assertEquals("code", result.steps().get(1).memberId());
        assertEquals(0.8, result.gradingScore(), 1e-6);
        assertTrue(result.synthesizedAnswer().contains("research:"));
        assertTrue(result.synthesizedAnswer().contains("code:"));
        // every member answer was verified with a NOUL primitive
        assertTrue(result.steps().stream().allMatch(s -> "NOUL".equals(s.jevPrimitive())));
    }

    @Test
    void boundsDelegationByMaxMembers() {
        ScriptedJev jev = new ScriptedJev(List.of("a", "b", "c"), 0.9, 0.7);
        TeamPipeline pipeline = new TeamPipeline(jev, newGrader(jev, 0.5),
                stubRunner(Map.of()), props(1, "a", "b", "c"));

        TeamRunResult result = pipeline.run(
                new AgentRequest("t", "s", "do something", List.of(), Map.of()));
        assertEquals(1, result.steps().size()); // capped at maxMembers=1
    }

    @Test
    void fallsBackToLocalSelectionWhenJevChoiceFails() {
        // CHOICE throws -> local keyword fallback should pick the member whose skill matches the goal
        ScriptedJev jev = new ScriptedJev(List.of(), 0.9, 0.6) {
            @Override
            public JevDecision decide(JevRequest request) {
                if (request.primitive() == JevPrimitive.CHOICE) {
                    throw new RuntimeException("jev down");
                }
                return super.decide(request);
            }
        };
        TeamPipeline pipeline = new TeamPipeline(jev, newGrader(jev, 0.5),
                stubRunner(Map.of()), props(1, "research", "code"));

        TeamRunResult result = pipeline.run(
                new AgentRequest("t", "s", "please research this topic", List.of(), Map.of()));
        assertEquals(1, result.steps().size());
        assertEquals("research", result.steps().get(0).memberId()); // keyword "research" matched
    }

    @Test
    void throwsWhenNoMembersRegistered() {
        ScriptedJev jev = new ScriptedJev(List.of(), 0.9, 0.5);
        TeamPipeline pipeline = new TeamPipeline(jev, newGrader(jev, 0.5),
                stubRunner(Map.of()), props(2));
        assertThrows(IllegalStateException.class,
                () -> pipeline.run(new AgentRequest("t", "s", "anything", List.of(), Map.of())));
    }
}
