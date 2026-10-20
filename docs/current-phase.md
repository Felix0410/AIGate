# AIGate Current Phase — P4 ROADMAP ALIGNMENT / DEVELOPMENT PAUSED

> **当前最新决议（2026-10-20）**：P4～P10 的业务开发顺序已获用户 approve，仅代表**高层路线 APPROVED**，**不代表 P4 已重新启动**。P4-T01 PAUSED / NO CODING；下方先前的 P4 Application RPM 任务合同是历史草案。企业保底预算二次耗尽后的处理目前为 **PENDING USER DECISION**；总控会参考行业硬停止、降级、紧急审批机制提出候选。开发导师禁止继续编码。


> **2026-10-20 总控临时暂停开发（最高优先级）**：用户要求在继续任何新功能前先从最上层重新确认 AIGate 的最终产品目标、用户/Team/Application 关系、额度归属与开发顺序。**P4-T01 暂停执行，禁止开发导师基于下方旧的已冻结 P4 限流合同继续编码；ADR-004 作为待复核候选方案，非当前执行授权。** Phase 1～3 已验收的代码事实不变。确认整体路线后由总控重新冻结执行合同。下方 P4 细节全部是历史设计草案，暂不代表最终需求。

> **历史总控冻结执行合同（2026-10-20）。** 由总控制定业务、接口、算法、数据库、任务边界和验收标准，并**独立负责 docs/ 维护**；开发导师只负责“业务关联讲解 → 实现代码 → 测试 → Code Review → 向总控报告结果”。不要重新选题，不要自行引入 Redis/MQ/微服务。历史 Phase 3 详见 `docs/phase-3-closeout.md`。

## 1. Phase & Task Status

| Phase / Task | Status |
|---|---|
| Phase 1 / Phase 2 / Phase 3 | **CLOSED / ACCEPTED** |
| **Phase 4 — Scope Review** | **ON HOLD / 业务全局路线重新确认中** |
| **P4-T01 — End-to-End Application Rate Limiting** | **PAUSED / 不得继续编码** |
| P4-T02 — Phase 4 E2E Hardening & Closeout | NOT STARTED |
| Phase 5 | NOT STARTED |

此次遵循用户要求，**一个完整业务能力 = 一个开发任务**。P4-T01 一次完成 Migration、配置接口、算法、Runtime 接入、测试；内部可分步骤编码，**不需要每步返回总控验收**。P4-T02 专注集中复核、补缺口和正式关闭，不再拆出低复杂度任务。

## 2. Business Goal

当前 Application 合法 API Key 可连续调用 `POST /v1/invoke`。若客户端程序失控或大量并发请求，网关在 Provider HTTP 前缺少本地保护；可能占用工作线程、上游能力及不必要的费用。

**Phase 4 交付目标：管理员为不同 Application 配置独立的模型请求速率策略，单 JVM 中网关在模型实际出站前原子消费令牌；超额返回安全 429 并零 Provider 出站。**

不是精确计费、每日/月度配额，也不声称跨实例全局限流。本阶段保留 Phase 2/3 模型调用、认证、SUCCESS/FAILED 记录与管理查询全部现有语义。

## 3. Frozen Design Decisions — ADR-004

**`docs/adr/ADR-004-application-rate-limiting.md` 已冻结：MySQL 存策略 + 单 JVM 令牌桶。**

为什么不选其他：
- Provider 自己 429 太晚，Provider 已收到请求；
- 每次请求用 MySQL 做原子计数会形成热行更新/锁竞争，不用 DB 持有实时令牌；
- 固定窗口更简单但交界处可出现约两倍突发；
- 令牌桶允许有限突发并平滑补充；
- Redis 适合真实多实例共享额度，但现阶段未发生分布式一致性问题，不引入；
- 不需要 MQ、Nacos、Spring Cloud、IP 限流或单独配置中心。

成本：每个合法 Invoke 多读取一次 Application 策略；桶占 JVM 内存，需要清理；重启令牌重置，多实例各自计数。**不能说任意滚动60秒绝对不超过 N；Token Bucket 只约束最大突发和平均补令牌速率。**

