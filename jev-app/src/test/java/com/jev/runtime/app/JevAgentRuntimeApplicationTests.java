package com.jev.runtime.app;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Smoke test: the whole application context (all 9 modules) wires successfully
 * with the offline Jev fallback.
 */
@SpringBootTest
class JevAgentRuntimeApplicationTests {

    @Test
    void contextLoads() {
        // fails if any bean/graph is broken
    }
}
