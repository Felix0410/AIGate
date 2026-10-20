# AIGate — Technology Strategy and Resume-Oriented Architecture Roadmap

> **Status: DISCUSSION DRAFT / NOT APPROVED — 2026-10-20**
>
> 用户要求在任何 P4 代码实现前，先落实足以支撑 Java 后端实习/校招简历的技术路线，并体现真实的架构演进。本文仅作总控提出的候选决策，不代表技术已经引入，也不解除 P4-T01 PAUSED。仍以业务问题→最简单可运行→主流程验证→出现真实问题→再优化为约束。

## 1. Proven actual baseline in repository

From root pom.xml checked on 2026-10-20:
- Java 21; Spring Boot **4.1.1**; Spring MVC (starter-webmvc); Spring Security; MyBatis-Plus **3.5.13** Boot4 starter; MySQL Connector/J + MySQL 8.4 baseline; Flyway MySQL; Jackson via Spring; Spring Validation; springdoc-openapi **3.1.1**.
- Test: Spring WebMVC Test / Security Test, JUnit Jupiter via Boot, MySQL Testcontainers; local JDK HttpServer upstream tests; Maven.
- Runtime currently sync RestClient to OpenAI compatible Provider with API Key authenticated Application, AES-GCM Provider credentials, P3 best-effort invocation records.
- **Not present in pom / not proven implemented**: Spring Data Redis, Redis Lua, Kafka/RocketMQ, Micrometer Prometheus/OTel dependencies, Nacos/Spring Cloud, Elasticsearch, CI gate and tested multi-instance deployment.
- Retain working baseline unless a concrete migration is justified; compatibility for every new Spring Boot 4.1.1 integration must be checked in a separate technical design.

## 2. Product-goal constraints (approved)

- Internal existing Agent traffic all via AIGate; Employee and Agent/Application dual identity; Employee-Agent permissions.
- Weekly Employee x Agent free points and Employee general points, extra Team grant, corporate limited fallback and emergency budget, tiered request governance; Token-based Model pricing and accurate reserve/settlement.
- Same logical Model routes between Deployments by health, latency, cost, load, weight; metrics, alerts and capacity decision support.
- P4..P10 top-level order approved, but each Phase scope and coding task not approved. Old P4 Application-only RPM token bucket ADR-004 is ON HOLD.

## 3. Target technical stack (RECOMMENDED / DISCUSSION ONLY)

| Dimension | Candidate technology | Business grounding and target evidence | Status |
|---|---|---|---|
| Java backend | Java 21, Spring Boot 4.1.1, Spring MVC, Spring Security, Maven | Agent+Employee auth, model proxy, Spring transactions, thread/concurrency and JVM tuning | BASELINE PRESENT |
| Reliable data | MySQL 8.4, MyBatis-Plus, Flyway, @Transactional | authoritative quota ledger, reserve/settle/refund, idempotency keys, row-level locking/CAS, indexes, query optimization | BASELINE PRESENT; ADVANCED USE PLANNED |
| Distributed state | Redis with atomic Lua, Spring Data Redis | shared multi-instance request rate/tier limits, efficient eligibility/permission cache; DB remains financial source of truth | RECOMMENDED WHEN MULTI-INSTANCE NEED PROVEN |
| Async events | **one** of RocketMQ / Kafka (default discussion candidate RocketMQ) | asynchronous employee notification, alert fan-out, audit/metric aggregation where temporary delay is allowed; at-least-once, idempotent consumer, retry/DLQ, outbox for DB-event consistency | RECOMMENDED IF ASYNC FAN-OUT NEEDED |
| Observability | Spring Boot Actuator, Micrometer, Prometheus, Grafana; OpenTelemetry/Micrometer Tracing when request span correlation is useful | JVM, gateway-model-deployment QPS, in-flight, p95/p99 latency, failure/429, quotas/tier activity; alerts; verify capacity decisions | RECOMMENDED |
| Delivery/test | Docker/Compose, Linux, GitHub Actions, Testcontainers (MySQL and later Redis/MQ), load tests (k6/JMeter), JFR | reproducible deploy, 2 replicas/load balancer load testing, concurrency fault injection, documented before-after numbers | RECOMMENDED; BASIC TESTING PRESENT |
| Reliability | RestClient timeouts; optional Resilience4j bulkhead/circuit breaker; deliberately restrained retry | Provider slow/fails; protect request threads, give stable errors; avoid retries that duplicate paid model consumption | CONDITIONAL |
| Modular design | package-by-domain + ArchUnit or Spring Modulith module verification | stable module boundaries, no cycles and unauthorized internal package coupling before microservices | CONDITIONAL LIGHTWEIGHT |
| Streaming | Spring MVC SseEmitter / Servlet async initially; evaluate WebFlux only if justified | Upstream Provider SSE passthrough for existing web-chat/Agent model calls; cancellation, timeout, final usage/partial-cost accounting, connection backpressure; user explicitly approved target SSE inclusion | **USER CONFIRMED TARGET (NOT IMPLEMENTED)** |

