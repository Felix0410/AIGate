# AIGate Roadmap

> 以实际交付为准。**已实现、已验收、计划/候选**必须明确区分。项目以业务问题驱动架构演进。

## Phase 1 — Foundation & Core Identity

**Status: CLOSED**

Team / Employee / Application、Spring Boot + Spring MVC + MyBatis-Plus + MySQL/Flyway 基础、Validation、异常处理、开发期 HTTP Basic 管理认证、MockMvc + Testcontainers。

## Phase 2 — Model Registry & Single Model Proxy

**Status: CLOSED / ACCEPTED（2026-10-18）**

P2-T01～T11 全部完成并验收。具体关闭依据：`docs/phase-2-closeout.md`。

实际交付：

```text
Provider / Model / ModelDeployment 管理
→ Provider Credential AES-GCM 加密
→ Application.defaultDeploymentId
→ Application API Key 创建 / 撤销 / Hash 校验
→ /v1/** 独立 Spring SecurityFilterChain
→ InvokeRequest / InvokeResponse
→ ProviderAdapter 转 OpenAI-Compatible JSON
→ RestClient 同步真实出站模型调用
→ ProxyErrorType 稳定、脱敏失败映射
→ 应用级 E2E 测试（Testcontainers MySQL + Mock Provider）
```

用户提供本地 Maven 汇总：**138 tests, 0 failures, 0 errors, 0 skipped**；**不是 GitHub CI**。当前数据库仍为 Flyway V1–V6。

Phase 2 不包含 Redis、MQ、微服务、SSE、自动重试、多模型路由、配额、账本或收费。

## Phase 3 — Business Problem → Minimal Feature

**Status: ACTIVE（2026-10-18 启动）**

**Current Task: P3-T01 — Business Goal & Scope Baseline（ACTIVE）**

Phase 3 尚未冻结下一项业务功能；因此第一项工作是确认真实业务需求，而不是凭技术栈清单决定开发任务。

优先评估的候选目标：**Invocation Traceability**——现在虽然能成功代理模型调用，但缺乏可按 Application 查询的历史调用结果/失败类别/耗时等跨请求业务记录。先确认这是当前要解决的问题。

方案比较：现有日志是否已足够 → 结构化日志 → 如确有查询需求，单 MySQL 最小调用元数据记录。不得在 P3-T01 直接创建 Usage 表、费用系统或 MQ。

### Phase 3 任务路线（逐步冻结）

| Task | 目标 | 状态 |
|---|---|---|
| P3-T01 | 确认业务痛点、最小范围、主流程、验收、候选方案与任务拆分 | **ACTIVE** |
| P3-T02 起 | 待 P3-T01 验收后根据已确认范围定义 | NOT STARTED |

**P3-T01 验收门**：确定单一主目标、为什么当前需要、最小 Happy Path、数据与安全边界、测试方案、下一任务清单及文档。验收通过后按用户规则自动推进下一任务。

## 保留的生产化/工程风险

- **TD-001**：管理面 HTTP Basic 只是当前开发阶段的安全基线。
- **TD-009**：尚无持续集成的自动测试门禁。
- **TD-011**：Provider endpointUrl 的 SSRF / 出站网络目标限制必须在公网或不可信管理者场景落实。
- **TD-010**：Provider Master Key 轮换/应急方案尚未设计。
- 其余技术债详见 progress / Phase 2 closeout。

这些风险不随 Phase 2 CLOSED 自动消失；是否属于 P3 当前核心功能应由业务目标及安全部署约束决定。
