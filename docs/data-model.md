---
title: Core Domain Models
type: database
status: accepted
phase: global
owner: ai
created: 2026-06-30
updated: 2026-06-30
related:
  - .agents/skills/aegisops/SKILL.md
  - docs/architecture.md
---

# Core Domain Models

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
- Severity（AlertEvent）：info | warning | average | high | disaster
- Incident Status：open | investigating | mitigating | resolved | closed | ignored
- Automation Status：pending → waiting_approval → approved → running → success/failed/cancelled/timeout
```

## 5. 数据库迁移规则（SKILL §17 + AGENTS §3.2）

- 命名：`V\d{4}__init_[a-z_]+\.sql`，不允许 v1 / legacy / old 前缀
- 唯一事实源：每张表一旦创建即为权威，不允许双写兼容层
- 详细规则见 `.agents/skills/aegisops/references/doc-governance.md` 与 SKILL §17
