# AIGate Current Phase

> 本文件为**总控冻结的唯一当前任务执行合同**；开发导师只讲解、编码、测试和 Code Review，不重新选择业务方案。2026-10-18 已调整任务粒度：按完整业务能力交付，相关子功能不再逐个启动、提交验收。

## 1. Current Status

**Phase 3 — Invocation Records & Basic Visibility：ACTIVE**

| Task | Scope | Status |
|---|---|---|
| P3-T01 | 总控业务需求/方案/ADR-003 | DONE / ACCEPTED |
| P3-T02 | Flyway V7 调用记录表/Entity/Mapper | DONE / ACCEPTED |
| P3-T03 | Runtime 成功调用 SUCCESS 记录 | DONE / ACCEPTED |
| P3-T04 | 管理端调用记录列表查询 | **DONE / ACCEPTED** |
| P3-T05 | 失败调用记录 + 30 天保留清理 + 全链路 E2E | **ACTIVE** |
| P3-T06 | Phase 3 Closeout | NOT STARTED |

**原路线调整：** 原 P3-T05 失败记录与原 P3-T06 数据清理/E2E 合并为新的 P3-T05；原 P3-T07 收尾改为 P3-T06。此后旧编号仅作为历史计划，不应重复执行。

### P3-T04 Acceptance — 2026-10-18

实现提交：`aae1546785e9daf7db3d40f989c2dfbaaede82ec`。

已实现并审查：`InvocationQueryController`、`InvocationQueryService`、`InvocationRecordResponse`、`InvalidInvocationQueryException` 和独立的 400 Error Handler；管理 API `GET /api/applications/{applicationId}/invocations?limit=20`。

结果：默认 20、允许 1..100、非法 400 INVALID_REQUEST、Application 不存在 404、无历史 200 []、按 `application_id` 隔离并 `id DESC LIMIT` 数据库端查询、仅六字段 DTO、HTTP Basic 管理身份边界。代码及 `InvocationQueryIntegrationTest` 的测试覆盖审查通过，无阻塞性问题。

**P3-T04 DONE / ACCEPTED。** 本次没有关联的 GitHub CI status/workflow 和独立 Maven 测试统计；基于代码审查及项目既定“提交代表本地已通过测试”的工作约定验收，不虚构测试数量。TD-009 仍开放。

---

## 2. Phase 3 Business Goal

真实调用成功和基础管理查询已实现。最后补齐的业务缺口是：**模型代理失败的请求现在不会记录 FAILED**；调用记录也缺乏 30 天生命周期管理。完成 P3-T05 后管理员才可以同时查成功和失败历史、区分原因并控制记录增长。

本阶段仍是模块化单体 + MySQL V1–V7 + Spring MVC/RestClient，不是计费账本、准确审计系统或分布式追踪平台。

## 3. P3-T05 — Business Goal

一次完成调用历史的**失败路径与生命周期闭环**，并真实验证：

- 有效 Runtime 调用失败也产生 FAILED + 稳定安全错误码；
- 记录保存失败不能覆盖原有错误或成功结果；
- 保留最近 30 天，定期清理到期记录；
- 现有管理查询可同时返回 SUCCESS/FAILED；
- 全链路回归、隐私和资源检查均通过。

**开发方式：同一任务内按 失败记录 → 清理 → E2E 的顺序实现并自测，中途无需返回总控单独验收。** 完成整包后一次提交/验收，允许开发导师视实际需要在同一任务内提交多个 Git commit。

## 4. Scope A — FAILED Invocation Recording

当前代码：`RuntimeInvokeController → InvocationTrackingFacade → ModelProxyService → ProviderAdapter/RestClient`；T03 的正常调用只记 SUCCESS。

在**现有 InvocationTrackingFacade** 扩展失败路径，不再新增一层 Facade：

1. 只对已经通过 Runtime API Key 身份认证和 `@Valid`、进入 `ModelProxyService.invoke` 的请求记录；无效 API Key 401、DTO 校验 400 不计入记录。
2. 正常 Provider Invoke → 保持 SUCCESS/null errorCode，写入**恰好一条**。
3. `ModelProxyService.invoke` 抛 `ModelInvocationException` 时，记录 FAILED + `exception.getErrorType().getCode()`（固定安全代码），然后重新抛出**同一个原异常**，让已有 GlobalExceptionHandler 保持原来的 HTTP/status/code。
4. `InvalidProviderResponseException` → FAILED / `PROVIDER_INVALID_RESPONSE`，原异常继续抛出；不泄漏其原始 message。
5. 对业务执行中的其他 `RuntimeException` → FAILED / `INTERNAL_SERVER_ERROR`，并原样抛出；只保存固定安全码，不保存异常 message/stacktrace。不要捕获 JVM `Error` 或试图保证进程崩溃时必然记录。
6. 与 T03 一致，`System.nanoTime()` 量取代理执行耗时（成功或失败路径都使用同一计时定义，**不包括记录写入**），`createdAt` 使用 Instant；durationMs 不得为负。
7. 在 Facade 中明确区分“模型代理异常”和“记录写入异常”，避免一条 SUCCESS insert 失败被误记为 FAILED；**同一次调用最多一次正常结果记录尝试**。
8. 记录入库 SQLException / RuntimeException 被局部 best-effort 隔离，日志只能输出固定安全告警，不得输出 Prompt、Credential、Bearer、模型正文、上游 error body、异常 cause/message。失败记录入库失败仍须把**原始代理异常对象**继续上抛；成功记录入库失败仍须返回原 InvokeResponse。
9. **不要**在整个 HTTP 调用 + DB 写入外层添加 `@Transactional`，不写 Retry/Fallback/MQ。

