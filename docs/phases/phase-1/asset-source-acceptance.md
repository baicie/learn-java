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
- CSV 冲突逐行创建、关联或跳过，问题行筛选与 CSV 导出，租户级资源统计。
- Zabbix 从标准 tags/inventory 提取 `machine_id`，与 CSV 强身份确定性合并。
- `asset:read`、`asset:write`、`asset:import` 权限与审计记录。

## 验证结果

| 检查项                                 | 结果                         |
| -------------------------------------- | ---------------------------- |
| Asset / Datasource Maven reactor tests | 通过                         |
| 真实 PostgreSQL Testcontainers IT      | 1/1 通过                     |
| Portal 资产导入 Playwright E2E         | 1/1 通过                     |
| Portal TypeScript typecheck            | 通过                         |
| Portal ESLint                          | 通过，0 warning              |
| Portal 全量 browser tests              | 292/292 通过                 |
| Portal production build                | 通过                         |
| Work Record 覆盖率门禁                 | 通过，补充 17 个字段校验测试 |
| Agent Ruff / pytest                    | 通过，140/140                |
| `scripts/ci/verify-local.sh`           | 通过                         |

## 真实场景证明

- CSV 先导入带 `machine_id` 的 Host，随后 Zabbix 同步相同 `machine_id`：最终只有一个 Asset、两个 SourceLink。
- 两个租户使用相同身份：各自得到独立 Asset，查询不可跨租户。
- 两台机器只有 IP 相同、machine ID 不同：不会自动合并。
- 软归档后的资源不再出现在默认列表和详情查询中。
- Portal E2E 覆盖管理员登录、模板下载、CSV 上传预检、确认、列表搜索、详情以及 machine ID/CSV 来源展示。

PostgreSQL IT 使用 `postgres:16` Testcontainers 执行 Flyway 后运行；本机 Docker socket 为 `/Users/liuzhiwei/.docker/run/docker.sock`，因此本地复现时需设置 `DOCKER_HOST` 与 `TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE=/var/run/docker.sock`。本次分支最终 `scripts/ci/verify-local.sh` 已完整通过。
