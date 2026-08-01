---
title: 默认采用 Core 最小部署
type: adr
status: accepted
phase: phase-8
owner: ai
created: 2026-08-01
updated: 2026-08-01
related:
  - docs/adr/0003-aiops-agent-boundary.md
  - docs/adr/0009-service-to-service-authentication.md
  - docs/adr/0010-service-authentication-oauth2-only.md
  - docs/designs/phase-8/2026-08-01-core-deployment-profiles.md
  - .agents/skills/aegisops/references/architecture-boundaries.md
---

# ADR 0011: 默认采用 Core 最小部署

## 状态

已接受（2026-08-01）。

本 ADR 仅替代 ADR 0010 中“本地 `infra/docker-compose.yml` 自动启动 Keycloak”以及 Agent
鉴权配置属于默认启动前提的决策。ADR 0010 的 OAuth2-only、独立 client、短期 JWT、
audience、scope 与 Diagnosis Grant 决策继续有效。

## 背景

AegisOps 的 Java 后端是模块化单体代码库，运行时按风险和工作负载隔离为 Server、Worker、
Runner，并外挂可选 Python Agent。Maven 模块数量不等于部署服务数量。

现有本地与 VM Compose 把 Keycloak、Agent、Runner、Redis、MinIO、VictoriaMetrics 和内置
Zabbix 组合成默认拓扑，使单人开发、基础功能验证和最小私有化安装承担不必要的资源与配置成本。
其中只有 Agent 跨进程 HTTP 调用需要 OAuth2；Server、Worker、Runner 通过同一 PostgreSQL、
Outbox、租约与状态机协作，不需要为此增加 HTTP 服务鉴权或新的 auth-service。

## 决策

1. 默认 Core 部署只包含 `aiops-server`、`aiops-worker` 与 PostgreSQL。
2. Portal 静态资源内嵌到 Server JAR，不在 Core 增加独立前端容器。
3. `aiops-agent` 与 Keycloak/企业 IdP 归入 AI 可选档；关闭时 Java 不装配 Agent HTTP Client、
   OAuth2 Client Credentials、Diagnosis Grant Provider 或 `/internal/agent/**`。
4. `aiops-runner` 归入 Automation 可选档并保持进程隔离；不得并入 Server。
5. 内置 Zabbix、VictoriaMetrics、MinIO 与 Redis 归入 Demo 或专项 profile。生产环境优先接入
   客户已有系统；Redis 仅在多副本限流、租约或缓存需求出现时显式启用。
6. 可选能力必须 fail closed。关闭 AI 或对象存储时，不允许退回静态 token、无鉴权 HTTP、
   本地文件存储或其它隐式兼容路径。
7. MVP 阶段继续禁止拆分 `auth-service`、`incident-service`、`asset-service` 等业务微服务。

## 影响

### 正向影响

- Core 从十余个进程收敛为三个容器，可独立完成基础 Incident 闭环验证。
- 未使用 AI 时不再要求 Keycloak/JWKS 可用，也不暴露内部 Agent API。
- Runner 的命令执行风险仍与控制面隔离。
- 完整 Demo 能力保留，但不再代表默认生产拓扑。

### 代价

- 不同部署档位需要明确的 Compose profile 与应用开关组合。
- Core 的进程内限流不适合 Server 多副本，扩容时必须启用 Redis 或等价共享后端。
- 部分可选功能在 Core 中会明确返回“未启用”，前端能力发现需要后续完善。

## 备选方案

- 默认保留完整 Compose：拒绝。资源和配置成本与最小部署目标冲突。
- 删除 OAuth2/Diagnosis Grant：拒绝。AI 启用后的跨进程与多租户授权边界仍然必要。
- 将 Agent 合并进 Server：拒绝。语言、运行时和故障隔离价值明确。
- 将 Runner 合并进 Server：拒绝。会扩大命令执行、凭据泄漏与远程代码执行风险。
- 继续拆业务微服务：拒绝。Incident 闭环的数据与事务仍高度耦合，当前没有独立扩缩容或团队边界收益。

## 验证

- Compose 解析测试固定默认服务和 profile 归属。
- Spring 条件装配测试固定关闭/开启状态下的 Bean 与 Controller 集合。
- Core 运行时冒烟必须在没有 Keycloak、Redis、MinIO、VictoriaMetrics、Agent 和 Runner 时通过。
