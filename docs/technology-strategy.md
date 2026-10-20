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
| Streaming | Spring MVC SseEmitter / servlet async or WebFlux carefully evaluated | LLM chat streaming/long requests and client cancellation, partial-cost accounting and backpressure; only if product confirms streaming necessity | OPTIONAL PRODUCT EXTENSION |

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
