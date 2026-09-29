package com.jev.runtime.common;

/**
 * Jev "System One" exposes three typed primitives, all answerable in a single forward pass:
 * <ul>
 *   <li>{@link #CHOICE} – pick one option from a closed candidate set (next tool, next action, route).</li>
 *   <li>{@link #SCORE}  – continuous quality/utility score in [0,1] (result grading, difficulty).</li>
 *   <li>{@link #NOUL}    – calibrated boolean probability ("did this succeed?", "is this unsafe?").</li>
 * </ul>
 * These replace high-frequency LLM-as-judge calls with a fast, cheap decision layer.
 */
public enum JevPrimitive {
    CHOICE,
    SCORE,
    NOUL
}
