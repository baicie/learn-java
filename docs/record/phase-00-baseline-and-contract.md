---
title: 工作记录 Phase 00 基线与契约冻结
type: design
status: review
phase: work-record
owner: ai
created: 2026-07-07
updated: 2026-07-07
related:
  - docs/record/index.md
---

# Phase 00：基线与契约冻结

## 目标

以 `docs/record/index.md` 的 portal-first 设计为唯一依据，冻结第一版边界：

- 后端复用已存在的 `modules/aiops-platform/.../dictionary` 与 `modules/aiops-work-record`。
- 前端只接入 `web/portal`，不再新增 `web/console` 代码。
- 现有 portal 菜单先不删除模板项，只追加“工作记录”和“平台管理”分组。
- 第一版只做字典、模板、Formily/Designable、记录填写、详情、列表、导出。

## 当前代码事实

后端已存在：

```text
modules/aiops-platform/src/main/java/io/aegisops/platform/dictionary/*
modules/aiops-work-record/src/main/java/io/aegisops/workrecord/*
apps/aiops-server/src/main/resources/db/migration/V0012__init_work_record.sql
apps/aiops-server/src/main/resources/db/migration/V0013__migrate_work_record_schema.sql
apps/aiops-server/src/main/resources/db/migration/V0014__init_work_record_default_template.sql
```

前端 `web/portal` 当前还没有：

```text
src/i18n/
src/features/work-records/
src/features/dictionaries/
src/routes/_authenticated/work-records/
src/routes/_authenticated/platform/
```

因此后续 Phase 是“在 portal 中新增能力 + 修正后端缺口”，不是从零重写后端。

## 代码变更清单

本 Phase 只做契约文件，不改业务实现：

```text
docs/record/phase-00-baseline-and-contract.md
docs/record/phase-01-portal-i18n-and-shell.md
docs/record/phase-02-platform-dictionary.md
docs/record/phase-03-work-record-template.md
docs/record/phase-04-formily-designer.md
docs/record/phase-05-record-runtime.md
docs/record/phase-06-record-list-export.md
docs/record/phase-07-role-permission-hardening.md
```

`docs/record/work-record-phases-design-code.md` 标记为 `deprecated`，旧 console 代码方案不再作为实施依据。

## 后续 Phase 顺序

```text
Phase 01: portal i18n + 空壳路由 + 追加菜单
Phase 02: 平台字典 API 与字典管理页
Phase 03: 工作记录模板与字段 API
Phase 04: Formily + Designable 表单设计器
Phase 05: 记录新建 / 编辑 / 详情运行态
Phase 06: 记录列表 / 筛选 / 导出
Phase 07: 角色权限、审计与安全收口
```

## 验收

```bash
bash scripts/ci/docs.sh
git status --short
```

验收标准：

- 每一步都有单独 Phase 文件。
- 新文件 frontmatter 合法。
- 旧 `work-record-phases-design-code.md` 不再被新设计引用为实施依据。
