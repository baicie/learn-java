---
title: Phase 1 Design — Zabbix Datasource Sync
type: design
status: accepted
phase: phase-1
owner: ai
created: 2026-06-14
updated: 2026-06-14
related:
  - docs/architecture/system-overview.md
  - docs/designs/phase-0/2026-06-12-phase-0-design.md
---

# Phase 1 Design — Zabbix Datasource Sync

## 1. Scope

将 Zabbix 接入 AegisOps 作为第一个外部数据源，把 Zabbix 主机与 Problem 转换为平台内部统一模型，并提供可观测的同步执行记录与最小操作页面。

## 2. Background

Phase 0 已完成：

- 多模块 Maven 工程骨架
- `datasource / asset / alert_event / incident` 等基础表
- `aiops-server` 提供基础 API（datasource 只有 list）

Phase 1 选定 Zabbix 作为首个数据源，原因：

- Zabbix 是中小团队最常见的告警源
- JSON-RPC 接口稳定，集成风险低
- 字段能较完整映射到 `Asset` / `AlertEvent`

## 3. Goals

```txt
Datasource 配置 CRUD
Zabbix 连接测试
Zabbix Host -> Asset upsert
Zabbix Problem -> AlertEvent upsert
每次同步写入 datasource_sync_run
datasource.last_sync_at 自动更新
最小可用的前端操作页面
```

## 4. Non-goals

```txt
AlertEvent -> Incident 自动聚合，留给 Phase 2
RCA 规则引擎，留给 Phase 3
AI 诊断，留给 Phase 4
Ansible 执行，留给 Phase 5
凭据加密，留给企业化阶段
```

## 5. Proposed Design

### 5.1 新增模块

```txt
modules/aiops-zabbix-adapter
  ├─ ZabbixClient
  ├─ ZabbixClientFactory
  ├─ DefaultZabbixClient
  ├─ ZabbixConfig
  ├─ ZabbixHost
  ├─ ZabbixProblem
  └─ ZabbixApiException
```

依赖：`aiops-common`、`spring-web`、`jackson-databind`。不依赖任何持久化框架，作为纯外部能力 client。

### 5.2 在 `aiops-datasource` 内扩展

```txt
DataSourceService      # 业务逻辑
CreateDataSourceRequest
ZabbixConfigRequest
TestDataSourceResponse
SyncDataSourceResponse
SyncRunRecord
DataSourceEntity       # 内部持久化对象
DataSourceController   # 新增 POST/test/sync/sync-runs
DataSourceRecord       # 增加 lastSyncAt
```

### 5.3 同步流程

```txt
用户点击 Sync
  ↓
DataSourceController#sync
  ↓
DataSourceService#sync
  ↓
插入 datasource_sync_run(status='running')
  ↓
ZabbixClient.getHosts(1000)
  ↓
upsertHostAsset() × N
  ↓
ZabbixClient.getProblems(1000)
  ↓
upsertAlertEvent() × N
  ↓
更新 datasource_sync_run(status='success', stats_json)
  ↓
更新 datasource.last_sync_at
```

失败路径同样写 `datasource_sync_run(status='failed', message)` 并设置 `datasource.status='error'`。

## 6. Data Model Changes

新增迁移：`V2__phase1_zabbix_sync.sql`

```sql
create table if not exists datasource_sync_run (
  id varchar(64) primary key,
  tenant_id varchar(64) not null references tenant(id),
  datasource_id varchar(64) not null references datasource(id) on delete cascade,
  sync_type varchar(64) not null default 'manual',
  status varchar(32) not null default 'running',
  message text,
  stats_json jsonb not null default '{}'::jsonb,
  started_at timestamptz not null default now(),
  finished_at timestamptz,
  created_by varchar(64)
);

alter table datasource add column if not exists last_sync_at timestamptz;

create index if not exists idx_datasource_sync_run_ds_started
  on datasource_sync_run(datasource_id, started_at desc);

create unique index if not exists uq_asset_tenant_source_source_id
  on asset(tenant_id, source, source_id) where source_id is not null;

create unique index if not exists uq_alert_tenant_source_source_event_id
  on alert_event(tenant_id, source, source_event_id) where source_event_id is not null;
```

唯一索引均为 partial index，保留 `source_id IS NULL` 的手工记录不入索引，避免与 Phase 0 既有数据冲突。

## 7. Backend Changes

### 7.1 Host → Asset 映射

