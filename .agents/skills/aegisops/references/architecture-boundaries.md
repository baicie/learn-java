# Architecture Boundaries

## Three Backend Applications

### aiops-server

Delivers REST API and SSE streaming to the frontend.

**Allowed to:**
- expose HTTP endpoints
- handle authentication and authorization
- manage tenant, user, and role data
- manage datasources (create, update, delete, test, sync trigger)
- query and aggregate incidents, alerts, assets
- trigger AI diagnosis tasks
- manage runbooks
- manage automation job lifecycle (create, approve, cancel)
- stream execution logs back to clients
- query and display audit logs
- push real-time updates via SSE

**Forbidden to:**
- execute shell commands directly
- execute Ansible directly
- execute SSH directly
- perform long-running data synchronization
- perform large-scale batch analysis
- access PostgreSQL connection pool for heavy ETL
- call VictoriaMetrics or ClickHouse for analytical queries on behalf of workers

### aiops-worker

Performs background ingestion, analysis, and orchestration.

**Allowed to:**
- poll Zabbix for hosts, triggers, problems, and events
- normalize and deduplicate AlertEvents
- aggregate AlertEvents into Incidents
- generate Incident timelines
- query VictoriaMetrics for metric context
- query ClickHouse for log and event context
- execute RCA rules
- execute AI diagnosis tasks
- generate postmortem drafts
- dispatch notifications
- write results back to PostgreSQL

**Forbidden to:**
- expose HTTP endpoints directly to the internet
- execute Ansible or SSH
- make direct user-facing API calls
- handle approval workflow logic (that's server's job)
- stream logs to clients (that's runner's job)

### aiops-runner

Executes automation jobs with isolation and safety.

**Allowed to:**
- execute Ansible playbooks
- execute SSH commands
- execute webhooks
- stream execution logs
- update job status in PostgreSQL
- write audit logs
- perform post-execution health checks

**Forbidden to:**
- expose any API endpoints
- initiate connections to aiops-server for instructions
- execute jobs without a valid AutomationJob record
- execute jobs that are not in an approved state

## Module Boundaries

Each module under `modules/` has a defined public API surface.

### adapters — aiops-*-adapter modules

All external system access goes through adapter modules. No business logic here.

**Public interfaces only:**
- `aiops-zabbix-adapter`: `ZabbixAdapter`, `ZabbixClient`
- `aiops-vm-adapter`: `MetricQueryClient`, `MetricAdapter`
- `aiops-clickhouse-adapter`: `LogQueryClient`, `EventQueryClient`
- `aiops-otel-adapter`: `OtelAdapter`

### domain — aiops-*-domain or co-located domain packages

Core business logic lives here. No dependencies on adapters or other domain modules.

**Public interfaces only:**
- `AlertEventService.fingerprint()`
- `IncidentAggregator.aggregate()`
- `RcaEngine.evaluate()`
- `DiagnosisOrchestrator.diagnose()`

### application — aiops-*-service packages (or `apps/`)

Orchestrates domain logic and adapters. No direct HTTP handling.

### infrastructure — cross-cutting concerns

- `aiops-common`: shared DTOs, constants, utilities
- `aiops-security`: Spring Security, JWT, RBAC
- `aiops-audit`: audit log creation and storage
- `aiops-notification`: notification dispatch

## Dependency Rule

```
frontend (web/)
  ↑
apps/aiops-server
  ↑
modules/ (domain, adapters, infrastructure)
  ↑
  ↓  (adapter implementations, infrastructure)
infra/ (docker-compose, external systems)
```

No reverse dependencies. Domain never depends on adapters. Server never imports runner code.

## External System Access Matrix

| Caller | Zabbix | VictoriaMetrics | ClickHouse | MinIO | PostgreSQL | Redis | Ansible |
|--------|--------|----------------|------------|-------|------------|-------|---------|
| server | — | — | — | — | write+read | read+write | — |
| worker | adapter | adapter | adapter | adapter | write+read | read+write | — |
| runner | — | — | — | read | write | read | executor |

No direct access. All go through the respective adapter/client module.
