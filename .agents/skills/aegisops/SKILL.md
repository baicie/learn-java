---
name: aegisops
description: Use this skill when working on the AegisOps/FaultLens AI Ops project. It guides agents to implement a Java Spring Boot based AIOps platform with Zabbix ingestion, Incident aggregation, RCA evidence chains, AI diagnosis, Runbook recommendation, Ansible automation, audit logging, and MVP phase discipline. Use for architecture, code generation, refactoring, review, tests, database schema, frontend console, backend modules, worker jobs, runner safety, and roadmap execution.
---

# AegisOps / FaultLens Project Skill

This skill defines the product direction, architecture rules, coding boundaries, MVP roadmap, and safety requirements for the AegisOps / FaultLens project.

Use this skill whenever the user asks to:

- design architecture
- write backend code
- write frontend code
- generate database schema
- implement Zabbix integration
- implement Incident aggregation
- implement RCA logic
- implement AI diagnosis
- implement Runbook / Ansible automation
- review project code
- plan MVP phases
- generate tests
- refactor modules
- write docs
- create tasks for Codex / Cursor / Claude Code

---

## 1. Product Identity

Project names:

```txt
Platform name: AegisOps
MVP / focused product name: FaultLens
```

Product positioning:

```txt
AI-powered observability, incident diagnosis, and controlled automation platform.
```

Chinese positioning:

```txt
面向可观测、智能排障和自动化处置的 AI Ops 平台。
```

The MVP must focus on this closed loop:

```txt
Zabbix alert
  ↓
AlertEvent normalization
  ↓
Incident aggregation
  ↓
Metric / log / asset / timeline context collection
  ↓
RCA evidence chain
  ↓
AI diagnosis
  ↓
Runbook recommendation
  ↓
Human-approved Ansible execution
  ↓
Execution result written back
  ↓
Postmortem report
  ↓
Historical knowledge reuse
```

Do not turn the product into a generic dashboard or a generic AI chat app.

---

## 2. Highest-Level Rule

Always design around `Incident`.

The core domain model is:

```txt
Incident
```

Not:

```txt
LLM chat
single alert
single metric
dashboard widget
raw Zabbix problem
Ansible script
```

Before adding any feature, ask:

```txt
Does this help detect, understand, resolve, or review an Incident?
```

If the answer is no, defer it.

---

## 3. Architecture Principles

### 3.1 Start as modular monolith

MVP must use:

```txt
apps/
  aiops-server
  aiops-worker
  aiops-runner
```

Do not split into microservices during MVP.

Avoid this too early:

```txt
auth-service
asset-service
alert-service
incident-service
ai-service
automation-service
```

Modular monolith first, service extraction later.

---

### 3.2 Three backend applications

#### aiops-server

Responsibilities:

```txt
REST API
SSE streaming
authentication
authorization
tenant management
user management
datasource management
asset query
alert query
incident query
AI diagnosis trigger
Runbook management
AutomationJob approval
audit query
frontend-facing APIs
```

Forbidden:

```txt
direct shell execution
direct Ansible execution
direct SSH execution
direct high-risk automation
long-running data synchronization
large batch analysis
```

---

#### aiops-worker

Responsibilities:

```txt
Zabbix host synchronization
Zabbix problem/event synchronization
AlertEvent normalization
AlertEvent fingerprinting
alert deduplication
Incident aggregation
Incident timeline generation
metric context collection
log/event context collection
RCA rule execution
AI diagnosis task execution
postmortem draft generation
notification dispatch
```

---

#### aiops-runner

Responsibilities:

```txt
Ansible execution
SSH runner execution
Webhook runner execution
Kubernetes runner execution, later
automation log streaming
job timeout control
execution status update
execution audit
post-execution health check
```

`aiops-runner` must be isolated from `aiops-server`.

The server may create and approve jobs, but only runner executes them.

---

## 4. Technology Stack

### 4.1 Frontend

Use:

