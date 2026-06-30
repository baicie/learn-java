---
title: Phase Z3: Zabbix Alert Aggregation Strategy
type: design
status: accepted
phase: global
owner: ai
created: 2026-06-30
updated: 2026-06-30
related: []
---
# Phase Z3: Zabbix Alert Aggregation Strategy

## Goal

Group related Zabbix alerts into one incident.

Before Phase Z3:

```txt
CPU High            -> Incident A
API Slow            -> Incident B
Health Check Failed -> Incident C
Error Log Increased -> Incident D
```

After Phase Z3:

```txt
CPU High
API Slow
Health Check Failed
Error Log Increased
  -> Incident: order-service 主机与服务异常
```

## Concepts

### fingerprint

Per-alert identity.

Example:

```txt
zabbix:ds_1:trigger_cpu_high
```

### aggregation_key

Incident grouping key.

Example:

```txt
zabbix:ds_1:10084:order-service:demo:202606210510
```

Format:

```txt
zabbix:{datasourceId}:{hostId}:{service}:{env}:{windowBucket}
```

## Time Window

Default window is 10 minutes.

Example:

```txt
2026-06-21T05:10:00Z -> 202606210510
2026-06-21T05:19:59Z -> 202606210510
2026-06-21T05:20:00Z -> 202606210520
```

## Resolution

When all alerts linked to an active incident become resolved, the incident is auto-resolved by the next aggregation run.

## Scope

Phase Z3 does not collect Zabbix metric evidence.

That belongs to Phase Z4.
