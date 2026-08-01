\set ON_ERROR_STOP on

revoke all on all tables in schema public from aegisops_runner;
revoke all on all sequences in schema public from aegisops_runner;

grant select on table execution_run, execution_step to aegisops_runner;
grant update (status,
  runner_id,
  started_at,
  finished_at,
  heartbeat_at,
  lease_until,
  error_message,
  summary,
  live_guard_passed_at,
  updated_at
) on table execution_run to aegisops_runner;
grant update (status,
  started_at,
  finished_at,
  output,
  error_message,
  artifact_count,
  updated_at
) on table execution_step to aegisops_runner;
grant insert on table execution_artifact, execution_audit_event to aegisops_runner;
grant select (tenant_id, id) on table automation_plan to aegisops_runner;
grant update (status, updated_at) on table automation_plan to aegisops_runner;
grant select (tenant_id, id, status) on table rollback_plan to aegisops_runner;
grant update (status, updated_at) on table rollback_plan to aegisops_runner;

grant select on table ansible_inventory, ansible_playbook, ansible_execution_policy, ansible_credential_ref
  to aegisops_runner;
grant select on table webhook_connector, webhook_execution_policy to aegisops_runner;
