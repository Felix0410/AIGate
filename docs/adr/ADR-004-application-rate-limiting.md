# ADR-004 — Application Rate Limiting: MySQL Policy + In-Memory Token Bucket

- **Date:** 2026-10-20
- **Status:** ACCEPTED / DESIGN FROZEN（尚未实现）
- **Phase:** 4 — Application Rate Limiting
- **Decision owner:** AIGate 总控

## 1. 背景

AIGate 已实现 Application Bearer Key → 同步模型代理 → SUCCESS/FAILED 尽力记录 → 管理历史。当前合法 Application 可不受网关本地限速而持续调用昂贵或较慢的 Provider；当客户端循环/并发激增时，可能占用请求线程、造成上游限流、产生资源成本。

当前项目是**单实例模块化单体 + MySQL**，尚无实测跨实例全局一致性、超大吞吐或精确计费需求。

## 2. 问题

如何让管理员给每个 Application 设置可持久化的速率策略，并在身份认证和入参校验通过后、模型 HTTP 出站之前按 Application 拒绝过快的请求？如何在同 JVM 并发下确保令牌消费正确，同时不改变原有 Provider 429 → 503 语义？

## 3. 候选方案

| Candidate | 优点 | 代价/问题 |
|---|---|---|
| A. 无保护 / 仅依赖 Provider 429 | 最简单，没有新开发 | Provider 已经收到请求；不同 Application 难独立保护，成本无法提前限制 |
| B. MySQL 每次请求计数 | 可持久、可见 | 每次调用高频 DB 更新，锁竞争和窗口并发语义复杂；对限流热点不合适 |
| C. **MySQL 持久策略 + 单 JVM 令牌桶** | 已有 DB 负责配置，内存原子计数响应快，单实例最容易正确验证 | 重启令牌重新初始化、多实例额度不共享、额外一次策略 DB 读取、需要桶生命周期清理 |
| D. Redis 原子脚本限流 | 可支持多实例共享配额 | 新增 Redis 可用性/网络故障策略、Lua 脚本、测试与运维成本；当前缺跨实例需求 |
| E. 固定时间窗口计数器 | 算法更易实现 | 窗口交界可能瞬间允许接近双倍突发，限流行为不够平滑 |

## 4. 最终方案

选择 **C，单 JVM、按 Application ID 隔离的 Token Bucket（令牌桶）**：

- **策略持久化**：在 `application` 表通过 Flyway **V8** 添加 `requests_per_minute INT NULL`，约束值为 `1..10000` 或 NULL；NULL 表示**不限流**（兼容 P2/P3 已有行为）。
- **管理 API**：新增 `GET/PUT /api/applications/{applicationId}/rate-limit`，复用 HTTP Basic。POST/PUT Application 原 API 不改契约。独立配置响应 `{applicationId,enabled,requestsPerMinute}`。
- **Runtime 接入**：在 `RuntimeInvokeController` 完成 Application Key 认证和 `@Valid` 后，通过 `ApplicationRateLimitService.checkAndConsume(applicationId)`，成功才调用现有 `InvocationTrackingFacade.invoke`；仅从 SecurityContext 的 ApplicationIdentity 取 appId。
- **桶算法**：每个 App 一个状态 `{capacity=RPM,tokens,lastRefillNanos,lastAccessNanos}`。初始 tokens=capacity；用 `System.nanoTime()` 单调时间刷新 `tokens=min(capacity,tokens+elapsedNanos*RPM/60_000_000_000d)`；tokens>=1 则减1通过，否则拒绝。多线程同 App 使用 `ConcurrentHashMap.compute` 或等效按键原子操作；**绝不“先判断后非原子递减”**。不同 App 不共享桶。
- **拒绝契约**：HTTP `429` + 现有 ApiErrorResponse 形状，固定 `code=APPLICATION_RATE_LIMITED`，`message=Application request rate limit exceeded`，附 `Retry-After: N`（整数秒，最小 1，按补足下一个令牌所需时间向上取整；不超过60）；**零 Provider 出站、零 invocation_record**。
- **已有 429 语义不变**：Provider 的 429 继续向 Runtime 客户端表现为 `503 PROVIDER_RATE_LIMITED`，不能与 AIGate 本地 429 混淆。
- **配置变化**：管理 PUT 在策略值实际变化时同步清除该 App 的桶状态；Runtime 每次从 DB 读取最新策略（不增加策略缓存），检查发现桶 capacity 与配置不同也初始化新桶。重复提交同配置不得重置令牌；NULL 不创建桶或清理旧桶。
- **空闲状态**：复用现有 Spring Scheduling，定期（每 10 分钟）清理连续 10 分钟未被访问的桶；使用原子条件移除避免误删并发更新状态。删除 Application 后旧桶最终清理。单 JVM 本地内存，不落库令牌状态。
- **失败策略**：读取 Application 策略所依赖的 MySQL 发生异常，不允许静默 fail-open 绕过限制；沿既有安全 5xx 兜底处理。无该 App 时沿既有 404 APPLICATION_NOT_FOUND 语义。新增限流拒绝发生在 TrackingFacade 前，不计为 P3 的“已经开始模型执行”的 FAILED。
- **不引入**新 Redis、MQ、流式、分布式锁、网关代理框架、准确费用账本或跨节点同步。

