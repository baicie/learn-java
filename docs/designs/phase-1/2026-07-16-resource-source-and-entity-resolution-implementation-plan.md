---
title: 统一资源来源与实体解析实施计划
type: design
status: accepted
phase: phase-1
owner: ai
created: 2026-07-16
updated: 2026-07-16
related:
  - .agents/skills/aegisops/SKILL.md
  - docs/data-model.md
  - docs/scenarios/phase-z9-zabbix-mvp-acceptance.md
  - docs/adr/0007-portal-route-oriented-source-layout.md
---

# 统一资源来源与实体解析 Implementation Plan

> 实施状态：Phase 1 范围已完成。原子步骤保留为设计时的执行分解；实际验收结果与复现方式以 [多来源资源中心验收记录](../../phases/phase-1/asset-source-acceptance.md) 为准。本次还补齐了 CSV 冲突处置、问题导出、租户统计、Zabbix machine ID 提取以及真实 PostgreSQL/Portal E2E，未提前实现 Kubernetes、APM、RUM 等后续阶段 Adapter。

> **For agentic workers:** REQUIRED SUB-SKILL: Use executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking. AegisOps 项目规则优先于通用 Skill；本计划不使用多 Agent，也不引入新微服务。

**Goal:** 在现有 AegisOps 模块化单体上建立统一 Asset、来源链接、确定性实体解析、手工维护、CSV 批量导入和 Zabbix 复用入口，使后续 Kubernetes、APM、RUM 与变更事件都能关联到同一个全局资源。

**Architecture:** `aiops-asset` 持有 canonical Asset、AssetSourceLink、AssetIdentity 与 AssetRelation；各数据源 Adapter 只负责采集和映射，通过 `AssetApplicationService` 写入。CSV 是导入通道而不是 DataSource；Zabbix/Kubernetes 等长期连接才是 DataSource。异步同步由 `aiops-worker` 消费 Outbox，server 只创建同步任务和提供查询 API。

**Tech Stack:** Java 21、Spring Boot 3.5、jOOQ、PostgreSQL/Flyway、Outbox、React 19、TanStack Router/Query/Table、Zod、Vitest Browser、Playwright。

---

## 1. 现状判断与架构取舍

### 1.1 已有能力

- `asset`、`asset_relation`、`datasource` 和 `datasource_sync_run` 表已经存在。
- `aiops-datasource` 已能创建 Zabbix 数据源、测试连接、同步 Host/Problem。
- `aiops-zabbix-adapter` 已封装 Zabbix API。
- `aiops-alert`、`aiops-incident`、`aiops-evidence`、`aiops-rca` 已形成告警到证据链的基础路径。
- `automation_outbox`、`aiops-worker` 和租约重试机制已经可复用。
- Portal 已完成登录、IAM、权限导航和带类型 API 的工程底座。

### 1.2 当前缺口

- `AssetController` 只有 `GET /api/assets`，且固定返回最近 100 条。
- Asset 没有写入 facade、分页、详情、来源、身份、关系管理、归档和审计。
- 当前 `asset.source + source_id` 只能表达单来源，无法表达同一主机同时来自 CSV、Zabbix、Kubernetes 和 CMDB。
- `DataSourceService` 直接写 asset/alert 表，绕过统一实体解析。
- `ZabbixSyncJob` 仍是 no-op，同步工作还停留在 server 请求线程。
- Portal 尚无数据源和资源中心页面。
- CSV 导入不存在，资源数据质量、冲突和错误行不可见。

### 1.3 对原方案的取舍

| 原方案内容                  | 当前决策           | 原因                                                                        |
| --------------------------- | ------------------ | --------------------------------------------------------------------------- |
| Keep                        | 暂不引入           | AegisOps 已有 Alert/Incident/Outbox/审批工作流，引入会形成双主系统          |
| HolmesGPT                   | 暂不引入           | 已有 Python LangGraph Agent，应补工具和评测，不再维护第二套 Agent Runtime   |
| SigNoz                      | 作为可选外部数据源 | 不嵌入产品；通过 OTel/ClickHouse/VictoriaMetrics Adapter 对接               |
| Kafka/Redpanda              | 暂不引入           | 当前 Outbox + Worker 足以支撑 MVP；达到明确吞吐阈值后再评估                 |
| ClickHouse 存指标           | 不采用             | 项目既定职责是 VictoriaMetrics 存指标，ClickHouse 存日志、Trace、RUM 与事件 |
| Neo4j                       | 暂不引入           | 当前拓扑用 PostgreSQL `asset_relation`，复杂图遍历成为瓶颈后再评估          |
| Kubernetes/APM/RUM 一次接入 | 拆阶段             | 先统一实体，再逐个 Adapter 接入，保证每阶段可独立验收                       |

## 2. 目标模型与接入契约

```text
Manual / CSV / Zabbix / Kubernetes / OTel / RUM / Git-CI
                         ↓
                  Source Mapper
                         ↓
                AssetUpsertCommand
                         ↓
               AssetIdentityResolver
                         ↓
        Asset + AssetSourceLink + AssetIdentity
                         ↓
                  AssetRelation
                         ↓
         Alert / Evidence / Change / Incident
```

### 2.1 三个概念必须分离

```text
AssetType: host/service/database/k8s_pod/page 等被观测对象
DataSource: Zabbix/Kubernetes/OpenTelemetry 等长期外部连接
IngestionChannel: manual/csv/webhook/sync 等接入方式
```

### 2.2 自动匹配规则

只允许以下强身份自动合并：

```text
cloud_instance_id
cmdb_ci_id
machine_id
k8s_uid
otel_service_instance_id
```

以下身份只产生疑似重复，不自动合并：

```text
fqdn
hostname + environment
ip
display_name
```

匹配顺序：

```text
1. tenant + source_type + source_instance_id + external_id 精确命中 SourceLink
2. tenant + identity_type + scope_key + normalized_value 精确命中强身份
3. 无强身份命中则创建新 Asset
4. 弱身份命中仅写入冲突/候选信息，等待人工合并
```

