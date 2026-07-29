---
title: Phase Z4: Zabbix Evidence Collector
type: design
status: accepted
phase: global
owner: ai
created: 2026-06-30
updated: 2026-07-27
related: []
---

# Phase Z4: Zabbix Evidence Collector

## Goal

Phase Z4 upgrades the MVP from alert-only diagnosis to evidence-based
diagnosis.

Before Z4:

```txt
Incident
  -> Alerts
  -> RCA / AI can only see alert titles
```

After Z4:

```txt
Incident
  -> Alerts
  -> Zabbix item/history/event/trigger evidence
  -> diagnosis_evidence
  -> RCA / AI can cite evidence
```

## API

Collect Zabbix evidence:

```bash
curl -X POST "http://localhost:8080/api/incidents/<incidentId>/evidence/zabbix/collect" \
  -H "Content-Type: application/json" \
  -H "X-Tenant-Id: tenant_default" \
  -d '{
    "lookbackMinutes": 30
  }'
```

List evidence:

```bash
curl "http://localhost:8080/api/incidents/<incidentId>/evidence" \
  -H "X-Tenant-Id: tenant_default"
```

## Evidence Types

```txt
metric_cpu_high
metric_memory_high
metric_load_high
metric_api_slow
metric_health_check_failed
metric_error_log_increased
zabbix_event_timeline
zabbix_trigger_expression
```

## Zabbix APIs Used

```txt
item.get
history.get
trend.get
event.get
trigger.get
```

## Demo Mapping

Phase Z9 demo 通过 `history.push` 向真实 Zabbix trapper items 注入以下指标：

```txt
demo.cpu.util
demo.memory.util
demo.load.avg
demo.order.create.time
demo.health.status
demo.error.count
```

Z4 classifies these items and converts abnormal values into
diagnosis_evidence records.

这些 key 与原 HTTP Agent 方案保持一致，因此 Evidence Collector 不依赖指标产生方式；已删除的 demo-order-service 不再是采集前置条件。

## Idempotency

`diagnosis_evidence.evidence_key` is unique per `(tenant_id, incident_id)`,
so re-collecting the same evidence performs `ON CONFLICT DO UPDATE`
without inserting duplicates.

## Scope

Phase Z4 does not perform AI diagnosis.

That belongs to Phase Z6.