### Frozen Failure Flow

```text
valid POST /v1/invoke
→ TrackingFacade
→ ModelProxyService
  ├── succeeds → best-effort SUCCESS → original InvokeResponse
  └── throws original exception
         → classify safe FAILED errorCode
         → best-effort FAILED
         → rethrow original exception
         → existing GlobalExceptionHandler → original ApiErrorResponse
```

只做“已分类错误码”，不保存原始 HTTP 状态、内部 URL 或请求内容。Application 默认部署缺失等运行时配置错误进入 Facade 后也属于业务失败，使用原 ProxyErrorType 记录；未认证与请求校验失败不记录。

## 5. Scope B — 30-Day Retention

对 `invocation_record.created_at` 建立最简单、可验证的保留策略：

- 默认保留 **30×24 小时**，使用 UTC Instant 语义；`created_at < now - 30 days` 删除，**边界等于 cutoff 的记录暂不删**。
- 新增独立轻量 `InvocationRecordRetentionService`（可用 MyBatis Mapper/JdbcTemplate 完成按 cutoff 删除），以及一个 Spring `@Scheduled` 每日 UTC 定时触发器。调度配置若项目尚未启用，只添加必要的 `@EnableScheduling`，不能重复启动任务。
- 为确定性测试，把“基准时间”和删除逻辑分离：公开/包级方法接受 `Instant cutoff` 或可注入 `Clock`。测试不依赖真的等待一天。
- 只清理 invocation_record，不删除 Application、API Key、其他表；删除失败应以安全告警隔离，不影响模型运行/应用启动。
- 目前是单进程、小数据量项目，不提前引入 Quartz、分布式锁、MQ。当前默认可使用一次定时 `DELETE WHERE created_at < ?` 的最简方案；但在文档明确大表全表扫描/大事务风险，真实数据量增长后再引入索引和分批删除。**禁止修改 V7**，如有真实数据库索引缺口需要新的 V8，则先由总控评估；本阶段不要为假想高并发强行加库表。
- 清理属于“尽力保留”策略，不能宣称严格到秒删除或多节点恰好一次。

## 6. Scope C — Full E2E & Regression

复用现有 MySQL Testcontainers、MockMvc、JDK HttpServer 和 T03/T04 测试；优先补增量场景，不复制整个测试夹具。

验收要求：

1. 真实 HTTP 200 → 单条 SUCCESS，状态/耗时/时间正确。
2. Provider 400、401/403、429、5xx、timeout、invalid response → 对应 FAILED + ProxyErrorType 安全 errorCode，原有对客户端 HTTP/status/code 不变。
3. 默认 Deployment 未配置/disabled 等运行时业务前置错误也按原错误码记 FAILED；无外部 Provider 出站。
4. 成功记录插入失败仍返回 200 和原 Provider InvokeResponse；失败记录插入失败仍返回原 Provider 错误响应且错误码不变。单位测试核对**同一个异常对象**向上传播。
5. 未授权 401、Bean Validation 400 不记录；API Key 不泄漏；一条请求不会记入两条结果。
6. 管理端 GET 查询正确展示混合 SUCCESS/FAILED，按 App 隔离、数据库 limit 1..100、排序与安全 DTO 不变。
7. 预置 30 天以前、刚好 cutoff、cutoff 之后记录；手动触发清理后仅旧记录删除，其他记录和 Application 保留。测试调度配置及异常隔离，避免线程/测试上下文泄漏。
8. 不保存 Prompt、回复正文、密钥、内部 URL 或原始错误；日志不打印原始异常/敏感上下文。
9. 实际运行 Maven 全量测试，提交明确 `Tests run / Failures / Errors / Skipped / BUILD SUCCESS`，并关注测试资源可重复清理。
10. 核对 P2 全量安全与模型代理回归：既有 `POST /v1/invoke` 请求响应契约不变、provider HTTP 不重试。

## 7. Tasks / Architecture / Learning

同一任务内三段实施（不再逐个验收）：
- Step 1：Facade 失败记录 / 异常透传 / 最小单元与 MockMvc 测试。
- Step 2：RetentionService + UTC scheduler / 确定性数据库测试。
- Step 3：完整 E2E 回归、数据库增长成本检查、文档更新、Maven 运行结果。

架构保持：

```text
Runtime Controller → InvocationTrackingFacade
                  → ModelProxyService (existing)
                  → InvocationRecordMapper (best effort)

Management QueryController → QueryService → Mapper (existing)

Scheduled RetentionService → MySQL invocation_record
```

导师只重点讲本任务新增的 **异常路径记账与原异常保留、为什么记录系统是 best-effort、Spring Scheduling 的最小用法、UTC 时间边界与 SQL 删除代价**；沿既有业务→原理→执行流程→代码，避免反复重讲 CRUD/MyBatis 初级知识。

## 8. Out of Scope / Phase Gate

不做 Token Usage、费用、计费账本、统计图表、路由、Redis/MQ/ES、微服务、复杂幂等、请求级精确审计或基础设施堆叠；不提前优化为复杂事件驱动。

沿用 ADR-003；此次只合并执行任务，没有新的重大架构决策，不新增 ADR。保留 TD-001 管理 Basic、TD-009 CI、TD-010 Master Key Rotation、TD-011 Provider Endpoint SSRF 等生产前风险。

**Current: P3-T05 ACTIVE。完成失败记录 + 清理 + E2E 后统一验收；通过且无阻塞时自动启动 P3-T06 Phase Closeout。** Phase 3 尚未 CLOSED。