## 5. 技术是什么 / 解决什么

- 令牌桶：容量限制最大瞬时突发，令牌以固定速率补充；每个有效请求消耗 1。用于对合法请求做**应用级速率保护**，不是用户身份认证，也不是精确分钟次数账本。
- ConcurrentHashMap 的 per-key compute 在单 JVM 中对同一键的更新是原子的；用于避免多个线程同时看到同一剩余令牌。
- `System.nanoTime` 是耗时比较的单调时钟，不是用于展示的绝对 UTC 时间，不能拿它做 DB `created_at`。

## 6. 为什么现在需要 / 不采用会怎样

当前 Runtime 请求已能进入真实 Provider；来自同 Application 的并发洪峰会将慢模型响应与上游资源占满，网关缺乏在 Provider 之前的本地拒绝能力。一个配置后可验证的 429 和零出站足以形成业务闭环。不做则只能被动等待 Provider 429/超时或借助客户端自律。

## 7. 收益与引入的新复杂度

收益：配置持久/易管理，单实例不新增基础设施，按应用身份分桶，限流决定在任何 Provider HTTP 之前发生，测试可用假时钟与多线程证明原子性。

代价：请求多一次 application DB 读；桶占内存且需清理；配置更新与在途请求有短时间竞争；服务重启桶重新填满；多实例各自独立计数；令牌桶允许容量大小的短时突发，**不保证任意连续 60 秒严格 <= RPM 次**。

## 8. 一致性与将来演进

- 本阶段仅**单实例保护**；多个实例启动会各自拥有 N 容量，总额可能上升，不能称作分布式全局限流。
- 未来若确实上线多实例且需要统一额度，另行对 Redis 原子 Lua、单机入口限流、集中限流服务作权衡，并决策 Redis 故障时 fail-open / fail-closed。
- 若需要精确每日/每月“调用配额”、Token 计费、扣费幂等，与本令牌桶是另一业务模型，必须新 ADR/数据设计，不从本内存数据派生账本。
- 后续如果策略 DB 读取成为性能瓶颈，再比较安全的缓存/失效方案，不提前添加 Redis。

## 9. 验收与非目标

**必须验证**：默认无限制；按 App 限流与多 Key 共享；N 次消费和第 N+1 次 429；单调假时钟补令牌；并发原子性；配置修改/关闭；未认证401/BeanValidation400优先；429零 Provider HTTP和零InvocationRecord；Provider429仍503；旧 API/Phase 3 回归；完整 Maven 运行。

**本 ADR 仅冻结 Phase 4 方案。V8、API、桶算法均尚未实现。**
