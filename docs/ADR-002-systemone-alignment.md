# ADR-002：RemoteJevClient 对齐官方 System One API 并补齐容错语义

## 状态

已接受（2026-10-02）

## 背景

代码审查确认，初版 `RemoteJevClient` 在「接入真实 Jev」这件事上存在差距。先说明
初版**没有**的问题，避免误伤：它用 `Map.of(...)` + Spring `RestClient` 做请求
序列化（**不是**字符串拼接，因此没有 JSON 转义缺陷），响应也用 `Map` 解析，并且
已经有「远程失败即回退本地兜底」的优雅降级。这些都是对的。

真正的差距在于：

1. **协议端点不符**：请求打到自定义的 `POST {base}/decide`，payload 为
   `{primitive, instruction, input, candidates, context}`。官方 System One API 是
   `POST {base}/systemone`，body 为 `{state, model, questions:{...}}`。
   照原实现填入真实 API Key 后无法与该端点对接。
2. **没有可重试错误的重试**：除超时外没有任何重试；429/529/5xx 这类「可重试」错误
   直接走降级。
3. **降级不可配**：降级策略被硬编码为「永远放行」（fail-open），无法切换到
   fail-closed（决策路径不可用时直接拒绝）。
4. **无答案校验**：未校验响应结构与取值域（如概率/分数是否落在 [0,1]）。

同时参考 AgentScope Java 官方 Jev 集成（`agentscope-extensions-jev`）的语义：
默认 `jev-latest` 模型、429/529/5xx 指数退避、答案校验、以及 `failOpen` 降级策略
（判断服务不可用不应成为 Agent 的单点故障，且该策略应可配置）。

## 决策

重写 `RemoteJevClient`，对齐官方 System One API，并引入可配置的容错语义，同时
**保留**初版正确的部分（`RestClient` 传输、`Map` 序列化、本地兜底）：

- **协议**：`POST {base}/systemone`，body `{state, model, questions:{q:{type,question,options?}}}`；
  解析 `answers.q`，并按原语映射到 `JevDecision` 的字段（CHOICE→value/choiceIndex、
  NOUL→probability、SCORE→score），校验结构与取值域。
- **重试**：仅对 HTTP 429/529/5xx 做指数退避重试（`jev.max-retries` /
  `jev.initial-backoff-ms` 可配）；**超时与连接错误不重试**，让决策路径快速失败、交给上层降级。
- **降级**：新增 `jev.fail-open`（默认 `true`）：远程失败回退到确定性本地兜底
  （`LocalJevClient.INSTANCE`）；`fail-open=false` 时抛 `JevException`（fail closed）。
- **传输**：继续用 `RestClient`（与代码库一致，不引入 Reactor/Netty 第二套栈）。

## 影响

- 配置项新增：`jev.model`（默认 `jev-latest`）、`jev.fail-open`、`jev.max-retries`、
  `jev.initial-backoff-ms`。默认配置行为与此前一致（`fallback-local=true`，离线可运行）。
- 新增 `JevException`，fail-closed 模式下由决策调用方感知。
- `jev-runtime-common` 显式声明 `jackson-databind` 依赖（`RestClient` 的 JSON 消息
  转换在运行期需要它；由 Spring Boot BOM 管理版本），使模块可独立用于真实 HTTP。
- 新增 `RemoteJevClientTest`：用 JDK 内置 `HttpServer` 做桩，覆盖三种原语解析、
  429 重试后成功、400 不重试、重试耗尽、答案越界/缺失/非法 JSON、fail-open 回退、
  fail-closed 抛错。

## 备选方案

- **保留 `/decide` 自定义协议**：被否——与官方 System One 端点不兼容。
- **超时也重试**：被否——决策路径必须快速失败，否则放大 Agent 每轮延迟；这与
  AgentScope「判断要快、失败要有降级路径」的设计一致。
- **改用 `java.net.http.HttpClient`**：被否——初版用 `RestClient` 与整个代码库一致，
  更换只会增加差异而无实质收益。

## 参考

- AgentScope Java Jev 集成：https://java.agentscope.io/v2/zh/jev/index
- 官方博客《让 Agent 学会判断：Jev 与结构化决策》：https://java.agentscope.io/v2/zh/blogs/jev-structured-decision-for-agents