## 4. Frozen MySQL V8 Schema — PLANNED until code accepted

只能新增 Flyway `V8__add_application_rate_limit.sql`，不能修改 V1～V7：

```sql
ALTER TABLE application
    ADD COLUMN requests_per_minute INT NULL,
    ADD CONSTRAINT chk_application_requests_per_minute
        CHECK (requests_per_minute IS NULL
            OR requests_per_minute BETWEEN 1 AND 10000);
```

- `NULL`：不限流，**兼容已有 Application**，无隐式默认 60 RPM。
- `1..10000`：每个 Application 的令牌桶容量 N，平均每分钟恢复 N 个令牌。
- 在 `Application` Entity 新增 `Integer requestsPerMinute`；MyBatis-Plus 按现有驼峰映射读写。
- 旧 `POST /api/applications` 和 `PUT /api/applications/{id}` 的 JSON **保持原状**；旧 PUT **必须保留原有 requests_per_minute**，不能无意清空或重置。
- 关闭限流时管理专用 Service 用 `LambdaUpdateWrapper.set(Application::getRequestsPerMinute, null)` 或等价明确的 SQL SET NULL（MyBatis-Plus 默认 null 更新策略可能跳过；不可只 set 一个 null Entity 然后 updateById 期待有效）。
- 不新建 `rate_limit_policy` 表、计数表、日志表；不改 V7 `invocation_record`。

## 5. Frozen Management API — PLANNED

当前管理认证沿用 `/api/**` HTTP Basic。新增**两个端点**：

```http
GET /api/applications/{applicationId}/rate-limit
Authorization: Basic <admin>
```

响应 HTTP 200：

```json
{"applicationId":1,"enabled":false,"requestsPerMinute":null}
```

开启/修改（覆盖旧配置）：

```http
PUT /api/applications/{applicationId}/rate-limit
Authorization: Basic <admin>
Content-Type: application/json

{"enabled":true,"requestsPerMinute":60}
```

关闭：

```json
{"enabled":false,"requestsPerMinute":null}
```

PUT 响应 HTTP 200，内容同 GET。业务规则：
- `enabled=true` 必须有整数 `requestsPerMinute`，范围 **1～10000**。
- `enabled=false` 时 `requestsPerMinute` 必须为 null 或省略；保存为 DB NULL。
- 缺少 `enabled`、enabled=null、true+null、false+非null、数值超界 → HTTP **400** `INVALID_RATE_LIMIT_POLICY`，固定安全消息 `Invalid application rate limit policy`。
- 无法反序列化的 JSON（例如不合法类型）使用现有 HTTP 400 `INVALID_REQUEST`；不要返回 500。
- App 不存在 → HTTP 404 `APPLICATION_NOT_FOUND`；没有 Basic 认证 → 401。
- 不对 Runtime Bearer Key 开放此管理接口。
- 用 `RateLimitPolicyController` + `ApplicationRateLimitPolicyService` + Request/Response DTO（或等职责类），直接复用已有 ApplicationMapper 和 ApplicationService.getApplicationById；不添加新的权限架构。
- PUT 成功后下一次请求重新查 DB 策略，**只有实际值变化**才要求重置桶；同值重复 PUT 不能凭管理调用反复刷满令牌。

## 6. Frozen Token Bucket Algorithm & Concurrency

推荐包：`com.felix.aigate.ratelimit`。推荐职责：
- `ApplicationRateLimitService`：读取当前 App 的 `requestsPerMinute` 并决定允许/拒绝。
- `InMemoryTokenBucketLimiter`：单 JVM 保存每个 `applicationId` 的桶、补充/消费令牌并返回 retryAfterSeconds。
- `ApplicationRateLimitPolicyService/Controller`：管理 GET/PUT、明确 null 更新及变更时桶重置。
- `ApplicationRateLimitExceededException`：携带受控的 Retry-After 秒数；统一 Handler 返回安全 429。
- `InvalidRateLimitPolicyException`：管理配置违反业务规则的 400。
- 上述类名可按项目命名风格微调，**职责和算法不允许改变**。

