# AIGate Current Phase

> 总控冻结方案、接口、任务与验收；开发导师只负责讲解、实现、测试和 Code Review。当前任务优先于历史阶段讨论。

## 1. Current Status

**Phase 3 — Invocation Records & Basic Visibility（ACTIVE）**

- Phase 1 / Phase 2：CLOSED
- P3-T01：DONE / ACCEPTED（业务目标/ADR-003）
- P3-T02：DONE / ACCEPTED（Flyway V7/Entity/Mapper）
- P3-T03：**DONE / ACCEPTED（成功调用记录）**
- **Current Task：P3-T04 — Management Invocation History Query（ACTIVE）**
- Next：P3-T05 — Failed Invocation Recording（NOT STARTED）
- P3-T06、P3-T07：NOT STARTED

## 2. P3-T03 Acceptance — 2026-10-18

实现提交：`ee38c95f20781de6aa219ea80b99ab590e7016bb`。

生产代码已审查：
- 新增 InvocationTrackingFacade，显式包装 ModelProxyService。
- RuntimeInvokeController 保持原 `POST /v1/invoke`、身份来源 SecurityContext、`@Valid`，仅改为调用 Facade。
- 成功调用后用 `System.nanoTime()` 计算非负 durationMs，写入 SUCCESS / null errorCode / applicationId / createdAt。
- Mapper.insert 异常被局部隔离，安全固定 warn，不覆盖原 InvokeResponse，不自动重试。
- Provider 失败不吞原异常，T03 不写 FAILED；未认证 401、非法请求 400 均不进入 Facade。
- 无新 schema / MQ / Redis / AOP / 新公开 API。

测试代码：`InvocationTrackingFacadeTest`（写入失败仍返回同一 InvokeResponse，Provider 异常透传）和 `RuntimeInvokeRecordingIntegrationTest`（真实 MockMvc + Testcontainers MySQL + 本地 HTTP Provider，SUCCESS 写入、两次调用两条记录、401/400 无记录、Provider 500/429 错误原样回归）。

**结论：P3-T03 DONE / ACCEPTED，无阻塞性问题。** 本次提交未返回 CI status/workflow run，未取得该提交的 Maven 汇总；本次依据 GitHub 代码/测试审查与用户既有的提交前测试约定验收，不声称独立执行 149+ 测试。TD-009 继续保留。

---

## 3. P3-T04 — Business Goal

> 管理员能通过现有 HTTP Basic 管理入口，查看**某一个 Application 最近的模型调用记录**，快速知道成功/失败、错误类别和耗时。此任务是 Phase 3 第一个可对管理端使用的调用历史查询 API。

目前 P3-T03 成功记录已落库，但管理员无法通过 HTTP 查询。优先做一个最简单、受限、无敏感信息的列表接口，不做分页平台和统计报表。

## 4. Frozen API Contract

```http
GET /api/applications/{applicationId}/invocations?limit=20
Authorization: Basic <management-credentials>
```

成功：**HTTP 200，JSON 数组**（无分页包装对象）；按 `id DESC` 最新在前。

响应示例：

```json
[
  {
    "id": 12,
    "applicationId": 3,
    "status": "SUCCESS",
    "errorCode": null,
    "durationMs": 157,
    "createdAt": "2026-10-18T09:00:00Z"
  }
]
```

请求约束：
- `applicationId` 仅来自 URL path，必须确认 Application 存在，调用 `ApplicationService.getApplicationById(id)` 复用现有 404 `APPLICATION_NOT_FOUND`。
- `limit` 默认 **20**；必须在 **1～100**，包括两端。0、负数、101、非数字均返回 400 `INVALID_REQUEST`，安全固定消息，不返回 500。
- 无记录返回 `[]`，不是 404；Application 本身不存在才 404。
- 此端点只在现有 `/api/**` HTTP Basic 管理链，未认证 401。Runtime Application Bearer Key 不可代替管理认证。
- 不暴露客户端 Invoke Request/Response、Provider URL/Secret、Application API Key、Prompt、原始上游错误 Body。
- 字段只允许：`id, applicationId, status, errorCode, durationMs, createdAt`。不要直接返回数据库 Entity 或任意其他内部字段。
- P3-T05 之前只会查到 P3-T03 保存的 SUCCESS，FAILED 数据如果被测试预置可正常显示，但**本任务不实施 FAILED 自动写入**。

