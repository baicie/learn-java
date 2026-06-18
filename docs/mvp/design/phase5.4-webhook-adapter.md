# Phase5.4 Webhook Adapter

## 目标

Phase5.4 为 aiops-runner 增加 Webhook Adapter。允许在 Runbook 中通过 `actionType=webhook` 的 step 调用外部 HTTP 服务。

## 原则

- server 不发 webhook
- runner 发 webhook
- 默认 dry-run
- live webhook 默认关闭
- live webhook 必须通过 allowlist 和安全校验
- 请求与响应摘要写 `execution_artifact`
- 默认脱敏 `authorization` / `x-api-key` / `x-token` / `cookie`

## 安全策略

webhook live 执行必须同时满足:

1. `execution_run.mode = live`
2. `aiops.execution.live-enabled = true`
3. `webhook_connector.enabled = true`
4. `webhook_execution_policy.enabled = true`
5. `webhook_execution_policy.allow_live = true`
6. method 命中 `allowed_methods`
7. URL scheme 是 `http` / `https`
8. host 命中 `allowed_hosts`
9. host 不是 `localhost`
10. host 不是 `127.0.0.0/8`(loopback)
11. host 不是 `169.254.169.254`(metadata)
12. host 不是 private / link-local IP
13. body 大小 ≤ `max_body_bytes`

阻断目标:

- localhost
- 127.0.0.0/8
- private IP
- link-local IP
- metadata IP 169.254.169.254
- 非 http(s) scheme
- 非 allowlist host
- 不允许的 method
- 超过 `max_body_bytes` 的 body

## action_payload

```json
{
  "connectorId": "whc_xxx",
  "method": "POST",
  "path": "/internal/restart",
  "headers": {
    "X-Source": "aegisops"
  },
  "body": {
    "serviceName": "order-service"
  }
}
```

`body` 在 Phase5.4 直接作为 JSON 字符串发送,不做复杂模板渲染;模板渲染放 Phase5.5/5.6 做。

## dry-run

dry-run 不发送 HTTP 请求,只校验 scheme / method / host,并写 `webhook-dry-run-request.json` artifact。

## live

live 需要:

- `execution_run.mode = live`
- `aiops.execution.live-enabled = true`
- `connector.enabled = true`
- `policy.enabled = true`
- `policy.allow_live = true`

## 敏感 header

默认脱敏:

- `authorization`
- `x-api-key`
- `x-token`
- `cookie`

敏感 header 会在 artifact 中被替换为 `***`。

## API

- `GET /api/webhook-connectors`
- `POST /api/webhook-connectors`
- `GET /api/webhook-connectors/{connectorId}`
- `POST /api/webhook-connectors/{connectorId}/enable`
- `POST /api/webhook-connectors/{connectorId}/disable`

## 后续

Phase5.5 做 Ansible Adapter。