```txt
React
Vite
TypeScript
shadcn/ui
Tailwind CSS
TanStack Query
TanStack Table
ECharts
React Flow
Monaco Editor
SSE for streaming
WebSocket only when SSE is insufficient
```

Frontend pages in MVP priority order:

```txt
1. Login
2. Empty dashboard
3. Datasource management
4. Asset list
5. Alert list
6. Incident list
7. Incident detail
8. Incident timeline
9. AI diagnosis panel
10. Runbook list
11. Automation approval
12. Automation logs
13. Audit logs
```

Rules:

```txt
Do not put complex data transformation in React pages.
Do not overuse global state.
Use TanStack Query for server state.
Use route-level pages and feature-level components.
Use typed API clients.
Avoid any unless absolutely necessary.
```

---

### 4.2 Backend

Use:

```txt
Java 21
Spring Boot 3.x or 4.x
Spring MVC
Spring Security
JWT
RBAC
MyBatis-Flex or jOOQ
PostgreSQL
Redis
ClickHouse JDBC
VictoriaMetrics HTTP API
MinIO Java SDK
OpenAPI / springdoc-openapi
Micrometer
OpenTelemetry Java Agent
```

Preferred MVP backend stack:

```txt
Java 21
Spring Boot 3.x
Spring Security
JWT
MyBatis-Flex
PostgreSQL
Redis
ClickHouse
VictoriaMetrics
MinIO
Flyway
```

Rules:

```txt
Controllers must not contain business logic.
Services must not directly call external systems.
External systems must be accessed through adapters or clients.
Do not return raw Map<String, Object> for main API responses.
Use DTO / VO / Entity / Domain models clearly.
Every tenant-scoped query must include tenantId.
Every security-sensitive action must generate audit logs.
```

---

### 4.3 Storage Responsibilities

Use storage systems by responsibility, not randomly.

```txt
PostgreSQL:
  users
  tenants
  roles
  permissions
  datasources
  assets
  asset_relations
  alert current index
  incidents
  incident events
  diagnoses
  runbooks
  automation jobs
  audit logs
  deployment records
  knowledge metadata

Redis:
  cache
  queues
  distributed locks
  rate limits
  temporary AI context
  SSE session state

VictoriaMetrics:
  host metrics
  service metrics
  API metrics
  resource metrics
  time series data

ClickHouse:
  raw alert events
  Zabbix event details
  logs
  RUM events
  timeline event details
  high-volume analytic events

MinIO:
  reports
  attachments
  sourcemaps
  session replay files
  Ansible artifacts
  uploaded diagnostics

pgvector:
  MVP vector search for:
    historical incidents
    runbooks
    knowledge documents
    postmortem reports

Milvus:
  Introduce only after vector data volume becomes large.
```

Do not introduce Milvus in Phase 0 unless explicitly requested.

Do not introduce Kafka in Phase 0 unless the user explicitly asks for high-throughput ingestion.

---

## 5. Recommended Repository Structure

Target structure:

```txt
aegisops/
├─ pom.xml
├─ apps/
│  ├─ aiops-server/
│  ├─ aiops-worker/
│  └─ aiops-runner/
│
├─ modules/
│  ├─ aiops-common/
│  ├─ aiops-web/
│  ├─ aiops-security/
│  ├─ aiops-tenant/
│  ├─ aiops-user/
│  ├─ aiops-datasource/
│  ├─ aiops-asset/
│  ├─ aiops-alert/
│  ├─ aiops-incident/
│  ├─ aiops-rca/
│  ├─ aiops-ai/
│  ├─ aiops-runbook/
│  ├─ aiops-automation/
│  ├─ aiops-audit/
│  ├─ aiops-notification/
│  ├─ aiops-zabbix-adapter/
│  ├─ aiops-vm-adapter/
│  ├─ aiops-clickhouse-adapter/
│  ├─ aiops-otel-adapter/
│  └─ aiops-rum/
│
├─ web/
│  └─ console/
│
├─ infra/
│  ├─ docker-compose.yml
│  ├─ postgres/
│  ├─ redis/
│  ├─ clickhouse/
│  ├─ victoria-metrics/
│  ├─ minio/
│  └─ zabbix/
│
├─ docs/
│  ├─ architecture.md
│  ├─ data-model.md
│  ├─ rca-design.md
│  ├─ ai-agent-design.md
│  ├─ automation-safety.md
│  └─ mvp-roadmap.md
│
└─ .agents/
   └─ aegisops/
      └─ SKILL.md
```

