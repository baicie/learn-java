---
title: 数据源 API
type: api
status: accepted
phase: phase-1
owner: operations
created: 2026-07-18
updated: 2026-07-18
related:
  - modules/aiops-datasource/src/main/java/io/aegisops/datasource/DataSourceController.java
  - web/portal/src/pages/datasources/index.tsx
---

# 数据源 API

## 更新数据源

`PUT /api/datasources/{id}` 需要 `datasource:write` 权限，并使用当前租户与路径 ID 共同定位数据源。数据源类型不可修改。

请求体包含 `name`，并按数据源类型提供 `zabbix`、`kubernetes` 或 `passive` 配置。Zabbix 的 `username`、`password`、`apiToken` 与 Kubernetes 的 `apiToken` 省略或留空时沿用已保存值；提供非空值时替换对应值。敏感字段不会出现在响应中。

更新成功后连接状态重置为 `inactive`，需要重新执行连接测试。响应为更新后的数据源公开字段。