## 3. 数据模型

### Task 1: 建立 Flyway Schema 与 jOOQ 模型

**Files:**

- Create: `apps/aiops-server/src/main/resources/db/migration/V0037__init_asset_source_foundation.sql`
- Modify: `modules/aiops-persistence/src/main/resources/jooq-codegen.xml`
- Test: `apps/aiops-server/src/test/java/io/aegisops/server/AssetSourceFoundationMigrationContractTest.java`
- Test: `apps/aiops-server/src/test/java/io/aegisops/server/FlywayMigrationVersionUniquenessTest.java`

- [ ] **Step 1: 写 migration 契约测试，要求新增表、租户字段、唯一键和索引存在**

测试必须读取 migration 文本并断言：`asset_source_link`、`asset_identity`、`asset_import_job`、`asset_import_row`、`tenant_id`、`uq_asset_source_link_external`、`uq_asset_identity_strong` 均存在。

- [ ] **Step 2: 运行测试确认因 V0037 不存在而失败**

Run:

```bash
JAVA_HOME=$(/usr/libexec/java_home -v 21) mvn -pl apps/aiops-server -am \
  -Dtest=AssetSourceFoundationMigrationContractTest test
```

Expected: FAIL，错误包含 `V0037__init_asset_source_foundation.sql`。

- [ ] **Step 3: 新增 migration**

```sql
alter table asset add column if not exists description text;
alter table asset add column if not exists site varchar(128);
alter table asset add column if not exists owner_team varchar(128);
alter table asset add column if not exists criticality varchar(32) not null default 'normal';
alter table asset add column if not exists last_seen_at timestamptz;
alter table asset add column if not exists deleted_at timestamptz;
alter table asset add column if not exists version bigint not null default 0;

create table asset_source_link (
  id varchar(64) primary key,
  tenant_id varchar(64) not null references tenant(id),
  asset_id varchar(64) not null references asset(id) on delete cascade,
  source_type varchar(64) not null,
  source_instance_id varchar(128) not null,
  datasource_id varchar(64) references datasource(id) on delete set null,
  external_id varchar(256) not null,
  ingestion_channel varchar(32) not null,
  sync_status varchar(32) not null default 'active',
  raw_payload jsonb not null default '{}'::jsonb,
  first_seen_at timestamptz not null default now(),
  last_seen_at timestamptz not null default now(),
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  constraint ck_asset_source_link_channel
    check (ingestion_channel in ('manual', 'csv', 'webhook', 'sync')),
  constraint ck_asset_source_link_status
    check (sync_status in ('active', 'stale', 'missing', 'error', 'archived'))
);

create unique index uq_asset_source_link_external
  on asset_source_link(tenant_id, source_type, source_instance_id, external_id);
create index idx_asset_source_link_asset
  on asset_source_link(tenant_id, asset_id);
create index idx_asset_source_link_datasource
  on asset_source_link(tenant_id, datasource_id, last_seen_at desc);

create table asset_identity (
  id varchar(64) primary key,
  tenant_id varchar(64) not null references tenant(id),
  asset_id varchar(64) not null references asset(id) on delete cascade,
  source_link_id varchar(64) references asset_source_link(id) on delete set null,
  identity_type varchar(64) not null,
  scope_key varchar(256) not null default 'global',
  identity_value varchar(512) not null,
  normalized_value varchar(512) not null,
  strength varchar(16) not null,
  verified boolean not null default false,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  constraint ck_asset_identity_strength check (strength in ('strong', 'weak'))
);

create unique index uq_asset_identity_strong
  on asset_identity(tenant_id, identity_type, scope_key, normalized_value)
  where strength = 'strong';
create index idx_asset_identity_asset
  on asset_identity(tenant_id, asset_id);
create index idx_asset_identity_weak_lookup
  on asset_identity(tenant_id, identity_type, scope_key, normalized_value)
  where strength = 'weak';

create table asset_import_job (
  id varchar(64) primary key,
  tenant_id varchar(64) not null references tenant(id),
  source_instance_id varchar(128) not null,
  file_name varchar(256) not null,
  content_sha256 varchar(64) not null,
  status varchar(32) not null default 'previewed',
  total_rows integer not null default 0,
  valid_rows integer not null default 0,
  invalid_rows integer not null default 0,
  created_rows integer not null default 0,
  updated_rows integer not null default 0,
  conflict_rows integer not null default 0,
  created_by varchar(64) not null,
  confirmed_by varchar(64),
  confirmed_at timestamptz,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  constraint ck_asset_import_job_status
    check (status in ('previewed', 'confirmed', 'running', 'success', 'partial', 'failed', 'cancelled')),
  constraint uq_asset_import_job_checksum unique (tenant_id, source_instance_id, content_sha256)
);

create table asset_import_row (
  id varchar(64) primary key,
  tenant_id varchar(64) not null references tenant(id),
  job_id varchar(64) not null references asset_import_job(id) on delete cascade,
  row_number integer not null,
  external_id varchar(256),
  normalized_payload jsonb not null default '{}'::jsonb,
  validation_status varchar(32) not null,
  resolution_action varchar(32),
  resolved_asset_id varchar(64) references asset(id) on delete set null,
  error_codes jsonb not null default '[]'::jsonb,
  created_at timestamptz not null default now(),
  constraint uq_asset_import_row_number unique (tenant_id, job_id, row_number),
  constraint ck_asset_import_row_validation check (validation_status in ('valid', 'invalid', 'conflict')),
  constraint ck_asset_import_row_action check (
    resolution_action is null or resolution_action in ('create', 'update', 'link', 'skip')
  )
);

create index idx_asset_import_job_tenant_created
  on asset_import_job(tenant_id, created_at desc);
create index idx_asset_import_row_job_status
  on asset_import_row(tenant_id, job_id, validation_status, row_number);

create index if not exists idx_asset_active_tenant_type
  on asset(tenant_id, asset_type, updated_at desc)
  where deleted_at is null;
```

