---
title: Scenario 01 - Zabbix Host & Service Incident Diagnosis
type: design
status: deprecated
phase: z0
owner: platform-team
created: 2026-06-21
updated: 2026-07-27
related:
  - docs/architecture/phase-z0-module-boundaries.md
  - docs/scenarios/phase-z9-zabbix-mvp-acceptance.md
---

# Scenario 01: Zabbix Host & Service Incident Diagnosis

> 本文保留 Phase Z0 历史基线，当前验收流程已由 `phase-z9-zabbix-mvp-acceptance.md` 取代。已删除的 demo-order-service 不再是运行时依赖。

## Goal

This scenario defines the first AegisOps MVP path:

```txt
Zabbix collects host/service signals
  -> AegisOps receives/syncs Zabbix problems
  -> alert_event
  -> incident
  -> RCA / AI diagnosis
  -> report
```

Phase Z0 only freezes the baseline. The full scenario will be completed by later Phase Z1-Z9.

## Current Phase Z0 Baseline

At commit f61134a, the system already supports:

```txt
1. Create Zabbix datasource.
2. Test Zabbix datasource connection.
3. Manually sync Zabbix hosts.
4. Manually sync Zabbix problems.
5. Write Zabbix hosts into asset.
6. Write Zabbix problems into alert_event.
7. Manually aggregate open alerts into incidents.
8. Run RCA / AI diagnosis based on current incident context.
```

## Target Full Scenario

The final target scenario is:

```txt
1. Start local docker-compose environment.
2. Zabbix 为 aiops-demo-host 配置 trapper items 与 triggers。
3. 通过 history.push 注入 CPU high / API slow / health check failed 信号。
4. Zabbix triggers problems.
5. Zabbix sends webhook to AegisOps.
6. AegisOps creates alert_event.
7. AegisOps aggregates related alerts into one incident.
8. AegisOps collects Zabbix evidence.
9. RCA produces deterministic suspected root cause.
10. AI Diagnosis summarizes evidence and recommendations.
11. AegisOps generates Markdown incident report.
```

## Demo Signal Source

当前 Phase Z9 使用 Zabbix trapper item 接收可复现信号：

```txt
scripts/demo/setup-zabbix-demo.py --action setup
scripts/demo/setup-zabbix-demo.py --action incident
scripts/demo/setup-zabbix-demo.py --action recover
```

该脚本驱动真实 Zabbix trigger/problem，但不新增第四个 Java app，也不模拟业务 HTTP 服务。

Fault profiles:

```txt
CPU high
API slow
health check failed
error log increased
```

## Zabbix Signals

Minimum Zabbix signals:

```txt
host.cpu.util
vm.memory.util
system.cpu.load
web.test.rspcode[/health]
web.test.time[/api/order/create]
log error count
```

## Zabbix Triggers

Minimum triggers:

```txt
Host CPU High
Order API Slow
Order Health Check Failed
Order Error Log Increased
```

## Current Manual Flow

Phase Z0 supports the current manual flow:

```txt
POST /api/datasources/{id}/sync
  -> host.get
  -> asset upsert
  -> problem.get
  -> alert_event upsert

POST /api/incidents/aggregate
  -> open alert_event
  -> incident
```

## Known Gaps After Phase Z0

```txt
1. No Zabbix webhook ingestion yet.
2. No history.get / trend.get evidence yet.
3. Incident aggregation is still basic.
4. No Markdown report yet.
5. No repeatable demo fault service yet.
```

## Next Phase

After Phase Z0, continue with:

```txt
Phase Z1: Demo environment and fault scenario
Phase Z2: Zabbix webhook ingestion
Phase Z3: Alert standardization and incident aggregation strategy
```
