# Automation Safety

## Risk Levels

### Low Risk

Can run without approval if policy allows, but must always create audit logs.

- query logs
- query metrics
- run inspection scripts
- read process list
- read disk usage
- read service status
- create ticket
- send notification
- health check
- read configuration (non-sensitive)

### Medium Risk

Requires approval by default. Audit logs always required.

- restart stateless service
- clear temporary files
- refresh cache
- scale replicas up/down
- restart gateway/proxy
- reload configuration (no restart)
- drain traffic

### High Risk

Requires approval, rollback plan, and audit. Never auto-execute.

- rollback deployment
- modify configuration files
- traffic switch / failover
- restart database
- restart core middleware
- delete files or directories
- terminate processes
- execute raw shell commands
- run kubectl against production namespace

### Forbidden by Default

Never implement, never auto-execute, never allow via runbook unless explicit user request with full safety design.

- `rm -rf` or any recursive delete on production paths
- `drop database`
- `truncate table`
- delete Kubernetes namespace
- stop core middleware (Kafka, Redis, database)
- modify firewall rules
- flush Redis (FLUSHDB / FLUSHALL)
- format disk
- shutdown production host

## Execution Flow

All automation must follow this chain:

```
AI suggestion
  ↓
Policy check (risk level + approval requirement)
  ↓
AutomationJob created (status: pending)
  ↓
Approval if required (status: waiting_approval)
  ↓
Runner picks up approved job (status: approved → running)
  ↓
Stream logs back to server
  ↓
Post-execution health check
  ↓
Update job status (success / failed / timeout)
  ↓
Audit log created
  ↓
Incident timeline updated with AutomationEvent
```

## Policy Engine

Before creating an AutomationJob, the policy engine checks:

1. Is the action on the forbidden list? → reject immediately
2. What is the risk level? → determine approval requirement
3. Is there a matching Runbook? → link to job
4. Is the runbook enabled? → reject if disabled
5. Does the user have `automation:execute` permission? → reject if not
6. Does the user have `automation:approve` if approval is required? → flag for approval

## Health Check

After executing a medium or high risk job:

1. Wait a configurable period (default 30 seconds)
2. Query the target asset's health endpoint or metric
3. If health check fails, create an alert and notify the owner
4. Record health check result in job output

## Audit Log Requirements

Every automation execution must record:

- `actor_user_id` — who triggered it
- `incident_id` — which incident it belongs to (if any)
- `runbook_id` — which runbook was used
- `risk_level` — low / medium / high
- `approval_user_id` — who approved it (if applicable)
- `command_executed` — what was run (sanitized)
- `duration_ms` — how long it took
- `exit_code` — success / failure
- `output_summary` — first/last N lines of output
- `health_check_passed` — yes / no / skipped
- `created_at` — when it ran

## Runbook Safety Requirements

Every runbook must declare:

- `risk_level`: low / medium / high
- `approval_required`: true / false
- `forbidden_actions`: list of explicitly forbidden commands in this runbook
- `health_check`: what to check after execution

Runbook steps must use parameterized inputs, never raw string concatenation.

The runner must reject any runbook step that matches the forbidden pattern list, even if the runbook itself passes validation.
