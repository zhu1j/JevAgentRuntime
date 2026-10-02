package com.jev.runtime.decision.team;

import com.jev.runtime.common.AgentRequest;
import com.jev.runtime.common.JevClient;
import com.jev.runtime.common.JevDecision;
import com.jev.runtime.common.JevRequest;
import com.jev.runtime.common.JevTeamProperties;
import com.jev.runtime.decision.JevGrader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Leader–member team pipeline: the elastic multi-agent complement.
 *
 * <p>For a goal the leader:</p>
 * <ol>
 *   <li>asks Jev (CHOICE) which members to involve — up to {@code maxMembers};</li>
 *   <li>delegates each sub-task through the injected {@link TeamMemberRunner};</li>
 *   <li>verifies each member answer with Jev (NOUL, trajectory verification);</li>
 *   <li>grades the synthesized answer with {@link JevGrader} (SCORE).</li>
 * </ol>
 *
 * <p>Every Jev decision degrades gracefully to a deterministic local heuristic
 * when the remote model is unavailable, so the pipeline always completes.</p>
 */
public class TeamPipeline {

    private static final Logger log = LoggerFactory.getLogger(TeamPipeline.class);

    private final JevClient jev;
    private final JevGrader grader;
    private final TeamMemberRunner runner;
    private final List<TeamMember> members;
    private final int maxMembers;

    public TeamPipeline(JevClient jev,
                        JevGrader grader,
                        TeamMemberRunner runner,
                        JevTeamProperties props) {
        this.jev = jev;
        this.grader = grader;
        this.runner = runner;
        this.maxMembers = Math.max(1, props.getMaxMembers());
        List<TeamMember> ms = new ArrayList<>();
        for (JevTeamProperties.Member m : props.getMembers()) {
            if (m.getId() != null && !m.getId().isBlank()) {
                ms.add(new TeamMember(m.getId(), m.getDescription(), m.getSkills()));
            }
        }
        this.members = List.copyOf(ms);
    }

    /** Run the team pipeline for one goal. */
    public TeamRunResult run(AgentRequest request) {
        List<TeamMember> chosen = selectMembers(request.goal());
        if (chosen.isEmpty()) {
            log.info("team pipeline: no members selected, falling back to first registered member");
            if (members.isEmpty()) {
                throw new IllegalStateException("team pipeline has no registered members");
            }
            chosen = List.of(members.get(0));
        }

        List<TeamRunResult.Step> steps = new ArrayList<>();
        List<String> answers = new ArrayList<>();
        for (TeamMember member : chosen) {
            String subGoal = "[" + member.id() + "] " + request.goal();
            String answer = runner.run(request.tenantId(), request.sessionId(), member.id(), subGoal);
            JevDecision verified = jev.decide(JevRequest.noul(
                    "Did the member complete its delegated sub-task? Answer true if the answer substantively addresses the sub-goal.",
                    "sub-goal: " + subGoal + "\nanswer: " + answer));
            steps.add(new TeamRunResult.Step(member.id(), subGoal, answer, "NOUL", verified.probability()));
            answers.add(member.id() + ": " + answer);
        }

        String synthesized = synthesize(request.goal(), answers);
        double grading = grader.grade(request.goal(), synthesized).score();
        log.info("team pipeline done: members={}, grading={}",
                chosen.stream().map(TeamMember::id).toList(), grading);
        return new TeamRunResult(synthesized, steps, grading);
    }

    // ------------------------------------------------------------------ internals

    /** Jev CHOICE over candidate members, bounded by maxMembers, with a deterministic local fallback. */
    private List<TeamMember> selectMembers(String goal) {
        if (members.isEmpty()) return List.of();
        List<TeamMember> picked = new ArrayList<>();
        List<TeamMember> pool = new ArrayList<>(members);
        for (int i = 0; i < maxMembers && !pool.isEmpty(); i++) {
            TeamMember pick = jevChoose(goal, pool);
            if (pick == null) break;
            picked.add(pick);
            pool.remove(pick);
        }
        return picked;
    }

    private TeamMember jevChoose(String goal, List<TeamMember> pool) {
        List<String> ids = pool.stream().map(TeamMember::id).toList();
        try {
            JevDecision d = jev.decide(JevRequest.choice(
                    "Which single team member is best suited for the next step of this goal? "
                            + "Return its id, or the id of the most relevant one.",
                    goal, ids));
            if (d.value() != null) {
                for (TeamMember m : pool) {
                    if (m.id().equals(d.value())) return m;
                }
            }
        } catch (RuntimeException e) {
            log.debug("jev member selection failed ({}), using local fallback", e.getMessage());
        }
        return localChoose(goal, pool);
    }

    /** Deterministic fallback: match goal keywords against member skills/description. */
    private TeamMember localChoose(String goal, List<TeamMember> pool) {
        String g = goal.toLowerCase(Locale.ROOT);
        TeamMember best = pool.get(0);
        int bestScore = -1;
        for (TeamMember m : pool) {
            int score = 0;
            for (String s : m.skills()) {
                if (!s.isBlank() && g.contains(s.toLowerCase(Locale.ROOT))) score++;
            }
            for (String w : m.description().toLowerCase(Locale.ROOT).split("\\W+")) {
                if (w.length() > 3 && g.contains(w)) score++;
            }
            if (score > bestScore) {
                bestScore = score;
                best = m;
            }
        }
        return best;
    }

    private String synthesize(String goal, List<String> answers) {
        StringBuilder sb = new StringBuilder();
        sb.append("Synthesized answer for goal: ").append(goal).append('\n');
        for (String a : answers) {
            sb.append("- ").append(a).append('\n');
        }
        return sb.toString().trim();
    }
}
