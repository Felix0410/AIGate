# Phase 2 Closeout — Model Registry & Single Model Proxy

**Review date:** 2026-10-18  
**Decision:** **CLOSED / ACCEPTED**  
**Code baseline:** latest Phase 2 feature change `2943dce28313742acc608fa11aaf962e23aae3c0`; subsequent changes through 2026-10-18 before this closeout were documentation reconciliation only.  
**Next phase:** Phase 3 starts with requirements/boundary definition; there is no frozen Phase 3 business feature scope in the prior roadmap.

## 1. Business Goal — 实际完成

建立从管理面模型配置到运行面模型调用的最小真实闭环：

```text
Management HTTP Basic
→ Provider / Model / ModelDeployment
→ Application.defaultDeploymentId
→ Application API Key (CREATE/LIST/REVOKE)
↓
Client POST /v1/invoke + Bearer Application Key
→ Runtime SecurityFilterChain
→ ApplicationIdentity
→ ModelProxyService (Application → enabled Deployment → Provider)
→ ProviderAdapter / ProviderHttpClient / Spring RestClient
→ OpenAI-Compatible Provider HTTP
→ InvokeResponse or sanitized ApiErrorResponse
```

**验收结果：核心调用闭环已落地。** 当前只支持同步非流式的默认单 Deployment；不含路由、流式、平台配额/用量账本、自动重试/熔断。

## 2. Ten-point Phase Closeout Review

| Gate | 结论 | 依据 |
|---|---|---|
| 1. 功能检查 | PASS | P2-T01～T10 已逐项验收；管理配置、绑定、Key、真实 HTTP Invoke、错误均有实现 |
| 2. Code Review | PASS with debts | P2-T05～T10 的生产代码、Adapter、Security 和测试已按阶段审查；遗留改进单列 |
| 3. 测试检查 | PASS (local reported) | 用户提供本地 Maven 汇总 **138 run, 0 failures, 0 errors, 0 skipped**；不等于 CI 执行证据 |
| 4. 架构检查 | PASS | 模块化单体，明确 Controller、Service、Adapter、HTTP 边界；未引入无需求技术 |
| 5. 技术债记录 | PASS | TD-001～TD-011 有历史记录，明确生产前安全与工程风险 |
| 6. 更新 progress | PASS | 关闭 Phase 2，开始 Phase 3 目标定义 |
| 7. 更新 architecture | PASS | 记录当前真实 Runtime 链路、验证边界与阶段状态 |
| 8. 必要时增加 ADR | PASS | 既有 ADR 记录认证决策；此次收尾未新增重大方案，不强制创建 ADR |
| 9. 阶段知识点 | PASS | knowledge-map 已按 P2-T01～T10 顺序补全，包含 Spring Security、密码学、网络、测试 |
| 10. 面试表达 | PASS | interview-notes 已补齐 Phase 2 业务介绍和设计取舍问题 |

## 3. Test Evidence

用户于 2026-10-17 提供：

```text
[INFO] Tests run: 138, Failures: 0, Errors: 0, Skipped: 0
```

项目测试基线包含 MockMvc、MySQL Testcontainers、Flyway 和本地 HTTP Mock Provider；覆盖 ACTIVE/REVOKED Key、默认 Deployment 切换、凭证不串用、Provider 错误/超时与 E2E Happy Path。

**证据边界：** 此汇总来自用户本地终端，不是仓库中可下载的 CI/Surefire Artifact；没有提供独立完整命令退出码。不可声称 GitHub CI 绿灯。CI 测试门禁 TD-009 仍然开放。

## 4. Database / Security / API Facts

- Flyway V1–V6；本阶段后续未新增 schema。
- 管理面 `/api/**`：临时 HTTP Basic。
- 运行面 `/v1/**`：Application API Key，仅保存高熵 Secret SHA-256 摘要；SecurityContext 中只留 ApplicationIdentity。
- Provider Credential：AES-256-GCM 加密存储，出站时解密；客户端 API Key 不转发 Provider。
- `POST /v1/invoke` 使用 `InvokeRequest(messages, temperature?, maxTokens?)`，返回 `InvokeResponse(content, model, finishReason)`。
- Provider Adapter 映射 OpenAI-Compatible JSON；RestClient 执行同步 HTTP。
- ProxyErrorType 将上游 HTTP/超时/结构错误映射为 502/503/504 和固定安全错误码；本地缺少或禁用默认部署返回 409。

## 5. Open Technical Debt & Release Boundaries

**生产安全门槛（正式公网或不可信租户前必须评估/处理）：**
- **TD-001**：管理面 HTTP Basic 为开发期认证基线；正式用户/权限体系需重新设计。
- **TD-011**：Provider endpointUrl 触发服务端请求；需要 URL scheme/目标地址、DNS、重定向及内网/metadata 出站限制（SSRF 风险）。
- **TD-010**：Provider Master Key Rotation 缺失；若正式运营需确定密钥轮换/应急方案。

**工程质量优先改进：**
- **TD-009**：没有 GitHub CI Test Gate；本地通过不替代自动化门禁。
- **TD-006**：ApiErrorResponse 无 requestId/traceId/path；需要真实排障时补齐。
- **TD-008**：测试目前围绕关键链路；新业务应增量覆盖。

**按实际业务演进而不是提前强行实施：**
- TD-002 暂无 Role/Permission；TD-003 事务边界仍需明确；TD-004 跨模块 Mapper 直接依赖；TD-005 Error Code 字符串；TD-007 单组织唯一约束假设。

保留未实现项目能力：Redis、MQ、Elasticsearch、微服务、自动重试、SSE、Routing、Quota/Usage/Cost Ledger。未实现不是当前阶段缺陷，不将其写成已完成事实。

## 6. Architecture Decision Record

沿用已有 ADR-001（管理认证基线）和 ADR-002（Application Runtime Authentication）。本次为既有方案的完成与复盘，**没有新的重大架构替代决策**，因此不强制新增 ADR。

## 7. Final Closeout Decision

Phase 2 达成已冻结的业务目标、代码与测试验收标准，完成 Phase-level 复盘与文档沉淀，**2026-10-18 正式 CLOSED / ACCEPTED**。

这表示 Phase 2 学习与最小可运行模型代理闭环完成，**并不表示公网生产安全审计或生产级运营能力全部完成**。

用户已明确授权启动 Phase 3；下一步首先应冻结新的业务问题/最小 Scope，再启动该阶段的功能编码，防止未经确认直接引入 Redis/MQ/路由/配额。
