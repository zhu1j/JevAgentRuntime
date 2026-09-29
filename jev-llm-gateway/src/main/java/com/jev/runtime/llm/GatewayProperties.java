package com.jev.runtime.llm;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * LLM gateway policy configuration: rate limits, budget defaults and timeouts.
 */
@ConfigurationProperties(prefix = "jev.gateway")
public class GatewayProperties {

    /** Fixed-window rate limit per tenant. */
    private int rateLimitPerWindow = 60;
    /** Window size in milliseconds. */
    private long rateWindowMillis = 1000L;
    /** Default per-tenant budget in USD when none is configured explicitly. */
    private double defaultBudgetUsd = 5.0;
    /** Timeout for a single provider call, in milliseconds. */
    private int providerTimeoutMs = 15000;

    public int getRateLimitPerWindow() { return rateLimitPerWindow; }
    public void setRateLimitPerWindow(int rateLimitPerWindow) { this.rateLimitPerWindow = rateLimitPerWindow; }
    public long getRateWindowMillis() { return rateWindowMillis; }
    public void setRateWindowMillis(long rateWindowMillis) { this.rateWindowMillis = rateWindowMillis; }
    public double getDefaultBudgetUsd() { return defaultBudgetUsd; }
    public void setDefaultBudgetUsd(double defaultBudgetUsd) { this.defaultBudgetUsd = defaultBudgetUsd; }
    public int getProviderTimeoutMs() { return providerTimeoutMs; }
    public void setProviderTimeoutMs(int providerTimeoutMs) { this.providerTimeoutMs = providerTimeoutMs; }
}
