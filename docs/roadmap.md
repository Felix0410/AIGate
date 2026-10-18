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

## Phase 3 — Invocation Records & Basic Visibility

**Status: ACTIVE（P3-T01 DONE / P3-T02 ACTIVE）**

**总控已于 2026-10-18 冻结业务目标**：允许管理员按 Application 查询最近模型调用的 SUCCESS/FAILED、固定错误分类、耗时及时间，解决 Phase 2 已能代理模型但缺少业务调用历史的问题。

**选定方案：** Flyway V7 一张 MySQL `invocation_record` 表，最小元数据同步 best-effort 写入；管理面只提供有上限的最近记录查询。设计取舍见 `ADR-003-invocation-recording-persistence.md`。不存原始 Prompt、模型回复、任何 API Key / Provider Secret 或原始上游错误。保留 30 天，后续做简单清理。

| Task | Deliverable | Status |
|---|---|---|
| P3-T01 | 总控冻结业务目标、范围、数据/API 契约、架构与 ADR | **DONE / ACCEPTED** |
| P3-T02 | V7 invocation_record + Entity/Mapper + DB Tests | **ACTIVE** |
| P3-T03 | 成功 Invoke 记录与写入失败隔离 | NOT STARTED |
| P3-T04 | GET /api/applications/{id}/invocations?limit=20 | NOT STARTED |
| P3-T05 | 失败 Invoke 的安全分类记录 | NOT STARTED |
| P3-T06 | 30 天清理 + 完整 E2E / Maven 回归 | NOT STARTED |
| P3-T07 | Phase 3 Closeout、文档、技术债、知识与面试表达 | NOT STARTED |

**P3-T02 当前只开发 Schema/Entity/Mapper/Test**，不修改生产 Runtime 调用，不抢跑 Controller。验收通过后由总控自动启动下一任务。

**明确不做：** 计费/用量账本、聚合仪表盘、Retry、SSE、多模型路由、Redis、MQ、ES、微服务、全量 Prompt/Response 存储。

## 保留的生产化/工程风险

- **TD-001**：管理面 HTTP Basic 只是当前开发阶段的安全基线。
- **TD-009**：尚无持续集成的自动测试门禁。
- **TD-011**：Provider endpointUrl 的 SSRF / 出站网络目标限制必须在公网或不可信管理者场景落实。
- **TD-010**：Provider Master Key 轮换/应急方案尚未设计。
- 其余技术债详见 progress / Phase 2 closeout。

这些风险不随 Phase 2 CLOSED 自动消失；是否属于 P3 当前核心功能应由业务目标及安全部署约束决定。
