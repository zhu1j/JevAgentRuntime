package com.jev.runtime.llm;

import com.jev.runtime.observability.AgentMetrics;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.DoubleAdder;

/**
 * Per-tenant cost accounting with a configurable budget.
 * Guards against runaway loops that would otherwise burn budget silently.
 */
@Component
public class CostMeter {

    private final Map<String, DoubleAdder> spendByTenant = new ConcurrentHashMap<>();
    private final Map<String, Double> budgetByTenant = new ConcurrentHashMap<>();
    private final AgentMetrics metrics;

    public CostMeter(AgentMetrics metrics) {
        this.metrics = metrics;
    }

    public void setBudget(String tenantId, double usd) {
        budgetByTenant.put(tenantId, usd);
    }

    public boolean withinBudget(String tenantId) {
        double budget = budgetByTenant.getOrDefault(tenantId, Double.MAX_VALUE);
        return spendByTenant.getOrDefault(tenantId, new DoubleAdder()).sum() < budget;
    }

    public void record(String tenantId, String provider, String model, double costUsd) {
        spendByTenant.computeIfAbsent(tenantId, k -> new DoubleAdder()).add(costUsd);
        metrics.recordCost(tenantId, provider, model, costUsd);
    }

    public double spent(String tenantId) {
        return spendByTenant.getOrDefault(tenantId, new DoubleAdder()).sum();
    }
}
