# AIGate Module Design

## 部署形态
当前是**单 Spring Boot 进程中的模块化单体**，共享 MySQL；模块划分是代码职责边界，不是已拆分的网络服务。

## 管理面
`team`、`employee`、`application`、`provider`、`model`、`deployment`、`credential` 等模块负责管理实体、关联、默认部署和凭证生命周期。`/api/**` 当前采用 HTTP Basic。

## 运行面
```text
Spring Security Runtime FilterChain
  → ApiKeyAuthenticationFilter / Service
  → ApplicationIdentity (SecurityContext)
  → RuntimeInvokeController (@Valid InvokeRequest)
  → InvocationTrackingFacade（SUCCESS/FAILED best-effort）
  → ModelProxyService
       ├── Application / ModelDeployment / Provider Mapper
       ├── ProviderAdapterRegistry
       ├── CredentialService.decrypt (可选)
       └── ProviderHttpClient (Spring RestClient)
  → OpenAI-Compatible Provider
  → ProviderAdapter.parseResponse → InvokeResponse
```

## 职责边界
- **Controller**：请求入口、参数校验、使用已认证身份，不自行解析 Bearer Secret。
- **ModelProxyService**：根据 Application 当前默认部署解析业务配置、检查 enabled、选择 Adapter、编排调用；不重复实现 Provider JSON。
- **ProviderAdapter / Registry**：将 AIGate DTO 映射到供应商协议，并校验解析上游成功响应；不连接数据库或 HTTP。
- **ProviderHttpClient**：执行同步 POST、设置独立 Provider Bearer、区分上游状态/网络超时；不自行选部署。
- **GlobalExceptionHandler / ProxyErrorType**：安全稳定地将本地配置/上游错误转换为 ApiErrorResponse。
- **CredentialService**：AES-GCM 保存/还原 Provider Secret；与 API Key hash 验证链严格分离。

## 本阶段架构取舍
当前没有引入异步队列、Redis、Nacos、微服务、SSE 或重试。为真实高并发/可用性需求再评估复杂方案；避免将业务范围之外的技术当成既成事实。

## 关联文档
`architecture.md`、`domain-model.md`、`api-design.md`、`database-design.md`、`knowledge-map.md`。

---

## Phase 3 模块扩展（P3-T05 已实现并验收）

新增轻量 `invocation` 业务模块。P3-T02 先只有 `InvocationRecord` + `InvocationRecordMapper`，不改变 P2 的代理行为。

后续任务按需加入：
- P3-T03 `InvocationTrackingFacade`：**已实现并验收**；包装原 ModelProxyService，用单调时钟量取耗时，成功后 best-effort 写 SUCCESS，不接管 Provider HTTP。
- P3-T04 `InvocationQueryService` / 管理 Controller：**DONE / ACCEPTED**；管理 Basic 查询指定 Application 最近记录，限制 limit 1..100。
- P3-T05：**DONE / ACCEPTED**；Facade 记录失败安全码，继续抛出原始错误；RetentionService 和 Scheduler 实现 30 天自动清理。
- P3-T06：Phase 3 Closeout（**ACTIVE**），阶段审查、测试稳定性与文档/面试整理，不额外增加清理功能。

依赖方向：Runtime 调用边界 → Tracking → Invocation Mapper/MySQL；Provider Adapter 只负责协议，ProviderHttpClient 只负责网络；管理查询不读 Prompt/模型响应原文。不增加 MQ/Redis/ES 等额外基础设施。

**当前代码状态 2026-10-18：P3-T03/P3-T04/P3-T05 DONE / ACCEPTED；P3-T06 Phase Closeout ACTIVE。**

### P3-T05 新增组件职责

**InvocationTrackingFacade**：
- 包装 ModelProxyService 的同步 invoke，使用 try-catch 双路径控制流。
- 成功路径：记录 SUCCESS，返回 InvokeResponse。
- 失败路径：classifyErrorCode 分类异常 → 记录 FAILED + 安全错误码 → throw original（原异常对象透传，不变换、不吞）。
- saveBestEffort：best-effort 隔离，记录写入失败只输出固定安全警告，不影响业务响应。
- 不使用 @Transactional。

**InvocationRecordRetentionService**：
- 接收 Instant cutoff，调用 Mapper.deleteCreatedBefore。
- 不包含事务编排、批次框架或通用清理引擎。

**InvocationRecordRetentionScheduler**：
- @Scheduled(cron = "0 0 3 * * *", zone = "UTC")，每天 UTC 03:00 执行。
- 计算 cutoff = Instant.now() - Duration.ofDays(30)，调用 RetentionService。
- 记录成功/失败条数（安全边界内），异常时只输出固定告警，不向上抛出。

**InvocationSchedulingConfig**：
- 项目级 @EnableScheduling，不重复启用。不引入 Quartz/Redis 锁/MQ。

**总控验收更新：** P3-T05 生产代码和测试覆盖检查通过；后续只做集中阶段收尾和测试稳健性检查。模块职责继续保持 Tracking Facade / Query Service / Retention Scheduler 分离，严禁改造成通用消息驱动体系。
