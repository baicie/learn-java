---
title: Phase 0 Design
type: design
status: draft
phase: phase-0
owner: human
created: 2026-06-12
updated: 2026-06-12
related: []
---

# Phase 0 Design

## Goal

Phase 0 only builds the project foundation. It does not implement Zabbix integration, Incident aggregation, RCA, AI, or Ansible execution.

Phase 0's responsibility is to ensure subsequent phases can iterate on a stable architecture:

- Project skeleton
- Infrastructure
- Database migrations
- Authentication
- Basic RBAC
- Tenant model
- Audit model
- Core domain tables
- OpenAPI
- Frontend console shell
- Server / Worker / Runner three-process boundary

## Application Boundaries

```txt
aiops-server:
  Exposes API to frontend. Handles login, tenant, user, datasource, asset,
  alert, and basic Incident queries.

aiops-worker:
  Phase 0 only preserves process and health check. Phase 1 onwards
  handles Zabbix sync, event consumption, and Incident aggregation.

aiops-runner:
  Phase 0 only preserves process and health check. Phase 5 onwards
  handles Ansible / SSH / Webhook execution.
```

## Database

Phase 0 establishes core tables, but many tables are only structured here without full business logic in this phase.

Core tables:

```txt
tenant
sys_user
sys_role
sys_permission
sys_user_role
sys_role_permission
datasource
asset
asset_relation
alert_event
incident
incident_event
incident_timeline
audit_log
```

## Security

Phase 0 uses:

```txt
Spring Security
JWT
BCrypt
Basic ROLE_admin / ROLE_operator
```

Default account for local development:

```txt
admin / admin123
```

Production environments must change JWT secret via environment variables and remove default initialization logic.

## Out of Scope

```txt
Zabbix Adapter
Alert aggregation
RCA
AI Provider
Runbook
AutomationJob
Ansible Runner
RUM SDK
OpenTelemetry
```

These are added starting from Phase 1.
