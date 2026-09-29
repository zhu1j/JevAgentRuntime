package com.jev.runtime.common;

import java.util.List;
import java.util.Map;

/**
 * A single decision request sent to the Jev "System One" model.
 * A request can carry one or more primitives, evaluated in the same forward pass.
 */
public record JevRequest(
        JevPrimitive primitive,
        String instruction,
        String input,
        /** Closed candidate set for CHOICE; ignored for SCORE / NOUL. */
        List<String> candidates,
        /** Arbitrary typed context (tenant, task id, candidate metadata). */
        Map<String, Object> context
) {
    public static JevRequest choice(String instruction, String input, List<String> candidates) {
        return new JevRequest(JevPrimitive.CHOICE, instruction, input, candidates, Map.of());
    }

    public static JevRequest score(String instruction, String input) {
        return new JevRequest(JevPrimitive.SCORE, instruction, input, List.of(), Map.of());
    }

    public static JevRequest noul(String instruction, String input) {
        return new JevRequest(JevPrimitive.NOUL, instruction, input, List.of(), Map.of());
    }

    public JevRequest withContext(Map<String, Object> ctx) {
        return new JevRequest(primitive, instruction, input, candidates, ctx);
    }
}