**Do not add solely for resume:** Elasticsearch without real high-dimensional query/search needs; Nacos/Spring Cloud without independently deployed services; Kubernetes without operational justification; a second MQ; distributed transactions instead of a correct MySQL ledger; Spring AI when current ProviderAdapter already supplies required protocol; microservices just to claim high-tech.

## 4. Mandatory introduction tradeoffs: main candidates

### Redis + Lua

- **What:** external fast shared in-memory key store; Lua runs a short atomic server-side read/check/update script.
- **Problem:** independent AIGate JVM buckets/caches cannot maintain a common concurrency/rate limit; retry-safe cheap per-request rate checking.
- **Why AIGate:** relevant when 2+ gateway instances are actually deployed or permission/routing policy DB reads are shown hot via load test; **not** required simply because quotas exist.
- **Without:** single replica can implement in-memory limiter, exact budget stays in MySQL; multi-replica limits could over-admit (N times).
- **Alternatives:** JVM ConcurrentHashMap single instance; gateway layer central rate limit; MySQL row updates (high contention); dedicated limiter.
- **Why candidate:** targeted shared atomic limiter with comparatively small operational footprint, easy Docker setup and repeatable 2-replica test.
- **Cost/risks:** Redis failure policy fail-open/fail-closed, TTL/clock/time window, hot keys, network cost, persistence/eviction. **Do not replace MySQL accurate financial ledger with Redis counter.**

### MQ (RocketMQ vs Kafka: choose one only after use case)

- **What:** durable event transport/consumption and retry mechanism.
- **Problem:** quota approvals can trigger slow notifications and usage analytics asynchronously, and consumers need independent processing/retry; avoid blocking model response for non-authoritative side effects.
- **Why AIGate:** valuable when reliable notification, usage aggregation or alert fan-out appears, especially multiple consumers or peaks.
- **Without:** sync service call or DB outbox+scheduled worker can meet lower volumes; simpler initially.
- **Alternatives:** local Spring events/transactional outbox polling; RocketMQ; Kafka. Recommendation for discussion: RocketMQ for task/notification retry/DLQ; Kafka if event-stream replay/realtime analytics becomes dominant.
- **Why candidate:** demonstrate durable message, retry, DLQ and idempotency tied to real quota/monitoring events.
- **Cost/risks:** Broker ops, duplicated/out-of-order delivery, business idempotency, reliable DB→MQ publication. **Do not put accurate quota deduction only in best-effort async messages**. Use DB transaction to establish ledger/balance before publish.

### Metrics/tracing

- **What:** Micrometer+Actuator instrument JVM and request metrics; Prometheus time-series query/scrapes; Grafana dashboards/alerts; trace spans connect gateway-to-provider call.
- **Problem:** model 'pressure', failure/latency/cost and scale planning otherwise guesswork.
- **Why AIGate:** user explicitly requires Model and Deployment level real pressure, historical trends and expansion/contraction decision support.
- **Without:** DB invocation records alone do not provide cheap time series, in-flight concurrency and p95/p99 tracking; manual diagnostics.
- **Alternatives:** logs+MySQL periodic reports, managed cloud monitoring, OpenTelemetry exporters; choose simplest local stack first.
- **Why candidate:** concrete measurable endpoint and dashboards with capacity experiments.
- **Cost/risks:** metric cardinality (never put employee IDs as Prometheus labels), retention/storage, trace overhead, secure Actuator endpoints; only claim gateway-visible load, not Provider GPU utilization.