## 5. Frozen Minimal Architecture

建议新增：
- `invocation.controller.InvocationQueryController`：只接参数/返回 DTO，映射上述固定管理 API；不要修改原有 /v1/invoke。
- `invocation.service.InvocationQueryService`：先调用已有 `ApplicationService.getApplicationById` 验证存在，再查询 InvocationRecordMapper。
- `invocation.dto.InvocationRecordResponse`：只暴露合同中的六个字段（可用 record）。
- 最小参数错误类型/Handler（若原项目尚无可复用的 400 路径）：类型转换错误如 `limit=abc` 和范围错误如 `limit=101` 都必须返回安全 400，不让 GlobalExceptionHandler 的异常兜底变为 500。

查询语义：`WHERE application_id = ? ORDER BY id DESC LIMIT ?`，按已实现 V7 复合索引 `(application_id,id)`。可使用 MyBatis-Plus `LambdaQueryWrapper`；limit 必须先转换为 int 并验证 1..100 后才拼入固定 SQL 尾部。**不接受用户提供的 SQL 片段**。不需要额外分页依赖、动态 SQL XML、缓存或复杂 Repository 框架。

依赖方向：

```text
Management HTTP Basic
→ InvocationQueryController
→ InvocationQueryService
  ├── ApplicationService.getApplicationById（复用存在性检查）
  └── InvocationRecordMapper → MySQL V7
→ List<InvocationRecordResponse>
```

现有 InvocationTrackingFacade/ModelProxyService/ProviderHttpClient 完全不改职责。

## 6. Tasks

1. 新增 Query Controller / Service / Response DTO，实施合同和合法 limit。
2. 处理非数字、超界参数、Application 不存在与无历史的具体 HTTP 行为。
3. 确保按 Application 过滤、id 降序、限制数据库层最多加载 limit 条（**不能先全表加载到 Java 再截取**）。
4. 使用现有管理 HTTP Basic；不增加新的 SecurityFilterChain、RBAC 或 Runtime 查询端点。
5. 补充 MockMvc + Testcontainers MySQL 集成测试，复用之前数据库和 MockProvider 测试支持；记录可直接通过 Mapper 预置，避免每个查询测试都发真实 LLM 请求。
6. 更新 API 设计文档（实现前仍为 PLANNED；代码完成时记录实际接口与响应）。
7. 完整回归已有测试；报告本地 Maven 的 Tests run / Failures / Errors / Skipped / BUILD 结果。

## 7. Acceptance Criteria

- 管理 Basic 请求成功返回合法 JSON 数组，只含六个约定字段。
- 默认 20，显式 1 / 100 合法；0、负数、101、非数字为 400 INVALID_REQUEST。
- 预置两个 App 的交错数据，A 只能看到 A；结果按 id DESC，真实 SQL 查询限定条数。
- 20+ 数据能验证默认截断；limit 参数控制返回上限；不允许不限量读取。
- Application 不存在 404 APPLICATION_NOT_FOUND；存在但无记录 HTTP 200 []。
- 未认证管理请求 401；仅持有 Runtime Application API Key 不得访问管理 API。
- 预置 SUCCESS/FAILED 均能映射 DTO（P3-T05 才让真实失败自动生成记录）。
- 返回 JSON 及错误消息不泄露数据库内部对象、凭证、Prompt、模型文本、内部 Provider URL。
- Runtime POST /v1/invoke 及 P3-T03 SUCCESS 持久化的测试继续通过；不新增 Flyway 迁移。
- 可实际执行完整 Maven 测试并提供结果；不强制增加新框架。

## 8. Knowledge / Boundaries

导师本任务只需重点解释：**为什么管理端要有查询 API、业务记录和 Entity 的边界、MySQL 复合索引与 ORDER BY/LIMIT、参数校验为什么影响安全及性能、404（资源不存在）与 []（无数据）的业务区别**。先业务、执行流程、技术原理，再实现代码。避免重讲 P1/P2 基础。

**明确不做：** FAILED 自动写入（P3-T05）、30 天定时清理（P3-T06）、分页/统计/费用/Quota、Redis/MQ/ES、微服务、SSE、路由、生产 RBAC。

**Execution Gate：P3-T04 ACTIVE；P3-T05 NOT STARTED。** 开发导师按本文件执行，完成代码/测试后回总控验收；通过且无阻塞时总控自动启动 P3-T05。
