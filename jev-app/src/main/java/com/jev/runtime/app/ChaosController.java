package com.jev.runtime.app;

import com.jev.runtime.common.JevClient;
import com.jev.runtime.common.ResilientJevClient;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Game-day / chaos switch for the Jev decision layer.
 *
 * <p>Turning chaos on deliberately makes the primary Jev backend fail on every
 * decision, so the degradation path (primary → local calibrated fallback) can
 * be exercised live — the demo that proves an agent run never dies together
 * with its decision model.</p>
 */
@RestController
@RequestMapping("/api/chaos")
public class ChaosController {

    private final JevClient jevClient;

    public ChaosController(JevClient jevClient) {
        this.jevClient = jevClient;
    }

    @PostMapping("/jev")
    public ResponseEntity<Map<String, Object>> toggle(@RequestParam boolean enabled) {
        if (jevClient instanceof ResilientJevClient resilient) {
            resilient.setChaos(enabled);
            return ResponseEntity.ok(Map.of(
                    "chaos", enabled,
                    "message", enabled
                            ? "chaos ON: primary Jev failures are now injected; decisions fall back to the local calibrated heuristic"
                            : "chaos OFF: decisions use the primary Jev backend again"));
        }
        return ResponseEntity.ok(Map.of(
                "chaos", false,
                "message", "the active Jev client is not the resilient wrapper; chaos is unavailable"));
    }

    @GetMapping("/jev")
    public Map<String, Object> status() {
        boolean on = jevClient instanceof ResilientJevClient resilient && resilient.isChaos();
        return Map.of("chaos", on, "backend", jevClient.backend());
    }
}
