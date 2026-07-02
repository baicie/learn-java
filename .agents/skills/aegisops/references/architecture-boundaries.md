# 架构边界

## 三个后端应用

### aiops-server

为前端暴露 REST API 与 SSE 流式响应。

**允许:**

```txt
- 暴露 HTTP 端点
- 处理认证与鉴权
- 管理租户、用户、角色数据
- 管理数据源 (新建、更新、删除、测试、触发同步)
- 查询并聚合 Incident、告警、资产
- 触发 AI 诊断任务
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
- 执行长时间数据同步
- 执行大规模批分析
- 为 ETL 重负载直接访问 PostgreSQL 连接池
- 代表 Worker 向 VictoriaMetrics / ClickHouse 发起分析查询
```

### aiops-worker

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
- 执行 AI 诊断任务
- 生成 Postmortem 草稿
- 下发通知
- 将结果回写 PostgreSQL
```

**禁止:**

```txt
- 直接暴露 HTTP 端点给互联网
- 执行 Ansible / SSH
- 直接面向用户的 API 调用
- 处理审批流逻辑 (Server 职责)
- 向客户端流式日志 (Runner 职责)
```

### aiops-runner

在隔离与安全前提下执行自动化任务。

**允许:**

```txt
- 执行 Ansible Playbook
- 执行 SSH 命令
- 执行 Webhook
- 流式回传执行日志
- 更新 PostgreSQL 中任务状态
- 写审计日志
- 执行后健康检查
```

**禁止:**

```txt
- 暴露任何 API 端点
- 主动连接 aiops-server 获取指令
- 在没有有效 AutomationJob 记录的情况下执行任务
- 执行未处于审批通过状态的任务
- 直接注入 `ExecutionRepository` 或 `RollbackRepository`; 对 `io.aegisops.execution` 的跨模块写入必须经过 `io.aegisops.execution.service.ExecutionApplicationService` 或 `io.aegisops.execution.service.RollbackApplicationService` (ArchUnit 守卫: `RunnerArchUnitGuardTest`)
```

## 模块边界

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
