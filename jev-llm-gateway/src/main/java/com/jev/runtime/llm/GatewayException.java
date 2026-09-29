package com.jev.runtime.llm;

/**
 * Thrown when a tenant exceeds its rate limit, budget, or when all endpoints fail.
 */
public class GatewayException extends RuntimeException {
    public GatewayException(String message) {
        super(message);
    }
}