Do not flatten all backend code into one module.

Do not put Zabbix, ClickHouse, VictoriaMetrics, AI provider, and automation logic inside the same service package.

---

## 6. Core Domain Models

### 6.1 Tenant

Every business object must be tenant-scoped unless explicitly global.

Core fields:

```txt
id
name
status
created_at
updated_at
```

---

### 6.2 User

Core fields:

```txt
id
tenant_id
username
display_name
email
password_hash
status
created_at
updated_at
```

Never store plain text passwords.

---

### 6.3 Role / Permission

Use RBAC.

Permissions should support resources like:

```txt
datasource:read
datasource:write
asset:read
alert:read
incident:read
incident:write
incident:diagnose
runbook:read
runbook:write
automation:read
automation:approve
automation:execute
audit:read
admin:manage
```

---

### 6.4 DataSource

Represents external systems.

Types:

```txt
zabbix
victoriametrics
clickhouse
opentelemetry
prometheus
rum
webhook
github
gitlab
jenkins
```

Core fields:

```txt
id
tenant_id
type
name
endpoint
auth_type
encrypted_config
status
last_sync_at
created_at
updated_at
```

Secrets must be encrypted or handled by secret storage.

Do not store raw tokens in plain columns.

---

### 6.5 Asset

Asset types:

```txt
host
service
endpoint
database
redis
middleware
application
page
tenant
k8s_cluster
k8s_namespace
k8s_pod
```

Core fields:

```txt
id
tenant_id
asset_type
name
display_name
source
source_id
env
ip
tags
status
created_at
updated_at
```

---

### 6.6 AssetRelation

Relationship types:

```txt
depends_on
runs_on
contains
calls
owns
related_to
```

Core fields:

```txt
id
tenant_id
from_asset_id
to_asset_id
relation_type
confidence
source
created_at
updated_at
```

---

### 6.7 AlertEvent

Unified alert event.

Sources:

```txt
zabbix
prometheus
rum
opentelemetry
webhook
manual
```

Core fields:

```txt
id
tenant_id
source
source_event_id
severity
title
description
asset_id
entity_type
entity_name
labels
starts_at
ends_at
status
raw_payload
fingerprint
created_at
```

Severity values:

```txt
info
warning
average
high
disaster
```

Status values:

```txt
open
recovered
ignored
suppressed
```

Fingerprint rule:

```txt
source + asset_id + source_trigger_id + normalized_title
```

---

### 6.8 Incident

The central aggregate root.

Core fields:

```txt
id
tenant_id
title
summary
severity
status
source
primary_asset_id
suspected_root_cause
confidence
impact_score
started_at
detected_at
resolved_at
owner_user_id
created_at
updated_at
```

Status values:

```txt
open
investigating
mitigating
resolved
closed
ignored
```

Rules:

```txt
An Incident can contain many AlertEvents.
An Incident can contain timeline events.
An Incident can contain AI diagnoses.
An Incident can contain AutomationJobs.
An Incident can generate one postmortem.
```

---

### 6.9 IncidentEvent

Links Incident with events.

Event types:

```txt
alert
metric_anomaly
log_error
change
automation
ai_diagnosis
manual_note
recovery
```

Relation types:

```txt
primary
related
upstream
downstream
evidence
noise
```

---

### 6.10 IncidentTimeline

Used for detail page display.

