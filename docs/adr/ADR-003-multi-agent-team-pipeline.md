# ADR-003：弹性多 Agent 团队管线 —— 原生 leader–member 实现（不接入 AgentScope 框架）

- 状态：已采纳（Accepted）
- 日期：2026-10-02
- 分支：`feat/multi-agent`
- 关联：ADR-002《RemoteJevClient 对齐 System One 协议》（`feat/systemone-alignment` 分支）

## 背景（Context）

项目的设计哲学是「**单 Agent 为主线，弹性多 Agent 为补充**」（见 README）。
主线（`AgentOrchestrator` 的有界 ReAct 循环）已经把 Jev 五大用途做深；剩下的问题是：
**如何在不引入 token 爆炸、不推翻主线的前提下，提供多 Agent 能力？**

候选路径：

- **路径 A：接入 AgentScope 框架**，把通用 Agent Runtime 能力交给成熟框架，
  自己只做 Jev 决策中间件、Tool 治理和 Java 后端生产能力。
- **路径 B：原生实现**一个轻量的 leader–member 团队管线，复用现有决策层与编排器。

## 评估证据（Evidence）

### A-1：AgentScope Java 无可获取构件（决定性证据）

对 Maven Central 的两次查询（2026-10-02）：

```bash
curl -s "https://search.maven.org/solrsearch/select?q=g:io.agentscope&rows=30&wt=json"
# → response.numFound: 0

curl -s "https://search.maven.org/solrsearch/select?q=agentscope&rows=20&wt=json"
# → 无 io.agentscope 相关构件
```

即：AgentScope Java 文档站（java.agentscope.io）虽存在并描述了集成方式，
但**框架构件未发布到 Maven Central**。在本项目（Maven 多模块、可复现构建）中
「接入 AgentScope」在工程上不成立——声明依赖即构建失败，除非引入第三方私有仓库
或自行构建源码，二者都会破坏「clone 即可端到端运行」的项目承诺。

### A-2：架构哲学契合度

路径 A 要求把「编排 / 记忆 / 工具调用」的控制权交给框架运行时，与本项目
「把高频判断下沉到 Jev 决策层、统一控制面」的核心卖点冲突：
框架接管后，Jev 只能作为框架内的一个模型适配器存在，而非贯穿全链路的决策层。

### A-3：成本与风险

原生实现的核心工作量是一个协调器 + 一个 SPI + 一个装配类（约 300 行），
复用现有 `JevClient` / `JevGrader` / `AgentOrchestrator`，无新运行时依赖；
而框架接入的学习、升级与调试成本不可控，且构件不可用（A-1）。

## 决策（Decision）

采用**路径 B：原生 leader–member 团队管线**。

```text
POST /api/team/run
      │
      ▼
TeamPipeline（leader，jev-decision-layer）
  1. Jev CHOICE：从 jev.team.members 中选出 ≤ max-members 个成员
     （Jev 不可用时退化为确定性关键词匹配的本地兜底）
  2. 逐成员委派子任务 —— 经 TeamMemberRunner SPI
       └─ TeamConfiguration（jev-app）把 SPI 适配到 AgentOrchestrator，
          成员会话以 sessionId:memberId 命名空间隔离分层记忆
  3. Jev NOUL：逐成员验证产出是否实质完成子任务
  4. JevGrader（SCORE）：对综合答案评分（eval-as-gate 语义复用）
      │
      ▼
TeamRunResult（synthesizedAnswer + 逐成员轨迹 + gradingScore）
```

关键设计约束：

- **无循环 Maven 依赖**：SPI（`TeamMemberRunner`）定义在决策层，实现（委派给
  `AgentOrchestrator`）在应用层；决策层不依赖 `jev-app`。
- **单 Agent 主线不受影响**：`AgentOrchestrator` 零改动；团队管线整体由
  `jev.team.enabled` 开关控制，`false` 即退回纯单 Agent 部署（端点返回 503）。
- **委派扇出有界**：`jev.team.max-members` 限制每次运行的成员数，防止 token 爆炸。
- **全程可观测**：`TeamRunResult.steps` 记录每个成员的 Jev 原语与取值。

## 后果（Consequences）

正面：

- 多 Agent 能力以约 300 行新增代码落地，主线零改动；
- 所有成员级决策复用 Jev 三原语，可观测、可测试（`TeamPipelineTest` 4 个用例，
  脚本化内存 `JevClient`，无网络）；
- 远程成员演进路径清晰：为 `TeamMemberRunner` 增加一个经 `A2aDirectory`
  委派远程 Agent 的实现即可（A2A 缝已存在）。

负面 / 已知限制：

- 综合步骤目前是确定性拼接，未来可用一次 LLM 调用生成综合稿；
- 成员目前是同一编排器的不同会话变体，能力差异完全来自配置与提示，
  尚无真正的异构成员；
- AgentScope 等框架的编排抽象（如消息总线、管道）未引入，复杂协作模式需自行演进。

## 重新评估触发条件（Revisit Trigger）

当 `io.agentscope`（或同类框架）在 Maven Central 出现稳定构件、且其编排模型
允许 Jev 继续作为外部决策层时，重新评估路径 A。
