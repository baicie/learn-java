# 架构边界

## 1. 端口表

所有本地进程端口必须登记, 避免和 docker-compose / 其它本地服务撞端口。

| 进程                 | 默认端口         | 来源环境变量        | 备注                                                     |
| -------------------- | ---------------- | ------------------- | -------------------------------------------------------- |
| aiops-server         | 8080             | `AIOPS_SERVER_PORT` | 主 API + SSE, 默认前端访问入口                           |
| aiops-worker         | 8081             | `AIOPS_WORKER_PORT` | 仅 actuator / `/internal/worker/status`, 不暴露业务 API  |
| aiops-runner         | 8092             | `AIOPS_RUNNER_PORT` | actuator + `/internal/runner/status`, 不接收外部业务调用 |
| aiops-agent (Python) | 9008             | `AIOPS_AGENT_PORT`  | FastAPI, 与 Java 通过 OAuth2 + Diagnosis Grant 解耦      |
| zabbix-web (docker)  | 8081 → 容器 8080 | docker-compose      | ⚠️ 与 aiops-worker 默认端口冲突                          |

冲突处理:

```txt
- 本地同时启动 zabbix-web + aiops-worker 时, 把 worker 端口改成 8091
  AIOPS_WORKER_PORT=8091 mvn -pl apps/aiops-worker -am spring-boot:run
- 或者 docker compose 不暴露 zabbix-web 宿主机端口, 仅 docker 网络内访问
- AI server 默认端口 8080, 不要随意改动, OpenAPI / 前端代理都按 8080 写死
- AI runner / agent 一旦确定, 改端口必须同步更新:
    - SKILL.md §3.1
    - references/architecture-boundaries.md §1
    - infra/docker-compose.yml
    - deploy/docker-compose.core.yml
    - deploy/helm/aegisops/values.yaml
    - docs/api/ 与前端代理配置
```

## 2. 三个后端应用 + 一个 Agent

后端定性: **模块化单体 (Java) + 外挂 Python Agent + 独立 Runner**。三 app 共享同一 Maven 多模块仓库与同一 PostgreSQL, 但运行时独立部署。

### 2.1 aiops-server

为前端暴露 REST API 与 SSE 流式响应。

**允许:**

```txt
- 暴露 HTTP 端点
- 处理认证与鉴权
- 管理租户、用户、角色数据
- 管理数据源 (新建、更新、删除、测试、触发同步)
- 查询并聚合 Incident、告警、资产
- 触发 AI 诊断任务 (经 aiops-ai-client → aiops-agent)
- 管理 Runbook
- 管理 AutomationJob 生命周期 (创建、审批、取消)
- 将执行日志流回客户端
- 查询并展示审计日志
- 通过 SSE 推送实时更新
```

**禁止:**

```txt
- 直接执行 Shell 命令
- 直接执行 Ansible
- 直接执行 SSH
- 执行长时间数据同步 (worker 的活)
- 执行大规模批分析 (worker 的活)
- 为 ETL 重负载直接访问 PostgreSQL 连接池
- 代表 Worker 向 VictoriaMetrics / ClickHouse 发起分析查询
- 引入 aiops-runner 任何代码
```

### 2.2 aiops-worker

执行后台接入、分析与编排。

**允许:**

```txt
- 轮询 Zabbix 主机、触发器、问题、事件
- 归一与去重 AlertEvent
- 将 AlertEvent 聚合为 Incident
- 生成 Incident 时间线
- 通过 VictoriaMetrics Adapter 查询指标上下文
- 通过 ClickHouse Adapter 查询日志与事件上下文
- 执行 RCA 规则
- 触发 AI 诊断任务 (与 server 走相同的 aiops-ai-client 入口)
- 生成 Postmortem 草稿
- 下发通知
- Outbox poller 消费
- 将结果回写 PostgreSQL
```

**禁止:**

```txt
- 直接暴露 HTTP 端点给互联网 (仅 actuator / internal)
- 执行 Ansible / SSH / Webhook (runner 的活)
- 处理审批流逻辑 (server 的活)
- 向客户端流式日志 (runner / server 的活)
- 直接引入 aiops-runner 任何代码
```

### 2.3 aiops-runner

在隔离与安全前提下执行自动化任务。

**允许:**

```txt
- 执行 Ansible Playbook
- 执行 SSH 命令
- 执行 Webhook
- 流式回传执行日志
- 通过 ExecutionApplicationService / RollbackApplicationService 更新任务状态 (ArchUnit 守卫)
- 写审计日志
- 执行后健康检查
```

**禁止:**

```txt
- 暴露业务 API 端点 (仅 actuator + `/internal/runner/status`)
- 主动连接 aiops-server 获取指令
- 在没有有效 AutomationJob 记录的情况下执行任务
- 执行未处于审批通过状态的任务
- 直接注入 `ExecutionRepository` 或 `RollbackRepository`; 对 `io.aegisops.execution` 的跨模块写入必须经过 `io.aegisops.execution.service.ExecutionApplicationService` 或 `io.aegisops.execution.service.RollbackApplicationService` (ArchUnit 守卫: `RunnerArchUnitGuardTest`)
```

