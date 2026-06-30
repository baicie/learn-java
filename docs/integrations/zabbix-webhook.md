---
title: Zabbix Webhook Integration
type: design
status: accepted
phase: global
owner: ai
created: 2026-06-30
updated: 2026-06-30
related: []
---
# Zabbix Webhook Integration

## Endpoint

```txt
POST /api/integrations/zabbix/events?datasourceId=<datasourceId>
```

Header:

```txt
X-AegisOps-Webhook-Token: <webhook-token>
Content-Type: application/json
```

## Server Configuration

Set environment variable for aiops-server:

```bash
AIOPS_INTEGRATIONS_ZABBIX_WEBHOOK_TOKEN=dev-zabbix-webhook-token
```

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
  "startsAt": "{EVENT.DATE}T{EVENT.TIME}+09:00",
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
  "endsAt": "{EVENT.RECOVERY.DATE}T{EVENT.RECOVERY.TIME}+09:00",
  "tags": {
    "app": "mall",
    "env": "demo",
    "service": "order-service"
  }
}
```

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
  dev-zabbix-webhook-token
```

Webhook script:

```javascript
var params = JSON.parse(value);

var request = new HttpRequest();
request.addHeader("Content-Type: application/json");
request.addHeader("X-AegisOps-Webhook-Token: " + params.token);

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
  startsAt: params.starts_at,
  endsAt: params.ends_at,
  tags: {
    app: params.app,
    env: params.env,
    service: params.service,
    endpoint: params.endpoint,
  },
};

var response = request.post(params.url, JSON.stringify(payload));

if (request.getStatus() < 200 || request.getStatus() >= 300) {
  throw (
    "AegisOps webhook failed. HTTP status=" +
    request.getStatus() +
    ", response=" +
    response
  );
}

return response;
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

starts_at:
  {EVENT.DATE}T{EVENT.TIME}+09:00

ends_at:
  {EVENT.RECOVERY.DATE}T{EVENT.RECOVERY.TIME}+09:00
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

```bash
curl -X POST "http://localhost:8080/api/integrations/zabbix/events?datasourceId=ds_xxx" \
  -H "Content-Type: application/json" \
  -H "X-AegisOps-Webhook-Token: dev-zabbix-webhook-token" \
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
