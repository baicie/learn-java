---
title: Zabbix Webhook Integration
type: integration
status: accepted
phase: global
owner: ai
created: 2026-06-30
updated: 2026-07-29
related:
  - docs/scenarios/phase-z9-zabbix-mvp-acceptance.md
---

# Zabbix Webhook Integration

## 接入模式定位

AegisOps 的默认真实 Zabbix 链路由 aiops-worker 周期轮询 `active` 数据源：

```text
Zabbix problem.get -> Worker -> AlertEvent -> Incident
```

Webhook 是可选的低延迟入口，适用于已经在 Zabbix 中维护 Media Type 与 Action 的环境。`scripts/demo/setup-zabbix-demo.py` 不会自动修改通知策略、用户媒介或 Action；本地端到端验收可直接使用 polling 路径。

## Endpoint

```txt
POST /api/integrations/zabbix/events?datasourceId=<datasourceId>
```

Header:

```txt
X-AegisOps-Webhook-Token: <webhook-token>
Content-Type: application/json
```

Webhook token 只接受 `X-AegisOps-Webhook-Token` Header，不支持 query 参数。token 以 `zwh_` 开头，并绑定 URL 中的 `datasourceId`；一个数据源的 token 不能用于另一个数据源。

Header 缺失、token 无效或签名 secret 轮换导致旧 token 失效时，接口统一返回 HTTP 401 和
`UNAUTHORIZED`。Token 领取操作写入请求 ID、客户端 IP 与 User-Agent 审计字段，但不记录
token 或签名 secret。

## Server Configuration

为 aiops-server 配置 token 签名 secret：

```bash
export AIOPS_INTEGRATIONS_ZABBIX_WEBHOOK_TOKEN="$(openssl rand -base64 32)"
```

该环境变量是服务端 HMAC 签名 secret，不是直接填写到 Zabbix Media Type 的 Webhook token。Portal 会在下载模板时通过受保护的 `GET /api/datasources/{datasourceId}/zabbix-webhook-token` 接口领取 datasource-scoped token，并把它写入 YAML；接口要求 `datasource:write` 权限、active 租户和 active Zabbix 数据源，响应使用 `Cache-Control: no-store`。修改签名 secret 会使此前下载的所有模板 token 失效，需要重新下载并导入。

Compose 部署必须显式提供该环境变量；Helm 使用
`security.zabbixWebhookSigningSecret`，并只把它注入 aiops-server。未显式配置时，普通本地进程
会生成仅在当前进程有效的随机 secret，适合临时联调；跨重启使用 Webhook 或运行多个
aiops-server 实例时必须配置同一个稳定随机值，否则已有模板会返回 401。

## Payload

Minimum PROBLEM payload:

```json
{
  "eventId": "{EVENT.ID}",
  "problemId": "{EVENT.ID}",
  "triggerId": "{TRIGGER.ID}",
  "status": "PROBLEM",
  "eventValue": "{EVENT.VALUE}",
  "severity": "{EVENT.SEVERITY}",
  "title": "{EVENT.NAME}",
  "message": "{EVENT.OPDATA}",
  "hostId": "{HOST.ID}",
  "hostName": "{HOST.NAME}",
  "app": "mall",
  "env": "demo",
  "service": "order-service",
  "tags": {
    "app": "mall",
    "env": "demo",
    "service": "order-service"
  }
}
```

Minimum RESOLVED payload:

```json
{
  "eventId": "{EVENT.ID}",
  "problemId": "{EVENT.ID}",
  "recoveryEventId": "{EVENT.RECOVERY.ID}",
  "triggerId": "{TRIGGER.ID}",
  "status": "RESOLVED",
  "eventValue": "0",
  "severity": "{EVENT.SEVERITY}",
  "title": "{EVENT.NAME}",
  "message": "Resolved: {EVENT.NAME}",
  "hostId": "{HOST.ID}",
  "hostName": "{HOST.NAME}",
  "app": "mall",
  "env": "demo",
  "service": "order-service",
  "tags": {
    "app": "mall",
    "env": "demo",
    "service": "order-service"
  }
}
```

`startsAt` / `endsAt` 只接受 RFC 3339 时间。不要直接拼接 `{EVENT.DATE}` 或 `{EVENT.RECOVERY.DATE}`，因为 Zabbix 输出的日期是 `YYYY.MM.DD`，无法直接反序列化为 `OffsetDateTime`。省略时，AegisOps 使用 Webhook 到达时间；下面的 Media Type 脚本显式发送到达时钟，并在恢复通知中补充 `endsAt`。