### Correct MySQL accounting (not a new dependency)

- **What:** atomic ledger/hold/release/settlement with invariants, database transactions and concurrency controls.
- **Problem:** concurrent Agent requests, duplicate settlement, expiration across week boundary and uncertain upstream usage can overcharge/overspend.
- **Why:** budget correctness is AIGate's core differentiated business value and strong Java concurrency/transaction interview material.
- **Without:** P3 best-effort invocation metadata cannot be authoritative, eventual duplicate/omitted charges possible.
- **Alternatives:** pessimistic SELECT FOR UPDATE / conditional UPDATE optimistic version / unique idempotency key; decide with measured contention.
- **Risks:** deadlocks, transaction boundaries, lock contention, uncertain timeout, weekly reset interacting with in-flight holds. Require reproducible concurrent tests and reconciliation story.

## 5. Phase-to-technology adoption (discussion matrix, business order unchanged)

| Approved business phase | Primary technical focus | Optional/new infrastructure entry | Required engineering evidence |
|---|---|---|---|
| P4 Employee + Agent auth/access | Spring Security 2 principals, secure credential lifecycle, RBAC/ABAC, MySQL query/index/permission test | Opaque personal token first; SSO/OAuth2 later if needed; no Redis/MQ | missing/invalid/forged employee credential rejected; authorized agent+model happy path |
| P5 token points & weekly budget | MySQL ACID, reservation/settlement/compensation, idempotency, isolation and optimistic/pessimistic contention tests | no new service required; correctness > speed | multi-thread no double spend; failure/timeout unknown handling, billing record reconciliations |
| P6 Team grants/approval | transactional grant, expire/ledger, audit, workflow state | MQ only for asynchronous notification if demonstrated; else event/outbox worker | double-approval rejected; team pool debit/employee grant exactly once in business ledger |
| P7 corporate fallback/three tiers | request rate, concurrency caps, queue fairness, priority; single JVM first | Redis+Lua when Docker 2 gateway replicas demonstrate cross-instance inconsistency | shared bound across replicas, rejection reason, fallback safety, Redis outage policy |
| P8 deployment routing & load observability | routing algorithms, metrics histogram/inflight, Actuator, Prometheus/Grafana, alerts; OTel optional | Prometheus/Grafana; OTel trace when multi-hop visibility valuable | injected upstream failure/latency, dashboard p95/cost/pressure; grounded capacity decisions |
| P9 emergency tasks | task authorization, expiry/revoke, emergency budget transactions, queue priority + fairness | RocketMQ/Kafka for noncritical alert/fan-out where needed | emergency gains pending capacity but no security/budget bypass; event retry/idempotency if MQ used |
| P10 prod readiness/actual scaling | Docker/Compose, Actions CI (start early), Testcontainers, JVM JFR, load test, security/SSRF, multi-replica hardening | possible service split only if independently scaled components and boundary justify it; Nacos/Spring Cloud optional | reproduce services from scratch; CI green; measured before/after p95 throughput and correctness |

**Cross-cutting cannot wait till P10**: CI and Docker setup early; baseline Micrometer request/JVM metrics can be adopted before P8 to collect before/after. P8 means completion of model/Deployment insights, not first installation.

## 6. Resume proof (not promises)

Aim to show 3–5 verifiable result-oriented project bullets after implementation:
- Secure dual principal identity and per-Agent/Model authorization tests.
- Immutable quota ledger and prepaid reservation/actual token settlement with concurrency, failure/reconciliation/idempotency tests.
- Shared atomic multi-replica Redis rate/priority policies with load test and outage semantics.
- Broker-driven retryable notification/analytics pipeline with unique business key idempotency and DLQ if MQ selected.
- Prometheus/Grafana + tracing with measurable p95/p99, failure and capacity scaling experiment.
- CI integration, Docker Compose reproducibility, Testcontainers and JVM diagnosis.

