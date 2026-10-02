# 🏛 架构设计（ARCHITECTURE）

> 本文档描述 Jev Agent Runtime 的分层结构、组件职责、关键设计决策、数据流与扩展点。

## 目录

- [1. 总体分层](#1-总体分层)
- [2. 组件图](#2-组件图)
- [3. 一次运行的时序](#3-一次运行的时序)
- [4. 关键设计决策](#4-关键设计决策)
- [5. 数据流](#5-数据流)
- [6. 多租户隔离模型](#6-多租户隔离模型)
- [7. 容错与降级](#7-容错与降级)
- [8. 扩展点](#8-扩展点)

---

## 1. 总体分层

系统分为四层：**接入层 → 编排层 → 能力层 → 基础设施层**。

```mermaid
flowchart TB
    subgraph L1["① 接入层 (jev-app)"]
        CTRL["AgentController<br/>/api/agent/run · /models · /tools · /a2a · /eval"]
    end

    subgraph L2["② 编排层 (jev-app)"]
        RUNTIME{{"AgentRuntime<br/>local | agentscope<br/>(可插拔引擎)"}}
        ORCH["AgentOrchestrator<br/>单 Agent ReAct 变体主循环<br/>(local 引擎)"]
        HARNESS["AgentScopeAgentRuntime<br/>HarnessAgent<br/>(agentscope 引擎)"]
    end
    RUNTIME --> ORCH
    RUNTIME --> HARNESS

    subgraph L3["③ 能力层"]
        DL["jev-decision-layer<br/>Jev × 5"]
        LG["jev-llm-gateway"]
        MG["jev-mcp-gateway"]
        A2A["jev-a2a"]
        MEM["jev-memory"]
    end

    subgraph L4["④ 基础设施层"]
        JEV{{"JevClient<br/>remote / local-fallback"}}
        PROV["ModelProvider<br/>simulated（可替换）"]
        MCPEX["McpExecutor<br/>simulated（可替换）"]
        STORE["MemoryStore<br/>in-memory（可替换）"]
        OBS["jev-observability<br/>Metrics + Trace"]
        EVAL["jev-eval<br/>Jev-as-judge"]
    end

    CTRL --> ORCH
    ORCH --> DL & LG & MG & A2A & MEM
    DL --> JEV
    LG --> PROV
    MG --> MCPEX
    MEM --> STORE
    ORCH -.-> OBS
    LG -.-> OBS
    DL -.-> OBS
    EVAL --> DL
```

---

## 2. 组件图

```mermaid
flowchart LR
    subgraph decision["jev-decision-layer"]
        R["JevRouter<br/>SCORE"]
        G["JevGuardrail<br/>NOUL"]
        V["JevTrajectoryVerifier<br/>NOUL"]
        C["JevContextCompactor<br/>SCORE"]
        GR["JevGrader<br/>SCORE"]
    end

    subgraph llm["jev-llm-gateway"]
        LGW["LlmGateway"]
        SEL["EndpointSelector"]
        CB["CircuitBreaker<br/>(per endpoint)"]
        RL["TenantRateLimiter"]
        CM["CostMeter"]
        KP["KeyPool"]
    end

    subgraph mcp["jev-mcp-gateway"]
        MREG["InMemoryMcpRegistry"]
        MEXEC["McpExecutor"]
        MPERM["租户权限边界"]
    end

    subgraph common["jev-runtime-common"]
        PRIM["JevPrimitive<br/>CHOICE · SCORE · NOUL"]
        JC["JevClient"]
        LJC["LocalJevClient"]
        RJC["RemoteJevClient"]
        AC["JevAutoConfiguration"]
    end

    R & G & V & C & GR --> JC
    JC --> LJC & RJC
    LGW --> SEL --> CB
    LGW --> RL & CM & KP
    MREG --> MPERM --> MEXEC
    AC -.注册.-> JC
```

---

## 3. 一次运行的时序

```mermaid
sequenceDiagram
    autonumber
    participant C as Client
    participant CT as AgentController
    participant O as AgentOrchestrator
    participant G as JevGuardrail
    participant R as JevRouter
    participant L as LlmGateway
    participant M as McpGateway
    participant V as JevTrajectoryVerifier
    participant K as JevContextCompactor
    participant GR as JevGrader

    C->>CT: POST /api/agent/run {tenant, session, goal}
    CT->>O: run(request)
    O->>G: check(goal)
    alt 风险 >= 阈值
        G-->>O: allowed=false (risk=0.9)
        O-->>CT: AgentResponse(BLOCKED)
        CT-->>C: 200 BLOCKED
    else 放行
        G-->>O: allowed=true
        loop 直到完成或达到 MAX_STEPS
            O->>R: route(goal, history)
            R-->>O: TaskRoutingDecision(tier, difficulty)
            O->>L: complete(prompt, tier)
            L-->>O: LlmResponse(content, cost, fallback)
            O->>M: invoke(tool, tenant)
            M-->>O: McpToolResult(success, output)
            O->>V: verify(goal, history)
            V-->>O: TrajectoryVerdict(completed, prob)
            O->>K: compact(history)
            K-->>O: CompactionResult(kept, dropped, savedTokens)
        end
        O->>GR: grade(goal, answer)
        GR-->>O: GradingResult(score, pass)
        O-->>CT: AgentResponse(COMPLETED, answer, steps[], metrics)
        CT-->>C: 200 COMPLETED
    end
```

---

## 4. 关键设计决策

### 4.1 单 Agent 为核心 + 弹性多 Agent 补充
核心循环是单 Agent 的 **ReAct 变体**（reason → act → observe → verify）。只有当任务更适合专家时，才通过 A2A 目录发现并委派给 peer agent。
> 避免「盲目堆叠多 Agent」带来的 token 爆炸与协调成本。

### 4.2 决策下沉（Jev）与生成上浮（LLM）分离
每步的高频判断（难度、是否完成、相关性、质量、安全）全部由 **Jev** 完成，只有「生成」走 LLM。同一份上下文里，Jev 一次前向返回**类型化结果 + 校准置信度**。

```mermaid
flowchart LR
    subgraph Slow["慢思考（贵、低频）"]
        LLM["LLM 生成"]
    end
    subgraph Fast["快判断（廉、高频）"]
        JV["Jev：难度/完成/相关/质量/安全"]
    end
    Fast -- 只把必要信息上浮 --> Slow
    Slow -- 结果回流 --> Fast
```

### 4.3 网关作为控制面
所有对模型 / 工具的访问都必须经过 **LLM Gateway / MCP Gateway**，从而集中实现路由、限流、成本、权限、审计、可观测。

### 4.4 多租户隔离贯穿全链路
限流、预算、工具可见性、记忆分区都以 `tenantId` 为键。

### 4.5 默认离线可运行
所有外部依赖（Jev、模型供应商、MCP）都有**确定性兜底实现**，保证仓库 clone 即可端到端跑通；替换为真实实现只需换 Bean。

---

## 5. 数据流

```mermaid
flowchart TD
    A["AgentRequest(tenant, session, goal)"] --> B["JevGuardrail.check → GuardrailResult(allowed, risk)"]
    B --> C["LayeredMemoryService.recordEpisode"]
    C --> D{"loop until done or MAX_STEPS"}
    D --> E["JevRouter.route → TaskRoutingDecision(tier, difficulty)"]
    E --> F["LlmGateway.complete → LlmResponse(content, cost, fallback)"]
    F --> G["McpGateway.invoke → McpToolResult(success, output)"]
    G --> H["JevTrajectoryVerifier → TrajectoryVerdict(completed, prob)"]
    H --> I["JevContextCompactor → CompactionResult(kept, dropped, saved)"]
    I --> D
    D --> J["JevGrader.grade → GradingResult(score, pass)"]
    J --> K["AgentResponse(status, answer, tier, steps[], metrics)"]
```

---

## 6. 多租户隔离模型

| 维度 | 隔离手段 | 组件 |
|---|---|---|
| 流量 | 每租户固定窗口限流 | `TenantRateLimiter` |
| 成本 | 每租户预算，超支熔断 | `CostMeter` / `budget` |
| 工具 | 工具可见性按 `tenantId` 过滤 | `McpGateway` / 权限边界 |
| 记忆 | 记忆分区键含 `tenantId` | `LayeredMemoryService` |
| 指标 | 指标标签含 `tenant` | `AgentMetrics` |

```mermaid
flowchart LR
    T1[support] --> RT{tenant router}
    T2[finance] --> RT
    T3[dev] --> RT
    RT -->|独立限流/预算/工具/记忆| ISO["逻辑隔离域"]
```

---

## 7. 容错与降级

```mermaid
flowchart TD
    REQ["LLM 请求"] --> CB{"熔断器状态?"}
    CB -- CLOSED --> CALL["调用供应商"]
    CB -- OPEN --> FB["切换到备用端点 / 兜底"]
    CALL -- 失败 --> RETRY["重试 / 计数"]
    CALL -- 成功 --> OK["返回"]
    RETRY --> CB
    FB --> OK

    JREQ["Jev 请求"] --> JTRY{"远程可用?"}
    JTRY -- 否 --> LOCAL["LocalJevClient 离线兜底"]
    JTRY -- 是 --> REMOTE["RemoteJevClient"]
```

- **LLM 侧**：Resilience4j **每端点独立熔断器**，故障端点快速失败并切换备用端点；`llm_request_total{status="circuit_open"}` 可观测。
- **Jev 侧**：`fallback-local=true` 时使用确定性离线实现；远程不可用自动降级。

---

## 8. 扩展点

| 需求 | 替换点 |
|---|---|
| 接入真实 Jev | `jev.fallback-local=false` + `jev.api-key` |
| 接入真实模型 | 实现 `ModelProvider`，注册到 `LlmGateway` |
| 接入真实 MCP | 实现 `McpExecutor` / 对接 Nacos MCP Registry |
| 接入真实 A2A | `A2aDirectory.delegate` 换成 A2A JSON-RPC |
| 持久化记忆 | 实现 `MemoryStore`（Redis + 向量库） |
| 可观测增强 | `AgentMetrics` 增加 OTel span，`TraceRecorder` 导出 |

---

<p align="right"><a href="../README.md">← 返回 README</a></p>
