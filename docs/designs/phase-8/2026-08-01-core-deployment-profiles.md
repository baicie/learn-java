---
title: Core 与可选能力部署模式
type: design
status: deprecated
phase: phase-8
owner: ai
created: 2026-08-01
updated: 2026-08-01
related:
  - docs/adr/0010-service-authentication-oauth2-only.md
  - docs/adr/0011-default-core-deployment.md
  - docs/adr/0012-internal-mtls-task-grants.md
  - docs/api/internal-service-authentication.md
  - docs/deployment/production-security-checklist.md
  - .agents/skills/aegisops/references/architecture-boundaries.md
---

# Core 与可选能力部署模式

> 本设计已由 ADR 0012 与 `2026-08-01-mtls-task-grants.md` 取代。以下内容保留为历史背景，
> 不再作为当前部署契约。

## 1. Scope

本设计把默认部署收敛为模块化单体的最小可运行拓扑，并把 AI、自动化、演示数据源与外部
存储改为显式启用的能力。当前工作属于 Phase 8 生产加固，不改变 Phase 0-7 的业务模型。

默认 Core 仅包含：

```text
aiops-server（内嵌 Portal）
aiops-worker
PostgreSQL
```

## 2. Background

Java 后端仍只有 Server、Worker、Runner 三个可执行应用，其余 Maven 模块是编译期边界，
并不是业务微服务。当前复杂度来自部署描述符把 Keycloak、Redis、MinIO、VictoriaMetrics、
内置 Zabbix、Python Agent 与 Runner 都变成了默认拓扑，同时应用在 Agent 未使用时仍装配
OAuth2 Client Credentials、JWKS 验证器和内部 Agent Controller。

这使最小环境需要启动十余个进程，也让与 Incident 核心闭环无关的基础设施影响启动和健康检查。

## 3. Goals

- `deploy/docker-compose.core.yml` 默认只启动 Server、Worker、PostgreSQL。
- Portal 继续打进 Server JAR，不新增独立 Portal 容器。
- Agent 关闭时不创建 Agent HTTP Client、OAuth2 凭据 Provider 或 Diagnosis Grant Provider。
- 内部 Agent API 关闭时不注册 `/internal/agent/**` Controller，也不创建 JWKS Decoder。
- MinIO、VictoriaMetrics、Redis、Runner、Agent、Keycloak 和内置 Zabbix 均不属于 Core 默认服务。
- 使用 Compose profiles 显式启动 `ai`、`automation`、`demo-zabbix`、`observability`、
  `object-storage` 与 `distributed-cache` 能力。
- 保持 OAuth2-only 与 Diagnosis Grant 安全设计；AI 启用后这些配置仍为必填。

## 4. Non-goals

- 不拆分 `auth-service`、`incident-service` 等业务微服务。
- 不改变 Server、Worker、Runner 通过 PostgreSQL/Outbox 协作的方式。
- 不把 Runner 合并进 Server 或放宽审批、租约、审计边界。
- 不删除 AI、Runner、MinIO、VictoriaMetrics 或 Zabbix 能力。
- 不在本次改动中实施数据库账号最小权限；该项需要独立 migration 与运维设计。
- 不改变外部 REST/OpenAPI 数据契约。

## 5. Proposed Design

### 5.1 部署档位

| 档位       | 默认进程                              | 额外能力                                             |
| ---------- | ------------------------------------- | ---------------------------------------------------- |
| Core       | Server、Worker、PostgreSQL            | 登录、数据源接入、资产、告警、Incident、RCA 基础闭环 |
| AI         | Core + Agent + Keycloak/外部 IdP      | AI 诊断、工作记录 AI、内部 Agent 工具                |
| Automation | Core + Runner                         | 审批后的 Ansible/SSH/Webhook 执行                    |
| Demo       | Core + AI + Automation + 可选基础设施 | 内置 Zabbix、VictoriaMetrics、MinIO、Redis 演示环境  |

Core 服务不声明 profile，因此不带 `--profile` 的 `docker compose up` 就是最小部署。可选服务
声明 profile；启用 AI 或对象存储时，调用方还必须同时打开对应应用开关，避免“容器启动了但
能力仍关闭”或“能力打开了但依赖未启动”的隐式状态。

### 5.2 开关

