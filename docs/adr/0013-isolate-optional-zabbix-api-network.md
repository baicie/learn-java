---
title: 隔离可选 Zabbix API 网络
type: adr
status: accepted
phase: phase-8
owner: ai
created: 2026-08-02
updated: 2026-08-02
related:
  - docs/adr/0011-default-core-deployment.md
  - docs/adr/0012-internal-mtls-task-grants.md
  - docs/operations/runbook.md
  - deploy/docker-compose.core.yml
  - deploy/docker-compose.zabbix.yml
---

# ADR 0013: 隔离可选 Zabbix API 网络

## 状态

已接受（2026-08-02）。

本 ADR 延续 ADR 0011 和 ADR 0012 中“内置 Zabbix 是可选基础设施”的决策。Core 不启动、
不等待也不检查 Zabbix 服务；仅预置一个空的集成网络，使后续单独部署 Zabbix 时无需重建 App。

## 背景

App 需要调用 Zabbix Web API，但不应加入包含 Zabbix PostgreSQL、Server 和 Agent 的默认网络，
也不应通过公网或宿主机全接口访问 Web API。App 与 Zabbix 可以分别部署，若依赖 Compose 项目
默认网络或临时执行 `docker network connect`，App 重建后容易丢失连接，并且不同发布 workflow
并发覆盖同一服务器目录时可能混用不同提交的部署脚本。

Docker Compose 不能按 profile 条件化单个服务的网络附件。为保持单一声明式 App 描述符，需要
在“所有 App 档位预置空网络”和“为 Zabbix 维护一套 Compose override 与部署分支”之间取舍。

## 决策

1. 使用固定外部网络 `aegisops-zabbix-api`。该网络由共享 helper 幂等创建，必须同时满足
   `driver=bridge` 与 `internal=true`；同名网络属性不兼容时失败关闭，不自动删除或替换。
2. `aegisops-app` 在 Core、diagnostic 和 automation 档位都加入该网络。未部署 Zabbix 时它是
   空的集成槽位，不启动任何 Zabbix 容器，也不构成 Zabbix 服务健康依赖。
3. Zabbix Compose 只把 Web 容器加入该网络，并固定别名 `aegisops-zabbix-web`。Zabbix
   PostgreSQL、Server 和 Agent 仍只存在于 Zabbix 默认网络。
4. App 的 Zabbix 数据源使用
   `http://aegisops-zabbix-web:8080/api_jsonrpc.php`。Web 与 Server 的宿主机端口默认只绑定
   `127.0.0.1`；显式扩大监听面必须先完成安全评审。
5. 所有 App 启动入口都必须在 legacy migration 或 Compose 启动前验证网络。Zabbix 组件
   workflow 必须在部署前确认 App 已加入网络，并在部署后确认网络成员严格为 App 与 Web。
6. 会写入生产服务器同一部署目录的 workflow 共用 `aegisops-prod-deploy` concurrency group，
   串行复制和执行部署材料。

## 影响

### 正向影响

- App 与 Zabbix 仍可独立发布，后部署 Zabbix 不需要重建 App。
- App 无需暴露公网 Zabbix API，也不会进入含数据库和 Server 的 Zabbix 默认网络。
- 网络属性、发布顺序和成员漂移在变更生产状态前失败关闭。

### 负向影响

- 即使不使用 Zabbix，Core 安装也会保留一个没有 Zabbix 成员的 Docker 网络。
- 绕过安装器手工执行 Core Compose 前，必须先运行网络 helper。
- Docker bridge 对成员是双向通信，不是单向 ACL。App 的 8080 用户鉴权和 8443 mTLS 仍是
  服务安全边界；不能仅依赖网络名称或成员列表授权。
- Docker 管理员仍可手工把其它容器加入外部网络，生产巡检需要继续检查实际成员。

## 备选方案

- 通过宿主机端口调用 Zabbix：拒绝。会扩大监听面并依赖宿主机路由。
- 让 App 加入 Zabbix 默认网络：拒绝。会扩大到数据库和 Server 的横向访问面。
- 部署后执行临时 `docker network connect`：拒绝。App 重建后容易漂移，Compose 也无法声明
  真实拓扑。
- 为 Zabbix 维护条件化 Compose override：当前拒绝。它会让安装、发布、smoke 和回滚增加一套
  参数组合；MVP 单机部署阶段的复杂度高于预置一个空 internal bridge 网络的成本。

## 验证

- Compose 契约断言 App 和 Zabbix Web 是唯一声明加入共享网络的业务容器。
- helper 测试覆盖首次创建、幂等复用、并发创建和不安全同名网络失败关闭。
- workflow 契约断言生产部署共用并发锁，并在 Zabbix 部署前后校验网络成员。
- 真实服务器验收检查网络属性、实际成员、回环端口、mTLS、数据源同步和 Nebula 健康状态。
