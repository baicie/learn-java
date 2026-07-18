---
title: 多来源可观测与变更接入 API
type: api
status: accepted
phase: phase-1
owner: ai
created: 2026-07-17
updated: 2026-07-17
related:
  - modules/aiops-integration/src/main/java/io/aegisops/integration/api/OperationsIngestController.java
  - apps/aiops-server/src/main/resources/db/migration/V0040__init_service_catalog_and_ingestion.sql
---

# 多来源可观测与变更接入 API

三个接入接口均位于受保护的 `/api/**` 空间，使用当前 JWT 中的 `tenant_id`，并校验路径中的 DataSource 属于同一租户且类型匹配。客户端不能提交租户 ID。全局请求体上限由 `aiops.web.request.max-body-bytes` 控制，默认 2 MiB。

| 方法 | 路径                                                     | DataSource 类型                          | 用途                                       |
| ---- | -------------------------------------------------------- | ---------------------------------------- | ------------------------------------------ |
| POST | `/api/integrations/opentelemetry/{datasourceId}/signals` | `opentelemetry`                          | 接收 OTLP JSON trace、log、metric envelope |
| POST | `/api/integrations/rum/{datasourceId}/events`            | `rum`                                    | 接收页面错误和 Web Vitals                  |
| POST | `/api/integrations/changes/{datasourceId}/events`        | `github`、`gitlab`、`jenkins`、`webhook` | 接收部署、Helm、CI/CD 等变更               |

## OpenTelemetry

接口支持 OTLP JSON 的 `resourceSpans`、`resourceLogs`、`resourceMetrics`，也兼容单条规范化 `resource + signal` 请求。`resource.attributes` 必须包含 `service.name`；`service.instance.id` 存在时作为强身份写入统一 Asset。返回值包含本批接收数量及逐条幂等结果。

Trace、log 与指标索引保存 `source_event_id`。同一租户、DataSource 和 source ID 重放不会新增记录。指标在 `aiops.evidence.victoria.enabled=true` 时转发到 VictoriaMetrics `/api/v1/import/prometheus`；PostgreSQL `telemetry_metric` 只保留 Incident 证据窗口所需的租户化索引。

## RUM

错误事件示例：

```json
{
  "eventId": "rum-error-1",
  "eventType": "error",
  "page": "/checkout",
  "sessionId": "session-1",
  "userId": "alice@example.com",
  "errorMessage": "payment failed",
  "traceId": "trace-checkout",
  "occurredAt": "2026-07-17T10:00:00Z"
}
```

`error` 必须包含 `errorMessage`；`web_vital` 必须包含 `vitalName` 和数值型 `vitalValue`。Page 通过 URL 弱身份写入 Asset。原始 `userId` 不落库，只保存 SHA-256；证据响应仅返回受影响 session/page 去重计数和 Web Vitals 的 min/max/avg/sample 聚合。

## 变更与 Service Catalog

变更请求必须包含 `sourceEventId`、`serviceName`、`changeType`、`title` 和 `occurredAt`。可选的 `ownerTeam`、`repositoryUrl`、`runbookId` 更新轻量 Service Catalog，`dependencyAssetIds` 写入 `depends_on` 关系。关系两端必须属于当前租户。

幂等键为 `tenant_id + source + source_event_id`。证据查询按 service/asset/time window 返回变更来源和 source ID，便于 RCA 回查。

## 安全边界

- 接口只摄取与归一，不创建自动化执行任务。
- AI/RCA 只读取证据和输出建议，Runner 仍必须经过既有审批状态机。
- Kubernetes Token 只进入 Authorization header；DataSource 查询只返回 endpoint，不回显配置 JSON 或 token。
- RUM 不返回原始用户标识；所有事件表、目录表和查询均带 `tenant_id`。
