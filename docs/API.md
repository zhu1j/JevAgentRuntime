# 🔌 接口文档（API）

> 基础地址：`http://localhost:8080` · 交互式文档：`/swagger-ui.html`

## 目录

- [1. 约定](#1-约定)
- [2. 运行 Agent 任务](#2-运行-agent-任务)
- [3. 模型舰队](#3-模型舰队)
- [4. 租户工具](#4-租户工具)
- [5. A2A 伙伴 Agent](#5-a2a-伙伴-agent)
- [6. 评测门禁](#6-评测门禁)
- [7. 运维端点](#7-运维端点)
- [8. 错误码](#8-错误码)

---

## 1. 约定

| 项 | 说明 |
|---|---|
| 协议 | HTTP/1.1 |
| 编码 | UTF-8 |
| 请求体 | `application/json` |
| 响应体 | `application/json` |
| 多租户 | 通过请求体内 `tenantId` 或查询参数传递 |

---

## 2. 运行 Agent 任务

`POST /api/agent/run`

### 请求体

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `tenantId` | string | 是 | 租户标识（如 support/finance/dev） |
| `sessionId` | string | 是 | 会话标识，用于记忆与追踪 |
| `goal` | string | 是 | 任务目标（自然语言） |

### 请求示例

```bash
curl -X POST http://localhost:8080/api/agent/run \
  -H 'Content-Type: application/json' \
  -d '{
    "tenantId": "support",
    "sessionId": "s1",
    "goal": "refund order 123 because it arrived damaged"
  }'
```

### 响应体

| 字段 | 类型 | 说明 |
|---|---|---|
| `sessionId` | string | 会话标识 |
| `status` | string | `COMPLETED` / `BLOCKED` / 其他 |
| `answer` | string | 最终答案 |
| `modelTier` | string | 选择到的模型档位 `SMALL`/`MEDIUM`/`LARGE` |
| `steps` | array | 决策轨迹（每步 kind/jevPrimitive/jevValue） |
| `metrics` | object | 运行指标（步数、评分等） |

### 成功响应示例

```json
{
  "sessionId": "s1",
  "status": "COMPLETED",
  "answer": "[deepseek/deepseek-chat] simulated answer for: refund order 123 ...",
  "modelTier": "LARGE",
  "steps": [
    { "index": 0, "kind": "guardrail", "jevPrimitive": "NOUL",  "jevValue": 0.05 },
    { "index": 1, "kind": "route",     "jevPrimitive": "SCORE", "jevValue": 0.72 },
    { "index": 2, "kind": "act",       "summary": "model=deepseek-chat" },
    { "index": 3, "kind": "verify",    "jevPrimitive": "NOUL",  "jevValue": 0.9 }
  ],
  "metrics": { "steps": 3, "gradingScore": 0.53 }
}
```

### 被拦截响应示例

```json
{
  "sessionId": "s3",
  "status": "BLOCKED",
  "answer": "Request blocked by guardrail (risk=0.90).",
  "steps": [
    { "index": 0, "kind": "guardrail", "jevPrimitive": "NOUL", "jevValue": 0.9 }
  ]
}
```

---

## 3. 模型舰队

`GET /api/models`

列出当前可用模型与档位映射。

```bash
curl http://localhost:8080/api/models
```

```json
[
  { "tier": "SMALL",  "provider": "simulated", "model": "qwen-turbo",        "costPer1k": 0.0005 },
  { "tier": "MEDIUM", "provider": "simulated", "model": "deepseek-chat",     "costPer1k": 0.002  },
  { "tier": "LARGE",  "provider": "simulated", "model": "gpt-4o",            "costPer1k": 0.01   }
]
```

---

## 4. 租户工具

`GET /api/tools?tenantId={tenantId}`

返回指定租户**可见**的 MCP 工具（体现租户权限边界）。

```bash
curl 'http://localhost:8080/api/tools?tenantId=finance'
```

```json
[
  { "name": "ledger.query",  "description": "query ledger entries", "tenantScoped": true },
  { "name": "invoice.create", "description": "create invoice",       "tenantScoped": true }
]
```

---

## 5. A2A 伙伴 Agent

### 5.1 注册

`POST /api/a2a/agents`

```bash
curl -X POST http://localhost:8080/api/a2a/agents \
  -H 'Content-Type: application/json' \
  -d '{
    "name": "data-agent",
    "description": "analytics specialist",
    "endpoint": "http://data:8083/a2a",
    "version": "1.0",
    "skills": ["sql.run"],
    "streaming": true
  }'
```

### 5.2 列表

`GET /api/a2a/agents`

```bash
curl http://localhost:8080/api/a2a/agents
```

```json
[
  { "name": "data-agent", "description": "analytics specialist",
    "endpoint": "http://data:8083/a2a", "version": "1.0",
    "skills": ["sql.run"], "streaming": true }
]
```

---

## 6. 评测门禁

`POST /api/eval/run`

在黄金数据集上运行 Jev-as-judge 评测，返回通过率。

```bash
curl -X POST http://localhost:8080/api/eval/run
```

```json
{
  "total": 3,
  "passed": 3,
  "passRate": 1.0,
  "cases": [
    { "id": "normal-refund",  "passed": true, "score": 0.62 },
    { "id": "injection-block","passed": true, "score": 0.90 },
    { "id": "multi-step",     "passed": true, "score": 0.55 }
  ]
}
```

---

## 7. 运维端点

| 端点 | 说明 |
|---|---|
| `GET /actuator/health` | 健康检查 |
| `GET /actuator/info` | 应用信息 |
| `GET /actuator/metrics` | 指标列表 |
| `GET /actuator/prometheus` | Prometheus 指标 |

---

## 8. 错误码

| 状态码 | 场景 | 说明 |
|---|---|---|
| 200 | 成功 | 即使 `status=BLOCKED` 也返回 200（业务态） |
| 400 | 参数错误 | 缺少必填字段或格式错误 |
| 429 | 限流 | 该租户超出窗口限流阈值 |
| 500 | 服务器错误 | 未预期异常 |
| 503 | 熔断 | 供应商端点全部熔断且无兜底 |

---

<p align="right"><a href="../README.md">← 返回 README</a></p>
