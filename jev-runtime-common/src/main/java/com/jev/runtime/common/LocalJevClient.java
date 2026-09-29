package com.jev.runtime.common;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Deterministic, dependency-free calibrated heuristic that mirrors the three Jev
 * primitives. It keeps the entire platform fully runnable when the remote Jev
 * endpoint is unavailable (e.g. outside its supported regions). It is intentionally
 * side-effect free so it can be unit tested deterministically.
 */
public final class LocalJevClient implements JevClient {

    public static final LocalJevClient INSTANCE = new LocalJevClient();

    /** Matches the "progress N/M" marker that the orchestrator puts into observations. */
    private static final Pattern PROGRESS = Pattern.compile("progress\\s+(\\d+)\\s*/\\s*(\\d+)");

    private LocalJevClient() {}

    @Override
    public JevDecision decide(JevRequest request) {
        long start = System.nanoTime();
        JevDecision decision = switch (request.primitive()) {
            case CHOICE -> choice(request);
            case SCORE -> score(request);
            case NOUL -> noul(request);
        };
        long latencyMs = Math.max(0, (System.nanoTime() - start) / 1_000_000L);
        return new JevDecision(
                decision.primitive(), decision.value(), decision.choiceIndex(),
                decision.probability(), decision.score(), decision.confidence(),
                latencyMs, 0.0, "local-fallback");
    }

    /** Keyword-overlap heuristic: pick the candidate most lexically aligned with the input. */
    private JevDecision choice(JevRequest request) {
        List<String> candidates = request.candidates();
        if (candidates == null || candidates.isEmpty()) {
            return new JevDecision(JevPrimitive.CHOICE, null, null, null, null, 0.0, 0, 0.0, "local-fallback");
        }
        String input = norm(request.input());
        int bestIdx = 0;
        double best = -1;
        for (int i = 0; i < candidates.size(); i++) {
            double sim = similarity(input, norm(candidates.get(i) + " " + request.instruction()));
            if (sim > best) { best = sim; bestIdx = i; }
        }
        double confidence = clamp(0.5 + best);
        return new JevDecision(JevPrimitive.CHOICE, candidates.get(bestIdx), bestIdx,
                null, null, confidence, 0, 0.0, "local-fallback");
    }

    /** Difficulty heuristic: longer, more imperative inputs trend harder. */
    private JevDecision score(JevRequest request) {
        String input = norm(request.input());
        String instruction = norm(request.instruction());
        int len = input.length();
        double lengthFactor = clamp(Math.log10(len + 2) / 3.0);          // 0..~1
        double imperative = containsAny(instruction + " " + input,
                "plan", "multi-step", "refactor", "architect", "optimi", "debug",
                "reason", "compare", "design", "规划", "多步", "重构", "架构", "优化", "调试")
                ? 0.35 : 0.0;
        double score = clamp(0.15 + lengthFactor * 0.5 + imperative);
        double confidence = clamp(0.55 + Math.abs(score - 0.5) * 0.6);
        return new JevDecision(JevPrimitive.SCORE, null, null, null, score, confidence, 0, 0.0, "local-fallback");
    }

    /**
     * Safety / completion heuristic driven by calibrated risk & progress markers.
     *
     * <p>Important: the keyword scan runs only over the <b>subject under judgment</b>
     * ({@link JevRequest#input()}), never over the instruction text. The instruction
     * legitimately contains words such as "unsafe", "injection" or "jailbreak" to
     * describe the task, and scanning it would make the heuristic always report risk.</p>
     */
    private JevDecision noul(JevRequest request) {
        String subject = norm(request.input());
        String instruction = norm(request.instruction());

        boolean isSafety = containsAny(instruction,
                "unsafe", "malicious", "injection", "jailbreak", "prompt-injection", "安全", "注入");

        double p;
        if (isSafety) {
            // High risk only when the subject itself carries attack markers.
            p = containsAny(subject,
                    "ignore previous", "system prompt", "jailbreak", "developer mode",
                    "bypass", "exfiltrate", "drop table", "rm -rf", "忽略之前", "越狱", "绕过")
                    ? 0.9 : 0.05;
        } else {
            // Goal-completion: the explicit "progress N/M" marker (emitted by the
            // orchestrator and immune to context compaction) drives convergence.
            Matcher m = PROGRESS.matcher(subject);
            if (m.find()) {
                int done = Integer.parseInt(m.group(1));
                int total = Math.max(1, Integer.parseInt(m.group(2)));
                int halfway = (total + 1) / 2;   // ceil(total / 2)
                p = done >= halfway ? 0.9 : 0.3;
            } else {
                // Fallback: success markers in the observation text.
                p = containsAny(subject,
                        "done", "completed", "succeeded", "resolved", "finish",
                        "完成", "成功", "已解决")
                        ? 0.9 : 0.3;
            }
        }
        double confidence = clamp(0.4 + Math.abs(p - 0.5) * 0.8);
        return new JevDecision(JevPrimitive.NOUL, null, null, p, null, confidence, 0, 0.0, "local-fallback");
    }

    private static String norm(String s) { return s == null ? "" : s.toLowerCase(Locale.ROOT); }

    private static boolean containsAny(String haystack, String... needles) {
        for (String n : needles) if (haystack.contains(n)) return true;
        return false;
    }

    /** Cheap Jaccard-style token overlap in [0,1]. */
    private static double similarity(String a, String b) {
        if (a.isEmpty() || b.isEmpty()) return 0.0;
        java.util.Set<String> ta = new java.util.HashSet<>(List.of(a.split("\\W+")));
        java.util.Set<String> tb = new java.util.HashSet<>(List.of(b.split("\\W+")));
        ta.remove("");
        tb.remove("");
        if (ta.isEmpty() || tb.isEmpty()) return 0.0;
        int inter = 0;
        for (String t : ta) if (tb.contains(t)) inter++;
        return (double) inter / (double) (ta.size() + tb.size() - inter);
    }

    private static double clamp(double v) { return Math.max(0.0, Math.min(1.0, v)); }

    @Override
    public String backend() { return "local-fallback"; }
}
