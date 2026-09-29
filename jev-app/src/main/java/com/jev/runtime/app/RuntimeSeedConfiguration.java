package com.jev.runtime.app;

import com.jev.runtime.a2a.A2aDirectory;
import com.jev.runtime.a2a.AgentCard;
import com.jev.runtime.llm.CostMeter;
import com.jev.runtime.llm.GatewayProperties;
import com.jev.runtime.llm.KeyPool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * Seeds the runtime at startup: per-tenant budgets, provider keys and a couple of
 * A2A peer agent cards. In production these come from Nacos/config-center + the
 * tenant service; here they provide a runnable, self-contained demo.
 */
@Configuration
public class RuntimeSeedConfiguration {

    private static final Logger log = LoggerFactory.getLogger(RuntimeSeedConfiguration.class);

    @Bean
    public ApplicationRunner seedRuntime(KeyPool keyPool,
                                         CostMeter costMeter,
                                         GatewayProperties props,
                                         A2aDirectory a2aDirectory) {
        return args -> {
            // round-robin key pools (placeholders; real keys come from config-center)
            keyPool.register("openai", List.of("sk-openai-a", "sk-openai-b"));
            keyPool.register("anthropic", List.of("sk-anthropic-a"));
            keyPool.register("qwen", List.of("sk-qwen-a"));
            keyPool.register("deepseek", List.of("sk-ds-a"));

            // per-tenant budgets
            for (String tenant : List.of("support", "finance", "dev", "eval-tenant")) {
                costMeter.setBudget(tenant, props.getDefaultBudgetUsd());
            }

            // A2A peer agents (specialists the core agent can delegate to elastically)
            a2aDirectory.register(new AgentCard(
                    "research-agent", "Deep research specialist", "http://research:8081/a2a",
                    "1.0", List.of("web.search", "doc.summarize"), true));
            a2aDirectory.register(new AgentCard(
                    "code-agent", "Code generation specialist", "http://code:8082/a2a",
                    "1.0", List.of("repo.grep", "code.patch"), true));

            log.info("Jev Agent Runtime seeded: {} model endpoints, {} A2A peers",
                    keyPool.hasKeys("openai") ? "multi-provider" : "single", a2aDirectory.list().size());
        };
    }
}