### 2.4 apps/aiops-agent (Python)

诊断工作流运行时, 详见 `docs/adr/0003-aiops-agent-boundary.md`。

```txt
- HTTP 接口: /health, /v1/diagnose, /v1/diagnose/resume, /v1/contracts/diagnosis
- 通过 OAuth2 服务凭据和 Diagnosis Grant 调用 aiops-server 的 /internal/agent/*
- 不直连 PostgreSQL / MinIO / ClickHouse
- 不触发 Runner 执行
- contract version 由 Java 端 AgentContractValidator 校验
```

### 2.5 Java 与 Agent 服务间鉴权

生产环境使用企业 IdP / Keycloak 的 OAuth2 Client Credentials，不新建业务
`auth-service`。server、worker、agent 使用独立 client、短期 JWT、目标 audience 和端点
scope。

```txt
server -> agent:
  audience: aiops-agent-api
  scopes: agent:diagnose / agent:resume / agent:work-record

worker -> agent:
  audience: aiops-agent-api
  scopes: agent:diagnose / agent:work-record

agent -> server:
  audience: aegisops-internal-api
  scopes: evidence:read / cases:read / plugin:authorize
          memory:read / memory:write / checkpoint:read / checkpoint:write
```

每次 Incident 诊断由 Java 签发短期 Diagnosis Grant，绑定 `tenantId + incidentId +
traceId`。Agent 只传播 Grant，不持有签名密钥；Java 从有效 Grant 恢复 `TenantContext`，不得
信任 `X-Tenant-Id` 作为内部 API 的授权来源。

Kubernetes 中四个组件使用独立 ServiceAccount 和 Secret，并以 NetworkPolicy 限制调用
方向；启用 Istio 时使用 STRICT mTLS 和 AuthorizationPolicy。服务间鉴权只支持 OAuth2
Client Credentials，不保留静态 Token 兼容模式。完整决策见
`docs/adr/0010-service-authentication-oauth2-only.md`。

### 2.6 默认部署档位

部署默认值不等于完整产品能力。最小 Core 拓扑固定为：

```txt
aiops-server（内嵌 Portal）
aiops-worker
PostgreSQL
```

以下能力必须显式启用，不得成为 Core 强依赖：

```txt
AI:             aiops-agent + Keycloak/企业 IdP
Automation:     aiops-runner
Demo Zabbix:    Zabbix Server/Web/PostgreSQL/Agent2
Observability:  VictoriaMetrics
Object Storage: MinIO
Shared Cache:   Redis
```

Java 与 Agent 的 OAuth2-only 和 Diagnosis Grant 规则不变，但只在 AI 档启用。Agent 关闭时
不得创建 Agent HTTP Client、OAuth2 Client Credentials 或 JWKS Decoder，也不得注册
`/internal/agent/**`。Runner 关闭时只是不启动执行进程，严禁把执行能力合并进 Server。

Compose 契约与开关见 `deploy/docker-compose.core.yml` 和
`docs/designs/phase-8/2026-08-01-core-deployment-profiles.md`。

## 3. 模块四分类

`modules/` 下的每个模块都明确属于下面四类之一, 详细包结构见 `references/module-package-conventions.md`。

```txt
基础底座 (foundation):
  aiops-common / persistence / web / security / tenant / user
  aiops-audit / observability / platform

运维领域 (operations-domain):
  aiops-datasource / zabbix-adapter / asset / alert / incident
  aiops-evidence / rca / report / inspection / runbook / integration

执行体系 (execution):
  aiops-execution / plugin / work-record
  apps/aiops-runner / apps/aiops-worker

AI 体系 (ai):
  aiops-ai-client
  apps/aiops-agent
```

新增模块前必须先确认归类, 评审中 "模块看起来很多" 不是问题, 但 "模块无明确分类" 是 P1。

## 4. 模块内包结构 (强制)

每个运维领域模块固定 4 个包 (短期不拆 `*-web` 子模块):

```txt
modules/<name>/src/main/java/io/aegisops/<name>/
  api/             # Controller / Request DTO / Response DTO (对外入口)
  application/     # 用例编排, ApplicationService facade, 跨模块只暴露这一层
  domain/          # 实体 / 状态机 / 规则 / 领域服务 (禁止 Spring Web)
  infrastructure/  # Jdbc / Jooq Repository / 外部系统 Adapter / Job
  dto/             # 跨层或跨模块共享 DTO (注意 ExecutionApplicationService 暴露的就是 dto 包)
  service/         # (可选) 与 application 同义, 当前 aiops-execution 用此名, 不强制统一
  internal/        # 仅本模块内部使用, 不允许跨模块依赖
```

跨模块调用规则:

```txt
rca      -> EvidenceApplicationService / EvidenceQueryService        (不允许直接 @Autowired EvidenceRepository)
report   -> IncidentReadService                                      (不允许直接查 incident 表)
agent    -> InternalAgentEvidenceApi                                  (不允许直接调 Zabbix / 数据库)
plugin   -> PlatformPluginRegistry                                    (不允许反射业务模块私有类)
runner   -> ExecutionApplicationService / RollbackApplicationService   (ArchUnit 强制)
worker   -> OutboxApplicationService / IncidentAggregationService     (推荐, 未强制)
```

判断 "我是不是违反 facade 规则" 的快捷方式:

```txt
1. 在模块 A 注入模块 B 的任意 *Repository / *Dao / *Jdbc* / *Jooq* → 违反
2. 在模块 A 注入模块 B 的 *Controller → 违反
3. 在模块 A 注入模块 B 的 *ApplicationService / *ReadService / *QueryService → 允许
4. 在模块 A 通过 EventPublisher 异步驱动模块 B → 允许 (但要注意事件 payload 用 dto 包, 不要泄漏 jooq)
```

## 5. 模块边界

`modules/` 下的每个模块都有明确的公开 API 边界。

### adapters — aiops-\*-adapter 模块

所有外部系统访问都通过 Adapter 模块; 这里不写业务逻辑。

**仅暴露的公开接口:**

```txt
- aiops-zabbix-adapter: ZabbixAdapter, ZabbixClient
- aiops-vm-adapter: MetricQueryClient, MetricAdapter
- aiops-clickhouse-adapter: LogQueryClient, EventQueryClient
- aiops-otel-adapter: OtelAdapter
```

### domain — aiops-\*-domain 或同模块内的 domain 包

承载核心业务逻辑, 不得依赖 Adapter 或其他 domain 模块。

**仅暴露的公开接口:**

```txt
- AlertEventService.fingerprint()
- IncidentAggregator.aggregate()
- RcaEngine.evaluate()
- DiagnosisOrchestrator.diagnose()
```

### application — aiops-\*-service 包 (或 `apps/`)

编排 domain 与 adapter, 不直接处理 HTTP。

**跨模块契约** 集中在各模块的 `service/` 子包, 只暴露其他模块真正需要的方法, 避免内部 JOOQ/DB 类型跨模块泄露。例如 `io.aegisops.execution.service.ExecutionApplicationService` 是 `aiops-execution` 对 runner 唯一可见的边界; `RunnerArchUnitGuardTest` 在 CI 层强制该约束。

### infrastructure — 横切关注点

```txt
- aiops-common: 共享 DTO、常量、工具
- aiops-security: Spring Security、JWT、RBAC
- aiops-audit: 审计日志创建与存储
- aiops-notification: 通知下发
```

## 依赖方向

```txt
frontend (web/)
  ↑
apps/aiops-server
  ↑
modules/ (domain, adapters, infrastructure)
  ↑
  ↓  (adapter implementations, infrastructure)
infra/ (docker-compose, external systems)
```

禁止反向依赖: domain 不得依赖 adapter; server 不得引入 runner 代码。

## 外部系统访问矩阵

| 调用方 | Zabbix  | VictoriaMetrics | ClickHouse | MinIO   | PostgreSQL   | Redis      | Ansible  |
| ------ | ------- | --------------- | ---------- | ------- | ------------ | ---------- | -------- |
| server | —       | —               | —          | —       | write + read | read+write | —        |
| worker | adapter | adapter         | adapter    | adapter | write + read | read+write | —        |
| runner | —       | —               | —          | read    | write        | read       | executor |

一律通过对应 Adapter / Client, 不允许直连。

## 6. ArchUnit 守卫

```txt
现状 (已存在, 不能删):
  apps/aiops-server/src/test/java/io/aegisops/server/ControllerPersistenceBoundaryTest.java
    - 所有 *Controller 不得直接依赖 JdbcTemplate / DSLContext

  apps/aiops-runner/src/test/java/io/aegisops/runner/RunnerArchUnitGuardTest.java
    - RunnerExecutionService 不得直接依赖 ExecutionRepository / RollbackRepository

新增模块后, 推荐立刻在 apps/aiops-server 下补两类 ArchUnit 测试:
  - <DomainModule>NoDirectPersistenceRule: *Controller 不得依赖该模块 Repository
  - <DomainModule>NoReverseDependencyRule: 该模块 domain 包不得依赖任何 -adapter / -client / -web

Runner 模块若新增执行器, 同步在 RunnerArchUnitGuardTest 加白名单类。

CI 入口: bash scripts/ci/backend.sh 已运行 mvn verify, ArchUnit 会自动失败导致红 PR。
```

## 7. 参考

```txt
- SKILL.md §3 架构原则
- SKILL.md §14 Phase 路线图
- docs/adr/0003-aiops-agent-boundary.md (Python Agent 边界)
- references/module-package-conventions.md (包结构细则)
- references/frontend-conventions.md (前端约束)
- references/ai-agent-frontend-stack.md (前端组件栈)
```