Timeline event types:

```txt
alert_triggered
metric_anomaly_detected
log_error_detected
change_detected
ai_diagnosis_created
runbook_recommended
automation_started
automation_finished
manual_note_added
incident_resolved
incident_closed
```

The Incident detail page should be organized around timeline.

---

### 6.11 DiagnosisResult

AI diagnosis must be structured.

Shape:

```json
{
  "summary": "string",
  "severity": "low | medium | high | critical",
  "suspectedRootCause": "string",
  "confidence": 0.0,
  "evidence": [
    {
      "type": "metric | log | alert | trace | change | zabbix | runbook | history",
      "description": "string",
      "sourceId": "string",
      "query": "string",
      "value": {}
    }
  ],
  "impact": {
    "affectedHosts": 0,
    "affectedServices": [],
    "affectedUsers": 0,
    "affectedTenants": []
  },
  "suggestions": [
    {
      "title": "string",
      "action": "string",
      "risk": "low | medium | high",
      "requiresApproval": true
    }
  ]
}
```

Rules:

```txt
AI output must be parsed and validated.
If parsing fails, return a safe fallback result.
AI must not invent data outside provided context.
Confidence must be explicit.
Evidence must reference actual collected data.
```

---

### 6.12 Runbook

Core fields:

```txt
id
tenant_id
name
description
trigger_condition
risk_level
approval_required
enabled
created_at
updated_at
```

Step types:

```txt
manual
ssh
ansible
webhook
http
k8s
query_metric
query_log
health_check
```

---

### 6.13 AutomationJob

Core fields:

```txt
id
tenant_id
incident_id
runbook_id
status
risk_level
approval_required
created_by
approved_by
started_at
finished_at
input_json
output_json
created_at
updated_at
```

Status values:

```txt
pending
waiting_approval
approved
running
success
failed
cancelled
timeout
```

Automation logs:

```txt
id
job_id
step_id
log_time
level
content
```

---

### 6.14 AuditLog

Every sensitive operation must create audit logs.

Must include:

```txt
id
tenant_id
actor_user_id
action
resource_type
resource_id
request_id
ip
user_agent
payload
created_at
```

Audit must cover:

```txt
login
logout
datasource create/update/delete
incident status change
AI diagnosis trigger
runbook create/update/delete
automation approval
automation execution
permission change
secret change
```

---

## 7. Zabbix Integration Rules

Zabbix is a data source, not the platform core.

Correct flow:

```txt
Zabbix Server
  ↓
Zabbix Adapter
  ↓
Asset / AlertEvent
  ↓
Incident aggregation
```

Do not make Ansible call Zabbix directly.

Do not make AI call Zabbix directly.

All Zabbix API operations must be inside:

```txt
aiops-zabbix-adapter
```

Required adapter features:

```txt
test connection
sync host groups
sync hosts
sync triggers
sync problems
sync events
convert host to Asset
convert problem/event to AlertEvent
preserve raw payload
normalize severity
generate fingerprint
```

Expected client interface:

```java
public interface ZabbixClient {
    boolean testConnection();

    List<ZabbixHost> listHosts();

    List<ZabbixHostGroup> listHostGroups();

    List<ZabbixTrigger> listTriggers();

    List<ZabbixProblem> listProblems();

    List<ZabbixEvent> listEvents(ZabbixEventQuery query);
}
```

Expected adapter interface:

```java
public interface ZabbixAdapter {
    SyncResult syncAssets(Long tenantId, Long datasourceId);

    SyncResult syncAlertEvents(Long tenantId, Long datasourceId);
}
```

---

## 8. Metrics Integration Rules

VictoriaMetrics should be accessed through an adapter.

Do not call VictoriaMetrics directly from controllers.

Expected interface:

```java
public interface MetricQueryClient {
    MetricSeries queryRange(MetricRangeQuery query);

    InstantValue queryInstant(MetricInstantQuery query);
}
```

MVP metric queries:

