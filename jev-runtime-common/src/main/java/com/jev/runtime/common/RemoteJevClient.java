package com.jev.runtime.common;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * HTTP client for the remote Jev "System One" endpoint, aligned with the
 * official TypeSafe API and with the semantics of AgentScope's
 * {@code agentscope-extensions-jev}.
 *
 * <p><b>Protocol.</b> {@code POST {baseUrl}/systemone} with body
 * {@code {state, model, questions:{q:{type:choice|score|noul, ...}}}}.
 * The typed answer is read from {@code answers.q} and mapped onto the
 * primitive-specific fields of {@link JevDecision}.</p>
 *
 * <p><b>Transport.</b> Uses Spring 6.1's synchronous {@link RestClient} (as the
 * rest of the codebase does), so no second web stack (Reactor/Netty) is pulled
 * into an otherwise MVC application.</p>
 *
 * <p><b>Resilience.</b></p>
 * <ul>
 *   <li>HTTP 429 / 529 / 5xx are retried with exponential backoff
 *       ({@code jev.max-retries} / {@code jev.initial-backoff-ms}).</li>
 *   <li>Timeouts and connection errors are NOT retried — the decision path must
 *       fail fast so the caller can fall back.</li>
 *   <li>{@code jev.fail-open=true} (default): any failure degrades gracefully to
 *       the deterministic local heuristic, so a Jev outage never fails the whole
 *       request. {@code fail-open=false} raises {@link JevException} (fail closed).</li>
 * </ul>
 */
public class RemoteJevClient implements JevClient {

    private static final Logger log = LoggerFactory.getLogger(RemoteJevClient.class);
    /** Statuses retried with exponential backoff (parity with agentscope-extensions-jev). */
    private static final List<Integer> RETRYABLE_STATUSES = List.of(429, 529);

    private final RestClient restClient;
    private final JevProperties properties;

    public RemoteJevClient(JevProperties properties) {
        this.properties = properties;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofMillis(properties.getTimeoutMs()));
        factory.setReadTimeout(Duration.ofMillis(properties.getTimeoutMs()));
        this.restClient = RestClient.builder()
                .baseUrl(properties.getBaseUrl())
                .defaultHeader("Authorization", "Bearer " + properties.getApiKey())
                .requestFactory(factory)
                .build();
    }

    @Override
    public JevDecision decide(JevRequest request) {
        try {
            return callRemote(request);
        } catch (JevException e) {
            if (properties.isFailOpen()) {
                // Graceful degradation: never fail the caller because Jev is unreachable.
                log.warn("remote Jev call failed ({}), degrading to local calibrated fallback", e.getMessage());
                return LocalJevClient.INSTANCE.decide(request);
            }
            throw e;
        }
    }

    // ------------------------------------------------------------------ remote

    private JevDecision callRemote(JevRequest request) {
        long start = System.nanoTime();
        Map<String, Object> body = buildRequestBody(request);
        JevException last = null;

        for (int attempt = 0; attempt <= properties.getMaxRetries(); attempt++) {
            if (attempt > 0) {
                sleep(properties.getInitialBackoffMs() * (1L << (attempt - 1)));
            }
            try {
                Map<?, ?> resp = restClient.post()
                        .uri("/systemone")
                        .body(body)
                        .retrieve()
                        .body(Map.class);
                long latencyMs = (System.nanoTime() - start) / 1_000_000L;
                return parse(request, resp, latencyMs);
            } catch (RestClientResponseException e) {
                int status = e.getStatusCode().value();
                last = new JevException("Jev HTTP " + status, status, e);
                if (!isRetryable(status)) {
                    throw last;
                }
                log.debug("Jev returned retryable HTTP {}, attempt {}/{}",
                        status, attempt + 1, properties.getMaxRetries() + 1);
            } catch (ResourceAccessException e) {
                // Timeouts and connection errors: fail fast, do NOT retry.
                throw new JevException("Jev request failed: " + e.getMessage(), null, e);
            } catch (RestClientException e) {
                // Message-conversion and other client-side errors (e.g. a non-JSON body).
                throw new JevException("Jev response unreadable: " + e.getMessage(), null, e);
            }
        }
        throw last != null ? last : new JevException("Jev request failed after retries");
    }

    private boolean isRetryable(int status) {
        return RETRYABLE_STATUSES.contains(status) || status >= 500;
    }

    private void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    // ------------------------------------------------------------------ payload

    private Map<String, Object> buildRequestBody(JevRequest request) {
        Map<String, Object> question = new LinkedHashMap<>();
        question.put("type", request.primitive().name().toLowerCase());
        question.put("question", request.instruction());
        if (request.primitive() == JevPrimitive.CHOICE && request.candidates() != null) {
            Map<String, String> options = new LinkedHashMap<>();
            for (String c : request.candidates()) {
                options.put(c, c);
            }
            question.put("options", options);
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("state", request.input());
        body.put("model", properties.getModel());
        body.put("questions", Map.of("q", question));
        return body;
    }

    // ------------------------------------------------------------------ parsing

    private JevDecision parse(JevRequest request, Map<?, ?> resp, long latencyMs) {
        if (resp == null) {
            throw new JevException("Jev returned null response");
        }
        Object answersObj = resp.get("answers");
        if (!(answersObj instanceof Map<?, ?> answers)) {
            throw new JevException("Jev response missing \"answers\" object");
        }
        Object qObj = answers.get("q");
        if (!(qObj instanceof Map<?, ?> ans)) {
            throw new JevException("Jev response missing \"answers.q\" object");
        }
        double confidence = dblOr(ans.get("confidence"), 0.5);
        double cost = dblOr(ans.get("cost_usd"), 0.0);

        return switch (request.primitive()) {
            case NOUL -> {
                Double p = dbl(ans.get("noul"));
                if (p == null) p = dbl(ans.get("probability"));
                if (p == null) throw new JevException("noul answer missing probability");
                checkRange(p);
                yield new JevDecision(JevPrimitive.NOUL, null, null, p, null,
                        confidence, latencyMs, cost, "remote");
            }
            case SCORE -> {
                Double s = dbl(ans.get("score"));
                if (s == null) s = dbl(ans.get("value"));
                if (s == null) throw new JevException("score answer missing value");
                checkRange(s);
                yield new JevDecision(JevPrimitive.SCORE, null, null, null, s,
                        confidence, latencyMs, cost, "remote");
            }
            case CHOICE -> {
                Object c = ans.get("choice");
                if (c == null) throw new JevException("choice answer missing choice");
                String choice = c.toString();
                yield new JevDecision(JevPrimitive.CHOICE, choice, indexOf(request.candidates(), choice),
                        null, null, confidence, latencyMs, cost, "remote");
            }
        };
    }

    private static Integer indexOf(List<String> candidates, String choice) {
        if (candidates == null) return null;
        int i = candidates.indexOf(choice);
        return i >= 0 ? i : null;
    }

    private static void checkRange(double v) {
        if (v < 0.0 || v > 1.0) {
            throw new JevException("Jev answer value " + v + " out of [0, 1]");
        }
    }

    private static Double dbl(Object o) {
        return o instanceof Number n ? n.doubleValue() : null;
    }

    private static double dblOr(Object o, double fallback) {
        return o instanceof Number n ? n.doubleValue() : fallback;
    }

    @Override
    public String backend() {
        return "remote";
    }

    @Override
    public boolean healthy() {
        return properties.getApiKey() != null && !properties.getApiKey().isBlank();
    }
}