- [ ] **Step 4: 生成 jOOQ 并运行 migration 契约**

Run:

```bash
JAVA_HOME=$(/usr/libexec/java_home -v 21) mvn -pl modules/aiops-persistence -am generate-sources
JAVA_HOME=$(/usr/libexec/java_home -v 21) mvn -pl apps/aiops-server -am \
  -Dtest=AssetSourceFoundationMigrationContractTest,FlywayMigrationVersionUniquenessTest test
```

Expected: PASS。

- [ ] **Step 5: 提交**

```bash
git add apps/aiops-server/src/main/resources/db/migration/V0037__init_asset_source_foundation.sql \
  modules/aiops-persistence/src/main/resources/jooq-codegen.xml \
  apps/aiops-server/src/test/java/io/aegisops/server/AssetSourceFoundationMigrationContractTest.java
git commit -m "feat(asset): 建立多来源资源身份模型"
```

### Task 2: 整理 aiops-asset 包边界并增加 ArchUnit 守卫

**Files:**

- Delete: `modules/aiops-asset/src/main/java/io/aegisops/asset/AssetController.java`
- Delete: `modules/aiops-asset/src/main/java/io/aegisops/asset/AssetQueryService.java`
- Delete: `modules/aiops-asset/src/main/java/io/aegisops/asset/AssetRecord.java`
- Create: `modules/aiops-asset/src/main/java/io/aegisops/asset/api/AssetController.java`
- Create: `modules/aiops-asset/src/main/java/io/aegisops/asset/application/AssetApplicationService.java`
- Create: `modules/aiops-asset/src/main/java/io/aegisops/asset/application/AssetQueryService.java`
- Create: `modules/aiops-asset/src/main/java/io/aegisops/asset/domain/model/Asset.java`
- Create: `modules/aiops-asset/src/main/java/io/aegisops/asset/infrastructure/persistence/AssetRepository.java`
- Create: `modules/aiops-asset/src/main/java/io/aegisops/asset/infrastructure/persistence/JooqAssetRepository.java`
- Create: `modules/aiops-asset/src/test/java/io/aegisops/asset/AssetArchUnitTest.java`
- Modify: `modules/aiops-asset/pom.xml`

- [ ] **Step 1: 写 ArchUnit 测试**

规则必须同时验证：Controller 只在 `..api..`、Repository 只在 `..infrastructure.persistence..`、domain 不依赖 Spring Web/JDBC/jOOQ、api 不依赖 JDBC/jOOQ。

- [ ] **Step 2: 运行测试确认当前根包平铺结构失败**

```bash
JAVA_HOME=$(/usr/libexec/java_home -v 21) mvn -pl modules/aiops-asset -am \
  -Dtest=AssetArchUnitTest test
```

Expected: FAIL，指出 `AssetController`、`AssetQueryService` 位于根包。

- [ ] **Step 3: 按 domain/application/infrastructure/api 移动文件并更新 pom**

`aiops-asset/pom.xml` 增加 `aiops-persistence`、`aiops-audit`、validation、test scope ArchUnit；保留 web 仅供 api 包使用。所有跨模块调用只暴露 `AssetApplicationService` 与 `AssetQueryService`。

- [ ] **Step 4: 运行 ArchUnit 与既有 server 边界测试**

```bash
JAVA_HOME=$(/usr/libexec/java_home -v 21) mvn -pl modules/aiops-asset,apps/aiops-server -am \
  -Dtest=AssetArchUnitTest,ControllerPersistenceBoundaryTest test
```

Expected: PASS。

- [ ] **Step 5: 提交**

```bash
git add modules/aiops-asset apps/aiops-server/src/test/java/io/aegisops/server/ControllerPersistenceBoundaryTest.java
git commit -m "refactor(asset): 建立领域模块包边界"
```

### Task 3: 实现确定性实体解析与统一 Upsert

**Files:**

- Create: `modules/aiops-asset/src/main/java/io/aegisops/asset/api/dto/AssetUpsertCommand.java`
- Create: `modules/aiops-asset/src/main/java/io/aegisops/asset/api/dto/AssetUpsertResult.java`
- Create: `modules/aiops-asset/src/main/java/io/aegisops/asset/domain/model/AssetIdentityInput.java`
- Create: `modules/aiops-asset/src/main/java/io/aegisops/asset/domain/rule/AssetIdentityNormalizer.java`
- Create: `modules/aiops-asset/src/main/java/io/aegisops/asset/domain/rule/AssetIdentityResolver.java`
- Create: `modules/aiops-asset/src/test/java/io/aegisops/asset/domain/rule/AssetIdentityResolverTest.java`
- Create: `modules/aiops-asset/src/test/java/io/aegisops/asset/application/AssetApplicationServiceTest.java`

- [ ] **Step 1: 写以下失败测试**

```java
@Test
void exactSourceLinkMustReuseCanonicalAsset() {
  AssetUpsertResult first = service.upsert(command("zabbix", "ds-1", "host-10084", "machine-1"));
  AssetUpsertResult second = service.upsert(command("zabbix", "ds-1", "host-10084", "machine-1"));

  assertThat(second.assetId()).isEqualTo(first.assetId());
  assertThat(second.action()).isEqualTo("updated");
}

@Test
void sameStrongIdentityAcrossSourcesMustLinkOneAsset() {
  AssetUpsertResult csv = service.upsert(command("csv", "cmdb-export", "row-1", "machine-1"));
  AssetUpsertResult zabbix = service.upsert(command("zabbix", "ds-1", "host-10084", "machine-1"));

  assertThat(zabbix.assetId()).isEqualTo(csv.assetId());
  assertThat(repository.sourceLinks(csv.assetId())).hasSize(2);
}

@Test
void ipOnlyMatchMustNotAutoMerge() {
  AssetUpsertResult first = service.upsert(commandWithOnlyIp("manual", "manual", "host-a", "10.0.0.8"));
  AssetUpsertResult second = service.upsert(commandWithOnlyIp("csv", "sheet-a", "host-b", "10.0.0.8"));

  assertThat(second.assetId()).isNotEqualTo(first.assetId());
  assertThat(second.hasWeakIdentityConflict()).isTrue();
}
```