```txt
CPU usage
memory usage
disk usage
network IO
host availability
trigger-related metric history
```

Incident RCA should query metrics in this default window:

```txt
incident.started_at - 30 minutes
incident.started_at + 30 minutes
```

Allow the user to adjust this later.

---

## 9. ClickHouse Integration Rules

ClickHouse stores high-volume event details.

Do not use ClickHouse as the primary metadata database.

Use ClickHouse for:

```txt
raw alert event payloads
Zabbix event details
logs
timeline event details
RUM events, later
```

Rules:

```txt
Parameterized queries only.
No string-concatenated SQL from user input.
Large result sets must be paginated.
Long-running queries must have timeout.
```

---

## 10. AI Agent Rules

### 10.1 AI responsibilities

AI can:

```txt
summarize Incident
explain evidence
generate investigation steps
recommend Runbook
match historical incidents
generate postmortem draft
generate safe query suggestions
explain risk
```

AI cannot directly:

```txt
execute SSH
execute Ansible
delete files
rollback production
modify config
restart database
stop core middleware
close incidents without user confirmation
```

---

### 10.2 LLM provider abstraction

Use provider abstraction.

Required interface:

```java
public interface LlmProvider {
    ChatResult chat(ChatRequest request);

    EmbeddingResult embed(EmbeddingRequest request);
}
```

Possible implementations:

```txt
OpenAiProvider
DeepSeekProvider
QwenProvider
OllamaProvider
```

Do not hard-code a specific LLM vendor in business logic.

---

### 10.3 AI tool registry

Allowed tools:

```txt
queryMetrics
queryLogs
queryAlerts
queryAssets
queryTopology
queryChanges
searchRunbooks
searchSimilarIncidents
generateIncidentReport
recommendRunbook
proposeAutomation
```

Forbidden tools:

```txt
executeCommand
executeShell
restartServiceDirectly
deleteFileDirectly
modifyProductionConfigDirectly
```

Execution must go through:

```txt
proposeAutomation
  ↓
AutomationJob
  ↓
approval
  ↓
aiops-runner
```

---

### 10.4 Diagnosis prompt rules

When generating diagnosis, include:

```txt
Incident basic info
related alerts
asset info
metric context
timeline events
RCA rule evidence
historical similar incidents
available runbooks
automation policy constraints
```

The AI must output:

```txt
summary
impact
suspected root cause
confidence
evidence
recommended next steps
recommended runbooks
automation risk warning
```

The AI must not claim certainty if evidence is weak.

Use language like:

```txt
疑似
可能
根据当前证据
需要进一步确认
```

when confidence is low.

---

## 11. RCA Engine Rules

MVP uses:

```txt
rule-based RCA + evidence chain + AI summarization
```

Do not train a model in MVP.

Do not implement complex graph algorithms before basic RCA rules work.

First RCA rules:

```txt
R1: recent change exists within ±30 minutes
R2: same asset has multiple alerts
R3: upstream asset alert appears before downstream alert
R4: issue concentrated on one host / service / version
R5: metric anomaly time overlaps with log errors
R6: historical similar incident exists
R7: existing Runbook matches current incident
R8: alert storm duplicate detected
```

Every RCA rule must output:

```txt
rule_id
score
evidence
related_asset_id
related_event_id
explanation
```

Never return only a score.

RCA result must be explainable.

---

## 12. Alert Aggregation Rules

MVP aggregation rules:

```txt
same tenant
same fingerprint
close time window
same primary asset
```

Default aggregation window:

```txt
10 minutes
```

Additional grouping rules:

```txt
same host + multiple resource alerts → host-level Incident
same host group + many alerts → host-group Incident
same datasource + alert storm → storm Incident
```

Do not overfit early.

Keep aggregation rules configurable later.

---

## 13. Automation Safety Rules

Automation is dangerous. Always prioritize safety.

### 13.1 Risk levels

Low risk:

