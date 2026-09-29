package com.jev.runtime.llm;

import com.jev.runtime.decision.JevRouter;
import com.jev.runtime.decision.TaskRoutingDecision;
import com.jev.runtime.observability.AgentMetrics;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * The LLM Gateway — the control plane between the agent runtime and model providers.
 *
 * <p>Responsibilities (all Java-backend-native concerns):</p>
 * <ul>
 *   <li>Jev-driven tier routing (fast/slow division of labor)</li>
 *   <li>Multi-provider failover with an ordered fallback chain</li>
 *   <li><b>Per-endpoint circuit breaking</b> (Resilience4j): a failing endpoint is
 *       short-circuited so the next call fails fast into the fallback chain</li>
 *   <li>Per-tenant fixed-window rate limiting</li>
 *   <li>Per-tenant cost accounting and budget enforcement</li>
 *   <li>Round-robin key pool to spread provider rate limits</li>
 *   <li>Unified metrics/latency accounting</li>
 * </ul>
 */
@Service
public class LlmGateway {

    private static final Logger log = LoggerFactory.getLogger(LlmGateway.class);

    private final JevRouter router;
    private final EndpointSelector selector;
    private final ModelRegistry registry;
    private final KeyPool keyPool;
    private final TenantRateLimiter rateLimiter;
    private final CostMeter costMeter;
    private final ModelProvider provider;
    private final AgentMetrics metrics;
    private final CircuitBreakerRegistry circuitBreakers;

    public LlmGateway(JevRouter router,
                      EndpointSelector selector,
                      ModelRegistry registry,
                      KeyPool keyPool,
                      TenantRateLimiter rateLimiter,
                      CostMeter costMeter,
                      ModelProvider provider,
                      AgentMetrics metrics,
                      CircuitBreakerRegistry circuitBreakers) {
        this.router = router;
        this.selector = selector;
        this.registry = registry;
        this.keyPool = keyPool;
        this.rateLimiter = rateLimiter;
        this.costMeter = costMeter;
        this.provider = provider;
        this.metrics = metrics;
        this.circuitBreakers = circuitBreakers;
    }

    /**
     * Route the request to a tier (via Jev), then execute with circuit breaking,
     * failover, rate limiting and cost accounting.
     */
    public LlmResponse complete(LlmRequest request) {
        if (!rateLimiter.tryAcquire(request.tenantId())) {
            throw new GatewayException("rate limit exceeded for tenant " + request.tenantId());
        }
        if (!costMeter.withinBudget(request.tenantId())) {
            throw new GatewayException("budget exceeded for tenant " + request.tenantId());
        }

        TaskRoutingDecision routing = router.route(request.prompt(), "");
        EndpointSelection selection = selector.select(routing.tier());

        java.util.List<ModelEndpoint> chain = new java.util.ArrayList<>();
        chain.add(selection.primary());
        chain.addAll(selection.fallbacks());

        RuntimeException last = null;
        for (int i = 0; i < chain.size(); i++) {
            ModelEndpoint endpoint = chain.get(i);
            boolean isFallback = i > 0;
            CircuitBreaker breaker = circuitBreakers.circuitBreaker(endpoint.provider() + ":" + endpoint.model());
            try {
                if (breaker.getState() == CircuitBreaker.State.OPEN) {
                    log.warn("circuit OPEN for {}, skipping to next endpoint", endpoint.model());
                    metrics.recordLlmRequest(request.tenantId(), endpoint.provider(), endpoint.model(),
                            endpoint.tier().name(), "circuit_open", 0L);
                    continue;
                }
                return CircuitBreaker.decorateCheckedSupplier(breaker, () -> invoke(request, endpoint, isFallback)).get();
            } catch (CallNotPermittedException ex) {
                last = ex;
                metrics.recordLlmRequest(request.tenantId(), endpoint.provider(), endpoint.model(),
                        endpoint.tier().name(), "circuit_open", 0L);
            } catch (Throwable ex) {
                last = ex instanceof RuntimeException re ? re : new GatewayException(ex.getMessage());
                log.warn("endpoint {} failed, trying next: {}", endpoint.model(), ex.getMessage());
            }
        }
        throw new GatewayException("all endpoints failed for tier " + routing.tier()
                + (last != null ? " (" + last.getMessage() + ")" : ""));
    }

    private LlmResponse invoke(LlmRequest request, ModelEndpoint endpoint, boolean fallback) {
        long start = System.nanoTime();
        String apiKey = keyPool.next(endpoint.provider());
        ModelProvider.RawCompletion raw = provider.complete(apiKey, endpoint, request);
        long latencyMs = (System.nanoTime() - start) / 1_000_000L;

        double cost = raw.inputTokens() * endpoint.costPerInputTokenUsd()
                + raw.outputTokens() * endpoint.costPerOutputTokenUsd();

        costMeter.record(request.tenantId(), endpoint.provider(), endpoint.model(), cost);
        metrics.recordTokens(request.tenantId(), "input", raw.inputTokens());
        metrics.recordTokens(request.tenantId(), "output", raw.outputTokens());
        metrics.recordLlmRequest(request.tenantId(), endpoint.provider(), endpoint.model(),
                endpoint.tier().name(), "success", latencyMs);

        return new LlmResponse(endpoint.provider(), endpoint.model(), endpoint.tier().name(),
                raw.content(), raw.inputTokens(), raw.outputTokens(), cost, latencyMs, fallback);
    }

    /** Expose the registry for admin/introspection endpoints. */
    public ModelRegistry registry() {
        return registry;
    }
}