- [ ] **Step 2: 运行测试确认 resolver 尚不存在**

```bash
JAVA_HOME=$(/usr/libexec/java_home -v 21) mvn -pl modules/aiops-asset -am \
  -Dtest=AssetIdentityResolverTest,AssetApplicationServiceTest test
```

Expected: FAIL，编译错误指向缺失类型。

- [ ] **Step 3: 定义跨模块命令契约**

```java
public record AssetUpsertCommand(
    String tenantId,
    String assetType,
    String name,
    String displayName,
    String description,
    String environment,
    String site,
    String ownerTeam,
    String criticality,
    String ip,
    Map<String, Object> tags,
    String sourceType,
    String sourceInstanceId,
    String datasourceId,
    String externalId,
    String ingestionChannel,
    Map<String, Object> rawPayload,
    List<AssetIdentityInput> identities) {}
```

- [ ] **Step 4: 实现 resolver 和事务性 upsert**

`AssetApplicationService.upsert` 必须在同一事务中：校验 tenant、按 SourceLink 查询、按强身份查询、创建或更新 Asset、写 SourceLink、写 Identity、刷新 `last_seen_at`、返回弱身份冲突。不得根据 IP 自动合并。

- [ ] **Step 5: 运行测试**

```bash
JAVA_HOME=$(/usr/libexec/java_home -v 21) mvn -pl modules/aiops-asset -am test
```

Expected: PASS。

- [ ] **Step 6: 提交**

```bash
git add modules/aiops-asset
git commit -m "feat(asset): 实现确定性实体解析与统一 upsert"
```

### Task 4: 增加 Asset CRUD、分页、详情、来源和关系 API

**Files:**

- Create: `modules/aiops-asset/src/main/java/io/aegisops/asset/api/dto/CreateAssetRequest.java`
- Create: `modules/aiops-asset/src/main/java/io/aegisops/asset/api/dto/UpdateAssetRequest.java`
- Create: `modules/aiops-asset/src/main/java/io/aegisops/asset/api/dto/AssetResponse.java`
- Create: `modules/aiops-asset/src/main/java/io/aegisops/asset/api/dto/AssetPageResponse.java`
- Create: `modules/aiops-asset/src/main/java/io/aegisops/asset/api/dto/CreateAssetRelationRequest.java`
- Create: `apps/aiops-server/src/main/resources/db/migration/V0038__init_asset_permissions.sql`
- Modify: `modules/aiops-asset/src/main/java/io/aegisops/asset/api/AssetController.java`
- Modify: `modules/aiops-security/src/main/java/io/aegisops/security/PermissionCodes.java`
- Create: `modules/aiops-asset/src/test/java/io/aegisops/asset/api/AssetControllerTest.java`
- Create: `modules/aiops-asset/src/test/java/io/aegisops/asset/application/AssetAuthorizationTest.java`
- Create: `docs/api/assets.md`

- [ ] **Step 1: 写 API 测试，固定以下契约**

```text
GET    /api/assets?page=1&pageSize=20&assetType=host&sourceType=zabbix&keyword=db
POST   /api/assets
GET    /api/assets/{id}
PUT    /api/assets/{id}
POST   /api/assets/{id}/archive
GET    /api/assets/{id}/sources
GET    /api/assets/{id}/identities
GET    /api/assets/{id}/relations
POST   /api/assets/{id}/relations
DELETE /api/assets/{id}/relations/{relationId}
```

分页最大 `pageSize=100`；普通读取要求 `asset:read`；写入、归档和关系变更要求 `asset:write`；所有查询强制 tenant 条件。

- [ ] **Step 2: 新增权限常量和 V0038 权限种子**

```java
public static final String ASSET_READ = "asset:read";
public static final String ASSET_WRITE = "asset:write";
public static final String ASSET_IMPORT = "asset:import";
```

`V0038__init_asset_permissions.sql` 向 `permission` 表幂等插入 `asset:write` 和 `asset:import`，并把三项权限加入管理员角色；不回改已经在 Task 1 提交的 `V0037`。

- [ ] **Step 3: 实现 API 和审计**

必须写审计动作：`asset.create`、`asset.update`、`asset.archive`、`asset.relation.create`、`asset.relation.delete`。创建请求中的 tenantId、createdBy、createdAt 一律忽略，由后端上下文生成。

- [ ] **Step 4: 运行测试**

```bash
JAVA_HOME=$(/usr/libexec/java_home -v 21) mvn -pl modules/aiops-asset,modules/aiops-security -am test
```

Expected: PASS。

- [ ] **Step 5: 提交**

```bash
git add modules/aiops-asset modules/aiops-security \
  apps/aiops-server/src/main/resources/db/migration/V0038__init_asset_permissions.sql docs/api/assets.md
git commit -m "feat(asset): 增加资源管理与关系 API"
```

### Task 5: 实现 CSV 预检、确认和幂等导入

**Files:**

- Modify: `modules/aiops-asset/pom.xml`
- Create: `modules/aiops-asset/src/main/java/io/aegisops/asset/api/AssetImportController.java`
- Create: `modules/aiops-asset/src/main/java/io/aegisops/asset/api/dto/AssetImportPreviewResponse.java`
- Create: `modules/aiops-asset/src/main/java/io/aegisops/asset/application/AssetImportService.java`
- Create: `modules/aiops-asset/src/main/java/io/aegisops/asset/domain/rule/AssetCsvRowValidator.java`
- Create: `modules/aiops-asset/src/main/java/io/aegisops/asset/infrastructure/adapter/AssetCsvParser.java`
- Create: `modules/aiops-asset/src/main/java/io/aegisops/asset/infrastructure/persistence/AssetImportRepository.java`
- Create: `modules/aiops-asset/src/test/java/io/aegisops/asset/infrastructure/adapter/AssetCsvParserTest.java`
- Create: `modules/aiops-asset/src/test/java/io/aegisops/asset/application/AssetImportServiceTest.java`
- Create: `modules/aiops-asset/src/test/resources/assets/valid-assets.csv`
- Create: `modules/aiops-asset/src/test/resources/assets/conflicting-assets.csv`
- Modify: `docs/api/assets.md`