Do NOT invent QPS, latency improvement, production deployment, user scale or 'distributed transaction' claims. Store real commands, output, benchmark reports and architecture ADR in docs before including numbers on a resume.

## 7. Open decisions before freezing stack

1. Whether to **target Redis + one MQ + Micrometer/Prometheus/Grafana + Docker CI** as core graduation resume stack, conditional upon actual business scenarios and phased evidence.
2. RocketMQ **or** Kafka; if no real async workload emerges, defer despite resume value.
3. Whether streaming/SSE is in eventual business scope; current model proxy is synchronous and Agent uses may include streaming.
4. Is a two-gateway-instance pressure test an explicit learning/acceptance goal (useful to make Redis adoption evidence based)?
5. Is Spring Cloud/Nacos/Elasticsearch desired for actual operation or only keyword coverage? Recommend not making them mandatory.

**Status remains: Technology Strategy DRAFT; Phase4 business roadmap APPROVED; P4-T01 PAUSED / NO CODING.**


## 8. 2026-10-20 — SSE Approved and Microservices Conditional Evolution (USER DECISION + ARCHITECT PROPOSAL)

### Confirmed change
User explicitly approved **SSE streaming as an eventual target capability**. This does **not** authorize coding or require switching from Spring MVC to WebFlux: official Spring MVC supports `SseEmitter` through servlet async. First prove end-to-end upstream SSE passthrough, event flushing, client disconnect → cancel upstream where possible, timeout/idle connection handling, bounded thread pools, status/error semantics, final/partial Token usage accounting, quota reservation release/reconciliation on abort, metrics for open streams and in-flight cost. A simple finite-duration stream can be implemented without microservices, Spring Cloud Gateway, or a reactive rewrite.

### Microservices are NOT approved as required
User asked whether a platform for an enterprise with 10,000+ employees **needs** microservices and how to give a legitimate business rationale. **Count of registered employees alone is not evidence**; measure peak concurrent employees/Agent jobs, active SSE connections, total requests per second, stream duration, CPU/heap/threads, network bandwidth, MySQL lock contention, quota settlement latency, p95/p99, Provider ceilings, deployment/reliability requirements.

**Business-grounded rationale candidate**:
> 当企业已有多种 Agent 接入且高峰期长时间 SSE 连接、大量同步模型代理与员工权限/额度/团队审批在同进程运行时，流量面与管理/账务面的资源特征和上线节奏显著不同。为支持网关请求独立水平扩容、降低高峰流量对企业管理及权威积分结算的故障影响，才按稳定边界拆分模型流量 Gateway 与管理 Control Plane；只有账务独立瓶颈/部署需求证明后才评估单独 Quota/Billing Service。

**Candidate evolution, in measurable steps:**

1. **Existing**: modular monolith + MySQL, synchronous proxy. Keep module boundaries explicit.
2. **Horizontal deployment (NOT microservices)**: same AIGate monolith artifact replicated 2+ times behind load balancer. Use Redis shared limits when cross-instance traffic correctness requires; MySQL authoritative ledger; simulate SSE burst. This by itself may meet 10,000-employee enterprise expectations.
3. **Measured split**: only when Gateway connection/traffic scaling, failure isolation, or independent rollout is materially constrained by management/accounting modules, extract a **Gateway Runtime Service** (streaming Provider I/O/routing/priority) and **Management/Policy Service** (employees/teams/Agent permission/config; authoritative account/ledger owner needs explicit placement). Define API, data ownership, service-side availability behavior, authentication context propagation and tracing.
4. **Quota/Billing extraction is optional**: retain transactions and account ledger in a single owning data boundary; distributed settlement adds reliability/latency and should not be invented. Do not have multiple services casually direct-update the same ledger.
5. Do **not** claim employees count implies 10k concurrent calls. Run reproducible synthetic workload, report labeled simulation not real enterprise production; compare 1 vs multi-replica monolith and candidate split, capture p95/p99 latency, active SSE, JVM memory/thread CPU and correctness, and write an ADR including the option **not to split**.

