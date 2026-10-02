# Changelog

本项目所有值得记录的变更都写在这里。格式参考 [Keep a Changelog](https://keepachangelog.com/zh-CN/1.1.0/)。

## [Unreleased]

### Changed（变更）
- `RemoteJevClient`：通信协议对齐官方 TypeSafe System One API ——
  `POST {base}/systemone`，body 为 `{state, model, questions}`，解析 `answers.q`；
  取代原先的自定义 `/decide` 端点（ADR-002）。
- `RemoteJevClient`：HTTP 429/529/5xx 以指数退避重试
  （`jev.max-retries`、`jev.initial-backoff-ms`）；超时/连接错误不重试、快速失败。
- `RemoteJevClient`：新增答案校验（结构完整性 + 取值域 [0,1]）。

### Added（新增）
- 配置项：`jev.model`（默认 `jev-latest`）、`jev.fail-open`（默认 `true`）、
  `jev.max-retries`、`jev.initial-backoff-ms`。
- `JevException`：fail-closed 模式下由决策调用方感知的异常类型。
- `RemoteJevClientTest`（13 个用例）：基于 JDK 内置 `HttpServer` 桩，覆盖
  三种原语解析、429 重试后成功、400 不重试、重试耗尽、答案越界/缺失/非法 JSON、
  fail-open 回退本地、fail-closed 抛错。
- `docs/ADR-002-systemone-alignment.md` 架构决策记录。
- `jev-runtime-common` 显式声明 `jackson-databind` 依赖（由 Spring Boot BOM 管理版本）。

### Notes（说明）
- 默认行为不变：`jev.fallback-local=true` 时仍用确定性本地兜底，clone 即可离线端到端运行，
  无需任何外部凭据；填入 `jev.api-key` 并置 `fallback-local=false` 即切换到远程 Jev。