```txt
query logs
query metrics
run inspection
create ticket
send notification
read process list
read disk usage
read service status
```

Can run without approval if policy allows, but must be audited.

Medium risk:

```txt
restart stateless service
clear temp files
refresh cache
scale replicas
```

Requires approval by default.

High risk:

```txt
rollback deployment
modify config
traffic switch
restart database
delete files
```

Requires approval, rollback plan, and audit.

Forbidden by default:

```txt
rm -rf
drop database
truncate table
delete Kubernetes namespace
stop core middleware
modify firewall
flush Redis
format disk
```

Never generate code that enables forbidden actions without explicit user request and safety design.

---

### 13.2 Execution flow

All automation must follow:

```txt
AI suggestion
  ↓
Policy check
  ↓
AutomationJob created
  ↓
approval if required
  ↓
runner execution
  ↓
stream logs
  ↓
post-execution health check
  ↓
audit log
  ↓
Incident timeline update
```

---

## 14. MVP Phases

### Phase 0: Foundation

Goal:

```txt
Project skeleton, infrastructure, authentication, database migration.
```

Deliverables:

```txt
Maven multi-module project
apps/aiops-server
apps/aiops-worker
apps/aiops-runner
React console
Docker Compose
PostgreSQL
Redis
ClickHouse
VictoriaMetrics
MinIO
Flyway migrations
Spring Security + JWT
basic RBAC
OpenAPI
health checks
```

Acceptance:

```txt
docker compose starts successfully
server starts successfully
user can log in
OpenAPI is accessible
tenant/user/role can be created
empty dashboard renders
```

Do not implement Zabbix before Phase 0 foundation is stable.

---

### Phase 1: Zabbix ingestion

Goal:

```txt
Use Zabbix as the first datasource.
```

Deliverables:

```txt
datasource management page
Zabbix datasource config
test connection
sync host groups
sync hosts
sync triggers
sync problems/events
convert to Asset
convert to AlertEvent
asset list
alert list
```

Acceptance:

```txt
After configuring Zabbix, hosts and alerts are visible in AegisOps.
A Zabbix problem becomes an AlertEvent.
Raw payload is preserved.
Fingerprint is generated.
```

---

### Phase 2: Incident center

Goal:

```txt
Turn alert list into Incident center.
```

Deliverables:

```txt
AlertEvent deduplication
Incident creation
Incident aggregation
Incident list
Incident detail
Incident timeline
status transition
severity calculation
```

Acceptance:

```txt
Repeated Zabbix alerts are grouped into one Incident.
Incident detail shows related alerts, assets, and timeline.
```

---

### Phase 3: RCA engine

Goal:

```txt
Generate evidence chain from metrics, assets, and events.
```

Deliverables:

```txt
VictoriaMetrics adapter
metric context query
Incident metric panel
RCA rule engine
evidence model
root cause scoring
RCA result display
```

Acceptance:

```txt
Incident detail can show metrics around the event window.
RCA engine outputs evidence, not just text.
```

---

### Phase 4: AI diagnosis

Goal:

```txt
Use AI to summarize RCA evidence into human-readable diagnosis.
```

Deliverables:

```txt
LLM provider abstraction
one provider implementation
AI tool registry
diagnosis prompt
structured DiagnosisResult
SSE streaming
diagnosis persistence
AI diagnosis panel
```

Acceptance:

```txt
Clicking AI Diagnose produces a report with summary, suspected root cause, evidence, confidence, and suggestions.
```

---

### Phase 5: Runbook and Ansible

Goal:

```txt
Move from diagnosis to controlled action.
```

Deliverables:

```txt
Runbook model
Runbook steps
Ansible Runner
AutomationJob model
approval flow
execution logs
result write-back
Incident timeline integration
```

Built-in Runbooks:

```txt
host inspection
disk check
Nginx status check
Java process check
service restart, approval required
```

Acceptance:

