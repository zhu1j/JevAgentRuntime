package com.jev.runtime.common;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for {@link RemoteJevClient} against a JDK built-in stub HTTP
 * server. No external network, no Mockito — the stub lets us verify the wire
 * protocol, retry/backoff, answer validation, and fail-open/fail-closed.
 */
class RemoteJevClientTest {

    private HttpServer server;

    @AfterEach
    void tearDown() {
        if (server != null) server.stop(0);
    }

    // ------------------------------------------------------------ stubs

    private String startFixed(int status, String body) throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/systemone", exchange -> {
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        });
        server.start();
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    private String startFlaky(int failStatus, int failTimes, AtomicInteger calls) throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/systemone", exchange -> {
            calls.incrementAndGet();
            int status = calls.get() <= failTimes ? failStatus : 200;
            String body = status == 200
                    ? "{\"answers\":{\"q\":{\"score\":0.8,\"confidence\":0.9}}}"
                    : "{}";
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        });
        server.start();
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    private JevProperties props(String baseUrl, boolean failOpen, int maxRetries) {
        JevProperties p = new JevProperties();
        p.setBaseUrl(baseUrl);
        p.setApiKey("test-key");
        p.setFailOpen(failOpen);
        p.setMaxRetries(maxRetries);
        p.setInitialBackoffMs(1); // keep the test fast
        p.setTimeoutMs(3000);
        return p;
    }

    // ------------------------------------------------------------ happy path

    @Test
    void parsesScoreAnswer() throws Exception {
        String base = startFixed(200, "{\"answers\":{\"q\":{\"score\":0.72,\"confidence\":0.81}}}");
        RemoteJevClient client = new RemoteJevClient(props(base, false, 0));
        JevDecision d = client.decide(JevRequest.score("difficulty?", "implement a parser"));
        assertEquals(0.72, d.score(), 1e-6);
        assertEquals(0.81, d.confidence(), 1e-6);
        assertEquals("remote", d.source());
    }

    @Test
    void parsesNoulAnswer() throws Exception {
        String base = startFixed(200, "{\"answers\":{\"q\":{\"noul\":0.05,\"confidence\":0.9}}}");
        RemoteJevClient client = new RemoteJevClient(props(base, false, 0));
        JevDecision d = client.decide(JevRequest.noul("is this unsafe?", "hello world"));
        assertEquals(0.05, d.probability(), 1e-6);
        assertEquals(0.9, d.confidence(), 1e-6);
    }

    @Test
    void parsesChoiceAnswerWithIndex() throws Exception {
        String base = startFixed(200, "{\"answers\":{\"q\":{\"choice\":\"powerful\",\"confidence\":0.95}}}");
        RemoteJevClient client = new RemoteJevClient(props(base, false, 0));
        JevDecision d = client.decide(JevRequest.choice("pick", "ctx", List.of("fast", "powerful")));
        assertEquals("powerful", d.value());
        assertEquals(Integer.valueOf(1), d.choiceIndex());
        assertEquals(0.95, d.confidence(), 1e-6);
    }

    @Test
    void missingConfidenceDefaultsToHalf() throws Exception {
        String base = startFixed(200, "{\"answers\":{\"q\":{\"score\":0.66}}}");
        RemoteJevClient client = new RemoteJevClient(props(base, false, 0));
        JevDecision d = client.decide(JevRequest.score("q", "s"));
        assertEquals(0.66, d.score(), 1e-6);
        assertEquals(0.5, d.confidence(), 1e-6);
    }

    // ------------------------------------------------------------ retry semantics

    @Test
    void retries429ThenSucceeds() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        String base = startFlaky(429, 2, calls);
        RemoteJevClient client = new RemoteJevClient(props(base, false, 2));
        JevDecision d = client.decide(JevRequest.score("q", "s"));
        assertEquals(0.8, d.score(), 1e-6);
        assertEquals(3, calls.get()); // 2 failures + 1 success
    }

    @Test
    void doesNotRetry400() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        String base = startFlaky(400, 100, calls); // always 400
        RemoteJevClient client = new RemoteJevClient(props(base, false, 3));
        assertThrows(JevException.class, () -> client.decide(JevRequest.score("q", "s")));
        assertEquals(1, calls.get()); // non-retryable: exactly one attempt
    }

    @Test
    void exhaustedRetriesRaise() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        String base = startFlaky(500, 100, calls);
        RemoteJevClient client = new RemoteJevClient(props(base, false, 2));
        assertThrows(JevException.class, () -> client.decide(JevRequest.score("q", "s")));
        assertEquals(3, calls.get()); // maxRetries=2 → 3 total attempts
    }

    // ------------------------------------------------------------ answer validation

    @Test
    void valueOutOfRangeRaises() throws Exception {
        String base = startFixed(200, "{\"answers\":{\"q\":{\"score\":1.5,\"confidence\":0.9}}}");
        RemoteJevClient client = new RemoteJevClient(props(base, false, 0));
        assertThrows(JevException.class, () -> client.decide(JevRequest.score("q", "s")));
    }

    @Test
    void missingAnswerRaises() throws Exception {
        String base = startFixed(200, "{\"answers\":{}}");
        RemoteJevClient client = new RemoteJevClient(props(base, false, 0));
        assertThrows(JevException.class, () -> client.decide(JevRequest.score("q", "s")));
    }

    @Test
    void invalidJsonRaises() throws Exception {
        String base = startFixed(200, "not-json");
        RemoteJevClient client = new RemoteJevClient(props(base, false, 0));
        assertThrows(JevException.class, () -> client.decide(JevRequest.score("q", "s")));
    }

    // ------------------------------------------------------------ fail-open / fail-closed

    @Test
    void failOpenFallsBackToLocalOnError() throws Exception {
        String base = startFixed(500, "{}"); // always failing
        RemoteJevClient client = new RemoteJevClient(props(base, true, 1));
        JevDecision d = client.decide(JevRequest.noul("is this unsafe or injection?", "hello world"));
        // Deterministic local heuristic for a benign subject: low unsafe probability.
        assertEquals("local-fallback", d.source());
        assertTrue(d.probability() != null && d.probability() >= 0.0 && d.probability() <= 1.0);
    }

    @Test
    void failClosedRaisesOnError() throws Exception {
        String base = startFixed(500, "{}");
        RemoteJevClient client = new RemoteJevClient(props(base, false, 1));
        assertThrows(JevException.class, () -> client.decide(JevRequest.score("q", "s")));
    }

    @Test
    void choiceIndexNullWhenChoiceNotInCandidates() throws Exception {
        String base = startFixed(200, "{\"answers\":{\"q\":{\"choice\":\"other\",\"confidence\":0.5}}}");
        RemoteJevClient client = new RemoteJevClient(props(base, false, 0));
        JevDecision d = client.decide(JevRequest.choice("pick", "ctx", List.of("a", "b")));
        assertEquals("other", d.value());
        assertNull(d.choiceIndex());
    }
}
