---
title: Core Domain Models
type: database
status: accepted
phase: global
owner: ai
created: 2026-06-30
updated: 2026-07-29
related:
  - .agents/skills/aegisops/SKILL.md
  - docs/architecture.md
---

# Core Domain Models

> **单一真相源：`.agents/skills/aegisops/SKILL.md` §6** — 本文件为顶层入口；详细字段、规则与
> 异常处理以 SKILL 为准。本文件只列实体清单与索引，方便快速查阅。

## 1. 实体清单（指向 SKILL §6）

| 编号 | 实体              | SKILL § | 一句话说明                                                                                 |
| ---- | ----------------- | ------- | ------------------------------------------------------------------------------------------ |
| 6.1  | Tenant            | §6.1    | 业务隔离根，所有业务对象必带 tenant_id                                                     |
| 6.2  | User              | §6.2    | 平台用户，租户内唯一，不存明文密码                                                         |
| 6.3  | Role / Permission | §6.3    | RBAC，支持 datasource:read / incident:write / automation:approve 等                        |
| 6.4  | DataSource        | §6.4    | 外部系统抽象（zabbix/vm/clickhouse/otel/webhook 等）                                       |
| 6.5  | Asset             | §6.5    | 被监控对象（host/service/db/k8s_pod 等）                                                   |
| 6.6  | AssetRelation     | §6.6    | 资产间关系（depends_on / runs_on / contains 等）                                           |
| 6.7  | AlertEvent        | §6.7    | 归一化后的告警事件，fingerprint = source + asset_id + source_trigger_id + normalized_title |
| 6.8  | Incident          | §6.8    | **核心聚合根**：聚合 AlertEvent / Diagnosis / AutomationJob / Postmortem                   |
| 6.9  | IncidentEvent     | §6.9    | Incident 与事件的关联（primary / upstream / evidence）                                     |
| 6.10 | IncidentTimeline  | §6.10   | 详情页时间线（10 种事件类型）                                                              |
| 6.11 | DiagnosisResult   | §6.11   | AI 诊断结构化输出（summary / evidence / suggestions）                                      |
| 6.12 | Runbook           | §6.12   | 内置/自定义操作手册（8 种 step 类型）                                                      |
| 6.13 | AutomationJob     | §6.13   | 自动化执行任务（pending → running → success/failed/timeout/cancelled）                     |
| 6.14 | AuditLog          | §6.14   | 敏感操作审计（login / datasource / automation approval 等 10 类）                          |

## 2. 核心聚合关系

```txt
Tenant 1 ─┬─ n User
          ├─ n Role/Permission
          ├─ n DataSource
          ├─ n Asset ─ n AssetRelation
          ├─ n AlertEvent ──┐
          │                  ▼
          ├─ n Incident ◄────┘ (聚合)
          │       ├─ n IncidentEvent
          │       ├─ n IncidentTimeline
          │       ├─ n DiagnosisResult
          │       ├─ n AutomationJob ─ n AutomationJobLog
          │       └─ 1 Postmortem (Phase 6)
          ├─ n Runbook
          └─ n AuditLog
```

## 3. 多租户强制约束（SKILL §6 + AGENTS §3.6）

- 除显式声明 global 的实体外，**所有**业务对象必须带 `tenant_id`
- Repository 层必须强制 tenant 过滤（PR4 ArchUnit 守门已落地）
- Flyway migration 必须为每个新表显式声明 `tenant_id` 约束（PR2 clean-slate 落地）

## 4. 字段约束（SKILL §6 节选）

```txt
- 密码：password_hash，禁止明文
- 配置：encrypted_config，禁明文 token 列
- Fingerprint：source + asset_id + source_trigger_id + normalized_title
- Severity（AlertEvent）：info | low | warning | medium | high | critical | disaster；兼容输入
  `average` 入库为 `medium`，Zabbix `disaster` 映射为平台 `critical`
- Alert Status：open | resolved；兼容输入 `recovered | closed | ok` 入库为 `resolved`
- Incident Status：open | investigating | mitigating | resolved | closed | ignored
- Automation Status：pending → waiting_approval → approved → running → success/failed/cancelled/timeout
```

## 5. 数据库迁移规则（SKILL §17 + AGENTS §3.2）

- 命名：`V\d{4}__init_[a-z_]+\.sql`，不允许 v1 / legacy / old 前缀
- 唯一事实源：每张表一旦创建即为权威，不允许双写兼容层
- 详细规则见 `.agents/skills/aegisops/references/doc-governance.md` 与 SKILL §17

## 6. Asset 多来源身份模型（Phase 1）

Asset 是租户内的规范资源，不再用单一 `source + source_id` 表达来源。来源、身份和关系拆分如下：

```text
Asset 1 ─ n AssetSourceLink
      1 ─ n AssetIdentity
      n ─ n AssetRelation
```

- `AssetSourceLink`：记录 `source_type + source_instance_id + external_id`，用于同一外部对象的幂等更新。
- `AssetIdentity`：记录跨来源身份。`machine_id`、`cloud_instance_id`、`cmdb_ci_id`、`k8s_uid`、`otel_service_instance_id` 为强身份，可确定性合并。
- hostname、FQDN、IP 和展示名仅为弱身份，不允许据此自动合并。
- `AssetRelation`：保存 `depends_on`、`runs_on`、`contains` 等拓扑关系，并记录来源与置信度。
- CSV 是 `ingestion_channel`，不是长期 DataSource；Zabbix、Kubernetes、OpenTelemetry 才是长期连接。
- 所有查找、更新、归档和关系操作均按 `tenant_id` 隔离；归档使用版本号进行乐观锁控制。

确定性解析顺序：

```text
SourceLink 精确命中
  → 强身份精确命中
  → 创建新 Asset
```

CSV 预检使用内容 SHA-256 保证同一租户、同一来源实例下的幂等性；确认阶段统一调用 Asset Upsert，不建立第二套写入逻辑。

## 7. 多来源证据与服务目录（Phase 1）

`V0040__init_service_catalog_and_ingestion.sql` 增加以下租户化模型：

| 表                 | 用途                                         | 幂等或查询键                          |
| ------------------ | -------------------------------------------- | ------------------------------------- |
| `trace_event`      | OTel Trace 证据索引                          | tenant + datasource + source_event_id |
| `telemetry_metric` | 指标证据索引；启用时同步转发 VictoriaMetrics | tenant + datasource + source_event_id |
| `rum_event`        | 页面错误、Web Vitals、Trace 关联             | tenant + datasource + source_event_id |
| `service_catalog`  | service asset 的 owner/repository/runbook    | tenant + asset_id                     |
| `change_event`     | Git/CI/Deployment/Helm 变更                  | tenant + source + source_event_id     |

Kubernetes Cluster、Node、Namespace、Workload、Pod、Service 与 Ingress 都映射为 Asset，外部 UID 使用 `k8s_uid` 强身份。Cluster 是每个 Kubernetes DataSource 的合成根资源；API owner reference 转为 `contains` 关系，无可解析 owner 的顶层资源直接挂到 Cluster。

RUM Page URL 是弱身份，只在同一 RUM DataSource 的 SourceLink 中保证幂等，不跨来源自动合并。原始用户标识不存储，`user_hash` 为不可逆 SHA-256。

## 8. 审计请求上下文（Phase Z9）

`V0049__init_audit_request_metadata.sql` 为 `audit_log` 增加 `request_id`、`ip` 和
`user_agent`。领取 Zabbix Webhook token 时写入这三项请求上下文，以便按一次具体请求追溯
敏感凭据操作；token 与签名 secret 不进入审计记录。
