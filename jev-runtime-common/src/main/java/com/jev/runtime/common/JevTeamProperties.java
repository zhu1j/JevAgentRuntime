package com.jev.runtime.common;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

/**
 * Configuration for the elastic multi-agent team pipeline ({@code jev.team.*}).
 * The single-agent orchestrator stays the main line; the team pipeline is an
 * additive elastic complement that a leader delegates sub-tasks to.
 */
@ConfigurationProperties(prefix = "jev.team")
public class JevTeamProperties {

    /** Whether the team pipeline bean is active. */
    private boolean enabled = true;
    /** Max members the leader may delegate to per run (bounds cost/latency). */
    private int maxMembers = 3;
    /** Registered member agents (leader chooses a subset per goal). */
    private List<Member> members = new ArrayList<>();

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    public int getMaxMembers() { return maxMembers; }
    public void setMaxMembers(int maxMembers) { this.maxMembers = maxMembers; }

    public List<Member> getMembers() { return members; }
    public void setMembers(List<Member> members) { this.members = members; }

    public static class Member {
        private String id;
        private String description = "";
        private List<String> skills = new ArrayList<>();

        public String getId() { return id; }
        public void setId(String id) { this.id = id; }

        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }

        public List<String> getSkills() { return skills; }
        public void setSkills(List<String> skills) { this.skills = skills; }
    }
}