**What justifies extraction:** gateway SSE connection count/CPU or Provider I/O scales while management API remains idle; failure of runtime flow adversely affects critical approval/control plane; independent deployment/resilience requirements cannot be met simply by monolith replicas; boundaries and operational capacity mature. **Counterexamples:** database/Provider is real bottleneck; a replicated monolith meets measured SLO; small team/limited platform ops; split adds network calls, cross-service authorization/rate coherency and difficult distributed quota consistency.

**Technology selection after proof:** Spring MVC SSE first; WebFlux/Netty only if measurable IO/thread bottleneck and team can handle nonblocking chain. Spring Cloud Gateway/Nacos not automatic prerequisites even for two services; they should solve real external routing/discovery/config needs if adopted. Redis for shared rate control and monitoring integration; MQ for durable non-authoritative events; never rely on MQ-only best effort to debit financial quotas.

**Candidate stage:** SSE delivery in a focused stream-proxy milestone after P4 identities, possibly within P8 gateway/routing deliverables (phase placement still for user approval). Microservice split is a **P10 conditional architectural experiment**, not a Phase 10 acceptance requirement. This does not change the already-approved P4–P10 business order or automatically activate any task.

**Status:** SSE target inclusion = USER CONFIRMED; microservice split = EVALUATION OPTION, **NOT APPROVED / NOT IMPLEMENTED**; P4-T01 remains PAUSED / NO CODING.

**Verification references:** Spring Framework MVC async/SseEmitter official reference https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-ann-async.html ; Microsoft Azure Architecture Center Microservices readiness https://learn.microsoft.com/en-us/azure/architecture/guide/architecture-styles/microservices ; AWS/Microsoft common app architectures note monolith replicas can scale without decomposition https://learn.microsoft.com/en-us/dotnet/standard/modern-web-apps-azure-architecture/common-web-application-architectures


## 9. 2026-10-20 — Related Systems and 2-vs-3/4-Service Boundary Review (RESEARCH, NOT APPROVED)

The user asked whether two services would be too few for resume credibility, whether AIGate is too shallow, and what scale warrants microservices. This section records verified reference designs and an **evaluation proposal**, not a user-approved requirement.

### Reference evidence (official projects / dated retrieval 2026-10-20)

- **LiteLLM Production Deployment**: monolithic proxy option or microservices with gateway (LLM traffic), backend (management API) and separate UI. Postgres/Redis are supporting infrastructure. Official: https://docs.litellm.ai/docs/proxy/deploy . It proves two core backend services plus UI can be an appropriate production deployment even for mature AI Gateway products.
- **Kong AI Gateway Architecture**: configurable control plane separate from independent, horizontally replicated proxy data-plane nodes; a major AI gateway demonstrates that CP/DP split is a first-class architectural boundary. Official: https://developer.konghq.com/ai-gateway/architecture/ .
- **Langfuse Architecture**: two application containers (Web/API and Worker) plus separate Postgres (transactions), ClickHouse (analytics), Redis and blob storage. Demonstrates splitting hot-path APIs from asynchronous high-volume event processing instead of decomposing each CRUD object. Official: https://langfuse.com/handbook/product-engineering/architecture .
- **Dify docker-compose**: API, worker, worker_beat, plugin_daemon, agent_backend, sandbox and others. This larger runtime exists because Dify additionally handles Agent execution/plugin sandbox and tasks, which **AIGate explicitly does not build**. Official: https://github.com/langgenius/dify/blob/main/docker/docker-compose.yaml .
- **Microsoft microservice boundary guide**: independently deployable services and minimizing cross-service consistency concerns, recommend coarse-grained boundary first. Official: https://learn.microsoft.com/zh-cn/azure/architecture/microservices/model/microservice-boundaries .

