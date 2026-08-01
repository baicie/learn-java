---
title: mTLS 与任务级授权实施计划
type: design
status: accepted
phase: phase-8
owner: ai
created: 2026-08-01
updated: 2026-08-02
related: []
---

# mTLS 与任务级授权实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use `executing-plans` to implement this plan
> task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 用内部 mTLS 证明固定组件身份，并用短期非对称 Grant 约束每次诊断与自动化执行，
移除完整部署对服务间 OAuth2/Keycloak 的强依赖。

**Architecture:** Java Server/Worker 收敛为单一 `aegisops-app` 运行入口，后台作业以
`aiops-worker-runtime` 模块装入同一 JVM。App 与 Agent 使用角色隔离的双向 TLS；控制面签发
Ed25519 Diagnosis Grant 与 Execution Grant，Agent/Runner 只持有公钥并在工作开始前失败关闭。

**Tech Stack:** Java 21、Spring Boot、Python/FastAPI、PyJWT、PostgreSQL、Flyway、Docker
Compose、Helm、OpenSSL。

---

## 1. Scope

- 替换 Java/Agent 双向 OAuth2 服务身份为 mTLS。
- 将 Server/Worker 生产入口合并为 `aegisops-app`，保留模块边界而移除独立 Worker 部署。
- 将 Diagnosis Grant 从 HS256 升级为 EdDSA，并让 Agent 校验完整任务上下文。
- 为普通执行、重试和回滚执行签发 Execution Grant，Runner 执行前验证。
- 增加 Runner 独立数据库凭据与最小权限模板。
- 更新 Core/AI/Automation/Demo Compose、Helm、发布探针和运维文档。

## 2. Background

当前 OAuth2-only 实现已具备 audience、scope、短期 Token 与 Diagnosis Grant，但完整发布需要
外部 IdP 及三个 Client Secret。独立 Server/Worker JVM 还重复持有相同控制面配置，却没有独立
扩缩容或数据所有权收益。Runner 依赖数据库审批快照和原子领取，却无法验证任务行是否由控制面
签发。新设计保留现有 HTTP/DTO/审批状态机，合并 Java 运行入口、替换工作负载身份并强化任务授权。

## 3. Goals

- 没有可信客户端证书时，Agent 诊断端点和 Java 内部 Agent API 均拒绝请求。
- Diagnosis Grant 只能由控制面私钥签发，Agent 与 Java 使用公钥验证。
- Execution Grant 绑定不可变执行/步骤摘要，数据库行或参数被修改后 Runner 拒绝执行。
- 默认诊断档为 App + Agent + PostgreSQL，不再需要 IdP；Runner 仅在 automation 档启用。
- 完整发布不再需要服务 OAuth2 URI 与 Client Secret。

## 4. Non-goals

- 不改变用户登录的 OIDC/JWT 方案。
- 不把 Agent 或 Runner 合并进 App。
- 不引入 SPIFFE、Istio、cert-manager 或新的 auth-service。
- 不重写审批、租约、重试、回滚、Ansible/Webhook 执行器。
- 不把数据库角色自动创建强加给外部托管 PostgreSQL。

## 5. Proposed Design

### 5.1 信任域

```text
Browser -- user JWT --> App :8080

App -- mTLS + Diagnosis Grant --> Agent
Agent -- mTLS + same Grant    --> App internal TLS port :8443

App -- execution snapshot + Execution Grant --> PostgreSQL
Runner -- restricted DB role + grant verify    --> PostgreSQL/executor
```

### 5.2 Diagnosis Grant

```json
{
  "iss": "aegisops-app",
  "aud": ["aiops-agent-api", "aegisops-internal-api"],
  "sub": "diagnosis:diag_123",
  "scope": ["diagnosis:execute", "evidence:read"],
  "tenantId": "tenant_1",
  "incidentId": "incident_1",
  "diagnosisId": "diag_123",
  "traceId": "trace_1",
  "iat": 1785592800,
  "exp": 1785593100,
  "jti": "grant_uuid"
}
```

### 5.3 Execution Grant

```json
{
  "iss": "aegisops-app",
  "aud": ["aiops-runner"],
  "sub": "execution:exec_123",
  "scope": ["runbook:execute"],
  "tenantId": "tenant_1",
  "incidentId": "incident_1",
  "executionId": "exec_123",
  "planId": "plan_1",
  "mode": "live",
  "snapshotSha256": "64-char-lowercase-hex",
  "maxDurationSeconds": 1800,
  "iat": 1785592800,
  "exp": 1785596400,
  "jti": "grant_uuid"
}
```

摘要输入使用稳定 JSON：执行 ID、租户、Incident、计划、模式、执行类型、回滚引用、重试次数、
最长执行时间、审批 ID/快照、风险等级，以及按顺序排列的步骤 ID、计划步骤 ID、名称、类型、
目标、参数、命令快照、重试次数和超时。签发和验证共用 Java `ExecutionGrantSnapshot`，避免
两套序列化规则漂移。

## 6. Data Model Changes

- 新增 Flyway `V0051__init_execution_grant.sql`：
  `execution_run.execution_grant text`、`execution_run.execution_snapshot_sha256 varchar(64)`、
  `execution_run.execution_grant_expires_at timestamptz`。升级时不立即增加 queued/running 非空
  约束，避免历史记录阻断 Flyway；Runner 对缺失字段的旧任务失败关闭。
- jOOQ 重新生成后，DTO/Repository 映射新增三个字段。
- Compose PostgreSQL 初始化脚本创建 `aegisops_runner`；外部数据库使用独立 SQL 模板显式
  `GRANT SELECT/UPDATE/INSERT` 到 Runner 实际访问表。