- [ ] **Step 1: 固定 CSV 模板**

```csv
external_id,asset_type,name,display_name,environment,site,owner_team,criticality,ip,machine_id,cloud_instance_id,k8s_uid,tags
host-001,host,db-prod-01,生产数据库一号,production,shanghai-idc,payment,tier-1,10.0.0.8,machine-001,,,"role=mysql;engine=postgresql"
```

必填列：`external_id,asset_type,name`。最大 5 MiB、5000 行；UTF-8；未知列拒绝；重复表头拒绝；公式样式内容按普通字符串处理，不执行。

- [ ] **Step 2: 写 parser 与 service 失败测试**

覆盖：quoted comma、UTF-8、非法 asset type、缺 external_id、重复 external_id、强身份冲突、同 checksum 幂等、非法行不落 asset、确认前不落 asset。

- [ ] **Step 3: 增加 Apache Commons CSV 依赖并实现解析**

使用 `org.apache.commons:commons-csv`，不自写逗号分隔器。Parser 只返回结构化行，不访问数据库；Validator 只做字段与类型规则；Service 负责编排预检和确认。

- [ ] **Step 4: 实现 API**

```text
GET  /api/assets/imports/template
POST /api/assets/imports/preview       multipart: file, sourceInstanceId
GET  /api/assets/imports/{jobId}
GET  /api/assets/imports/{jobId}/rows?page=1&pageSize=100&status=invalid
POST /api/assets/imports/{jobId}/confirm
POST /api/assets/imports/{jobId}/cancel
```

确认接口只处理 `valid` 行；存在 `conflict` 时返回 409 并要求用户选择 `create/link/skip`，不得静默覆盖。每行调用同一个 `AssetApplicationService.upsert`。

- [ ] **Step 5: 运行测试**

```bash
JAVA_HOME=$(/usr/libexec/java_home -v 21) mvn -pl modules/aiops-asset -am test
```

Expected: PASS。

- [ ] **Step 6: 提交**

```bash
git add modules/aiops-asset docs/api/assets.md
git commit -m "feat(asset): 支持 CSV 资源预检与幂等导入"
```

### Task 6: 将 Zabbix 同步迁移到统一 AssetApplicationService 和 Worker

**Files:**

- Modify: `modules/aiops-datasource/pom.xml`
- Create: `modules/aiops-datasource/src/main/java/io/aegisops/datasource/application/DataSourceSyncApplicationService.java`
- Create: `modules/aiops-datasource/src/main/java/io/aegisops/datasource/api/dto/StartSyncResponse.java`
- Modify: `modules/aiops-datasource/src/main/java/io/aegisops/datasource/DataSourceService.java`
- Modify: `modules/aiops-datasource/src/main/java/io/aegisops/datasource/DataSourceController.java`
- Modify: `apps/aiops-worker/src/main/java/io/aegisops/worker/job/ZabbixSyncJob.java`
- Create: `apps/aiops-worker/src/test/java/io/aegisops/worker/job/ZabbixSyncJobTest.java`
- Create: `modules/aiops-datasource/src/test/java/io/aegisops/datasource/application/ZabbixAssetReconciliationTest.java`
- Modify: `docs/api/assets.md`

- [ ] **Step 1: 写失败测试**

验证：server 的 sync 端点只创建 sync run 和 outbox；Worker 消费 `zabbix-sync`；Host 映射后调用 `AssetApplicationService.upsert`；重复同步只更新 SourceLink；不同 Zabbix 实例相同 hostid 不冲突；同步异常写 failed run。

- [ ] **Step 2: 将 endpoint 改为异步契约**

```json
{
  "runId": "sync_xxx",
  "status": "pending"
}
```

`POST /api/datasources/{id}/sync` 返回 HTTP 202，不再等待 30 秒。Outbox idempotency key 使用：

```text
zabbix-sync:{tenantId}:{datasourceId}:{runId}
```

- [ ] **Step 3: 实现 Worker Job**

`ZabbixSyncJob.handle` 从 payload 读取 tenantId/datasourceId/runId，调用 `DataSourceSyncApplicationService.execute`。应用服务通过 `ZabbixClient` 采集，通过 `AssetApplicationService` 写 Asset，通过现有 Alert facade 写 AlertEvent；Worker 不直接注入其它模块 Repository。

- [ ] **Step 4: 删除 DataSourceService 内直接写 asset 表的 SQL**

保留 DataSource 生命周期和 sync run 持久化；Asset 写入全部经 facade。同步完成后将本轮未出现、且超过配置宽限期的 SourceLink 标为 `missing`，第一版不自动归档 Asset。

- [ ] **Step 5: 运行 worker、datasource、asset 测试**

```bash
JAVA_HOME=$(/usr/libexec/java_home -v 21) mvn \
  -pl modules/aiops-asset,modules/aiops-datasource,apps/aiops-worker -am test
```

Expected: PASS。

- [ ] **Step 6: 提交**

```bash
git add modules/aiops-datasource modules/aiops-asset apps/aiops-worker docs/api/assets.md
git commit -m "refactor(zabbix): 统一资源解析并异步执行同步"
```

### Task 7: 按现有 Portal 设计实现数据源、资源中心和 CSV 导入流程

**Files:**

