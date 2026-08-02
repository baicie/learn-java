---
title: Phase 8.0：SaaS 多租户与生产安全加固
type: design
status: accepted
phase: phase-8
owner: ai
created: 2026-06-30
updated: 2026-08-02
related:
  - docs/adr/0012-internal-mtls-task-grants.md
  - docs/designs/phase-8/2026-08-01-mtls-task-grants.md
  - docs/api/internal-service-authentication.md
  - docs/deployment/private-deployment.md
  - docs/deployment/offline-package.md
  - docs/deployment/production-security-checklist.md
---

# Phase 8.0：SaaS 多租户与生产安全加固

## 1. 状态与范围

Phase 8 把 AegisOps 从功能型 MVP 推进到可私有化部署的生产安全基线，重点是租户上下文、
内部组件身份、任务级授权、Runner 隔离、审计和发布门禁，不增加新的业务域。

[ADR 0012](adr/0012-internal-mtls-task-grants.md) 已替代 ADR 0010 的内部 OAuth2-only 决策和
ADR 0011 的独立 Worker 部署决策。用户登录 JWT/OIDC 不变；本文件只描述 App、Agent、Runner
之间的内部信任边界。

## 2. 运行时架构

```text
Browser -- user JWT --> aegisops-app :8080

aegisops-app -- mTLS + Diagnosis Grant --> aiops-agent :9008
aiops-agent  -- mTLS + same Grant      --> aegisops-app :8443

aegisops-app -- execution row + Execution Grant --> PostgreSQL
aiops-runner -- restricted DB role             --> PostgreSQL / executor
```

`aegisops-app` 是单一 Java 模块化单体，加载 `modules/aiops-worker-runtime` 运行 Outbox、同步和
后台分析。Agent 是 Python AI 工作流边界；Runner 是高权限执行边界。三者是运行时与权限隔离，
不是按业务域拆分的微服务。

明确禁止：

```text
Agent -> Runner
Runner -> Agent
Browser -> Agent
Browser -> Runner
App 直接执行 Shell / Ansible / SSH
```

## 3. 目标

1. `/api/**` 只接受有效用户身份并由服务端建立租户上下文。
2. `/internal/agent/**` 只在 8443 mTLS Connector 上接受 Agent 证书与有效 Diagnosis Grant。
3. Diagnosis Grant 绑定租户、Incident、Diagnosis、Trace、scope、audience 和最长 300 秒有效期。
4. Execution Grant 绑定审批快照与不可变步骤摘要，Runner 在执行器调用前验证。
5. Grant 私钥只进入 App；Agent 与 Runner 只持有公钥。
6. Runner 使用 `aegisops_runner` 最小权限数据库账号，不执行 Flyway。
7. 认证、授权、Grant、限流和执行拒绝均生成不泄露凭据的审计。
8. Compose、Helm、离线包和发布工作流使用同一三进程契约。

## 4. Diagnosis Grant

App 每次发起诊断时签发 Ed25519 Grant，并通过以下 Header 发送：

```http
X-AegisOps-Diagnosis-Grant: <short-lived-grant>
```

Grant 至少绑定：

```text
issuer = aegisops-app
audience = aiops-agent-api + aegisops-internal-api
tenantId
incidentId
diagnosisId
traceId
scope
issuedAt / expiresAt / jti / keyId
```

Agent 在诊断入口验签，并在工具回调时原样传播。App 验签后从 Grant 恢复 `TenantContext`；
`X-Tenant-Id` 只能用于观测，不能建立或覆盖授权上下文。缺失、过期、错误 audience/scope、
资源不匹配或未知 keyId 均失败关闭。

## 5. Execution Grant

App 创建普通执行、重试或回滚执行时，对审批快照和按顺序排列的执行步骤生成稳定 SHA-256，
再签发 Ed25519 Execution Grant。有效期必须覆盖最大排队窗口与任务超时。

Runner 在以下时点验签：

```text
领取后
心跳和步骤状态变更前
调用任何 StepExecutor 前
```

验签失败时将执行和关联计划标记为失败，写固定脱敏的
`execution_grant_rejected` 审计，且执行器调用次数必须为零。

## 6. mTLS

App 和 Agent 使用角色隔离 CA：

```text
control-plane CA -> App certificate
agent CA         -> Agent certificate
```

证书 URI SAN 固定为：

```text
spiffe://aegisops.local/service/aegisops-app
spiffe://aegisops.local/service/aiops-agent
```

公共 8080 不启用可选客户端证书模式。Agent 回调只进入 8443，TLS 握手必须要求客户端证书。
不得提供 trust-all、明文 HTTP 或静态内部 Token 兼容路径。

## 7. 部署档位

| 档位               | 进程                              |
| ------------------ | --------------------------------- |
| Core               | App + PostgreSQL                  |
| Diagnostic（默认） | App + Agent + PostgreSQL          |
| Automation         | App + Agent + Runner + PostgreSQL |

Redis、MinIO、VictoriaMetrics 与内置 Zabbix 是可选能力，不得成为健康检查或安装的强依赖。
外部只暴露 App 8080；App 8443、Agent 9008 和 Runner 8092 仅存在于内部网络。

## 8. 验收标准

- App 同一 JVM 运行公共 API 与 Worker runtime，不存在独立 Worker 启动或镜像。
- App/Agent 双向请求同时验证 mTLS 身份与 Diagnosis Grant。
- 错误证书、Grant 篡改、过期、错误 audience/scope 和资源错配全部失败。
- Runner 数据库账号无法访问用户、租户、告警或 Incident 业务表。
- Execution Grant 篡改、过期、重试和回滚场景均在执行器调用前拒绝。
- 默认诊断安装不依赖 Keycloak、Redis、MinIO、VictoriaMetrics 或内置 Zabbix。
- 三镜像、Compose、Helm、离线包与发布门禁保持一致。

## 9. 验证入口

```bash
bash scripts/ci/backend.sh
bash scripts/ci/agent.sh
bash scripts/ci/deployment.sh
bash scripts/ci/docs.sh
bash scripts/ci/release-preflight.sh
bash scripts/ci/verify-local.sh
```