| 环境变量                           | 默认值   | 作用                                                                         |
| ---------------------------------- | -------- | ---------------------------------------------------------------------------- |
| `AIOPS_AGENT_ENABLED`              | `false`  | 装配 Java -> Agent HTTP Client、OAuth2 Client Credentials 与 Diagnosis Grant |
| `AIOPS_INTERNAL_AGENT_API_ENABLED` | `false`  | 装配 Agent -> Java 内部 Controller、JWKS Decoder 与安全 Filter               |
| `AIOPS_OBJECT_STORAGE_ENABLED`     | `false`  | 装配 MinIO Adapter 与 URL Signer                                             |
| `AIOPS_EVIDENCE_VICTORIA_ENABLED`  | `false`  | 装配 VictoriaMetrics 证据客户端                                              |
| `AIOPS_QUOTA_BACKEND`              | `memory` | Core 使用进程内限流；`redis` 为显式可选后端                                  |
| `AIOPS_REDIS_HEALTH_ENABLED`       | `false`  | 将 Redis 纳入 Actuator 健康检查                                              |

关闭能力时保留业务 Facade，并注入 fail-closed 实现。调用被关闭的 AI 或对象存储能力会返回
明确的“feature disabled”错误，不会静默降级到无鉴权 HTTP 或本地文件系统。

### 5.3 安全边界

`AIOPS_AGENT_ENABLED=true` 时，OAuth2 token URI、client id、client secret 仍必须通过现有
校验；`AIOPS_INTERNAL_AGENT_API_ENABLED=true` 时，issuer、JWKS URI、audience 与 Diagnosis
Grant 配置仍必须通过现有校验。关闭内部 API 时 Controller 不注册，而不是仅依靠鉴权失败隐藏。

Runner 仍只领取已经审批的数据库任务，不接受前端直接调用，也不依赖 Agent。

## 6. Data Model Changes

无数据库结构或数据迁移变更。

## 7. Backend Changes

- 为实际 Agent Client、OAuth2 Provider、Diagnosis Grant Provider 增加条件装配。
- 为关闭状态提供 fail-closed 的 `AiAgentClient` 与 `WorkRecordAiClient`。
- 为内部 Agent Controller、Authenticator、Filter 增加独立开关。
- 为 MinIO Adapter/Signer 增加独立开关和 fail-closed 实现。
- Server、Worker 的 `application.yml` 明确默认关闭可选能力。

## 8. Frontend Changes

无页面行为变更。Server 镜像继续构建并内嵌 `web/portal`；本次不删除 `web/console` 源码。

## 9. API Changes

外部 API 契约不变。Core 模式不注册 `/internal/agent/**`；AI 模式开启后按现有 OAuth2 scope
和 Diagnosis Grant 契约注册。

## 10. Tests

- 条件装配测试：默认无 Agent HTTP/OAuth2/JWKS Bean，显式开启后恢复生产实现。
- Controller 条件测试：Core 不注册内部 Agent API。
- Compose 契约测试：默认服务恰好为 Server、Worker、PostgreSQL，可选服务全部带 profile。
- Core 运行时冒烟：构建并启动三个容器，等待 Server/Worker 健康，确认无可选容器运行。

## 11. Verification Commands

```bash
npx tsx scripts/docs.ts check
mvn -B -ntp -pl modules/aiops-ai-client,modules/aiops-security,modules/aiops-work-record,apps/aiops-server,apps/aiops-worker -am verify
python3 -m pytest deploy/tests/test_core_compose.py
docker compose -f deploy/docker-compose.core.yml config
bash scripts/ci/core-deployment-smoke.sh
```

## 12. Risks

- Core 模式下用户仍能看到部分可选能力入口；调用时必须 fail closed，后续可由能力发现 API
  改善前端可见性。
- Compose profile 只决定容器是否启动，应用开关仍需同步设置；文档和契约测试必须固定命令。
- `memory` 限流不跨 Server 副本；多副本生产部署应显式启用 Redis。

## 13. Follow-up

- 为 Server、Worker、Runner 设计并实施独立数据库账号及最小表权限。
- 将 Helm 的 Agent、Runner、Redis 与对象存储依赖改为可选 values，并补渲染测试。
- 增加前端能力发现，隐藏 Core 中不可用的 AI、附件和自动化入口。
