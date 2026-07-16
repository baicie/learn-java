---
title: 资源中心 API
type: api
status: accepted
phase: phase-1
owner: ai
created: 2026-07-16
updated: 2026-07-16
related:
  - modules/aiops-asset/src/main/java/io/aegisops/asset/api/AssetController.java
  - apps/aiops-server/src/main/resources/db/migration/V0037__init_asset_source_foundation.sql
---

# 资源中心 API

资源中心以当前认证上下文中的 `tenant_id` 为唯一租户边界。客户端不能提交或覆盖租户字段；跨租户资源按不存在处理。写操作记录操作者、前后快照和资源 ID。

## 接口

| 方法   | 路径                                         | 权限           | 用途                                           |
| ------ | -------------------------------------------- | -------------- | ---------------------------------------------- |
| GET    | `/api/assets`                                | `asset:read`   | 分页查询资源，支持类型、来源、状态和关键词筛选 |
| POST   | `/api/assets`                                | `asset:write`  | 手工创建资源和身份                             |
| GET    | `/api/assets/{id}`                           | `asset:read`   | 查询资源详情                                   |
| PUT    | `/api/assets/{id}`                           | `asset:write`  | 按 `version` 乐观锁编辑规范字段                |
| POST   | `/api/assets/{id}/archive?version={version}` | `asset:write`  | 软归档资源                                     |
| GET    | `/api/assets/{id}/sources`                   | `asset:read`   | 查询该资源的来源映射                           |
| GET    | `/api/assets/{id}/identities`                | `asset:read`   | 查询强、弱身份                                 |
| GET    | `/api/assets/{id}/relations`                 | `asset:read`   | 查询双向资源关系                               |
| POST   | `/api/assets/{id}/relations`                 | `asset:write`  | 新增人工关系                                   |
| DELETE | `/api/assets/{id}/relations/{relationId}`    | `asset:write`  | 删除人工关系                                   |
| GET    | `/api/assets/imports/template`               | `asset:import` | 下载 UTF-8 CSV 模板                            |
| POST   | `/api/assets/imports/preview`                | `asset:import` | 上传 CSV 并生成预检任务                        |
| GET    | `/api/assets/imports/{jobId}`                | `asset:import` | 查询导入任务统计和状态                         |
| GET    | `/api/assets/imports/{jobId}/rows`           | `asset:import` | 分页查询预检行和错误码                         |
| POST   | `/api/assets/imports/{jobId}/confirm`        | `asset:import` | 确认并导入全部有效行                           |
| POST   | `/api/assets/imports/{jobId}/cancel`         | `asset:import` | 取消尚未确认的任务                             |

列表参数 `page` 从 1 开始，`pageSize` 默认 20 且最大 100。`sourceType` 通过 `asset_source_link` 过滤，因此一个资源可以同时被 Zabbix、CSV 与人工来源命中。

## 创建请求示例

```json
{
  "assetType": "host",
  "name": "db-prod-01",
  "displayName": "生产数据库一号",
  "environment": "production",
  "criticality": "tier-1",
  "ip": "10.0.0.8",
  "tags": { "role": "database" },
  "identities": [
    {
      "identityType": "machine_id",
      "scopeKey": "global",
      "identityValue": "machine-001",
      "verified": true
    }
  ]
}
```

手工创建也走统一 upsert 服务：系统生成 `manual` SourceLink，不允许绕过来源、身份和审计模型直接写 `asset` 表。

## CSV 导入

预检接口使用 `multipart/form-data`，字段为 `file` 和 `sourceInstanceId`。CSV 必须使用 UTF-8，最大 5 MiB、5000 个数据行；必填列为 `external_id`、`asset_type`、`name`。系统拒绝未知列、重复表头、重复 `external_id` 和非法资源类型。

预检只写 `asset_import_job` 与 `asset_import_row`，不会写入规范资源。同一租户、`sourceInstanceId` 和文件 SHA-256 重复上传时返回已有任务。确认时只处理 `valid` 行，并逐行调用统一 Asset upsert；存在 `conflict` 行时返回 HTTP 409，不静默覆盖。公式样式文本只作为普通字符串存储，不执行。

模板表头：

```csv
external_id,asset_type,name,display_name,environment,site,owner_team,criticality,ip,machine_id,cloud_instance_id,k8s_uid,tags
```

## Zabbix 资源同步

`POST /api/datasources/{id}/sync` 只创建状态为 `pending` 的同步运行和 `zabbix-sync` outbox 消息，并立即返回 HTTP 202：

```json
{
  "success": true,
  "data": { "runId": "sync_xxx", "status": "pending" }
}
```

Worker 校验 outbox 的租户后执行采集。Zabbix Host 使用 `sourceType=zabbix`、`sourceInstanceId={datasourceId}`、`externalId={hostId}` 调用统一 Asset upsert；因此同一实例重复同步只更新 SourceLink，不同实例的相同 hostid 不冲突。Problem 通过标准 Alert ingest 服务写入。同步开始前仍为 active、但本轮未再次出现的 SourceLink 标记为 `missing`，不会自动归档规范资源。

幂等键格式为 `zabbix-sync:{tenantId}:{datasourceId}:{runId}`。执行结果通过 `GET /api/datasources/{id}/sync-runs` 查询；采集失败时 run 和 datasource 都更新为 `failed/error`，由 outbox 重试策略决定是否重试。

## 一致性规则

- SourceLink 精确匹配或强身份匹配时复用规范资源。
- IP 等弱身份只产生冲突信号，不自动合并。
- 编辑与归档必须携带当前 `version`；版本过期返回 HTTP 409。
- 归档为软删除，默认列表与详情不再返回已归档资源。
- 关系两端必须属于当前租户，且禁止资源关联自身。

## 审计动作

`asset.create`、`asset.update`、`asset.archive`、`asset.relation.create`、`asset.relation.delete`。
