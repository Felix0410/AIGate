# AIGate Progress

> 当前任务事实与验收记录。历史详细设计、各次验收证据参见 `docs/phase-2-closeout.md`、GitHub 历史提交及核心文档。

## 1. Current Status

- **Phase 1 — CLOSED**
- **Phase 2 — CLOSED / ACCEPTED（2026-10-18）**
- **Phase 3 — ACTIVE**
- **Current Task：P3-T01 — Business Goal & Scope Baseline（ACTIVE）**
- **Next Task：P3-T02（NOT STARTED；待 P3-T01 冻结业务范围）**

Phase 2 的关闭已通过完整十项检查：功能、Code Review、测试、架构、技术债、progress、architecture、必要 ADR、知识点和面试表达。

## 2. Phase 2 任务里程碑

| Task | 实际完成 | 状态 |
|---|---|---|
| P2-T01 | Provider / Model / ModelDeployment 实体、表与管理接口 | DONE / ACCEPTED |
| P2-T02 | CredentialService AES-256-GCM，Provider 密文存储 | DONE / ACCEPTED |
| P2-T03 | Application.defaultDeploymentId 绑定/切换/解绑 | DONE / ACCEPTED |
| P2-T04 | Application API Key CREATE/LIST/REVOKE、Hash 存储 | DONE / ACCEPTED |
| P2-T05 | 两条 SecurityFilterChain、API Key Runtime 认证和 ApplicationIdentity | DONE / ACCEPTED |
| P2-T06 | InvokeRequest、InvokeMessage、MessageRole、InvokeResponse 校验与 JSON 契约 | DONE / ACCEPTED |
| P2-T07 | ProviderAdapter + Registry + OpenAiCompatibleProviderAdapter | DONE / ACCEPTED |
| P2-T08 | RuntimeInvokeController、ModelProxyService、ProviderHttpClient + RestClient HTTP Happy Path | DONE / ACCEPTED |
| P2-T09 | ProxyErrorType：Provider HTTP/超时/坏 JSON、安全错误映射 | DONE / ACCEPTED |
| P2-T10 | E2E：Key 生命周期、默认 Deployment 切换、错误、真实超时、测试清理 | DONE / ACCEPTED |
| P2-T11 | Phase Closeout：阶段检查、文档、知识点、面试复盘及技术债 | **DONE / ACCEPTED** |

### 已验证的 Runtime 主流程

```text
/api/** HTTP Basic 配置 Provider / Model / Deployment / Application
→ Application API Key 只创建时返回明文（高熵 Secret SHA-256 Hash 存储）
→ POST /v1/invoke + Bearer Key
→ ApplicationIdentity
→ Application.defaultDeploymentId → enabled ModelDeployment → Provider
→ ProviderAdapter → RestClient → 上游 Provider
→ InvokeResponse 或脱敏 ApiErrorResponse
```

上游 Provider Credential 使用 AES-GCM 加密存储，出站时解密；不会把客户端 Application API Key 转发上游。

### 测试结果与证据边界

用户于 2026-10-17 提供本地 Maven 输出：

```text
[INFO] Tests run: 138, Failures: 0, Errors: 0, Skipped: 0
```

不是 GitHub CI status / workflow artifact；未单独记录完整退出码与 Surefire XML。因此 Phase 2 功能验收通过，但 CI 缺口 **TD-009 保留**。

### 数据库状态

Flyway V1–V6，MySQL 8.4；无新增 Usage/Quota/InvocationRecord 表。Key 状态 ACTIVE/REVOKED，新 Key 不会自动撤销旧 ACTIVE Key。

## 3. P3-T01 — ACTIVE

**Business Goal:** 确定 Phase 3 要解决的下一个真实业务问题，冻结最简 Happy Path 和阶段路线。

当前优先评估：**是否需要最小调用可追踪性**。Phase 2 已能调用，但没有记录每次 Invoke 的可查询历史结果及耗时；这只是业务候选，尚未批准任何新表、接口或中间件。

待完成：

- 核实调用追踪缺口及目标使用者
- 比较应用日志、结构化日志、最小 MySQL 元数据方案及不做的代价
- 冻结单一目标、最小范围、隐私保护、验证与任务拆分
- 更新 requirements / roadmap / architecture，必要时 ADR
- 完成 P3-T01 评审后自动启动冻结的 P3-T02

**本任务不开发生产代码、不新增 Migration、不引入 Redis/MQ/ES。**

## 4. 技术债

- **生产/安全关键：** TD-001（HTTP Basic 管理面）、TD-011（Provider 出站 URL/SSRF）、TD-010（Provider 主密钥轮换）
- **质量及运维：** TD-009（CI Gate）、TD-006（缺 requestId / traceId / path）、TD-008（增量补全测试）
- **按实际业务演进：** TD-002（授权模型）、TD-003（事务边界）、TD-004（跨模块 Mapper 依赖）、TD-005（字符串错误码）、TD-007（单组织唯一约束）

Phase 3 规划需考虑技术债，但不能为丰富技术栈而在一个任务里全部偿还。

## 5. Advancement Rule

用户已授权：单任务验收通过、无阻塞时完成收尾文档后自动启动下一任务；未通过则修复复验。跨 Phase 先完成 Phase Closeout。

**当前：Phase 3 ACTIVE / P3-T01 ACTIVE；P3-T02 NOT STARTED。**
