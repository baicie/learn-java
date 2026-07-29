---
title: 数据源 API
type: api
status: accepted
phase: phase-1
owner: operations
created: 2026-07-18
updated: 2026-07-29
related:
  - modules/aiops-datasource/src/main/java/io/aegisops/datasource/DataSourceController.java
  - modules/aiops-integration/src/main/java/io/aegisops/integration/api/ZabbixWebhookTokenController.java
  - web/portal/src/pages/datasources/index.tsx
---

# 数据源 API

## 更新数据源

`PUT /api/datasources/{id}` 需要 `datasource:write` 权限，并使用当前租户与路径 ID 共同定位数据源。数据源类型不可修改。

请求体包含 `name`，并按数据源类型提供 `zabbix`、`kubernetes` 或 `passive` 配置。Zabbix 的 `username`、`password`、`apiToken` 与 Kubernetes 的 `apiToken` 省略或留空时沿用已保存值；提供非空值时替换对应值。敏感字段不会出现在响应中。

更新成功后连接状态重置为 `inactive`，需要重新执行连接测试。响应为更新后的数据源公开字段。

## 手工同步数据源

`POST /api/datasources/{id}/sync` 需要 `datasource:write` 权限，并使用当前租户与路径 ID 共同定位数据源。数据源必须已经通过连接测试且状态为 `active`；`inactive` 或 `error` 数据源返回 `DATASOURCE_SYNC_NOT_READY`，不会创建永久停留在 pending 的同步任务。

请求成功后返回 `202 Accepted` 和新建的手工同步任务。已有同数据源的 pending 或 lease
未过期的 running 任务时，接口返回 `DATASOURCE_SYNC_ALREADY_RUNNING`，不并发创建第二个同步任务；
lease 已过期的 running 任务会先标记为 failed，再创建新任务。

## 获取 Zabbix Webhook Token

`GET /api/datasources/{id}/zabbix-webhook-token` 需要 `datasource:write` 权限。接口使用当前登录租户与路径 ID 共同定位数据源，并要求当前租户、数据源均为 `active` 且数据源类型为 `zabbix`；inactive 租户、inactive 数据源、其他租户的数据源、非 Zabbix 数据源或不存在的 ID 均按数据源不存在处理。

成功响应：

```json
{
  "success": true,
  "data": {
    "token": "zwh_<datasource-scoped-signature>"
  }
}
```

响应包含 `Cache-Control: no-store`。Portal 仅在用户下载 Zabbix Media Type 模板时调用此接口，不把 token 放入长期 Query 缓存。该 token 只用于对应数据源的 Zabbix Webhook 请求，并通过 `X-AegisOps-Webhook-Token` Header 发送。

每次成功领取都会记录 `datasource.zabbix_webhook_token.issue` 审计事件，包含租户、操作者、数据源 ID、请求 ID、客户端 IP 和 User-Agent；审计快照与详情不记录 token。