## 7. Backend Changes

### Task 1: 非对称 Grant 基础能力

**Files:**

- Modify: `modules/aiops-common/src/main/java/io/aegisops/common/security/DiagnosisGrantCodec.java`
- Create: `modules/aiops-common/src/main/java/io/aegisops/common/security/Ed25519KeyLoader.java`
- Test: `modules/aiops-common/src/test/java/io/aegisops/common/security/DiagnosisGrantCodecTest.java`

- [x] 写 Ed25519 签发、错误公钥、过期、错误 audience 和资源绑定失败测试。
- [x] 运行 `mvn -pl modules/aiops-common -am -Dtest=DiagnosisGrantCodecTest test`，确认因 EdDSA API
      不存在而失败。
- [x] 实现 PKCS#8/X.509 PEM 密钥加载与 EdDSA JWT 签发/验证。
- [x] 重跑模块测试并提交。

### Task 2: Agent 双向 mTLS 与 Grant 校验

**Files:**

- Modify: `modules/aiops-ai-client/src/main/java/io/aegisops/ai/client/HttpAiAgentClient.java`
- Modify: `modules/aiops-security/src/main/java/io/aegisops/security/InternalAgentAuthFilter.java`
- Modify: `apps/aiops-agent/src/aiops_agent/main.py`
- Create: `apps/aiops-agent/src/aiops_agent/diagnosis_grant.py`
- Modify: `apps/aiops-agent/src/aiops_agent/settings.py`
- Test: Java AI client/security tests and `apps/aiops-agent/tests/test_diagnosis_grant.py`

- [x] 写 Java SSL bundle/证书主体校验和 Python EdDSA Grant 校验失败测试。
- [x] 运行定向 Maven/pytest，确认缺少 mTLS/Grant 实现而失败。
- [x] Java 客户端使用 PEM SSL Bundle；内部回调过滤器从 TLS peer certificate 读取 Agent 身份。
- [x] Agent 移除 Bearer ServiceAuthenticator，使用 ASGI TLS peer 身份依赖和 Grant scope/resource 校验。
- [x] Agent 所有内部 httpx 客户端统一挂载 CA、Agent 证书和私钥。
- [x] 重跑定向测试并提交。

### Task 3: Execution Grant

**Files:**

- Create: `modules/aiops-execution/src/main/java/io/aegisops/execution/grant/*`
- Modify: `modules/aiops-execution/src/main/java/io/aegisops/execution/ExecutionRequestService.java`
- Modify: `apps/aiops-runner/src/main/java/io/aegisops/runner/RunnerExecutionService.java`
- Create: `apps/aiops-server/src/main/resources/db/migration/V0051__init_execution_grant.sql`
- Test: execution service, repository and runner tests

- [x] 写签发/篡改/过期/错误 audience/重试与回滚测试。
- [x] 运行定向 Maven 测试，确认因 Execution Grant API/字段不存在而失败。
- [x] 创建执行步骤快照后签发 Grant 并持久化；Runner 领取后、步骤状态变化前验证。
- [x] Grant 无效时将任务安全标记 failed，写脱敏审计，不调用任何 StepExecutor。
- [x] 重新生成 jOOQ，重跑 migration/repository/runner 测试并提交。

## 8. Frontend Changes

无。公共 REST/OpenAPI 和 Portal 操作流程保持不变。

## 9. API Changes

- Agent `/v1/diagnose`、`/v1/diagnose/resume` 和 `/v1/work-record/generate` 不再接受服务 Bearer
  Token，改为 TLS 客户端身份；诊断端点仍要求 `X-AegisOps-Diagnosis-Grant`。
- Java `/internal/agent/**` 不再接受服务 Bearer Token，只在 8443 接受 TLS Agent 身份并继续
  要求 Grant。
- 公共 `/api/**` 无变化。

## 10. Tests

- Ed25519 PEM 加载、签发、验证、轮换双公钥窗口。
- Agent 无客户端证书、错误证书身份、缺失/错误/过期 Grant。
- Java 内部 API 错误证书和 Grant 的 401/403/审计语义。
- Execution Grant 对数据库任务、审批快照、步骤参数和有效期的篡改检测。
- Runner 验证失败时零执行器调用。
- Compose/Helm 启用 App/Agent 时缺少证书或公钥即失败；automation 档缺少 Runner 公钥或独立
  数据库凭据即失败。
- 发布探针完成 Java -> Agent -> Java 双向 mTLS，不读写业务数据。

## 11. Verification Commands

```bash
mvn -pl modules/aiops-common,modules/aiops-ai-client,modules/aiops-security,modules/aiops-execution,apps/aiops-runner -am verify
bash scripts/ci/agent.sh
pytest deploy/tests -q
bash scripts/ci/docs.sh
bash scripts/ci/verify-local.sh
```

## 12. Risks

- Spring 公共端口无法同时做到“浏览器不带证书”和“内部路径在握手期强制证书”；实现使用独立
  内部 TLS Connector/端口，避免 `client-auth=want` 被错误当作强认证。
- 证书轮换需支持新旧 CA/公钥短暂并存，否则滚动发布会产生双向调用中断。
- Execution Grant 不能只绑定顶层任务字段，必须覆盖实际步骤参数，否则数据库篡改仍可执行。
- 数据库最小权限必须和 Runner 实际 Repository 查询保持同步，不能凭文档猜测。

## 13. Follow-up

- Kubernetes 环境接入 cert-manager 或 SPIFFE Workload API 自动轮换工作负载证书。
- 增加证书到期指标和告警。
- 多 Runner 部署时评估按 Runner pool 绑定 Grant audience。