- Create: `web/portal/src/lib/datasources/datasource.ts`
- Create: `web/portal/src/api/datasources/datasources-api.ts`
- Create: `web/portal/src/api/datasources/query-keys.ts`
- Create: `web/portal/src/hooks/datasources/use-datasources.ts`
- Create: `web/portal/src/pages/datasources/index.tsx`
- Create: `web/portal/src/components/datasources/datasources-table.tsx`
- Create: `web/portal/src/components/datasources/datasource-columns.tsx`
- Create: `web/portal/src/components/datasources/datasource-form-dialog.tsx`
- Create: `web/portal/src/components/datasources/datasource-actions.tsx`
- Create: `web/portal/src/lib/assets/asset.ts`
- Create: `web/portal/src/api/assets/assets-api.ts`
- Create: `web/portal/src/api/assets/query-keys.ts`
- Create: `web/portal/src/hooks/assets/use-assets.ts`
- Create: `web/portal/src/pages/assets/index.tsx`
- Create: `web/portal/src/pages/assets/detail.tsx`
- Create: `web/portal/src/components/assets/assets-table.tsx`
- Create: `web/portal/src/components/assets/asset-columns.tsx`
- Create: `web/portal/src/components/assets/asset-form-dialog.tsx`
- Create: `web/portal/src/components/assets/asset-import-dialog.tsx`
- Create: `web/portal/src/components/assets/asset-import-preview-table.tsx`
- Create: `web/portal/src/components/assets/asset-summary-cards.tsx`
- Create: `web/portal/src/components/assets/asset-identity-list.tsx`
- Create: `web/portal/src/components/assets/asset-source-list.tsx`
- Create: `web/portal/src/components/assets/asset-relation-list.tsx`
- Create: `web/portal/src/routes/_authenticated/datasources/index.tsx`
- Create: `web/portal/src/routes/_authenticated/assets/index.tsx`
- Create: `web/portal/src/routes/_authenticated/assets/$assetId.tsx`
- Modify: `web/portal/src/components/layout/navigation.ts`
- Modify: `web/portal/src/i18n/locales/zh-CN/navigation.json`
- Modify: `web/portal/src/i18n/locales/en-US/navigation.json`
- Test: `web/portal/src/api/assets/assets-api.test.ts`
- Test: `web/portal/src/pages/datasources/index.test.tsx`
- Test: `web/portal/src/components/assets/asset-import-dialog.test.tsx`
- Test: `web/portal/src/pages/assets/index.test.tsx`

#### 前端设计基线

本任务不是新建独立前端，必须复用当前 Portal 的 `AuthenticatedLayout + AppSidebar + Header + Main`。页面密度、标题层级、卡片、表格、Dialog 和响应式行为参考现有 `pages/dashboard`、`pages/dictionaries`、`components/iam` 与 `components/data-table`；不从 `web/console` 复制组件或主题。

信息架构：

```text
Dashboard
资源与接入
  ├─ 数据源        /datasources   datasource:read
  └─ 资源中心      /assets       asset:read
工作记录
平台管理
```

“资源与接入”是现有 Sidebar 的同级折叠组，使用 `DatabaseZap` 图标；数据源使用 `PlugZap`，资源中心使用 `Boxes`。没有读取权限时隐藏对应子项；组内全部子项不可见时隐藏整个组。第一阶段数据源配置只展示真实可用的 Zabbix 类型，不展示不可操作的 Kubernetes、OTel 或 RUM 假入口。

页面骨架：

```text
Header: SidebarTrigger | 面包屑/页面上下文 | Search | ThemeSwitch | ProfileDropdown
Main:
  PageHeading: 标题 + 一句话说明 + 主操作
  Feedback: 页面级错误 / 同步状态
  Content: 筛选工具栏 + 表格或详情卡片
```

桌面端使用现有 `Main` 的最大宽度和容器查询；小于 `@md/content` 时，标题操作区纵向排列、表格横向滚动、Dialog 改用接近全屏的可滚动内容。颜色只使用 `background/foreground/muted/border/destructive` 等语义 token；状态差异同时使用文字和图标，不能只依赖颜色。

#### 数据源页面设计

`/datasources` 顶部标题为“数据源”，说明为“管理外部监控系统的连接、健康状态和同步任务”。右上角在 `datasource:write` 权限下显示“添加数据源”。

列表沿用 Portal DataTable，列为：

```text
名称 | 类型 | Endpoint | 连接状态 | 最近同步 | 最近结果 | 操作
```

- `连接状态` 使用集中状态映射：`active / disabled / error / testing`。
- 行操作包含编辑、测试连接、立即同步和停用；高影响操作进入 `AlertDialog`。
- “测试连接”和“立即同步”显示行级 pending 状态，避免阻塞整表；同步接口返回 `runId` 后显示“已加入队列”，通过轮询 Query 刷新最近结果。
- 新增/编辑使用 `Dialog + react-hook-form + zodResolver`。敏感字段保存后不回显，只展示“已配置”；空密码表示保持原值，后端错误通过 `setError` 映射到字段。
- 无数据时使用 `Empty`，主操作为“添加 Zabbix 数据源”，辅助说明指向连接地址、用户名和凭证要求。

#### 资源列表设计

`/assets` 顶部标题为“资源中心”，说明为“统一查看来自手工、CSV 和监控系统的主机与服务”。右上角依权限显示“新增资源”和“导入 CSV”。其下放四个紧凑摘要卡：资源总数、活跃资源、多来源资源、待处理冲突；它们用于数据质量反馈，不扩展为监控大屏。

DataTable 列为：

```text
名称 | 类型 | 环境 | 地址/标识 | 来源 | 负责人 | 关键等级 | 状态 | 最近更新 | 操作
```

筛选和分页全部落入 URL：`page/pageSize/keyword/assetType/sourceType/environment/status/criticality`。名称跳转 `/assets/$assetId`；来源列显示最多两个 Badge，超出部分显示 `+N` Tooltip；默认不提供批量删除，只允许有权限用户归档单个资源。

页面状态必须完整：

- 初次加载：表头和行使用 `Skeleton`，不闪现“暂无数据”。
- 空数据：`Empty` 提供“新增资源”和“导入 CSV”两个权限化入口。
- 筛选无结果：保留筛选条件并提供“清除筛选”，不显示新增引导。
- 查询失败：使用现有 `ErrorState` 并提供重试。