### Candidate AIGate topology with a meaningful third service

**Minimal independent services if real needs emerge (3 business backends):**
1. **AI Gateway Runtime** owns authenticated SSE ingress/upstream proxy, ModelDeployment runtime selection, entry flow-tier admission and operational request metrics. Horizontal replication is natural; no direct write to authoritative balance tables.
2. **Identity & Policy/Control Plane** owns Employee/Team relationships, Agent/Application access permissions, Provider/Model/Deployment configuration and policy versions, and administrative API. Keeps most low-QPS CRUD/control concerns grouped. Avoid creating separate "employee/agent/model" tiny services just for class-level separation.
3. **Quota & Billing** owns weekly employee-Agent free points, employee basic points, Team grant pool and grant ledger, reservation→settlement, fallback/emergency budgets, idempotency and exception reconciliation. **One authoritative ledger owner**; team approvals can be exposed through control-plane facade/UI but final grant and debit must obey Quota's transactional boundary. Cross-service employee authentication proof/context must be reliable.

**Fourth independently runnable component only when event processing warrants it:**
4. **Usage & Notification Worker** (asynchronous consumer, not necessarily independent domain microservice) processes committed outbox usage/grant notifications, alert fanout, trend aggregation and replayable audit projection. Own separate projection/read model if used; cannot become the authoritative billing source.

Examples of ownership discipline:
- Gateway runtime obtains trusted request identity and selected Model policy from Control Plane or verified policy snapshot, reserves cost with Quota BEFORE paid Provider request, settles confirmed actual usage afterward, releases unused holds and reconciles unknown provider-timeout usage. Avoid one RPC per SSE chunk; per-request transaction boundaries must be explicit.
- Failure scenarios: Quota unavailable? paid request fail-closed or use bounded preauthorization with strict correctness, not uncharged pass-through; Control Plane unavailable? signed/TTL policy snapshot if security semantics allow and tested stale-policy revocation; MQ unavailable? authoritative MySQL ledger complete, outbox pending rather than losing billing.
- **Tricky:** Central Quota RPC adds network latency/hotspot/cascading failure. If measured requirements are satisfied in the modular monolith, keeping Quota as a module is preferable. "Four containers" != "four microservices"; replicas and storage do not count as separate business services.

### AIGate's natural triggers for splitting (no arbitrary employee count)

- **Measured independent scaling:** SSE Gateway connection/network/CPU memory demand requires multiple replicas, while Control Plane remains quiet; scaling the monolith replicas increases database connections/memory/admin replicas wastefully. First test monolith horizontal replication; only if genuine resource mismatch remains should split.
- **Measured isolation need:** proxy spike, Provider outage or SSE thread/resource saturation endangers critical team-approval/admin endpoints or quota reliability, and thread pools/bulkheads/process isolation in replicated monolith are insufficient.
- **Domain ownership:** quota ledger grows to own substantial transactional state/reservation/reconciliation SLA and independently deployed fault/recovery paths; multiple code teams/independent release cadence when actual.
- **Async read/write separation:** immutable usage/log events and analytics materially outgrow OLTP transaction processing; independently scale an async worker without making main billing eventually consistent.
- **Complexity cost gate:** inter-service auth, policy snapshot invalidation, consistent pricing versions, exactly-once business charging despite retry, network failure, tracing, rollout rollback, separate schema ownership, CI/CD and operations must be demonstrable.

**Recommendation to discuss**: two backend services are not "too few". With AIGate's approved business complexity, an **optional 3-service end-state** (Gateway Runtime / Control-Policy / Quota-Billing) and a workload-triggered **4th Worker** are more convincing than an arbitrary 5–8 microservices. In a resume, clearly differentiate independently deployed service, worker, support infrastructure, multiple replicas, and a merely conceptual design. A 10k-employee corporate scenario is plausible motivation for capacity planning, **not evidence of actual production scale, concurrent usage or proof microservices are necessary**.

**No change to current authorization:** P4-T01 PAUSED / NO CODING; no new runtime services, dependencies, data partition, MQ, or source changes. Business phase order remains approved; microservices topology is a research option pending explicit user choice and proof.


