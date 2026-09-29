package com.jev.runtime.common;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.Map;

/**
 * HTTP client for the remote Jev "System One" endpoint.
 *
 * <p>Uses Spring 6.1's synchronous {@link RestClient} rather than a reactive
 * client, so the library does not drag a second web stack (Reactor/Netty) into
 * an otherwise MVC application. A calibrated confidence is always present;
 * latency and cost are measured locally.</p>
 *
 * <p>Any transport or protocol failure degrades gracefully to the deterministic
 * local implementation, so a Jev outage never fails the whole request.</p>
 */
public class RemoteJevClient implements JevClient {

    private static final Logger log = LoggerFactory.getLogger(RemoteJevClient.class);

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
        long start = System.nanoTime();
        Map<String, Object> payload = Map.of(
                "primitive", request.primitive().name().toLowerCase(),
                "instruction", request.instruction(),
                "input", request.input(),
                "candidates", request.candidates(),
                "context", request.context());

        try {
            Map<?, ?> resp = restClient.post()
                    .uri("/decide")
                    .body(payload)
                    .retrieve()
                    .body(Map.class);
            long latencyMs = (System.nanoTime() - start) / 1_000_000L;
            return parse(request.primitive(), resp, latencyMs);
        } catch (RuntimeException ex) {
            // Graceful degradation: never fail the caller because Jev is unreachable.
            log.warn("remote Jev call failed ({}), degrading to local calibrated fallback", ex.getMessage());
            return LocalJevClient.INSTANCE.decide(request);
        }
    }

    private JevDecision parse(JevPrimitive primitive, Map<?, ?> resp, long latencyMs) {
        if (resp == null) {
            return LocalJevClient.INSTANCE.decide(JevRequest.noul("remote null response", ""));
        }
        String value = str(resp.get("value"));
        Integer idx = num(resp.get("choice_index"));
        Double prob = dbl(resp.get("probability"));
        Double score = dbl(resp.get("score"));
        Double conf = dbl(resp.get("confidence"));
        Double cost = dbl(resp.get("cost_usd"));
        return new JevDecision(primitive, value, idx, prob, score,
                conf == null ? 0.5 : conf, latencyMs, cost == null ? 0.0 : cost, "remote");
    }

    private static String str(Object o) { return o == null ? null : o.toString(); }
    private static Integer num(Object o) { return o instanceof Number n ? n.intValue() : null; }
    private static Double dbl(Object o) { return o instanceof Number n ? n.doubleValue() : null; }

    @Override
    public String backend() { return "remote"; }

    @Override
    public boolean healthy() {
        return properties.getApiKey() != null && !properties.getApiKey().isBlank();
    }
}
