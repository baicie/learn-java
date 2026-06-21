---
title: Phase Z0 Module Boundaries
type: architecture
status: accepted
phase: z0
owner: platform-team
created: 2026-06-21
updated: 2026-06-21
related: docs/scenarios/zabbix-host-service-incident.md
---

# Phase Z0 Module Boundaries

Phase Z0 freezes the current AegisOps MVP baseline around one scenario:

> Zabbix host and service anomaly diagnosis.

The goal of this phase is not to add large features. The goal is to make existing modules explicit, stable, and testable before introducing Zabbix webhook ingestion, evidence collection, AI diagnosis enhancement, and report generation.

## MVP Scope

The current MVP only focuses on:

```txt
Zabbix datasource
  -> manual sync hosts/problems
  -> asset / alert_event
  -> incident aggregation
  -> RCA / AI diagnosis
```

Out of scope for Phase Z0:

```txt
Zabbix webhook
Zabbix history/trend evidence
Markdown reports
OpenTelemetry
RUM
log ingestion
runbook execution
complex dashboard
```

## Module Responsibilities

### aiops-zabbix-adapter

Responsible for calling Zabbix API.

Allowed responsibilities:

```txt
apiinfo.version
host.get
problem.get
later: item.get / history.get / trend.get / trigger.get
```

Forbidden responsibilities:

```txt
No database write
No asset mapping
No alert mapping
No incident creation
No AI diagnosis logic
```

### aiops-datasource

Responsible for datasource lifecycle and current manual sync.

Allowed responsibilities:

```txt
create datasource
test datasource
manual sync datasource
normalize Zabbix host/problem into stable internal mappings
upsert host asset
upsert alert_event
record sync run
```

Forbidden responsibilities:

```txt
No incident diagnosis
No RCA rule logic
No report generation
No runbook execution
```

### aiops-asset

Responsible for inventory entities.

Current Phase Z0 asset type:

```txt
host
```

Future asset types:

```txt
service
endpoint
database
middleware
```

### aiops-alert

Responsible for external alert events.

Zabbix Problem and future Zabbix Webhook events should eventually become:

```txt
alert_event
```

The alert model should keep:

```txt
source
source_event_id
severity
title
asset_id
entity_type
entity_name
labels
raw_payload
fingerprint
```

### aiops-incident

Responsible for representing one operational incident.

Current behavior:

```txt
manual aggregation from open alert_event
basic timeline
status transition
```

Future behavior:

```txt
host + service + env + time window aggregation
alert recovery handling
incident merge/split
```

### aiops-rca

Responsible for deterministic rule-based diagnosis.

RCA should consume:

```txt
incident
alerts
evidence
```

RCA should not directly call Zabbix API.

### aiops-evidence

Responsible for diagnosis evidence.

Phase Z0 only freezes the responsibility.

Future Phase Z4 will add:

```txt
ZabbixEvidenceCollector
diagnosis_evidence
metric evidence
trigger evidence
problem timeline evidence
```

### aiops-ai-client

Responsible for Java to Python Agent communication.

AI Client should not know:

```txt
Zabbix API details
database table details
asset sync details
```

### aiops-report

Not implemented in Phase Z0.

Future Phase Z7 will add:

```txt
incident_report
MarkdownReportRenderer
report preview
report regeneration
```

## Baseline Acceptance

Phase Z0 is complete when:

```txt
1. Existing Zabbix manual sync remains working.
2. Zabbix host/problem mapping is extracted into testable mapper classes.
3. Module boundary document exists.
4. Scenario document exists.
5. Basic unit tests cover mapping, tag normalization, severity mapping, and current incident aggregation behavior.
```
