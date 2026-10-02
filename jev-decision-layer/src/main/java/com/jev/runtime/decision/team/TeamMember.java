package com.jev.runtime.decision.team;

import java.util.List;

/**
 * A candidate team member that the leader can delegate a sub-task to.
 *
 * @param id          stable identifier (used as the Jev CHOICE option key)
 * @param description human-readable routing criteria shown to Jev
 * @param skills      capability tags used by the deterministic local fallback
 */
public record TeamMember(String id, String description, List<String> skills) {
    public TeamMember {
        skills = skills == null ? List.of() : List.copyOf(skills);
    }
}
