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
