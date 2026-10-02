package com.jev.runtime.common;

/**
 * Raised when the remote Jev System One API call fails.
 *
 * <p>Thrown for transport failures, non-retryable HTTP errors, exhausted
 * retries, and malformed/invalid responses. Decision callers that run with
 * {@code jev.fail-open=true} never see this (they silently fall back to the
 * deterministic local heuristic); callers with {@code fail-open=false} get it
 * so the decision path fails closed.
 */
public class JevException extends RuntimeException {

    private final Integer status;

    public JevException(String message) {
        this(message, null, null);
    }

    public JevException(String message, Integer status) {
        this(message, status, null);
    }

    public JevException(String message, Integer status, Throwable cause) {
        super(message, cause);
        this.status = status;
    }

    /** HTTP status of the final failed attempt, or {@code null} for transport/parse errors. */
    public Integer status() {
        return status;
    }
}