#### 新增/编辑资源设计

手工新增和编辑复用 `asset-form-dialog.tsx`，字段按以下分组：

```text
基本信息: 资源类型、名称、显示名称、状态
运行上下文: 环境、站点、负责人团队、关键等级
身份信息: machine_id / cloud_instance_id / cmdb_ci_id（可选）
标签: key/value（可选，限制数量和长度）
```

创建时来源固定为 `manual`，不让用户伪造 Zabbix/Kubernetes SourceLink。编辑页只修改 canonical Asset；外部来源标识在详情页只读展示，避免手工修改破坏下一次同步。

#### 资源详情设计

`/assets/$assetId` 使用独立路由，顶部提供返回列表、资源名称、类型/状态 Badge，以及编辑、归档操作。正文为响应式双栏：

```text
左侧 2/3
  ├─ 基本信息 Card
  ├─ 身份标识 Card
  └─ 标签 Card
右侧 1/3
  ├─ 数据来源 Card（来源、externalId、首次/最近发现、状态）
  └─ 资源关系 Card（关系类型、方向、目标资源、置信度）
```

移动端改为单栏。第一阶段关系以列表展示，不引入 React Flow 或伪拓扑图；后续真实 K8s/APM 关系规模达到需求后再单独设计拓扑视图。

#### CSV 导入设计

导入使用宽屏 `Dialog`，保持三步且不引入新的 Stepper 依赖：

```text
1 上传文件 → 2 校验预览 → 3 导入结果
```

- 上传：提供模板下载、拖放区和文件选择；明确 `.csv`、UTF-8、5 MiB、5000 行限制。
- 预览：顶部四项统计为新增、更新、冲突、错误；下方表格可按结果筛选，列为行号、资源名称、动作、匹配依据、目标资源、问题。
- 冲突：显示来源、externalId、疑似 Asset 和冲突原因；第一阶段只允许“跳过冲突”，不在导入弹窗中实现手工合并。
- 确认：按钮文案必须包含影响数量，如“确认导入 126 条”；存在阻断错误时禁用并解释原因。
- 结果：展示成功/跳过/失败统计，允许下载错误行；完成后失效资源列表、摘要和导入任务 Query。
- 关闭保护：预览后或提交期间关闭 Dialog 需要确认；提交期间禁止重复请求。

#### 状态、可访问性与文案

- 所有 `Dialog/AlertDialog` 必须有 Title 和 Description；图标按钮必须有 `aria-label`。
- 表单控件使用现有 `Form/Label/Input/Select`，错误与字段关联；焦点在 Dialog 打开、步骤切换和错误发生后落到正确位置。
- 所有枚举通过 `lib/assets`、`lib/datasources` 集中映射中英文 label 和 Badge variant，不在 cell 中散落判断。
- 页面文案默认中文；导航同步维护 `zh-CN/en-US`，不在本任务内重构现有全站国际化。
- 不新增全局状态。URL 保存表格状态，TanStack Query 保存服务端状态，Dialog/当前行放 feature Provider 或本地状态。

- [ ] **Step 1: 定义并测试 DataSource 与 Asset Zod Schema**

```ts
export const assetSchema = z.object({
  id: z.string().min(1),
  assetType: z.enum([
    'host',
    'service',
    'endpoint',
    'database',
    'redis',
    'middleware',
    'application',
    'page',
    'k8s_cluster',
    'k8s_namespace',
    'k8s_node',
    'k8s_workload',
    'k8s_pod',
    'k8s_service',
  ]),
  name: z.string().min(1),
  displayName: z.string().nullable(),
  environment: z.string().nullable(),
  site: z.string().nullable(),
  ownerTeam: z.string().nullable(),
  criticality: z.enum(['normal', 'tier-3', 'tier-2', 'tier-1']),
  status: z.enum(['active', 'disabled', 'archived']),
  sourceCount: z.number().int().nonnegative(),
  updatedAt: z.string(),
})
```

- [ ] **Step 2: 实现 DataSource/Asset API 和 Query Hooks**

API 统一使用 `apiClient`，回包必须经 Zod parse；列表参数来自路由 search；mutation 成功后 invalidates `assetKeys.lists()` 和对应 detail key；组件不直接调 axios。

- [ ] **Step 3: 实现文件路由和权限守卫**

```ts
const assetSearchSchema = z.object({
  page: z.coerce.number().int().min(1).catch(1),
  pageSize: z.coerce.number().int().min(1).max(100).catch(20),
  keyword: z.string().catch(''),
  assetType: z.array(z.string()).catch([]),
  sourceType: z.array(z.string()).catch([]),
  status: z.array(z.string()).catch([]),
})
```

数据源路由要求 `datasource:read`，新增、编辑、测试和同步按钮要求 `datasource:write`；资源列表路由要求 `asset:read`；新增和编辑按钮要求 `asset:write`；导入按钮要求 `asset:import`。

- [ ] **Step 4: 实现数据源页面与资源列表/详情**

先交付数据源空态、Zabbix 配置、连接测试和同步状态，再交付资源列表、手工新增、详情、来源和关系卡片。复用现有 `components/data-table`、`PermissionGate`、`ErrorState`、`Header` 和 `Main`，不得在页面中直接调用 API。

- [ ] **Step 5: 实现 CSV 三步交互**

```text
上传 CSV → 展示新增/更新/冲突/错误统计 → 用户确认执行
```

冲突行必须展示来源、externalId、疑似 Asset 和原因；错误行支持筛选和下载。Dialog 必须有 Title/Description；空态使用 `Empty`；表格分页和筛选使用 `useTableUrlState`。

- [ ] **Step 6: 更新导航与 i18n**

在 Dashboard 后新增“资源与接入”折叠组，包含“数据源”和“资源中心”；按子项权限过滤。不得新增 `src/features`，不得直接修改 `src/components/ui`。

- [ ] **Step 7: 运行 Portal 校验**

```bash
cd web/portal
pnpm run format:check
pnpm run lint
pnpm run typecheck
pnpm run test
pnpm run knip
pnpm run build
```

