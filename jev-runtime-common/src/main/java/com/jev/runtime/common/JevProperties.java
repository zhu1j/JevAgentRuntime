package com.jev.runtime.common;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration for the Jev decision layer.
 * Jev's public API is not yet generally available in mainland China; when
 * {@code fallbackLocal} is true the runtime uses a deterministic calibrated
 * heuristic so the whole platform stays runnable offline.
 */
@ConfigurationProperties(prefix = "jev")
public class JevProperties {

    /** Base URL of the System One API, including the version prefix (e.g. https://api.typesafe.ai/v1). */
    private String baseUrl = "https://api.typesafe.ai/v1";
    /** API key (optional; empty means fall back to local). */
    private String apiKey = "";
    /** Decision model id sent in each System One request. */
    private String model = "jev-latest";
    /** Request timeout in milliseconds. */
    private int timeoutMs = 2000;
    /** Force the local calibrated heuristic instead of the remote model. */
    private boolean fallbackLocal = true;
    /**
     * true: on remote failure, degrade to the local heuristic (never block the agent loop).
     * false: on remote failure, raise JevException (fail closed).
     */
    private boolean failOpen = true;
    /** Retries for HTTP 429/529/5xx with exponential backoff. */
    private int maxRetries = 2;
    /** Initial backoff between retries, in milliseconds. */
    private int initialBackoffMs = 500;
    /** Default threshold used to turn NOUL probabilities into booleans. */
    private double threshold = 0.5;

    public String getBaseUrl() { return baseUrl; }
    public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }

    public String getApiKey() { return apiKey; }
    public void setApiKey(String apiKey) { this.apiKey = apiKey; }

    public String getModel() { return model; }
    public void setModel(String model) { this.model = model; }

    public int getTimeoutMs() { return timeoutMs; }
    public void setTimeoutMs(int timeoutMs) { this.timeoutMs = timeoutMs; }

    public boolean isFallbackLocal() { return fallbackLocal; }
    public void setFallbackLocal(boolean fallbackLocal) { this.fallbackLocal = fallbackLocal; }

    public boolean isFailOpen() { return failOpen; }
    public void setFailOpen(boolean failOpen) { this.failOpen = failOpen; }

    public int getMaxRetries() { return maxRetries; }
    public void setMaxRetries(int maxRetries) { this.maxRetries = maxRetries; }

    public int getInitialBackoffMs() { return initialBackoffMs; }
    public void setInitialBackoffMs(int initialBackoffMs) { this.initialBackoffMs = initialBackoffMs; }

    public double getThreshold() { return threshold; }
    public void setThreshold(double threshold) { this.threshold = threshold; }
}