单 Application 状态包含 `{capacity, tokens, lastRefillNanos, lastAccessNanos}`：
1. 首次使用 N RPM：capacity=N，tokens=N（允许初始最多 N 次突发）。
2. 使用单调时钟 `System.nanoTime()`；时间差 `elapsedNanos=max(0, now-lastRefill)`。
3. 补令牌：`tokens=min(N, tokens + elapsedNanos * N / 60_000_000_000d)`（实现注意数值转换避免溢出）。
4. 若 `tokens >= 1` 则 **原子扣 1** 并通过；否则拒绝。
5. 拒绝时 `Retry-After = max(1, ceil((1-tokens)*60.0/N))` 整数秒（上界 60）。
6. **使用 ConcurrentHashMap.compute(applicationId,...) 或等效 per-app 原子临界区**，不能非原子读写 token；不同 App 各自独立。
7. 若从 DB 读到的策略 N 与桶 capacity 不同，则该 App 新建满桶（政策变更生效）；管理 PUT 发现值真正变化时也重置。关闭策略时清除桶或允许惰性清理，禁用时永远不拒绝。
8. 复用已有 `@EnableScheduling`，每10分钟清理连续10分钟未访问的桶状态；并发清理不能删掉刚被刷新过的状态。不要增加一个新的 `@EnableScheduling`。
9. **为单测提供可控纳秒时钟**（例如注入 LongSupplier/NanoTicker），避免 Thread.sleep(60s)。生产使用 System.nanoTime，测试可模拟加60秒；不可用 Instant.now 测时长。

## 7. Frozen Runtime Entry & HTTP Error

现有生产入口：

```text
POST /v1/invoke
→ /v1/** SecurityFilterChain + ApplicationIdentity
→ @Valid InvokeRequest
→ [NEW] ApplicationRateLimitService.checkAndConsume(applicationId)
    ├─ unlimited/has token → InvocationTrackingFacade → ModelProxyService → Provider
    └─ no token → 429 APPLICATION_RATE_LIMITED / Retry-After；STOP
```

直接在 `RuntimeInvokeController` 认证/校验之后显式调用限流 Service，然后保留原 `InvocationTrackingFacade.invoke`。**不要把限流注入 ProviderAdapter、ProviderHttpClient 或 Phase 3 记录 Mapper，不增加 AOP/Servlet Filter**。被拒绝的请求不进入 TrackingFacade：**零 Provider 出站、零 invocation_record**。这是当前 P3“已经开始模型业务执行才记录”的语义。

App ID 只能来自已认证的 `ApplicationIdentity`；多把 ACTIVE Key 共享同一个 App 的额度。请求体不得有 appId/selector 供客户规避限流。

新增 `GlobalExceptionHandler` 对 `ApplicationRateLimitExceededException` 的专用处理：
- HTTP 429 Too Many Requests；
- 响应继续使用现有 `ApiErrorResponse(code,message,timestamp,errors)`；
- 固定 `code=APPLICATION_RATE_LIMITED`；
- 固定 `message=Application request rate limit exceeded`；
- `Retry-After` HTTP Header 为整数秒（1..60），不输出内部 DB/Map 对象。
- 现有 Provider 429 仍经 `ProxyErrorType.PROVIDER_RATE_LIMITED` 映射 **503**，不要写成平台 429。
- 未认证401和校验400发生在限流前，不消费令牌；策略加载 DB 失败不得 fail-open 放行至 Provider。App 不存在返回既有 404 APPLICATION_NOT_FOUND，而不是未预期的 500。
- 策略在 PUT 过程中并发变化，已经开始的请求允许使用刚读取到的旧配置；PUT 返回之后的新请求应读到新 DB 配置。不要为极短竞态引入分布式事务。

**Phase 3 调用记录:** 限流拒绝**不算 Provider 调用失败**，不写 FAILED，也不写 SUCCESS。将来若有“拒绝次数统计”，应单独建业务指标，不能污染 invocation_record。

## 8. P4-T01 — Complete Business Feature Package (ACTIVE)