## 10. 2026-10-20 — Candidate Business Extensions to Justify Additional Domain Services (NOT USER APPROVED)

User asks whether additional BUSINESS capabilities could naturally support deeper resume-worthy service architecture. The following is a research/discussion proposal only, not a product change or permission to start a Phase. Business scope was previously confirmed as **external Agent traffic governance, never Agent authoring/orchestration/execution**.

### Relevant production references

- LiteLLM Enterprise: team/project model policies, per-key budgets and spend, short-lived budget increase, audit log, guardrails. https://docs.litellm.ai/docs/enterprise
- Kong AI Gateway: provider proxy, model routing, prompt safety/data governance, analytics, cache and billing. https://developer.konghq.com/ai-gateway/ ; https://docs.konghq.com/gateway/latest/ai-gateway/
- Kong AI Prompt Guard: rule-based allow/deny matching, illustrating an approachable FIRST safety implementation rather than a speculative semantic classifier. https://docs.konghq.com/hub/kong-inc/ai-prompt-guard/
- Kong AI Semantic Cache: optional opt-in caching with explicit `no-store` policy, not universally correct for user-private or contextual prompts. https://developer.konghq.com/plugins/ai-semantic-cache/

### Proposed additional independent business domains

**A. AI Security & Compliance Governance — high value**

- Business: company has sensitive customer/employee/sourcecode/credential information; teams may only send approved data classifications to approved model/providers. Create data-classification→Agent/Employee/Model/Provider policy matrix, change approval and immutable policy version, sensitive-content protection (basic deterministic rules, secret fingerprints, PII), blocking/redacting, safe metadata-only security audit. No Agent building.
- Happy path: admin publishes restricted-rule version → employee/Agent Model request checked against policy → allowed goes upstream, denied returns safe classified reason and metadata-only audit.
- Benefits: domain-specific security rule engine, risk decisions, policy snapshot/cache + TTL invalidation, tracing of decision ID, governance vs runtime separation, streaming preflight and fail-closed handling; later optional separate **AI Governance Service**, authoritative for published safety policies and audit decisions; gateway enforces policy in the hot path using bounded-local policy snapshot where safe.
- Cost/tradeoffs: rule precision/false positives, data privacy, PII scope, streaming response moderation delays (cannot guarantee full output blocked without buffering), bypass risks; **never persist raw prompts/responses by default**.
- Start as module. Extract only if security-policy change cadence/ownership, independent rule execution resource cost or isolation requirements are demonstrated.

**B. Cost Analytics & FinOps — high value**

- Business: company procurement/finance sees *real provider cost* separately from employee *internal points*, per team/Agent/Model/Deployment daily/weekly/monthly spend, budget burn, anomaly alerts, cost/performance comparison and capacity optimization recommendation.
- Happy path: authoritative Quota final settlement commits → durable outbox usage/cost event → async aggregation by team/Agent/Model/Deployment → query trend/anomaly report and alert, with reconciliation.
- Benefits: natural MQ + reliable outbox + at least once idempotent workers, materialized views/OLAP, monitoring, asynchronous backpressure, billing-vs-report consistency and eventual consistency.
- Costs: dual source of truth hazard; events lost/duplicated, analytics delay, sparse sample/price changes; **MySQL ledger remains the money truth**, Redis/Prometheus do not replace finance records, no fabricated external cloud provider invoices.
- Start as module + scheduled MySQL aggregates; separate **Analytics/FinOps Service or Worker** if real write throughput and expensive reports contend with OLTP/other services. ClickHouse/ES optional only if measured analytical query needs require it.

**C. Model Capacity & Deployment Operations — conditional**