```txt
asset_type:    host
source:        zabbix
source_id:     {datasourceId}:{zabbixHostId}     -- 避免多数据源 hostid 冲突
name:          host
display_name:  name
ip:            main interface ip，回退到 dns
tags:          { datasourceId, zabbixHostId, groups[] }
status:        1 -> disabled，0/其它 -> active
```

### 7.2 Problem → AlertEvent 映射

```txt
source:           zabbix
source_event_id:  {datasourceId}:{zabbixEventId}
severity:         0/1 -> info，2/3 -> warning，4 -> critical，5 -> disaster
title:            problem.name
description:      "Zabbix problem event {eventId}"
asset_id:         hosts[0] 对应的 host asset；查不到则 null
entity_type:      host
entity_name:      problem.name
labels:           { datasourceId, zabbixEventId, zabbixObjectId, zabbixTags }
raw_payload:      Zabbix problem 原始 JSON
fingerprint:      zabbix:{datasourceId}:{objectId}
status:           open
starts_at:        Zabbix clock 转换的 timestamptz
```

## 8. Frontend Changes

`web/console/src/api/client.ts` 新增类型与 client 方法：

- `DataSourceRecord`
- `CreateZabbixDataSourcePayload`
- `TestDataSourceResponse`
- `SyncDataSourceResponse`
- `AssetRecord`
- `AlertEventRecord`
- `listDataSources / createZabbixDataSource / testDataSource / syncDataSource / listAssets / listAlerts`

`DashboardPage.tsx` 增加：

- 创建 Zabbix 数据源表单
- 数据源列表（每条带 Test / Sync 按钮）
- Assets 列表（前 8 条）
- Alerts 列表（前 8 条）
- 操作提示横幅

## 9. API Changes

```txt
GET  /api/datasources
POST /api/datasources
POST /api/datasources/{id}/test
POST /api/datasources/{id}/sync
GET  /api/datasources/{id}/sync-runs
```

Create request body：

```json
{
  "type": "zabbix",
  "name": "Local Zabbix",
  "zabbix": {
    "endpoint": "http://localhost:8080/api_jsonrpc.php",
    "username": "Admin",
    "password": "zabbix",
    "apiToken": "",
    "connectTimeoutSeconds": 5,
    "readTimeoutSeconds": 20
  }
}
```

`zabbix.username + zabbix.password` 与 `zabbix.apiToken` 二选一即可；两者都填则优先使用 token。

## 10. Tests

Phase 1 以 `mvn verify` + 前端 `tsc -b` 作为通过门槛。后续 Phase 引入：

- `DefaultZabbixClient` 用 WireMock 单测覆盖 JSON-RPC 错误路径
- `DataSourceService` 用 JdbcTemplateTest 覆盖 upsert 唯一索引冲突
- 前端 Vitest 覆盖 datasource 列表与 sync mutation

## 11. Verification Commands

```powershell
mvn -pl apps/aiops-server -am test-compile
mvn -pl modules/aiops-zabbix-adapter -am test-compile
mvn -pl modules/aiops-datasource -am test-compile
cd web/console
pnpm install
pnpm exec tsc -b
```

启动验证：

```powershell
docker compose -f infra/docker-compose.yml up -d
mvn -pl apps/aiops-server -am spring-boot:run
```

打开 `http://localhost:5173`，登录 admin / admin123，创建 Zabbix 数据源 → Test → Sync，验证 `datasource_sync_run` 与 `asset` / `alert_event` 出现新数据。

## 12. Risks

1. **凭据明文**：MVP 阶段 `datasource.config_json` 仍是 JSON 明文，包含 Zabbix 密码 / token。需在企业化前替换为加密存储。
2. **同步阻塞**：`/sync` 接口同步执行，Zabbix 大规模主机时会阻塞 HTTP 线程。当前 `aiops-server` 是 API 服务，应当在 Phase 1 之后把同步放到 `aiops-worker`，controller 只入队。
3. **重复执行**：当前每次 Sync 全量拉取并按唯一索引 upsert，足够避免重复，但缺少增量游标。
4. **前端友好度**：最小操作页没有 token vs 密码切换、连接错误重试、租户切换等。

## 13. Follow-up

- Phase 1.1：把同步迁到 `aiops-worker`，引入定时调度与增量游标
- Phase 1.2：凭据加密 + datasource 审计
- Phase 2：AlertEvent 去重聚合到 Incident
- Phase 3：开始接入 VictoriaMetrics / ClickHouse
