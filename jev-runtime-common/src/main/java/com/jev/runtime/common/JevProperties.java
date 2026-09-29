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

    /** Base URL of the Jev REST endpoint, when reachable. */
    private String baseUrl = "https://api.typesafe.ai/v1";
    /** API key (optional; empty means fall back to local). */
    private String apiKey = "";
    /** Request timeout in milliseconds. */
    private int timeoutMs = 2000;
    /** Force the local calibrated heuristic instead of the remote model. */
    private boolean fallbackLocal = true;
    /** Default threshold used to turn NOUL probabilities into booleans. */
    private double threshold = 0.5;

    public String getBaseUrl() { return baseUrl; }
    public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }
    public String getApiKey() { return apiKey; }
    public void setApiKey(String apiKey) { this.apiKey = apiKey; }
    public int getTimeoutMs() { return timeoutMs; }
    public void setTimeoutMs(int timeoutMs) { this.timeoutMs = timeoutMs; }
    public boolean isFallbackLocal() { return fallbackLocal; }
    public void setFallbackLocal(boolean fallbackLocal) { this.fallbackLocal = fallbackLocal; }
    public double getThreshold() { return threshold; }
    public void setThreshold(double threshold) { this.threshold = threshold; }
}