- Business: admin governs Provider agreements and model deployments, active health, vendor limits, cost tiers, provider outage, price changes, safe capacity expansion/contraction. Autoscale is only feasible for *self-hosted deployments the company controls*, not for external black-box Provider GPU.
- Happy path: collect Deployment health/latency/cost → detect overload → recommend/approve change (weight/health/capacity) → Gateway applies versioned routing policy and reports outcome.
- Benefits: active health checks, weighted adaptive routing, circuit breaking, hysteresis, time-series statistics, concurrency/load shedding, capacity SLO testing.
- Tradeoff: health checker and metrics service might start as background tasks/config module, not automatically its own microservice. Separate **Resource & Routing Control Service** only if independent monitoring/control workload or deployment operations appear; gateway's per-request route execution remains local or a quick policy snapshot to avoid a synchronous bottleneck.

**D. Unified Approval & Audit Center — lower extraction priority**

- Business already exists in Team extra grants, emergency tasks, model/security policy exception and deployment capacity changes; unify approver, expiry, rollback, time-boxing and administrative audit where meaningful.
- Keep initially inside domain-specific modules; avoid "Workflow Service" just to add a service. If real multiple approvals plus independent business consumers grow, consider a central workflow process coordinator with clear state ownership, never having it silently mutate multiple authoritative ledgers.

**Avoid invented Agent business:** creating Agent execution engines, tool plugins, RAG ingestion, prompt-workflow designer, organization-wide semantic caching of private prompts, or external Provider auto GPU scaling are **not natural additions** to the agreed gateway-governance boundary.

### Candidate 5-service final topology (discussion)

| Service (provisional) | Domain ownership | Why independent runtime could be justified |
|---|---|---|
| 1 Gateway Runtime | SSE ingress and upstream I/O, local allowed Model routing, rate/priority admission | high long-lived network connections and independently scalable traffic |
| 2 Identity & Control Plane | Employees/Teams/Agent/Application identities, access grants, model registry, config publishing | low-QPS, strong administrative policy governance and separate rollout |
| 3 Quota & Billing | authoritative weekly/Team/fallback/emergency ledgers, reservation/settlement/grants, idempotency | strong ACID correctness, fault domain and hot balance contention |
| 4 AI Governance & Risk | sensitive data classification policy, policy publishing, rejection/audit metadata | policy lifecycle/special audit and protected enforcement independently owned; optional if organization needs it |
| 5 Cost Analytics / FinOps | aggregate projections, real provider cost estimates, trends, abnormal spend and recommendations | async compute and read load separate from OLTP; can deploy as worker + API |
| 6 OPTIONAL Resource Capacity Control | deployment health probes, routing policy versions, capacity recommendation/self-hosted deployment decisions | distinct ops workload if independently scaled/operated; otherwise remains Gateway/Control Plane module |

**IMPORTANT**: 5 services are a design hypothesis, not a requirement or implied actual deployment. The same functionality can be built and validated first in **one module-monolith** with well-defined packages and a single authoritative MySQL transaction owner. Splitting 5 service DBs too early creates unplanned distributed authorization and business transactions. Async Workers are not always independent business domain microservices; Redis, MQ broker, MySQL and Prometheus are *not* counted.

### Suggested controlled path to interview evidence

1. User confirms which **new business** truly belongs in AIGate (A security, B FinOps, C capacity, D approval). Update requirements/roadmap/architecture and ADR once accepted.
2. Each new domain gets distinct Business Goal, Main Flow, persisted authoritative data, API, E2E and failure cases within existing modular monolith first; start with smallest Happy Path.
3. Deploy 2 identical monolith replicas, load test SSE and cross-instance quota/rate invariants. Record baseline.
4. Only then compare extraction from 3 to 5 services on independent scale/isolation, operational ownership and transaction complexity. Verify Quota RPC outage no unpaid external calls, stale policy propagation safe, MQ duplicate/lag analytics reconciliation, model disconnect handling and trace propagation.
5. Claim microservices on resume only after independently deployable, tested, documented service topology with measurements. Simulation of 10k employees must be labeled synthetic; employees != concurrency.

**Decision gate**: these additions remain **PROPOSED / NOT CONFIRMED**. P4-T01 remains **PAUSED / NO CODING**. The approved P4–P10 BUSINESS ORDER is intact; any additional scope or Phase insertion requires user review, not autonomous restructuring.
