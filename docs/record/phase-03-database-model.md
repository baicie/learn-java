---
title: Phase 3 数据库模型重做
type: phase
status: draft
phase: work-record
owner: platform-team
created: 2026-07-09
updated: 2026-07-09
related:
  - docs/record/enterprise-roadmap.md
  - docs/record/schema-contract.md
  - docs/record/api-contract.md
  - docs/record/acceptance-checklist.md
---

# Phase 3 数据库模型重做

## 1. 目标

把工作记录数据模型从 `public.wr_*` 升级为 `work_record.wr_*` 企业级结构。

## 2. 关键决策

正式迁移到：

```text
work_record schema
```

旧表暂时保留：

```text
public.wr_template
public.wr_template_field
public.wr_record
```

原因：

1. 保留回退能力。
2. 避免一次性破坏旧代码。
3. Phase 4 再逐步切 Service / Repository 到新 schema。
4. 后续稳定后再做旧表清理。

## 3. 新企业级表

```text
platform_dict_type
platform_dict_item
platform_calendar
platform_calendar_day

work_record.wr_template
work_record.wr_template_version
work_record.wr_template_field
work_record.wr_record
work_record.wr_record_snapshot
work_record.wr_record_audit_event
```

## 4. 模板版本规则

1. 模板元数据在 `wr_template`。
2. 已发布版本在 `wr_template_version`。
3. 当前版本由 `wr_template.current_version_id` 指向。
4. 历史记录必须绑定 `template_version_id`。
5. 记录详情必须按 `template_version_id` 渲染。

## 5. 字段规则

1. 字段索引在 `wr_template_field`。
2. 字段绑定 `template_version_id`。
3. `field_code` 必须匹配正则：

```text
^[a-zA-Z][a-zA-Z0-9_]{0,63}$
```

4. `field_code` 创建后不可修改。
5. 字段删除只能 `enabled=false`。
6. 禁止物理删除字段。

## 6. 记录规则

1. 记录主表是 `work_record.wr_record`。
2. 记录必须绑定：

```text
template_id
template_version_id
```

3. 记录删除必须走：

```text
deleted_at
```

4. 禁止物理删除记录。

## 7. 快照规则

每条迁移过来的旧记录生成初始快照：

```text
snapshot_no = 1
reason = migration
actor_id = system
```

后续每次编辑记录都应该新增快照。

## 8. 审计规则

模块级审计事件进入：

```text
work_record.wr_record_audit_event
```

平台全局审计仍然保留。模块审计用于工作记录自身的详情页时间线和历史追踪。

## 9. 索引策略

必须包含：

1. 模板租户状态索引。
2. 模板版本索引。
3. 字段版本排序索引。
4. filterable 字段索引。
5. exportable 字段索引。
6. 记录时间索引。
7. 记录 owner / creator 索引。
8. 记录 template_version 索引。
9. `custom_data_json` GIN 索引。
10. `builtin_data_json` GIN 索引。
11. 审计资源索引。

## 10. 验收命令

```bash
mvn -pl apps/aiops-server -am test
pnpm exec tsx scripts/ci/check-record-phase3-model.ts
```

## 11. Phase 3 Hardening

除 `V0019__work_record_enterprise_schema.sql` 外，Phase 3 还包含：

```text
V0020__harden_work_record_enterprise_constraints.sql
```

该 migration 负责：

1. 增加 `tenant_id + id` 复合唯一约束。
2. 增加 `tenant_id + template_id + template_version_id` 复合外键。
3. 保证 `current_version_id` 必须属于当前模板。
4. 保证记录、字段、快照绑定的模板版本属于同一租户和同一模板。
5. 禁止模板、模板版本、快照、审计事件物理删除。
6. 回填 `wr_template_version.field_index_json`。
7. 清洗旧字段的非法 `option_source` / `dict_code` 数据。

## 12. Tenant 一致性约束

企业级模型必须在数据库层保证 tenant 隔离。外键关系通过复合外键约束：

```text
wr_template.current_version_id -> wr_template_version(tenant_id, template_id, id)
wr_template_field.template_id -> wr_template(tenant_id, id)
wr_template_field.template_version_id -> wr_template_version(tenant_id, template_id, id)
wr_record.template_id -> wr_template(tenant_id, id)
wr_record.template_version_id -> wr_template_version(tenant_id, template_id, id)
wr_record_snapshot.record_id -> wr_record(tenant_id, id)
wr_record_snapshot.template_version_id -> wr_template_version(tenant_id, template_id, id)
wr_record_audit_event.record_id -> wr_record(tenant_id, id)
wr_record_audit_event.template_id -> wr_template(tenant_id, id)
```

这确保了"记录 tenant=A，但 template_version 属于 tenant=B"这种脏数据在数据库层被拒绝。

## 13. 禁止物理删除覆盖范围

以下表禁止物理删除，只能通过软删除字段（`deleted_at`、`enabled=false`）：

```text
work_record.wr_template         (deleted_at)
work_record.wr_template_version
work_record.wr_template_field   (enabled=false)
work_record.wr_record           (deleted_at)
work_record.wr_record_snapshot
work_record.wr_record_audit_event
public.platform_dict_item       (enabled=false)
```