同一任务完整交付：Schema + Policy API + Token Bucket + Runtime Wiring + Tests。开发顺序建议：
1. V8 / Application Entity 和管理 GET/PUT；验证旧 Application CRUD 兼容及 null 禁用。
2. 纯内存令牌桶与可控纳秒时钟；先单测令牌/补充/并发/重置，再连策略 Service。
3. Runtime Controller 显式接入，429 和 Retry-After，保证零出站、零调用历史、现有 Provider 429 契约不变。
4. TTL 清理 + 用量边界 + 完整 HTTP Testcontainers E2E 与回归；**文档由总控在验收时更新，开发导师不修改 docs/**。
5. 跑完整 `./mvnw test`（Java21+Docker），提供 run/failures/errors/skipped/BUILD 及 Code Review。整个 P4-T01 **一次回总控验收**；中途可多次本地提交，不再开微任务。

### P4-T01 Acceptance Criteria

- V8 能在 MySQL Testcontainers 正确迁移；旧数据默认 requests_per_minute=NULL，V1～V7 不变；非法 0/10001 能被 DB CHECK 拒绝。
- 管理 GET/PUT 的启用/禁用/修改/同值重复 PUT 合同正确；非法请求400、安全认证401、App不存在404；旧 Application PUT 不重置策略。
- 不限流 Application 的多次请求原行为不变（回归）。
- N=2 测试：连续前两次进入 Provider，第三次429且 Retry-After合法，**第三次零出站、零 InvocationRecord**；多个 Key 同 App 共享 2 个令牌；不同 App 分桶不串限。
- 用可控时钟使令牌逐步回补，测试容量上限、满桶突发、退避秒数、策略变更立即生效及关闭后放行；没有任何真实等待60秒的慢单测。
- 同一 App 在多线程同时抢令牌，**恰好最多 N 个成功**，无超卖；不同 App 不互相锁死。
- 入站非法/未认证在限流前被拒绝且不消耗令牌；Provider 返回429仍为503 PROVIDER_RATE_LIMITED。
- 令牌状态只在 JVM 内存，不落数据库计数；10分钟无活动清理规则有确定性测试；不得把多实例额度宣传为全局。
- 调用记录 SUCCESS/FAILED、30天清理、管理查询、Credential 安全和 Provider HTTP 无重试等 P2/P3 回归通过。
- 不新增 Redis/MQ/Nacos/微服务、收费系统或复杂限流依赖；源代码与本任务文档一一对应。

## 9. P4-T02 — Closeout & Reliability Review（NOT STARTED）

完成 P4-T01 后由总控自动启动 P4-T02。它不是另一个低复杂度开发任务，而是完整收尾：
- Phase 4 核心功能和 API / Migration / 算法 Code Review；
- 压力/并发单测、令牌补充与配置变更、资源清理、全部 E2E/Maven 证据；
- 架构检查：单实例内存语义、重启/多实例局限、DB每请求读取成本；
- 技术债：TD-001/009/010/011 + 令牌桶状态清理/多实例额度偏差，明确公网发布门槛；
- project-overview、requirements、domain-model、module-design、architecture、roadmap、progress、api-design、database-design、knowledge-map、interview-notes 实际状态修订；必要时新增 ADR（当前已创建 ADR-004，不重复建）；
- 形成 Phase 4 Closeout 报告、知识/面试表达，关闭 Phase 4 前由总控正式审核。

**本轮只启动 P4-T01。P4-T02 / Phase 5 NOT STARTED。**

## 10. Knowledge Points (focus only on Phase 4 new concepts)

开发导师按业务→问题→技术定义→核心原理→线程执行流程→替代方案→在 AIGate 的代码设计→测试讲解：
1. 认证 vs 授权 vs 速率限制：为什么本次保护合法流量。
2. 令牌桶与固定窗口差异，burst / 平均补充 / 非精确60秒次数。
3. ConcurrentHashMap.compute 原子性与高并发“先检查后扣减”的竞争。
4. System.nanoTime、可控时钟和不依赖 Thread.sleep 的确定性测试。
5. MySQL 策略持久化 vs 内存计数、NULL默认无限制和历史 PUT 兼容。
6. 单实例与分布式全局限流的根本区别，什么真实问题才需要 Redis。
7. HTTP 429 Retry-After vs 既有上游 Provider429 →503。

不要重新讲 P1/P2 Spring MVC、Flyway、Security 基础教材；尽量关联这次真实新增的业务及设计权衡。

**最新执行门禁：P4-T01 PAUSED。用户要求先逐层确认产品目标、业务主体/额度归属、完整产品路线，再决定 P4 的确切目标与执行方式。旧方案当前只作备选草案，开发导师不得按其继续编码。**


---

## Product Discovery Decision #1 — 2026-10-20（覆盖旧 P4 执行草案）

用户确认 **AIGate 仅负责管理企业内部既有 Agent 的接入和模型流量/资源治理，暂不负责 Agent 开发、编排或自身执行引擎**。网页对话可看作一种已有 Agent 入口，不由本轮自动推导需要内建聊天产品。目标业务覆盖员工与团队、差异化额度及团队额度池、额外额度审批、紧急任务优先、Agent 允许模型、安全策略、不同模型计费与 AIGate 可见的运行压力；这些尚处于业务逐层确认阶段。

**未决问题：** Agent 与现有 Application/API Key 的关系、User 和 Employee 身份、员工/团队/Agent 额度归属与扣减，以及外部 Agent 的任务/员工身份如何可信关联。不得假设这些已经定案。

**执行状态不变：P4-T01 PAUSED / NO CODING。** 原 V8 RPM/令牌桶方案和 ADR-004 不作为当前编码指令。只有用户确认全局业务流程后，由总控重写 Phase/Tasks 并解除暂停。


### Product Discovery Decision #2 — Unified Model Gateway（2026-10-20）

用户确认**所有纳入管理的 Agent 对模型的调用必须统一经过 AIGate**；AIGate 是资源使用的治理入口，而不是 Agent 执行引擎。终端员工身份如何安全绑定 Agent 请求、员工/团队/Agent 额度如何扣除、模型权限与优先级如何配置均尚未确认。**P4-T01 继续 PAUSED / NO CODING**，旧 V8 + token bucket 只是历史候选方案。


### Product Discovery Decision #3 — Enterprise employee/team/Agent weekly quota (2026-10-20)

用户一次确认七项规则，唯一完整业务事实来源：`docs/product-business-rules.md`。**员工有主团队且可参与其他团队任务；Agent 由管理员统一纳管、开发/注册细节不在本平台；受管 Agent 与现有 Application 按 1:1；实际调用必须可信识别员工，无人值守任务可单独管理专用账号；按周管理，授权 Agent 先享免费额度，再扣员工个人额度，个人不足向团队申请；紧急任务需单任务临时增加额度+优先级；员工可从 Agent 允许的模型中选择，自动路由只在同一逻辑模型多个 Deployment 内进行。**

以上确认的是**业务规则而非编码授权**：周额度重置、费用归属、借调团队扣谁的额度、可信员工身份如何传递和模型定价仍待讨论。**P4-T01 PAUSED / NO CODING**。此前 Application RPM token bucket 与 V8 方案继续视为历史候选，不得自行执行。


### Product Discovery Decision #4 — Weekly Points & Dual Identity（2026-10-20）

用户确认第二轮六项业务规则，事实来源：`docs/product-business-rules.md`：
- 每个获授权员工在每个 Agent 独立享有周免费额度；周周期固定刷新，员工个人基础额度同样按周刷新、不滚存。
- 团队补充额度按笔独立额度及有效期。员工可申请，团队亦可主动发放；发放后用于员工模型调用，**模型请求无需每次查“费用由哪个团队承担”**，但团队发放记录必须可审计/防重复发放。
- 不同模型按 Token 价格折算**企业内部积分**；详细公式/定价版本待确认。
- **Agent Application API Key + 员工个人独立凭证**双身份：一方认证 Agent，另一方认证员工；不能用客户端随意提交的 employeeId 替代个人凭证。
- 已确认扣减顺序：员工×Agent 周免费 → 员工个人周基础 → 员工有效团队补充额度。周固定时间/时区、团队发放占池/到期返还、调用用量结算仍待确认。

**执行门禁仍为 P4-T01 PAUSED / NO CODING**；原 Application-only RPM 方案仅为历史候选。开发导师不得自行实现账户/积分/限流。


### Product Discovery Decision #5 — Weekly Billing and Zero-Balance Degradation (2026-10-20)

最新生效的业务确认详见 docs/product-business-rules.md 的 1.2 节；**以此覆盖前文尚未决或“额度不足直接拒绝”的旧叙述**：
- 公司时区每周一 00:00 刷新每员工×Agent 免费额度及员工个人基础额度（精确时区尚待部署确认）。
- 团队审批/主动发放补充额度时，团队池立即扣款、员工获得有效期独立的积分；未使用到期作废，不返还团队池，需可审计和防重复发放。
- 模型输入/输出等 Token 类型按各自价格换企业内部积分；准确价格版本/单位/舍入待定。
- Provider 失败/超时仅已确认实际用量才能最终计费，无法确认时标记待核对；调用前预留，完成后结算并释放差额，未知用量处理待定。
- **额度用完仍要基本可用**：员工降至最低速率、最低资源调度优先级并通知；这是用户的明确修正，覆盖先前的“余额不足直接拒绝”。**不得推断为无限免费模型使用**，需要另定公司出资保底资源池/最终成本上限、拥塞时处理规则。
- 正常消耗顺序仍为 Agent 免费→个人基础→有效团队补充；耗尽后进入尚待确认资金/容量边界的保底政策。

与出站前预留相冲突的零余额成本来源、最低优先级与最低可用性保障、同模型多部署调度需下一轮确定。**P4-T01 继续 PAUSED / NO CODING；旧限流 V8/令牌桶任务不得启动。**


### Product Discovery Decision #6 — Three Service Tiers / Fallback / Deployment Monitoring（2026-10-20）

正式业务依据 docs/product-business-rules.md §1.3，用户六项确认：
- 企业公用保底预算，每员工按周有保底资源上限；积分耗尽自动使用 **Agent 管理员预设的保底模型**，进入 **限流档**，以较低速率/最低排队优先级/有限基础并发容量提供服务。不是无上限免费；“慢”不能单靠人工延迟模拟节约成本。
- **三级流量**：限流档、普通档、紧急档；紧急档必须基于有效任务审批，可放大请求速率/并发并提高调度优先级，不绕过安全模型授权，也不强制中断已出站的普通请求。
- 正常模式员工手选 Agent 允许的逻辑模型，AIGate **只在该逻辑模型对应 Deployment 之间**按健康、负载、成本、延迟和权重选择；额度用尽切换管理员预设保底模型是唯一明确的例外，切换后的真实模型应可见。
- 管理员应能看 Model 汇总与 Deployment 分解的实时流量指标、历史趋势、告警，以及用于扩/缩容决策的容量分析；不能声称观测到第三方 GPU 实际压力。
- 保底预算总上限与用尽规则、Agent 保底模型安全合法性、排队超时、公平调度、紧急任务可信身份、定价/指标真实来源等仍未最终确认。

**Phase 4 继续 P4-T01 PAUSED / NO CODING**；此前 V8 单 JVM Application RPM 的任务合同不因“限流档”需求存在而恢复。所有新能力均为 TARGET / NOT IMPLEMENTED。业务到技术实施的 Phase 先后将重新提议并经用户确认。


### Product Discovery Decision #7 — Roadmap Approved / Fallback Exhaustion Pending（2026-10-20）

用户已批准顶层业务开发顺序 **P4 员工与 Agent 身份/权限、P5 计量/周额度、P6 团队发放/审批、P7 三档/保底、P8 多部署路由与容量监控、P9 紧急任务、P10 可靠性演进**；仍未批准任何一项开发任务，P4-T01 PAUSED。

当每员工及企业统一保底额度全部耗尽，用户选择 other 并询问企业常见做法，**没有确认最终策略**。根据行业产品公开机制，候选为“硬预算上限 + 预算告警 + 可用自托管/零外部 API 费用模型的受限回退 + 紧急审批补充预算”；模型没有外部计费不等于没有基础设施成本。正式用户答复前不可编入已经确认业务规则。