```txt
Incident can recommend a Runbook.
User can approve execution.
Runner executes inspection Playbook.
Logs stream back.
Result is written to Incident timeline.
```

---

### Phase 6: Postmortem and knowledge

Goal:

```txt
Turn every incident into reusable knowledge.
```

Deliverables:

```txt
postmortem draft generation
historical incident library
Runbook-to-Incident linkage
pgvector search
similar incident search
AI diagnosis enhanced by historical incidents
```

Acceptance:

```txt
Closing an Incident generates a postmortem draft.
Next similar Incident can reference previous incidents.
```

---

## 15. What Not To Build In MVP

Do not build these unless explicitly asked:

```txt
full Prometheus replacement
full log platform
full tracing platform
complex Kubernetes Operator
multi-region HA
complex CMDB
large wall-screen dashboard
fully autonomous self-healing
custom model training
full Milvus deployment
full Kafka pipeline
premature microservices
complex license system
```

Avoid platform bloat.

---

## 16. API Design Rules

Use REST first.

Use SSE for streaming AI output and runner logs.

Use WebSocket only when two-way real-time communication is required.

Base API groups:

```txt
/api/auth
/api/users
/api/roles
/api/tenants
/api/datasources
/api/assets
/api/alerts
/api/incidents
/api/ai
/api/runbooks
/api/automation
/api/audit
```

Required endpoints:

```txt
POST /api/auth/login
POST /api/auth/logout
GET  /api/auth/me

GET  /api/datasources
POST /api/datasources
POST /api/datasources/{id}/test
POST /api/datasources/{id}/sync

GET  /api/assets
GET  /api/assets/{id}
GET  /api/assets/{id}/relations

GET  /api/alerts
GET  /api/alerts/{id}
POST /api/alerts/{id}/ignore

GET  /api/incidents
POST /api/incidents
GET  /api/incidents/{id}
GET  /api/incidents/{id}/timeline
POST /api/incidents/{id}/diagnose
POST /api/incidents/{id}/resolve
POST /api/incidents/{id}/close

GET  /api/runbooks
POST /api/runbooks
GET  /api/runbooks/{id}
POST /api/runbooks/{id}/execute

GET  /api/automation/jobs
POST /api/automation/jobs
POST /api/automation/jobs/{id}/approve
POST /api/automation/jobs/{id}/cancel
GET  /api/automation/jobs/{id}/logs

GET  /api/audit/logs
```

---

## 17. Database Migration Rules

Use Flyway.

Rules:

```txt
Every schema change must add a migration file.
Do not edit old migrations after they are merged.
Use tenant_id where applicable.
Create indexes for tenant-scoped queries.
Create indexes for status and time range filters.
Use JSONB for flexible metadata in PostgreSQL.
Do not store secrets in plain text.
```

Naming:

```txt
V0001__init_tenant_user_rbac.sql
V0002__init_datasource_asset.sql
V0003__init_alert_incident.sql
V0004__init_ai_runbook_automation.sql
V0005__init_audit.sql
```

---

## 18. Testing Rules

Every phase must include tests.

Backend tests:

```txt
unit tests for domain logic
service tests for aggregation
adapter tests with mocked external systems
controller tests for API contracts
migration validation
```

Frontend tests:

```txt
component smoke tests
API mock tests
critical page rendering tests
Playwright E2E later
```

High-priority backend tests:

```txt
Alert fingerprint generation
AlertEvent deduplication
Alert to Incident aggregation
Incident status transition
RCA rule scoring
DiagnosisResult parsing
AutomationJob state machine
RBAC permission checks
Audit log creation
```

Never claim complete without running the relevant tests or clearly saying tests were not run.

---

## 19. Review Checklist

When reviewing code, check:

```txt
Does this preserve Incident as the central model?
Does this follow current MVP phase?
Does this avoid premature microservices?
Does this keep external systems behind adapters?
Does this protect tenant isolation?
Does this avoid AI direct execution?
Does this require approval for risky actions?
Does this create audit logs?
Does this include tests?
Does this update migration files?
Does this update OpenAPI or docs if API changed?
Does this avoid overengineering?
```

