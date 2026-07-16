---
title: 多来源资源中心验收记录
type: phase
status: accepted
phase: phase-1
owner: ai
created: 2026-07-16
updated: 2026-07-16
related:
  - docs/data-model.md
  - docs/designs/phase-1/2026-07-16-resource-source-and-entity-resolution-implementation-plan.md
---

# 多来源资源中心验收记录

## 已交付范围

- 规范 Asset、来源链接、强弱身份、资源关系和 CSV 导入任务模型。
- 确定性实体解析与统一 Upsert；来源精确命中优先于强身份命中，弱身份不自动合并。
- 资源分页、详情、新增、编辑、归档、来源、身份、关系与 CSV 预检/确认 API。
- Zabbix 同步改为 Outbox + Worker 异步执行，并复用统一 Asset Upsert 与 Alert Ingest。
- Portal 数据源管理、资源中心、资源详情及 CSV 三步导入交互。
- `asset:read`、`asset:write`、`asset:import` 权限与审计记录。

## 验证结果

| 检查项                                 | 结果            |
| -------------------------------------- | --------------- |
| Asset / Datasource Maven reactor tests | 通过            |
| Portal TypeScript typecheck            | 通过            |
| Portal ESLint                          | 通过，0 warning |
| Portal 全量 browser tests              | 290/290 通过    |
| Portal production build                | 通过            |
| Agent Ruff / pytest                    | 通过，140/140   |
| Git diff whitespace check              | 通过            |

## 环境限制

当前执行环境没有可用 Docker daemon，因此依赖 Testcontainers 的真实 PostgreSQL 场景无法在本机执行，不能将其记录为通过或 skipped-success。代码级单元测试、Controller 契约、迁移契约、Portal 全量测试和生产构建已执行。

`verify-local.sh` 的后端阶段已验证本次涉及的 Asset、Datasource 与依赖模块，但最终被既有 `aiops-work-record` 分支覆盖率门禁阻断：实际 0.44，阈值 0.45；该模块不在本次变更范围。合并前仍需在具备 Docker 的 CI 环境补跑真实 PostgreSQL 与 Portal E2E，并单独恢复 Work Record 覆盖率基线。