Expected: 全部 PASS，`routeTree.gen.ts` 已自动更新并纳入提交。

- [ ] **Step 8: 提交**

```bash
git add web/portal
git commit -m "feat(portal): 增加数据源与资源接入中心"
```

### Task 8: 完成真实 PostgreSQL 与 Portal E2E 验收

**Files:**

- Create: `apps/aiops-server/src/test/java/io/aegisops/server/acceptance/AssetSourceAcceptanceIT.java`
- Create: `web/portal/e2e/assets.spec.ts`
- Create: `docs/phases/phase-1/asset-source-acceptance.md`
- Modify: `scripts/ci/backend.sh`
- Modify: `scripts/ci/frontend.sh`
- Modify: `docs/data-model.md`
- Modify: `docs/INDEX.md` only through `npx tsx scripts/docs.ts index`

- [ ] **Step 1: 写 PostgreSQL IT**

场景固定为：CSV 导入 `machine_id=machine-001` 创建 Host；Zabbix 同步同一 machine ID；断言只有一个 Asset、两个 SourceLink；第二租户可创建相同 machine ID；IP 相同但 machine ID 不同不会合并；归档 Asset 后默认列表不可见。

- [ ] **Step 2: 写 Playwright E2E**

场景固定为：登录 → 打开资源中心 → 下载模板 → 上传 fixture → 预览 → 确认 → 查看详情 → 看到 CSV SourceLink → 触发 Zabbix 同步 → 看到 Zabbix SourceLink。

- [ ] **Step 3: 运行分项验收**

```bash
JAVA_HOME=$(/usr/libexec/java_home -v 21) mvn -pl apps/aiops-server -am \
  -Dtest=AssetSourceAcceptanceIT test
cd web/portal && pnpm exec playwright test e2e/assets.spec.ts
```

Expected: PASS；Docker 不可用时验收任务必须明确失败，不允许把核心验收计为成功且 skipped。

- [ ] **Step 4: 运行全量门禁并生成文档索引**

```bash
npx tsx scripts/docs.ts check
npx tsx scripts/docs.ts index
JAVA_HOME=$(/usr/libexec/java_home -v 21) bash scripts/ci/verify-local.sh
```

Expected: 全部 PASS。

- [ ] **Step 5: 提交**

```bash
git add apps/aiops-server web/portal scripts/ci docs
git commit -m "test(asset): 固化多来源资源端到端验收"
```

## 4. 后续独立子计划

本计划完成后按以下顺序分别编写 implementation plan，不合并成一个超大 PR。

### 4.1 Kubernetes Inventory Adapter

```text
范围: Cluster/Namespace/Node/Workload/Pod/Service/Ingress + owner reference
模块: 新增 aiops-kubernetes-adapter，写入仍经 AssetApplicationService
不做: Kubernetes 自动执行、全量日志采集、Operator
验收: 同一 K8s Node 与 Zabbix Host 通过 machine_id/provider_id 归一为一个 Asset
```

### 4.2 OpenTelemetry/APM 数据源

```text
范围: service.name/service.version/deployment.environment/resource attributes
存储: 指标进入 VictoriaMetrics；Trace/日志进入 ClickHouse
模块: aiops-otel-adapter + evidence collector
不做: 自研 Collector、嵌入 SigNoz UI、全量复制遥测到 PostgreSQL
验收: Trace、日志和指标可通过 entity_id 进入同一 Incident 证据链
```

### 4.3 变更事件与轻量 Service Catalog

```text
范围: GitHub Actions/Deployment/Helm 变更，service owner/repository/runbook/dependency
模型: change_event + service catalog 扩展 + AssetRelation
不做: 完整 Backstage/CMDB、审批流水线
验收: 发布事件在时间线出现，并能关联到 service/version/owner
```

### 4.4 跨源关联与 RCA 评分

```text
范围: 实体、时间、拓扑、Trigger、变更、历史案例六类确定性特征
实现: 规则输出每项子分和证据，不把加权评分交给 LLM
不做: 训练模型、复杂图数据库
验收: 已知故障根因进入 Top 3，且每个候选至少有一条可回查证据
```

### 4.5 RUM 与用户影响

```text
前置: APM service/trace 实体归一稳定后再开始
范围: page/session/error/web-vitals/trace correlation
存储: ClickHouse + MinIO session replay object
不做: 第一阶段自研完整前端监控 SDK
验收: RUM 错误能关联到 page → service → trace → Incident
```

### 4.6 完整 Phase Z9 验收

```text
Zabbix/OTel/K8s 故障注入
→ Asset/Alert/Incident
→ Evidence/RCA/AI
→ Runbook 推荐
→ 人工审批
→ Runner/Ansible
→ 日志和结果回写
→ Incident 关闭
→ Postmortem
```

## 5. 阶段关卡和成功指标

第一阶段不承诺原方案中尚无真实样本支撑的 95%/90% 指标，先建立可测基线：

| 指标            | 第一阶段关卡                                                    |
| --------------- | --------------------------------------------------------------- |
| SourceLink 幂等 | 相同来源 external_id 重放 100% 不产生重复 Asset                 |
| 强身份归一      | fixture 中 machine_id/cloud_instance_id/k8s_uid 精确映射率 100% |
| 弱身份安全      | 仅 IP/主机名相同的样本 0 次自动误合并                           |
| CSV 可解释性    | 每个失败行都有 row_number 和 error_codes                        |
| 多租户隔离      | 跨租户查询、更新、合并全部拒绝                                  |
| Zabbix 同步     | server 请求线程不执行长同步，Worker 可重试且幂等                |
| 审计            | 创建、更新、归档、导入确认、关系变更全部有审计记录              |
| Portal          | 资源列表、详情、来源、导入关键路径 E2E 通过                     |

进入 Kubernetes Adapter 前必须满足：Task 1–8 全部完成、无 P0/P1、全量门禁通过、Zabbix Host 已完全改走统一 Asset facade。