---

## 20. Output Style for Agent Responses

When proposing implementation, use this structure:

```txt
1. Scope
2. Files to add/change
3. Data model changes
4. Backend changes
5. Frontend changes
6. Tests
7. Verification commands
8. Risks / follow-up
```

When giving code, prefer complete file contents or precise patches.

When something is not implemented, say so clearly.

Do not say "done" unless verification was actually run.

---

## 21. Phase Discipline

Always identify current phase.

If the user asks for Phase 0, do not implement Phase 3 AI diagnosis.

If the user asks for Phase 1, do not implement Ansible Runner.

If the user asks for Phase 5, verify Phase 0-4 assumptions.

Default phase order:

```txt
Phase 0: foundation
Phase 1: Zabbix ingestion
Phase 2: Incident center
Phase 3: RCA engine
Phase 4: AI diagnosis
Phase 5: Runbook and Ansible
Phase 6: postmortem and knowledge
```

---

## 22. Default Implementation Priorities

Prefer:

```txt
simple domain model
clear adapter boundaries
explainable RCA
safe automation
testable services
typed API responses
migration-first database changes
```

Avoid:

```txt
fancy dashboard before data correctness
AI prompt complexity before evidence model
automation before approval flow
graph topology before asset basics
microservices before MVP
Kafka before Redis Stream is insufficient
Milvus before pgvector is insufficient
```

---

## 23. Final MVP Demo Requirement

A successful MVP must demonstrate:

```txt
1. User logs in.
2. User adds Zabbix datasource.
3. Platform syncs hosts and alerts.
4. Zabbix triggers a CPU alert.
5. Platform creates AlertEvent.
6. Platform aggregates Incident.
7. Incident detail shows related asset, alert, timeline, and metrics.
8. User clicks AI Diagnose.
9. AI outputs evidence-based diagnosis.
10. System recommends inspection Runbook.
11. User approves execution.
12. aiops-runner executes Ansible inspection.
13. Logs stream back.
14. Result is written to Incident timeline.
15. User closes Incident.
16. Platform generates postmortem draft.
```

If the implementation does not support this path, it is not MVP-complete.

---

## 24. Strongest Reminder

The product value is not:

```txt
more dashboards
more charts
more data sources
more AI chat
more microservices
```

The product value is:

```txt
turning raw alerts into explainable incidents,
turning incidents into safe actions,
turning actions into reusable knowledge.
```

---

## 25. Document Governance

All project documents must follow the governance rules in:

```txt
.skills/aegisops/references/doc-governance.md
```

Key rules:

```txt
- All documents go under docs/
- Every document requires YAML frontmatter (title, type, status, phase, owner, created, updated, related)
- Process documents (design, review, fix) are phase-scoped: docs/designs/<phase>/, docs/reviews/<phase>/, docs/fixes/<phase>/
- ADR uses numbered filenames: docs/adr/NNNN-slug.md
- Process documents use date prefix: YYYY-MM-DD-slug.md
- Accepted ADRs are never edited; create a new one to change a decision
- docs/INDEX.md is auto-generated; never edit it manually
```

Use the docs script to create and manage documents:

```txt
npx tsx scripts/docs.ts init    # initialize directory structure
npx tsx scripts/docs.ts new <type> <slug> --title "Title" --phase phase-0
npx tsx scripts/docs.ts check    # validate frontmatter and naming
npx tsx scripts/docs.ts index     # regenerate docs/INDEX.md
```

Reference docs for agents:

```txt
.skills/aegisops/references/doc-governance.md       # document rules
.skills/aegisops/references/phase-checklist.md     # phase start/close checklist
.skills/aegisops/references/architecture-boundaries.md  # module and app boundaries
.skills/aegisops/references/automation-safety.md     # risk levels and safety rules
```