## Zabbix Media Type

Create a new Media type:

```txt
Name:
  AegisOps Webhook

Type:
  Webhook
```

Parameters:

```txt
url:
  http://aiops-server:8080/api/integrations/zabbix/events?datasourceId=<datasourceId>

token:
  zwh_<datasource-scoped-signature>
```

推荐从 Portal 的数据源页面直接下载模板。模板已包含当前数据源的 Webhook URL 和 datasource-scoped token，无需手工替换服务端 secret。

Media Type 的 Webhook JavaScript 由 `zabbix-server` 执行，因此 URL 必须能从 `zabbix-server` 容器访问。完整 Compose 部署可使用 `http://aiops-server:8080`；aiops-server 运行在宿主机时，Docker Desktop 通常使用 `http://host.docker.internal:8080`。Linux 环境应配置明确可达的宿主机地址或容器网络别名。

这与 trapper item 的 Allowed hosts 是两个不同边界：`history.push` 的 Allowed hosts 必须同时覆盖 API 调用方经过 NAT 后的来源地址和 Zabbix Web 前端地址。本地 Compose 中对应 Docker 网络 gateway 与 `zabbix-web` 容器 IP，Demo 脚本会自动探测并写入两者；其他部署应填写实际客户端出口地址和 Web 前端 IP、受限网段或 DNS 名称，不得为了省事放开为 `0.0.0.0/0`。

Webhook script:

```javascript
var params = JSON.parse(value)

var request = new HttpRequest()
request.addHeader('Content-Type: application/json')
request.addHeader('X-AegisOps-Webhook-Token: ' + params.token)

var payload = {
  eventId: params.event_id,
  problemId: params.problem_id,
  recoveryEventId: params.recovery_event_id,
  triggerId: params.trigger_id,
  status: params.status,
  eventValue: params.event_value,
  severity: params.severity,
  title: params.title,
  message: params.message,
  hostId: params.host_id,
  hostName: params.host_name,
  app: params.app,
  env: params.env,
  service: params.service,
  endpoint: params.endpoint,
  clock: Math.floor(Date.now() / 1000),
  endsAt: params.status === 'RESOLVED' ? new Date().toISOString() : null,
  tags: {
    app: params.app,
    env: params.env,
    service: params.service,
    endpoint: params.endpoint,
  },
}

var response = request.post(params.url, JSON.stringify(payload))

if (request.getStatus() < 200 || request.getStatus() >= 300) {
  throw 'AegisOps webhook failed. HTTP status=' + request.getStatus() + ', response=' + response
}

return response
```

Message parameters:

```txt
event_id:
  {EVENT.ID}

problem_id:
  {EVENT.ID}

recovery_event_id:
  {EVENT.RECOVERY.ID}

trigger_id:
  {TRIGGER.ID}

status:
  {EVENT.STATUS}

event_value:
  {EVENT.VALUE}

severity:
  {EVENT.SEVERITY}

title:
  {EVENT.NAME}

message:
  {EVENT.OPDATA}

host_id:
  {HOST.ID}

host_name:
  {HOST.NAME}

app:
  mall

env:
  demo

service:
  order-service

endpoint:
  /api/order/create
```

## Action

Create Action:

```txt
Conditions:
  Host group = AegisOps Demo

Operations:
  Send message to users via AegisOps Webhook

Recovery operations:
  Send recovery message via AegisOps Webhook
```

## Verify

下面的请求只验证 AegisOps Webhook HTTP 契约，不证明 Zabbix Media Type 或 Action 已正确触发。完整验证需要从 Zabbix 产生 PROBLEM 并检查 Action delivery history。

```bash
curl -X POST "http://localhost:8080/api/integrations/zabbix/events?datasourceId=ds_xxx" \
  -H "Content-Type: application/json" \
  -H "X-AegisOps-Webhook-Token: zwh_<datasource-scoped-signature>" \
  -d '{
    "eventId": "20001",
    "triggerId": "30001",
    "status": "PROBLEM",
    "severity": "High",
    "title": "AegisOps Demo CPU High",
    "hostId": "10084",
    "hostName": "aiops-demo-host",
    "app": "mall",
    "env": "demo",
    "service": "order-service",
    "startsAt": "2026-06-21T05:10:00Z"
  }'
```

Expected result:

```json
{
  "data": {
    "datasourceId": "ds_xxx",
    "status": "open",
    "created": true
  }
}
```
