# Phase Z1: Zabbix Demo Environment and Fault Scenario

## Goal

This scenario makes the first AegisOps Zabbix incident path reproducible.

It provides:

```txt
demo-order-service
  GET  /health
  POST /api/order/create

fault injection
  CPU high
  API slow
  health check failed
  error log increased

Zabbix configuration
  demo host
  HTTP Agent items
  Web Scenario
  Triggers
```

## Build Demo Service

From repository root:

```bash
mvn -pl apps/demo-order-service -am package
```

## Start Local Stack

```bash
docker compose -f infra/docker-compose.yml up -d \
  zabbix-postgres \
  zabbix-server \
  zabbix-web \
  zabbix-agent2 \
  demo-order-service
```

Check demo service:

```bash
curl http://localhost:8088/health
curl http://localhost:8088/demo/fault/state
```

## Initialize Zabbix Demo Config

```bash
python3 scripts/demo/setup-zabbix-demo.py
```

Default values:

```txt
Zabbix API:
  http://localhost:8081/api_jsonrpc.php

Zabbix login:
  Admin / zabbix

Demo host:
  aiops-demo-host

Demo service URL from Zabbix:
  http://demo-order-service:8088
```

## Inject Incident

```bash
scripts/demo/inject-zabbix-incident.sh incident
```

This enables:

```txt
CPU high:
  /zabbix/cpu-util -> 95.00

API slow:
  /zabbix/order-create-time -> 2.50

Health down:
  /zabbix/health-status -> 0

Error log increased:
  /zabbix/error-count -> > 0
```

## Verify Zabbix Metrics Manually

```bash
curl http://localhost:8088/zabbix/cpu-util
curl http://localhost:8088/zabbix/order-create-time
curl http://localhost:8088/zabbix/health-status
curl http://localhost:8088/zabbix/error-count
```

## Verify Zabbix Problems

Open Zabbix Web:

```txt
http://localhost:8081
```

Login:

```txt
Admin / zabbix
```

Go to:

```txt
Monitoring -> Problems
```

Expected problems:

```txt
AegisOps Demo CPU High
AegisOps Demo API Slow
AegisOps Demo Health Check Failed
AegisOps Demo Error Log Increased
```

## Recover

```bash
scripts/demo/inject-zabbix-incident.sh recover
```

Or reset all counters:

```bash
scripts/demo/inject-zabbix-incident.sh reset
```

## Connect AI Ops to Zabbix

If aiops-server runs on host machine, use datasource URL:

```txt
http://localhost:8081/api_jsonrpc.php
```

If aiops-server runs inside docker compose, use datasource URL:

```txt
http://zabbix-web:8080/api_jsonrpc.php
```

Credential:

```txt
Admin / zabbix
```

Then run datasource sync in AI Ops.

Expected AegisOps flow:

```txt
Zabbix problem.get
  -> alert_event
  -> incident aggregation
  -> RCA / AI diagnosis
```

## Scope

Phase Z1 does not implement:

```txt
Zabbix webhook ingestion
Zabbix history/trend evidence collection
Markdown report generation
OpenTelemetry
RUM
```

Those belong to later phases.
