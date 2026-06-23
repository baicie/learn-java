-- AegisOps Final Schema Migration
-- All phases V1..V32 merged into one idempotent migration.
-- Order: (1) CREATE TABLE (2) ALTER TABLE ADD COLUMN (3) ALTER CONSTRAINT (4) CREATE INDEX
-- Usage: docker compose down -v && mvn spring-boot:run

-- =============================================================================
-- (1) CREATE TABLE
-- =============================================================================

-- Core Tables
create table if not exists tenant (
  id varchar(64) primary key,
  code varchar(64) not null unique,
  name varchar(128) not null,
  status varchar(32) not null default 'active',
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

create table if not exists sys_user (
  id varchar(64) primary key,
  tenant_id varchar(64) not null references tenant(id),
  username varchar(64) not null unique,
  display_name varchar(128) not null,
  email varchar(128),
  password_hash varchar(255) not null,
  status varchar(32) not null default 'active',
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

create table if not exists sys_role (
  id varchar(64) primary key,
  code varchar(64) not null unique,
  name varchar(128) not null,
  created_at timestamptz not null default now()
);

create table if not exists sys_permission (
  id varchar(64) primary key,
  code varchar(128) not null unique,
  name varchar(128) not null,
  created_at timestamptz not null default now()
);

create table if not exists sys_user_role (
  user_id varchar(64) not null references sys_user(id) on delete cascade,
  role_id varchar(64) not null references sys_role(id) on delete cascade,
  primary key(user_id, role_id)
);

create table if not exists sys_role_permission (
  role_id varchar(64) not null references sys_role(id) on delete cascade,
  permission_id varchar(64) not null references sys_permission(id) on delete cascade,
  primary key(role_id, permission_id)
);

create table if not exists datasource (
  id varchar(64) primary key,
  tenant_id varchar(64) not null references tenant(id),
  type varchar(64) not null,
  name varchar(128) not null,
  status varchar(32) not null default 'inactive',
  config_json jsonb not null default '{}'::jsonb,
  last_sync_at timestamptz,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

create table if not exists datasource_sync_run (
  id varchar(64) primary key,
  tenant_id varchar(64) not null references tenant(id),
  datasource_id varchar(64) not null references datasource(id) on delete cascade,
  sync_type varchar(64) not null default 'manual',
  status varchar(32) not null default 'running',
  message text,
  stats_json jsonb not null default '{}'::jsonb,
  started_at timestamptz not null default now(),
  finished_at timestamptz,
  created_by varchar(64)
);

create table if not exists asset (
  id varchar(64) primary key,
  tenant_id varchar(64) not null references tenant(id),
  asset_type varchar(64) not null,
  name varchar(256) not null,
  display_name varchar(256),
  source varchar(64) not null default 'manual',
  source_id varchar(256),
  env varchar(64),
  ip varchar(64),
  tags jsonb not null default '{}'::jsonb,
  status varchar(32) not null default 'active',
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

create table if not exists asset_relation (
  id varchar(64) primary key,
  tenant_id varchar(64) not null references tenant(id),
  from_asset_id varchar(64) not null references asset(id) on delete cascade,
  to_asset_id varchar(64) not null references asset(id) on delete cascade,
  relation_type varchar(64) not null,
  confidence numeric(5,4) not null default 1.0,
  source varchar(64) not null default 'manual',
  created_at timestamptz not null default now()
);

create table if not exists alert_event (
  id varchar(64) primary key,
  tenant_id varchar(64) not null references tenant(id),
  source varchar(64) not null,
  source_event_id varchar(256),
  severity varchar(32) not null default 'info',
  title varchar(512) not null,
  description text,
  asset_id varchar(64),
  entity_type varchar(64),
  entity_name varchar(256),
  labels jsonb not null default '{}'::jsonb,
  starts_at timestamptz not null default now(),
  ends_at timestamptz,
  status varchar(32) not null default 'open',
  raw_payload jsonb not null default '{}'::jsonb,
  fingerprint varchar(512) not null,
  aggregation_key varchar(512),
  updated_at timestamptz not null default now(),
  created_at timestamptz not null default now()
);

create table if not exists incident (
  id varchar(64) primary key,
  tenant_id varchar(64) not null references tenant(id),
  title varchar(512) not null,
  summary text,
  severity varchar(32) not null default 'low',
  status varchar(32) not null default 'open',
  source varchar(64) not null default 'system',
  primary_asset_id varchar(64),
  suspected_root_cause text,
  confidence numeric(5,4),
  impact_score numeric(10,4) not null default 0,
  aggregation_key varchar(512),
  last_seen_at timestamptz,
  alert_count integer not null default 0,
  started_at timestamptz not null default now(),
  detected_at timestamptz not null default now(),
  resolved_at timestamptz,
  owner_user_id varchar(64),
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

create table if not exists incident_event (
  id varchar(64) primary key,
  incident_id varchar(64) not null references incident(id) on delete cascade,
  event_type varchar(64) not null,
  event_id varchar(64) not null,
  relation_type varchar(64) not null default 'related',
  occurred_at timestamptz not null default now()
);

create table if not exists incident_timeline (
  id varchar(64) primary key,
  incident_id varchar(64) not null references incident(id) on delete cascade,
  event_time timestamptz not null,
  event_type varchar(64) not null,
  title varchar(512) not null,
  description text,
  source varchar(64) not null default 'system',
  payload jsonb not null default '{}'::jsonb
);

create table if not exists audit_log (
  id varchar(64) primary key,
  tenant_id varchar(64),
  actor_user_id varchar(64),
  action varchar(128) not null,
  target_type varchar(64),
  target_id varchar(64),
  detail_json jsonb not null default '{}'::jsonb,
  created_at timestamptz not null default now()
);

-- Phase 3: RCA
create table if not exists rca_analysis (
  id varchar(64) primary key,
  tenant_id varchar(64) not null references tenant(id),
  incident_id varchar(64) not null references incident(id) on delete cascade,
  status varchar(32) not null default 'completed',
  suspected_root_cause text not null,
  confidence numeric(5,4) not null default 0,
  summary text,
  evidence jsonb not null default '[]'::jsonb,
  model_version varchar(64) not null default 'rules-v1',
  created_at timestamptz not null default now()
);

-- Phase 4: AI Diagnosis
create table if not exists ai_diagnosis (
  id varchar(64) primary key,
  tenant_id varchar(64) not null references tenant(id),
  incident_id varchar(64) not null references incident(id) on delete cascade,
  status varchar(32) not null default 'completed',
  provider varchar(64) not null,
  model varchar(128) not null,
  agent_name varchar(128) not null default 'aegisops_diagnosis_graph',
  request_payload jsonb not null default '{}'::jsonb,
  response_raw jsonb not null default '{}'::jsonb,
  summary text not null,
  root_cause text not null,
  impact text not null,
  next_steps jsonb not null default '[]'::jsonb,
  runbook_suggestions jsonb not null default '[]'::jsonb,
  risks jsonb not null default '[]'::jsonb,
  created_at timestamptz not null default now()
);

-- Phase 4.3: Evidence Events
create table if not exists log_event (
  id varchar(64) primary key,
  tenant_id varchar(64) not null references tenant(id),
  asset_id varchar(64),
  service_name varchar(255),
  severity varchar(32) not null default 'info',
  message text not null,
  attributes jsonb not null default '{}'::jsonb,
  occurred_at timestamptz not null,
  created_at timestamptz not null default now()
);

create table if not exists change_event (
  id varchar(64) primary key,
  tenant_id varchar(64) not null references tenant(id),
  asset_id varchar(64),
  service_name varchar(255),
  change_type varchar(64) not null,
  title varchar(512) not null,
  description text,
  source varchar(64) not null default 'manual',
  operator varchar(128),
  risk_level varchar(32) not null default 'medium',
  attributes jsonb not null default '{}'::jsonb,
  occurred_at timestamptz not null,
  created_at timestamptz not null default now()
);

-- Phase 4.4: Agent Observability
create table if not exists agent_run (
  id varchar(64) primary key,
  diagnosis_id varchar(64) not null references ai_diagnosis(id) on delete cascade,
  tenant_id varchar(64) not null references tenant(id),
  incident_id varchar(64) not null references incident(id) on delete cascade,
  trace_id varchar(128) not null,
  contract_version varchar(64) not null,
  generation_mode varchar(64) not null,
  provider varchar(64) not null,
  model varchar(128) not null,
  status varchar(32) not null,
  started_at timestamptz,
  finished_at timestamptz,
  duration_ms bigint not null default 0,
  fallback_reason text,
  safety jsonb not null default '{}'::jsonb,
  eval_result jsonb not null default '{}'::jsonb,
  created_at timestamptz not null default now()
);

create table if not exists agent_run_step (
  id varchar(64) primary key,
  run_id varchar(64) not null references agent_run(id) on delete cascade,
  sequence_no int not null,
  step_name varchar(128) not null,
  step_type varchar(64) not null,
  status varchar(32) not null,
  started_at timestamptz,
  finished_at timestamptz,
  duration_ms bigint not null default 0,
  input_summary text,
  output_summary text,
  error_message text,
  metadata jsonb not null default '{}'::jsonb,
  created_at timestamptz not null default now()
);

create table if not exists agent_eval_result (
  id varchar(64) primary key,
  run_id varchar(64) not null references agent_run(id) on delete cascade,
  evaluator_name varchar(128) not null,
  check_name varchar(128) not null,
  passed boolean not null,
  score numeric(8,4) not null default 0,
  reason text,
  details jsonb not null default '{}'::jsonb,
  created_at timestamptz not null default now()
);

-- Phase 5.0: Runbook & Automation
create table if not exists runbook (
  id varchar(64) primary key,
  tenant_id varchar(64) references tenant(id) on delete cascade,
  name varchar(160) not null,
  description text,
  category varchar(64) not null default 'general',
  risk_level varchar(32) not null default 'medium',
  enabled boolean not null default true,
  matchers jsonb not null default '{}'::jsonb,
  variables jsonb not null default '{}'::jsonb,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

create table if not exists runbook_step_template (
  id varchar(64) primary key,
  runbook_id varchar(64) not null references runbook(id) on delete cascade,
  sequence_no int not null,
  name varchar(160) not null,
  action_type varchar(32) not null default 'manual',
  target_type varchar(32) not null default 'human',
  command_template text,
  description text,
  expected_result text,
  rollback_hint text,
  requires_approval boolean not null default true,
  timeout_seconds int not null default 300,
  metadata jsonb not null default '{}'::jsonb,
  created_at timestamptz not null default now()
);

create table if not exists automation_plan (
  id varchar(64) primary key,
  tenant_id varchar(64) not null references tenant(id) on delete cascade,
  incident_id varchar(64) not null references incident(id) on delete cascade,
  runbook_id varchar(64) references runbook(id) on delete set null,
  ai_diagnosis_id varchar(64) references ai_diagnosis(id) on delete set null,
  rca_analysis_id varchar(64) references rca_analysis(id) on delete set null,
  source varchar(64) not null default 'runbook-recommendation-v1',
  status varchar(32) not null default 'draft',
  risk_level varchar(32) not null default 'medium',
  confidence numeric(5,4) not null default 0,
  title varchar(200) not null,
  summary text not null,
  evidence jsonb not null default '{}'::jsonb,
  created_by varchar(64) not null default 'system',
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

create table if not exists automation_plan_step (
  id varchar(64) primary key,
  plan_id varchar(64) not null references automation_plan(id) on delete cascade,
  sequence_no int not null,
  name varchar(160) not null,
  action_type varchar(32) not null default 'manual',
  target_type varchar(32) not null default 'human',
  action_payload jsonb not null default '{}'::jsonb,
  description text,
  expected_result text,
  rollback_hint text,
  requires_approval boolean not null default true,
  status varchar(32) not null default 'pending',
  created_at timestamptz not null default now()
);

-- Phase 5.1: Approval
create table if not exists approval_policy (
  id varchar(64) primary key,
  tenant_id varchar(64) references tenant(id) on delete cascade,
  risk_level varchar(32) not null,
  required_approvals int not null default 1,
  require_comment boolean not null default false,
  enabled boolean not null default true,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

create table if not exists automation_approval (
  id varchar(64) primary key,
  tenant_id varchar(64) not null references tenant(id) on delete cascade,
  incident_id varchar(64) not null references incident(id) on delete cascade,
  plan_id varchar(64) not null references automation_plan(id) on delete cascade,
  status varchar(32) not null default 'pending',
  risk_level varchar(32) not null,
  required_approvals int not null,
  approved_count int not null default 0,
  rejected_count int not null default 0,
  submitted_by varchar(64) not null,
  submitted_at timestamptz not null default now(),
  completed_at timestamptz,
  reason text,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

create table if not exists approval_decision (
  id varchar(64) primary key,
  tenant_id varchar(64) not null references tenant(id) on delete cascade,
  approval_id varchar(64) not null references automation_approval(id) on delete cascade,
  plan_id varchar(64) not null references automation_plan(id) on delete cascade,
  reviewer varchar(64) not null,
  decision varchar(32) not null,
  comment text,
  decided_at timestamptz not null default now(),
  created_at timestamptz not null default now()
);

-- Phase 5.2-5.3: Execution
create table if not exists execution_run (
  id varchar(64) primary key,
  tenant_id varchar(64) not null references tenant(id) on delete cascade,
  incident_id varchar(64) not null references incident(id) on delete cascade,
  plan_id varchar(64) not null references automation_plan(id) on delete cascade,
  status varchar(32) not null default 'queued',
  mode varchar(32) not null default 'dry_run',
  requested_by varchar(64) not null,
  runner_id varchar(128),
  started_at timestamptz,
  finished_at timestamptz,
  error_message text,
  summary text,
  attempt int not null default 1,
  max_attempts int not null default 1,
  retry_of_execution_id varchar(64),
  lease_until timestamptz,
  heartbeat_at timestamptz,
  timeout_seconds int not null default 1800,
  approval_id varchar(64),
  approval_snapshot jsonb not null default '{}'::jsonb,
  plan_risk_level varchar(32),
  live_guard_passed_at timestamptz,
  execution_kind varchar(32) not null default 'normal',
  rollback_plan_id varchar(64),
  rollback_of_execution_id varchar(64),
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

create table if not exists execution_step (
  id varchar(64) primary key,
  tenant_id varchar(64) not null references tenant(id) on delete cascade,
  execution_id varchar(64) not null references execution_run(id) on delete cascade,
  plan_step_id varchar(64) not null references automation_plan_step(id) on delete cascade,
  sequence_no int not null,
  name varchar(160) not null,
  action_type varchar(32) not null,
  target_type varchar(32) not null,
  status varchar(32) not null default 'queued',
  action_payload jsonb not null default '{}'::jsonb,
  command_snapshot text,
  output text,
  error_message text,
  attempt int not null default 1,
  timeout_seconds int not null default 300,
  artifact_count int not null default 0,
  started_at timestamptz,
  finished_at timestamptz,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

create table execution_artifact (
  id varchar(64) primary key,
  tenant_id varchar(64) not null references tenant(id) on delete cascade,
  execution_id varchar(64) not null references execution_run(id) on delete cascade,
  step_id varchar(64),
  artifact_type varchar(32) not null default 'text',
  name varchar(160) not null,
  content text,
  metadata jsonb not null default '{}'::jsonb,
  created_at timestamptz not null default now()
);

-- Phase 5.4: Webhook
create table if not exists webhook_connector (
  id varchar(64) primary key,
  tenant_id varchar(64) not null references tenant(id) on delete cascade,
  name varchar(160) not null,
  description text,
  base_url varchar(512) not null,
  default_method varchar(16) not null default 'POST',
  default_headers jsonb not null default '{}'::jsonb,
  sensitive_headers jsonb not null default '["authorization", "x-api-key", "x-token", "cookie"]'::jsonb,
  enabled boolean not null default true,
  created_by varchar(64) not null,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

create table if not exists webhook_execution_policy (
  id varchar(64) primary key,
  tenant_id varchar(64) not null references tenant(id) on delete cascade,
  connector_id varchar(64) not null references webhook_connector(id) on delete cascade,
  allow_live boolean not null default false,
  allow_check_execution boolean not null default true,
  allowed_hosts jsonb not null default '[]'::jsonb,
  allowed_methods jsonb not null default '["POST"]'::jsonb,
  block_private_ip boolean not null default true,
  block_localhost boolean not null default true,
  block_metadata_ip boolean not null default true,
  max_body_bytes int not null default 32768,
  timeout_millis int not null default 5000,
  live_requires_approval boolean not null default true,
  allowed_live_risk_levels jsonb not null default '["low", "medium"]'::jsonb,
  allowed_credential_ref_ids jsonb not null default '[]'::jsonb,
  stdout_stderr_masking_enabled boolean not null default true,
  live_timeout_seconds int not null default 1800,
  enabled boolean not null default true,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

-- Phase 5.5: Ansible
create table if not exists ansible_inventory (
  id varchar(64) primary key,
  tenant_id varchar(64) not null references tenant(id) on delete cascade,
  name varchar(160) not null,
  description text,
  inventory_type varchar(32) not null default 'inline',
  inline_inventory text,
  file_ref varchar(512),
  enabled boolean not null default true,
  created_by varchar(64) not null,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

create table if not exists ansible_playbook (
  id varchar(64) primary key,
  tenant_id varchar(64) not null references tenant(id) on delete cascade,
  name varchar(160) not null,
  description text,
  playbook_ref varchar(512),
  playbook_content text,
  variables_schema jsonb not null default '{}'::jsonb,
  allowed_tags jsonb not null default '[]'::jsonb,
  enabled boolean not null default true,
  created_by varchar(64) not null,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

create table if not exists ansible_execution_policy (
  id varchar(64) primary key,
  tenant_id varchar(64) not null references tenant(id) on delete cascade,
  playbook_id varchar(64) not null references ansible_playbook(id) on delete cascade,
  allow_live boolean not null default false,
  default_check_mode boolean not null default true,
  allow_check_execution boolean not null default true,
  allowed_inventory_ids jsonb not null default '[]'::jsonb,
  allowed_extra_vars jsonb not null default '[]'::jsonb,
  max_extra_vars_bytes int not null default 32768,
  timeout_seconds int not null default 1800,
  live_requires_approval boolean not null default true,
  allowed_live_risk_levels jsonb not null default '["low", "medium"]'::jsonb,
  allowed_credential_ref_ids jsonb not null default '[]'::jsonb,
  stdout_stderr_masking_enabled boolean not null default true,
  live_timeout_seconds int not null default 1800,
  enabled boolean not null default true,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

create table ansible_credential_ref (
  id varchar(64) primary key,
  tenant_id varchar(64) not null references tenant(id) on delete cascade,
  name varchar(160) not null,
  description text,
  credential_type varchar(32) not null default 'ssh_key',
  secret_ref varchar(512) not null,
  enabled boolean not null default true,
  created_by varchar(64) not null,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

-- Phase 5.8: Rollback
create table if not exists rollback_plan (
  id varchar(64) primary key,
  tenant_id varchar(64) not null references tenant(id) on delete cascade,
  incident_id varchar(64) not null,
  source_plan_id varchar(64) not null,
  source_execution_id varchar(64) not null references execution_run(id) on delete cascade,
  status varchar(32) not null default 'draft',
  risk_level varchar(32) not null default 'high',
  reason text,
  required_approvals int not null default 1,
  approved_count int not null default 0,
  rejected_count int not null default 0,
  created_by varchar(64) not null,
  submitted_by varchar(64),
  submitted_at timestamptz,
  decided_at timestamptz,
  approval_snapshot jsonb not null default '{}'::jsonb,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

create table if not exists rollback_plan_step (
  id varchar(64) primary key,
  tenant_id varchar(64) not null references tenant(id) on delete cascade,
  rollback_plan_id varchar(64) not null references rollback_plan(id) on delete cascade,
  source_step_id varchar(64),
  step_order int not null,
  title varchar(240) not null,
  description text,
  action_type varchar(64) not null,
  target_type varchar(64) not null,
  action_payload jsonb not null default '{}'::jsonb,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

create table if not exists rollback_decision (
  id varchar(64) primary key,
  tenant_id varchar(64) not null references tenant(id) on delete cascade,
  rollback_plan_id varchar(64) not null references rollback_plan(id) on delete cascade,
  reviewer varchar(64) not null,
  decision varchar(16) not null,
  comment text,
  created_at timestamptz not null default now()
);

-- Phase 5.8: Report & Audit
create table if not exists execution_report (
  id varchar(64) primary key,
  tenant_id varchar(64) not null references tenant(id) on delete cascade,
  execution_id varchar(64) not null references execution_run(id) on delete cascade,
  report_type varchar(32) not null default 'standard',
  status varchar(32) not null default 'generated',
  title varchar(240) not null,
  summary text,
  markdown text not null,
  generated_by varchar(64) not null,
  generated_at timestamptz not null default now(),
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

create table if not exists execution_report_section (
  id varchar(64) primary key,
  tenant_id varchar(64) not null references tenant(id) on delete cascade,
  report_id varchar(64) not null references execution_report(id) on delete cascade,
  section_order int not null,
  section_type varchar(64) not null,
  title varchar(240) not null,
  content text not null,
  metadata jsonb not null default '{}'::jsonb,
  created_at timestamptz not null default now()
);

create table if not exists execution_verification (
  id varchar(64) primary key,
  tenant_id varchar(64) not null references tenant(id) on delete cascade,
  execution_id varchar(64) not null references execution_run(id) on delete cascade,
  step_id varchar(64),
  verification_type varchar(32) not null,
  target_type varchar(64) not null,
  target_id varchar(128),
  status varchar(32) not null,
  summary text not null,
  details jsonb not null default '{}'::jsonb,
  created_by varchar(64) not null,
  created_at timestamptz not null default now()
);

create table if not exists execution_audit_event (
  id varchar(64) primary key,
  tenant_id varchar(64) not null references tenant(id) on delete cascade,
  execution_id varchar(64) not null references execution_run(id) on delete cascade,
  step_id varchar(64),
  event_type varchar(64) not null,
  actor varchar(64) not null,
  summary text not null,
  payload jsonb not null default '{}'::jsonb,
  created_at timestamptz not null default now()
);

-- Phase 6.0: Postmortem
create table if not exists postmortem_report (
  id varchar(64) primary key,
  tenant_id varchar(64) not null references tenant(id) on delete cascade,
  incident_id varchar(64) not null,
  status varchar(32) not null default 'generated',
  severity varchar(32),
  title varchar(240) not null,
  summary text not null,
  impact text,
  root_cause text,
  detection text,
  resolution text,
  prevention text,
  markdown text not null,
  source_snapshot jsonb not null default '{}'::jsonb,
  generated_by varchar(64) not null,
  generated_at timestamptz not null default now(),
  reviewed_by varchar(64),
  reviewed_at timestamptz,
  archived_at timestamptz,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

create table if not exists postmortem_section (
  id varchar(64) primary key,
  tenant_id varchar(64) not null references tenant(id) on delete cascade,
  postmortem_id varchar(64) not null references postmortem_report(id) on delete cascade,
  section_order int not null,
  section_type varchar(64) not null,
  title varchar(240) not null,
  content text not null,
  metadata jsonb not null default '{}'::jsonb,
  created_at timestamptz not null default now()
);

create table if not exists postmortem_action_item (
  id varchar(64) primary key,
  tenant_id varchar(64) not null references tenant(id) on delete cascade,
  postmortem_id varchar(64) not null references postmortem_report(id) on delete cascade,
  title varchar(240) not null,
  description text,
  owner varchar(64),
  priority varchar(32) not null default 'medium',
  status varchar(32) not null default 'open',
  due_date date,
  source_type varchar(64) not null default 'manual',
  source_ref_id varchar(64),
  created_by varchar(64) not null,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

-- Phase 6.1: Incident Case Library
create table if not exists incident_case (
  id varchar(64) primary key,
  tenant_id varchar(64) not null references tenant(id) on delete cascade,
  source_postmortem_id varchar(64) not null references postmortem_report(id) on delete cascade,
  incident_id varchar(64) not null,
  status varchar(32) not null default 'draft',
  severity varchar(32),
  title varchar(240) not null,
  summary text not null,
  root_cause text,
  resolution text,
  prevention text,
  quality_score int not null default 60,
  created_by varchar(64) not null,
  reviewed_by varchar(64),
  published_at timestamptz,
  archived_at timestamptz,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

create table if not exists incident_case_symptom (
  id varchar(64) primary key,
  tenant_id varchar(64) not null references tenant(id) on delete cascade,
  case_id varchar(64) not null references incident_case(id) on delete cascade,
  symptom_type varchar(64) not null,
  name varchar(160) not null,
  description text,
  created_at timestamptz not null default now()
);

create table if not exists incident_case_resolution_step (
  id varchar(64) primary key,
  tenant_id varchar(64) not null references tenant(id) on delete cascade,
  case_id varchar(64) not null references incident_case(id) on delete cascade,
  step_order int not null,
  title varchar(240) not null,
  description text,
  action_type varchar(64),
  source_ref_id varchar(64),
  created_at timestamptz not null default now()
);

create table if not exists incident_case_tag (
  id varchar(64) primary key,
  tenant_id varchar(64) not null references tenant(id) on delete cascade,
  case_id varchar(64) not null references incident_case(id) on delete cascade,
  tag varchar(64) not null,
  created_at timestamptz not null default now()
);

-- Phase 6.2: Knowledge Base
create table if not exists kb_document (
  id varchar(64) primary key,
  tenant_id varchar(64) not null references tenant(id) on delete cascade,
  source_type varchar(64) not null,
  source_id varchar(64) not null,
  title varchar(240) not null,
  status varchar(32) not null default 'indexed',
  metadata jsonb not null default '{}'::jsonb,
  indexed_at timestamptz not null default now(),
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

create table if not exists kb_chunk (
  id varchar(64) primary key,
  tenant_id varchar(64) not null references tenant(id) on delete cascade,
  document_id varchar(64) not null references kb_document(id) on delete cascade,
  chunk_order int not null,
  source_type varchar(64) not null,
  source_id varchar(64) not null,
  title varchar(240) not null,
  content text not null,
  content_hash varchar(128) not null,
  token_estimate int not null default 0,
  embedding jsonb not null default '[]'::jsonb,
  metadata jsonb not null default '{}'::jsonb,
  status varchar(32) not null default 'indexed',
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

create table if not exists kb_search_log (
  id varchar(64) primary key,
  tenant_id varchar(64) not null references tenant(id) on delete cascade,
  query text not null,
  source_types jsonb not null default '[]'::jsonb,
  tags jsonb not null default '[]'::jsonb,
  top_k int not null default 5,
  result_count int not null default 0,
  created_by varchar(64) not null,
  created_at timestamptz not null default now()
);

-- Phase 6.3: Agent Eval
create table if not exists agent_eval_dataset (
  id varchar(64) primary key,
  tenant_id varchar(64) not null references tenant(id) on delete cascade,
  name varchar(160) not null,
  description text,
  status varchar(32) not null default 'draft',
  created_by varchar(64) not null,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

create table if not exists agent_eval_case (
  id varchar(64) primary key,
  tenant_id varchar(64) not null references tenant(id) on delete cascade,
  dataset_id varchar(64) not null references agent_eval_dataset(id) on delete cascade,
  source_type varchar(64) not null default 'manual',
  source_id varchar(64),
  incident_id varchar(64),
  title varchar(240) not null,
  severity varchar(32),
  input_context text not null,
  expected_root_cause text,
  expected_keywords jsonb not null default '[]'::jsonb,
  expected_actions jsonb not null default '[]'::jsonb,
  forbidden_actions jsonb not null default '[]'::jsonb,
  tags jsonb not null default '[]'::jsonb,
  enabled boolean not null default true,
  created_by varchar(64) not null,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

create table if not exists agent_prompt_profile (
  id varchar(64) primary key,
  tenant_id varchar(64) not null references tenant(id) on delete cascade,
  name varchar(160) not null,
  version varchar(64) not null,
  status varchar(32) not null default 'active',
  system_prompt text not null,
  diagnosis_prompt_template text not null,
  metadata jsonb not null default '{}'::jsonb,
  created_by varchar(64) not null,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

create table if not exists agent_eval_run (
  id varchar(64) primary key,
  tenant_id varchar(64) not null references tenant(id) on delete cascade,
  dataset_id varchar(64) not null references agent_eval_dataset(id) on delete cascade,
  prompt_profile_id varchar(64),
  mode varchar(64) not null default 'offline_latest_diagnosis',
  status varchar(32) not null default 'running',
  total_cases int not null default 0,
  passed_cases int not null default 0,
  failed_cases int not null default 0,
  average_score numeric(6,4) not null default 0,
  summary text,
  created_by varchar(64) not null,
  started_at timestamptz not null default now(),
  finished_at timestamptz,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

create table if not exists agent_eval_case_result (
  id varchar(64) primary key,
  tenant_id varchar(64) not null references tenant(id) on delete cascade,
  run_id varchar(64) not null references agent_eval_run(id) on delete cascade,
  case_id varchar(64) not null references agent_eval_case(id) on delete cascade,
  actual_diagnosis_id varchar(64),
  actual_summary text,
  actual_root_cause text,
  actual_recommendation text,
  score numeric(6,4) not null default 0,
  root_cause_score numeric(6,4) not null default 0,
  keyword_score numeric(6,4) not null default 0,
  action_score numeric(6,4) not null default 0,
  safety_score numeric(6,4) not null default 0,
  passed boolean not null default false,
  details jsonb not null default '{}'::jsonb,
  created_at timestamptz not null default now()
);

-- Phase 8.0: SaaS
create table if not exists tenant_quota_policy (
  id varchar(64) primary key,
  tenant_id varchar(64) not null references tenant(id) on delete cascade,
  status varchar(32) not null default 'active',
  public_api_requests_per_minute int not null default 600,
  internal_agent_requests_per_minute int not null default 1200,
  max_active_incidents int not null default 1000,
  max_agent_memories int not null default 10000,
  max_kb_documents int not null default 10000,
  max_monthly_ai_diagnoses int not null default 100000,
  created_by varchar(64) not null default 'system',
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

create table if not exists tenant_security_event (
  id varchar(64) primary key,
  tenant_id varchar(64),
  event_type varchar(64) not null,
  severity varchar(32) not null default 'medium',
  actor varchar(128) not null default 'unknown',
  request_path varchar(512),
  remote_addr varchar(128),
  summary text not null,
  metadata jsonb not null default '{}'::jsonb,
  created_at timestamptz not null default now()
);

-- Phase 7.3: Agent Memory
create table if not exists agent_memory (
  id varchar(64) primary key,
  tenant_id varchar(64) not null references tenant(id) on delete cascade,
  scope_type varchar(64) not null default 'tenant',
  scope_id varchar(128),
  memory_type varchar(64) not null,
  source_type varchar(64) not null default 'diagnosis',
  source_id varchar(128),
  title varchar(240) not null,
  content text not null,
  tags jsonb not null default '[]'::jsonb,
  confidence numeric(6,4) not null default 0,
  status varchar(32) not null default 'active',
  created_by varchar(64) not null,
  expires_at timestamptz,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

create table if not exists agent_memory_event (
  id varchar(64) primary key,
  tenant_id varchar(64) not null references tenant(id) on delete cascade,
  memory_id varchar(64),
  event_type varchar(64) not null,
  summary text not null,
  actor varchar(64) not null,
  metadata jsonb not null default '{}'::jsonb,
  created_at timestamptz not null default now()
);

-- Phase 8.1: Plugin
create table if not exists plugin_descriptor (
  id varchar(64) primary key,
  plugin_key varchar(128) not null,
  name varchar(160) not null,
  version varchar(64) not null,
  description text,
  provider varchar(128) not null default 'builtin',
  status varchar(32) not null default 'active',
  manifest_json jsonb not null default '{}'::jsonb,
  capabilities_json jsonb not null default '{}'::jsonb,
  created_by varchar(64) not null default 'system',
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

create table if not exists tenant_plugin (
  id varchar(64) primary key,
  tenant_id varchar(64) not null references tenant(id) on delete cascade,
  plugin_id varchar(64) not null references plugin_descriptor(id) on delete cascade,
  status varchar(32) not null default 'enabled',
  config_json jsonb not null default '{}'::jsonb,
  enabled_by varchar(64) not null default 'system',
  enabled_at timestamptz not null default now(),
  disabled_by varchar(64),
  disabled_at timestamptz,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

create table if not exists tenant_plugin_tool_policy (
  id varchar(64) primary key,
  tenant_id varchar(64) not null references tenant(id) on delete cascade,
  tenant_plugin_id varchar(64) not null references tenant_plugin(id) on delete cascade,
  plugin_id varchar(64) not null references plugin_descriptor(id) on delete cascade,
  tool_key varchar(128) not null,
  status varchar(32) not null default 'allowed',
  risk_level varchar(32) not null default 'low',
  created_by varchar(64) not null default 'system',
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

create table if not exists plugin_event (
  id varchar(64) primary key,
  tenant_id varchar(64),
  plugin_id varchar(64),
  tenant_plugin_id varchar(64),
  event_type varchar(64) not null,
  summary text not null,
  actor varchar(64) not null default 'system',
  metadata jsonb not null default '{}'::jsonb,
  created_at timestamptz not null default now()
);

-- Phase Z4: Diagnosis Evidence
create table if not exists diagnosis_evidence (
  id varchar(64) primary key,
  tenant_id varchar(64) not null,
  incident_id varchar(64) not null,
  evidence_key varchar(256) not null,
  source varchar(64) not null,
  evidence_type varchar(64) not null,
  title varchar(256) not null,
  summary text not null,
  time_range_start timestamptz,
  time_range_end timestamptz,
  confidence numeric(6,4) not null default 0,
  payload_json jsonb not null default '{}'::jsonb,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

-- Phase Z7: Incident Report
create table if not exists incident_report (
  id varchar(64) primary key,
  tenant_id varchar(64) not null,
  incident_id varchar(64) not null,
  version_no int not null,
  report_type varchar(64) not null default 'incident_markdown',
  format varchar(32) not null default 'markdown',
  title varchar(256) not null,
  markdown_content text not null,
  snapshot_json jsonb not null default '{}'::jsonb,
  created_by varchar(128),
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

-- =============================================================================
-- (2) ALTER TABLE ADD COLUMN (idempotent)
-- =============================================================================

alter table datasource add column if not exists last_sync_at timestamptz;

alter table alert_event add column if not exists updated_at timestamptz;
alter table alert_event add column if not exists aggregation_key varchar(512);

alter table incident add column if not exists aggregation_key varchar(512);
alter table incident add column if not exists last_seen_at timestamptz;
alter table incident add column if not exists alert_count integer not null default 0;

alter table execution_step add column if not exists attempt int not null default 1;
alter table execution_step add column if not exists timeout_seconds int not null default 300;
alter table execution_step add column if not exists artifact_count int not null default 0;

alter table ansible_execution_policy add column if not exists allow_check_execution boolean not null default true;

-- =============================================================================
-- (3) ALTER TABLE DROP + ADD CONSTRAINT
-- =============================================================================

alter table automation_plan drop constraint if exists ck_automation_plan_status;
alter table automation_plan add constraint ck_automation_plan_status
  check (status in ('draft', 'pending_approval', 'approved', 'rejected',
    'superseded', 'cancelled', 'executing', 'succeeded', 'failed'));

alter table execution_run drop constraint if exists ck_execution_run_status;
alter table execution_run add constraint ck_execution_run_status
  check (status in ('queued', 'running', 'succeeded', 'failed', 'cancelled', 'timeout'));

alter table execution_run drop constraint if exists ck_execution_run_mode;
alter table execution_run add constraint ck_execution_run_mode
  check (mode in ('dry_run', 'live'));

alter table execution_run drop constraint if exists ck_execution_run_attempt;
alter table execution_run add constraint ck_execution_run_attempt
  check (attempt >= 1 and max_attempts >= 1 and attempt <= max_attempts);

alter table execution_run drop constraint if exists ck_execution_run_timeout_seconds;
alter table execution_run add constraint ck_execution_run_timeout_seconds
  check (timeout_seconds >= 30 and timeout_seconds <= 86400);

alter table execution_run drop constraint if exists ck_execution_run_kind;
alter table execution_run add constraint ck_execution_run_kind
  check (execution_kind in ('normal', 'rollback'));

alter table execution_step drop constraint if exists ck_execution_step_status;
alter table execution_step add constraint ck_execution_step_status
  check (status in ('queued', 'running', 'succeeded', 'failed', 'skipped', 'cancelled', 'timeout'));

alter table execution_step drop constraint if exists ck_execution_step_attempt;
alter table execution_step add constraint ck_execution_step_attempt
  check (attempt >= 1);

alter table execution_step drop constraint if exists ck_execution_step_timeout_seconds;
alter table execution_step add constraint ck_execution_step_timeout_seconds
  check (timeout_seconds >= 1 and timeout_seconds <= 86400);

alter table execution_step drop constraint if exists ck_execution_step_artifact_count;
alter table execution_step add constraint ck_execution_step_artifact_count
  check (artifact_count >= 0);

alter table execution_artifact drop constraint if exists ck_execution_artifact_type;
alter table execution_artifact add constraint ck_execution_artifact_type
  check (artifact_type in ('text', 'json', 'log'));

alter table webhook_execution_policy drop constraint if exists ck_webhook_policy_max_body_bytes;
alter table webhook_execution_policy add constraint ck_webhook_policy_max_body_bytes
  check (max_body_bytes >= 0 and max_body_bytes <= 1048576);

alter table webhook_execution_policy drop constraint if exists ck_webhook_policy_timeout_millis;
alter table webhook_execution_policy add constraint ck_webhook_policy_timeout_millis
  check (timeout_millis >= 100 and timeout_millis <= 60000);

alter table webhook_execution_policy drop constraint if exists ck_ansible_policy_live_timeout_seconds;
alter table webhook_execution_policy add constraint ck_ansible_policy_live_timeout_seconds
  check (live_timeout_seconds >= 30 and live_timeout_seconds <= 86400);

alter table ansible_execution_policy drop constraint if exists ck_ansible_policy_max_extra_vars_bytes;
alter table ansible_execution_policy add constraint ck_ansible_policy_max_extra_vars_bytes
  check (max_extra_vars_bytes >= 0 and max_extra_vars_bytes <= 1048576);

alter table ansible_execution_policy drop constraint if exists ck_ansible_policy_timeout_seconds;
alter table ansible_execution_policy add constraint ck_ansible_policy_timeout_seconds
  check (timeout_seconds >= 30 and timeout_seconds <= 86400);

alter table ansible_credential_ref drop constraint if exists ck_ansible_credential_type;
alter table ansible_credential_ref add constraint ck_ansible_credential_type
  check (credential_type in ('ssh_key', 'password', 'token', 'vault_ref'));

alter table rollback_plan drop constraint if exists ck_rollback_plan_status;
alter table rollback_plan add constraint ck_rollback_plan_status
  check (status in ('draft', 'pending_approval', 'approved', 'rejected',
    'executing', 'succeeded', 'failed', 'cancelled'));

alter table rollback_plan drop constraint if exists ck_rollback_plan_risk_level;
alter table rollback_plan add constraint ck_rollback_plan_risk_level
  check (risk_level in ('low', 'medium', 'high', 'critical'));

alter table rollback_plan drop constraint if exists ck_rollback_plan_required_approvals;
alter table rollback_plan add constraint ck_rollback_plan_required_approvals
  check (required_approvals >= 1 and required_approvals <= 5);

alter table rollback_decision drop constraint if exists ck_rollback_decision;
alter table rollback_decision add constraint ck_rollback_decision
  check (decision in ('approve', 'reject'));

alter table execution_report drop constraint if exists ck_execution_report_type;
alter table execution_report add constraint ck_execution_report_type
  check (report_type in ('standard', 'rollback', 'audit'));

alter table execution_report drop constraint if exists ck_execution_report_status;
alter table execution_report add constraint ck_execution_report_status
  check (status in ('generated', 'superseded'));

alter table execution_verification drop constraint if exists ck_execution_verification_type;
alter table execution_verification add constraint ck_execution_verification_type
  check (verification_type in ('before', 'after', 'manual', 'post'));

alter table execution_verification drop constraint if exists ck_execution_verification_status;
alter table execution_verification add constraint ck_execution_verification_status
  check (status in ('passed', 'failed', 'warn', 'skipped'));

alter table postmortem_report drop constraint if exists ck_postmortem_report_status;
alter table postmortem_report add constraint ck_postmortem_report_status
  check (status in ('draft', 'generated', 'reviewed', 'archived'));

alter table postmortem_report drop constraint if exists ck_postmortem_report_severity;
alter table postmortem_report add constraint ck_postmortem_report_severity
  check (severity is null or severity in ('info', 'low', 'medium', 'high', 'critical'));

alter table postmortem_action_item drop constraint if exists ck_postmortem_action_item_priority;
alter table postmortem_action_item add constraint ck_postmortem_action_item_priority
  check (priority in ('low', 'medium', 'high', 'critical'));

alter table postmortem_action_item drop constraint if exists ck_postmortem_action_item_status;
alter table postmortem_action_item add constraint ck_postmortem_action_item_status
  check (status in ('open', 'in_progress', 'done', 'cancelled'));

alter table postmortem_action_item drop constraint if exists ck_postmortem_action_item_source_type;
alter table postmortem_action_item add constraint ck_postmortem_action_item_source_type
  check (source_type in ('manual', 'rca', 'ai_diagnosis', 'execution', 'rollback'));

alter table incident_case drop constraint if exists ck_incident_case_status;
alter table incident_case add constraint ck_incident_case_status
  check (status in ('draft', 'published', 'archived'));

alter table incident_case drop constraint if exists ck_incident_case_severity;
alter table incident_case add constraint ck_incident_case_severity
  check (severity is null or severity in ('info', 'low', 'medium', 'high', 'critical'));

alter table incident_case drop constraint if exists ck_incident_case_quality_score;
alter table incident_case add constraint ck_incident_case_quality_score
  check (quality_score >= 0 and quality_score <= 100);

alter table kb_document drop constraint if exists ck_kb_document_source_type;
alter table kb_document add constraint ck_kb_document_source_type
  check (source_type in ('incident_case', 'postmortem', 'manual'));

alter table kb_document drop constraint if exists ck_kb_document_status;
alter table kb_document add constraint ck_kb_document_status
  check (status in ('indexed', 'stale', 'disabled'));

alter table kb_chunk drop constraint if exists ck_kb_chunk_source_type;
alter table kb_chunk add constraint ck_kb_chunk_source_type
  check (source_type in ('incident_case', 'postmortem', 'manual'));

alter table kb_chunk drop constraint if exists ck_kb_chunk_status;
alter table kb_chunk add constraint ck_kb_chunk_status
  check (status in ('indexed', 'stale', 'disabled'));

alter table kb_search_log drop constraint if exists ck_kb_search_log_top_k;
alter table kb_search_log add constraint ck_kb_search_log_top_k
  check (top_k >= 1 and top_k <= 50);

alter table agent_eval_dataset drop constraint if exists ck_agent_eval_dataset_status;
alter table agent_eval_dataset add constraint ck_agent_eval_dataset_status
  check (status in ('draft', 'active', 'archived'));

alter table agent_eval_case drop constraint if exists ck_agent_eval_case_source_type;
alter table agent_eval_case add constraint ck_agent_eval_case_source_type
  check (source_type in ('manual', 'incident_case', 'postmortem'));

alter table agent_eval_case drop constraint if exists ck_agent_eval_case_severity;
alter table agent_eval_case add constraint ck_agent_eval_case_severity
  check (severity is null or severity in ('info', 'low', 'medium', 'high', 'critical'));

alter table agent_prompt_profile drop constraint if exists ck_agent_prompt_profile_status;
alter table agent_prompt_profile add constraint ck_agent_prompt_profile_status
  check (status in ('active', 'archived'));

alter table agent_eval_run drop constraint if exists ck_agent_eval_run_mode;
alter table agent_eval_run add constraint ck_agent_eval_run_mode
  check (mode in ('mock', 'offline_latest_diagnosis'));

alter table agent_eval_run drop constraint if exists ck_agent_eval_run_status;
alter table agent_eval_run add constraint ck_agent_eval_run_status
  check (status in ('running', 'succeeded', 'failed'));

alter table agent_memory drop constraint if exists ck_agent_memory_scope_type;
alter table agent_memory add constraint ck_agent_memory_scope_type
  check (scope_type in ('tenant', 'service', 'incident', 'asset'));

alter table agent_memory drop constraint if exists ck_agent_memory_type;
alter table agent_memory add constraint ck_agent_memory_type
  check (memory_type in ('incident_summary', 'root_cause_pattern',
    'service_behavior', 'runbook_hint', 'safety_note'));

alter table agent_memory drop constraint if exists ck_agent_memory_source_type;
alter table agent_memory add constraint ck_agent_memory_source_type
  check (source_type in ('diagnosis', 'postmortem', 'incident_case', 'manual'));

alter table agent_memory drop constraint if exists ck_agent_memory_status;
alter table agent_memory add constraint ck_agent_memory_status
  check (status in ('active', 'archived'));

alter table agent_memory drop constraint if exists ck_agent_memory_confidence;
alter table agent_memory add constraint ck_agent_memory_confidence
  check (confidence >= 0 and confidence <= 1);

alter table agent_memory_event drop constraint if exists ck_agent_memory_event_type;
alter table agent_memory_event add constraint ck_agent_memory_event_type
  check (event_type in ('created', 'searched', 'archived', 'rejected_by_policy'));

alter table tenant_quota_policy drop constraint if exists ck_tenant_quota_policy_status;
alter table tenant_quota_policy add constraint ck_tenant_quota_policy_status
  check (status in ('active', 'disabled'));

alter table tenant_quota_policy drop constraint if exists ck_tenant_quota_public_api_rpm;
alter table tenant_quota_policy add constraint ck_tenant_quota_public_api_rpm
  check (public_api_requests_per_minute > 0);

alter table tenant_quota_policy drop constraint if exists ck_tenant_quota_internal_agent_rpm;
alter table tenant_quota_policy add constraint ck_tenant_quota_internal_agent_rpm
  check (internal_agent_requests_per_minute > 0);

alter table tenant_security_event drop constraint if exists ck_tenant_security_event_type;
alter table tenant_security_event add constraint ck_tenant_security_event_type
  check (event_type in ('tenant_missing', 'internal_auth_failed', 'rate_limited',
    'quota_exceeded', 'cross_tenant_denied', 'internal_auth_succeeded'));

alter table tenant_security_event drop constraint if exists ck_tenant_security_event_severity;
alter table tenant_security_event add constraint ck_tenant_security_event_severity
  check (severity in ('low', 'medium', 'high', 'critical'));

alter table plugin_descriptor drop constraint if exists ck_plugin_descriptor_status;
alter table plugin_descriptor add constraint ck_plugin_descriptor_status
  check (status in ('active', 'disabled', 'deprecated'));

alter table tenant_plugin drop constraint if exists ck_tenant_plugin_status;
alter table tenant_plugin add constraint ck_tenant_plugin_status
  check (status in ('enabled', 'disabled'));

alter table tenant_plugin_tool_policy drop constraint if exists ck_tenant_plugin_tool_policy_status;
alter table tenant_plugin_tool_policy add constraint ck_tenant_plugin_tool_policy_status
  check (status in ('allowed', 'denied'));

alter table tenant_plugin_tool_policy drop constraint if exists ck_tenant_plugin_tool_policy_risk;
alter table tenant_plugin_tool_policy add constraint ck_tenant_plugin_tool_policy_risk
  check (risk_level in ('low', 'medium', 'high', 'critical'));

alter table plugin_event drop constraint if exists ck_plugin_event_type;
alter table plugin_event add constraint ck_plugin_event_type
  check (event_type in ('plugin_registered', 'plugin_enabled', 'plugin_disabled',
    'tool_allowed', 'tool_denied', 'tool_authorized',
    'tool_denied_by_policy', 'manifest_requested'));

alter table approval_policy drop constraint if exists ck_approval_policy_risk_level;
alter table approval_policy add constraint ck_approval_policy_risk_level
  check (risk_level in ('low', 'medium', 'high', 'critical'));

alter table approval_policy drop constraint if exists ck_approval_policy_required_approvals;
alter table approval_policy add constraint ck_approval_policy_required_approvals
  check (required_approvals >= 0 and required_approvals <= 5);

alter table automation_approval drop constraint if exists ck_automation_approval_status;
alter table automation_approval add constraint ck_automation_approval_status
  check (status in ('pending', 'approved', 'rejected', 'cancelled'));

alter table automation_approval drop constraint if exists ck_automation_approval_risk_level;
alter table automation_approval add constraint ck_automation_approval_risk_level
  check (risk_level in ('low', 'medium', 'high', 'critical'));

alter table automation_approval drop constraint if exists ck_automation_approval_counts;
alter table automation_approval add constraint ck_automation_approval_counts
  check (approved_count >= 0 and rejected_count >= 0 and required_approvals >= 0);

alter table approval_decision drop constraint if exists ck_approval_decision_decision;
alter table approval_decision add constraint ck_approval_decision_decision
  check (decision in ('approve', 'reject'));

alter table runbook drop constraint if exists ck_runbook_risk_level;
alter table runbook add constraint ck_runbook_risk_level
  check (risk_level in ('low', 'medium', 'high', 'critical'));

alter table runbook_step_template drop constraint if exists ck_runbook_step_template_action_type;
alter table runbook_step_template add constraint ck_runbook_step_template_action_type
  check (action_type in ('manual', 'shell', 'ansible', 'http'));

alter table runbook_step_template drop constraint if exists ck_runbook_step_template_target_type;
alter table runbook_step_template add constraint ck_runbook_step_template_target_type
  check (target_type in ('human', 'host', 'service', 'cluster'));

alter table automation_plan drop constraint if exists ck_automation_plan_risk_level;
alter table automation_plan add constraint ck_automation_plan_risk_level
  check (risk_level in ('low', 'medium', 'high', 'critical'));

alter table automation_plan_step drop constraint if exists ck_automation_plan_step_action_type;
alter table automation_plan_step add constraint ck_automation_plan_step_action_type
  check (action_type in ('manual', 'shell', 'ansible', 'http'));

alter table automation_plan_step drop constraint if exists ck_automation_plan_step_target_type;
alter table automation_plan_step add constraint ck_automation_plan_step_target_type
  check (target_type in ('human', 'host', 'service', 'cluster'));

alter table automation_plan_step drop constraint if exists ck_automation_plan_step_status;
alter table automation_plan_step add constraint ck_automation_plan_step_status
  check (status in ('pending', 'skipped'));

alter table webhook_connector drop constraint if exists ck_webhook_connector_method;
alter table webhook_connector add constraint ck_webhook_connector_method
  check (default_method in ('GET', 'POST', 'PUT', 'PATCH', 'DELETE'));

alter table ansible_inventory drop constraint if exists ck_ansible_inventory_type;
alter table ansible_inventory add constraint ck_ansible_inventory_type
  check (inventory_type in ('inline', 'file_ref'));

-- =============================================================================
-- (4) CREATE INDEX
-- =============================================================================

create index if not exists idx_asset_tenant_type on asset(tenant_id, asset_type);

create index if not exists idx_alert_event_tenant_starts on alert_event(tenant_id, starts_at desc);
create index if not exists idx_alert_event_fingerprint on alert_event(tenant_id, fingerprint);
create index if not exists idx_alert_event_tenant_source_status on alert_event(tenant_id, source, status);
create index if not exists idx_alert_event_tenant_updated_at on alert_event(tenant_id, updated_at desc);
create index if not exists idx_alert_event_tenant_ends_at on alert_event(tenant_id, ends_at desc);
create index if not exists idx_alert_event_tenant_aggregation_key on alert_event(tenant_id, aggregation_key);
create index if not exists idx_alert_event_tenant_status_aggregation_key on alert_event(tenant_id, status, aggregation_key);

create unique index if not exists uq_asset_tenant_source_source_id
  on asset(tenant_id, source, source_id) where source_id is not null;

create unique index if not exists uq_alert_event_tenant_source_source_event_id
  on alert_event(tenant_id, source, source_event_id) where source_event_id is not null;

create index if not exists idx_incident_tenant_started on incident(tenant_id, started_at desc);
create index if not exists idx_incident_tenant_status_started on incident(tenant_id, status, started_at desc);
create index if not exists idx_incident_tenant_aggregation_key on incident(tenant_id, aggregation_key);
create index if not exists idx_incident_tenant_aggregation_key_status on incident(tenant_id, aggregation_key, status);

create unique index if not exists uq_incident_active_aggregation_key
  on incident(tenant_id, aggregation_key)
  where aggregation_key is not null
    and status in ('open', 'investigating', 'mitigating');

create index if not exists idx_incident_event_incident on incident_event(incident_id);

create unique index if not exists uq_incident_event_incident_event
  on incident_event(incident_id, event_type, event_id);

create unique index if not exists uq_incident_event_alert_once
  on incident_event(event_type, event_id) where event_type = 'alert';

create index if not exists idx_incident_timeline_incident_time
  on incident_timeline(incident_id, event_time desc);

create index if not exists idx_audit_log_created on audit_log(created_at desc);

create index if not exists idx_datasource_sync_run_ds_started
  on datasource_sync_run(datasource_id, started_at desc);

create index if not exists idx_rca_analysis_tenant_incident_created
  on rca_analysis(tenant_id, incident_id, created_at desc);

create index if not exists idx_rca_analysis_incident on rca_analysis(incident_id);

create index if not exists idx_ai_diagnosis_tenant_incident_created
  on ai_diagnosis(tenant_id, incident_id, created_at desc);

create index if not exists idx_ai_diagnosis_incident on ai_diagnosis(incident_id);

create index if not exists idx_log_event_tenant_asset_time
  on log_event(tenant_id, asset_id, occurred_at desc);

create index if not exists idx_log_event_tenant_service_time
  on log_event(tenant_id, service_name, occurred_at desc);

create index if not exists idx_log_event_tenant_severity_time
  on log_event(tenant_id, severity, occurred_at desc);

create index if not exists idx_change_event_tenant_asset_time
  on change_event(tenant_id, asset_id, occurred_at desc);

create index if not exists idx_change_event_tenant_service_time
  on change_event(tenant_id, service_name, occurred_at desc);

create index if not exists idx_change_event_tenant_time
  on change_event(tenant_id, occurred_at desc);

create unique index if not exists uq_agent_run_diagnosis on agent_run(diagnosis_id);

create index if not exists idx_agent_run_tenant_incident_created
  on agent_run(tenant_id, incident_id, created_at desc);

create index if not exists idx_agent_run_trace on agent_run(trace_id);

create index if not exists idx_agent_run_step_run_sequence
  on agent_run_step(run_id, sequence_no);

create index if not exists idx_agent_eval_result_run on agent_eval_result(run_id);

create index if not exists idx_runbook_tenant_enabled on runbook(tenant_id, enabled);
create index if not exists idx_runbook_category on runbook(category);
create index if not exists idx_runbook_step_template_runbook
  on runbook_step_template(runbook_id, sequence_no);
create index if not exists idx_automation_plan_tenant_incident_created
  on automation_plan(tenant_id, incident_id, created_at desc);
create index if not exists idx_automation_plan_incident on automation_plan(incident_id);
create index if not exists idx_automation_plan_step_plan
  on automation_plan_step(plan_id, sequence_no);

create unique index if not exists uq_approval_policy_global_risk
  on approval_policy(risk_level) where tenant_id is null;
create unique index if not exists uq_approval_policy_tenant_risk
  on approval_policy(tenant_id, risk_level) where tenant_id is not null;
create index if not exists idx_automation_approval_tenant_plan_created
  on automation_approval(tenant_id, plan_id, created_at desc);
create index if not exists idx_automation_approval_tenant_status
  on automation_approval(tenant_id, status);
create unique index if not exists uq_automation_approval_pending_plan
  on automation_approval(plan_id) where status = 'pending';
create unique index if not exists uq_approval_decision_reviewer_once
  on approval_decision(approval_id, reviewer);
create index if not exists idx_approval_decision_approval
  on approval_decision(approval_id, decided_at asc);

create index if not exists idx_execution_run_tenant_plan_created
  on execution_run(tenant_id, plan_id, created_at desc);
create index if not exists idx_execution_run_status_created
  on execution_run(status, created_at asc);
create unique index if not exists uq_execution_run_active_plan
  on execution_run(plan_id) where status in ('queued', 'running');
create index if not exists idx_execution_run_running_lease
  on execution_run(status, lease_until);
create index if not exists idx_execution_run_retry_of
  on execution_run(retry_of_execution_id);
create index if not exists idx_execution_run_approval
  on execution_run(tenant_id, approval_id);
create index if not exists idx_execution_run_rollback_plan
  on execution_run(tenant_id, rollback_plan_id);
create index if not exists idx_execution_run_rollback_of
  on execution_run(tenant_id, rollback_of_execution_id);

create index if not exists idx_execution_step_execution_sequence
  on execution_step(execution_id, sequence_no);
create index if not exists idx_execution_step_status
  on execution_step(status);

create index if not exists idx_execution_artifact_execution
  on execution_artifact(execution_id, created_at asc);
create index if not exists idx_execution_artifact_step
  on execution_artifact(step_id, created_at asc);

create index if not exists idx_webhook_connector_tenant_enabled
  on webhook_connector(tenant_id, enabled);
create unique index if not exists uq_webhook_connector_tenant_name
  on webhook_connector(tenant_id, name);
create unique index if not exists uq_webhook_policy_connector
  on webhook_execution_policy(connector_id);
create index if not exists idx_webhook_policy_tenant_enabled
  on webhook_execution_policy(tenant_id, enabled);
create index if not exists idx_ansible_policy_check_execution
  on webhook_execution_policy(tenant_id, allow_check_execution, enabled);
create index if not exists idx_ansible_policy_live
  on webhook_execution_policy(tenant_id, allow_live, live_requires_approval, enabled);

create unique index if not exists uq_ansible_inventory_tenant_name
  on ansible_inventory(tenant_id, name);
create index if not exists idx_ansible_inventory_tenant_enabled
  on ansible_inventory(tenant_id, enabled);
create unique index if not exists uq_ansible_playbook_tenant_name
  on ansible_playbook(tenant_id, name);
create index if not exists idx_ansible_playbook_tenant_enabled
  on ansible_playbook(tenant_id, enabled);
create unique index if not exists uq_ansible_policy_playbook
  on ansible_execution_policy(playbook_id);
create index if not exists idx_ansible_policy_tenant_enabled
  on ansible_execution_policy(tenant_id, enabled);
create unique index if not exists uq_ansible_credential_ref_tenant_name
  on ansible_credential_ref(tenant_id, name);
create index if not exists idx_ansible_credential_ref_tenant_enabled
  on ansible_credential_ref(tenant_id, enabled);

create index if not exists idx_rollback_plan_tenant_source_execution
  on rollback_plan(tenant_id, source_execution_id, created_at desc);
create index if not exists idx_rollback_plan_tenant_status
  on rollback_plan(tenant_id, status, created_at desc);
create index if not exists idx_rollback_plan_step_plan
  on rollback_plan_step(tenant_id, rollback_plan_id, step_order);
create unique index if not exists uq_rollback_plan_step_order
  on rollback_plan_step(tenant_id, rollback_plan_id, step_order);
create unique index if not exists uq_rollback_decision_reviewer
  on rollback_decision(tenant_id, rollback_plan_id, reviewer);
create index if not exists idx_rollback_decision_plan
  on rollback_decision(tenant_id, rollback_plan_id, created_at);

create index if not exists idx_execution_report_execution
  on execution_report(tenant_id, execution_id, generated_at desc);
create unique index if not exists uq_execution_report_section_order
  on execution_report_section(tenant_id, report_id, section_order);
create index if not exists idx_execution_report_section_report
  on execution_report_section(tenant_id, report_id, section_order);
create index if not exists idx_execution_verification_execution
  on execution_verification(tenant_id, execution_id, created_at);
create index if not exists idx_execution_audit_event_execution
  on execution_audit_event(tenant_id, execution_id, created_at);

create index if not exists idx_postmortem_report_incident
  on postmortem_report(tenant_id, incident_id, generated_at desc);
create index if not exists idx_postmortem_report_status
  on postmortem_report(tenant_id, status, generated_at desc);
create unique index if not exists uq_postmortem_section_order
  on postmortem_section(tenant_id, postmortem_id, section_order);
create index if not exists idx_postmortem_section_postmortem
  on postmortem_section(tenant_id, postmortem_id, section_order);
create index if not exists idx_postmortem_action_item_postmortem
  on postmortem_action_item(tenant_id, postmortem_id, created_at);
create index if not exists idx_postmortem_action_item_owner_status
  on postmortem_action_item(tenant_id, owner, status);

create unique index if not exists uq_incident_case_source_postmortem
  on incident_case(tenant_id, source_postmortem_id);
create index if not exists idx_incident_case_incident
  on incident_case(tenant_id, incident_id, created_at desc);
create index if not exists idx_incident_case_status_quality
  on incident_case(tenant_id, status, quality_score desc, created_at desc);
create index if not exists idx_incident_case_symptom_case
  on incident_case_symptom(tenant_id, case_id, created_at);
create unique index if not exists uq_incident_case_resolution_step_order
  on incident_case_resolution_step(tenant_id, case_id, step_order);
create index if not exists idx_incident_case_resolution_step_case
  on incident_case_resolution_step(tenant_id, case_id, step_order);
create unique index if not exists uq_incident_case_tag
  on incident_case_tag(tenant_id, case_id, tag);
create index if not exists idx_incident_case_tag_tag
  on incident_case_tag(tenant_id, tag);

create unique index if not exists uq_kb_document_source
  on kb_document(tenant_id, source_type, source_id);
create index if not exists idx_kb_document_status
  on kb_document(tenant_id, status, indexed_at desc);
create unique index if not exists uq_kb_chunk_document_order
  on kb_chunk(tenant_id, document_id, chunk_order);
create index if not exists idx_kb_chunk_document
  on kb_chunk(tenant_id, document_id, chunk_order);
create index if not exists idx_kb_chunk_source
  on kb_chunk(tenant_id, source_type, source_id);
create index if not exists idx_kb_chunk_content_hash
  on kb_chunk(tenant_id, content_hash);
create index if not exists idx_kb_search_log_tenant_created
  on kb_search_log(tenant_id, created_at desc);

create index if not exists idx_agent_eval_dataset_status
  on agent_eval_dataset(tenant_id, status, created_at desc);
create index if not exists idx_agent_eval_case_dataset
  on agent_eval_case(tenant_id, dataset_id, enabled, created_at);
create index if not exists idx_agent_eval_case_source
  on agent_eval_case(tenant_id, source_type, source_id);
create unique index if not exists uq_agent_prompt_profile_name_version
  on agent_prompt_profile(tenant_id, name, version);
create index if not exists idx_agent_prompt_profile_status
  on agent_prompt_profile(tenant_id, status, created_at desc);
create index if not exists idx_agent_eval_run_dataset
  on agent_eval_run(tenant_id, dataset_id, created_at desc);
create unique index if not exists uq_agent_eval_case_result
  on agent_eval_case_result(tenant_id, run_id, case_id);
create index if not exists idx_agent_eval_case_result_run
  on agent_eval_case_result(tenant_id, run_id, score desc);

create unique index if not exists uq_tenant_quota_policy
  on tenant_quota_policy(tenant_id);
create index if not exists idx_tenant_quota_policy_status
  on tenant_quota_policy(tenant_id, status);
create index if not exists idx_tenant_security_event_tenant
  on tenant_security_event(tenant_id, created_at desc);
create index if not exists idx_tenant_security_event_type
  on tenant_security_event(event_type, created_at desc);

create index if not exists idx_agent_memory_scope
  on agent_memory(tenant_id, scope_type, scope_id, status, created_at desc);
create index if not exists idx_agent_memory_type
  on agent_memory(tenant_id, memory_type, status, created_at desc);
create index if not exists idx_agent_memory_source
  on agent_memory(tenant_id, source_type, source_id);
create index if not exists idx_agent_memory_event_memory
  on agent_memory_event(tenant_id, memory_id, created_at desc);
create index if not exists idx_agent_memory_event_type
  on agent_memory_event(tenant_id, event_type, created_at desc);

create unique index if not exists uq_plugin_descriptor_key_version
  on plugin_descriptor(plugin_key, version);
create index if not exists idx_plugin_descriptor_key
  on plugin_descriptor(plugin_key);
create index if not exists idx_plugin_descriptor_status
  on plugin_descriptor(status, created_at desc);
create unique index if not exists uq_tenant_plugin
  on tenant_plugin(tenant_id, plugin_id);
create index if not exists idx_tenant_plugin_tenant_status
  on tenant_plugin(tenant_id, status, created_at desc);
create unique index if not exists uq_tenant_plugin_tool_policy
  on tenant_plugin_tool_policy(tenant_id, tenant_plugin_id, tool_key);
create index if not exists idx_tenant_plugin_tool_policy_lookup
  on tenant_plugin_tool_policy(tenant_id, tool_key, status);
create index if not exists idx_plugin_event_tenant
  on plugin_event(tenant_id, created_at desc);
create index if not exists idx_plugin_event_plugin
  on plugin_event(plugin_id, created_at desc);

create unique index if not exists uk_diagnosis_evidence_key
  on diagnosis_evidence(tenant_id, incident_id, evidence_key);
create index if not exists idx_diagnosis_evidence_incident
  on diagnosis_evidence(tenant_id, incident_id, created_at desc);
create index if not exists idx_diagnosis_evidence_type
  on diagnosis_evidence(tenant_id, incident_id, evidence_type);

create unique index if not exists uk_incident_report_version
  on incident_report(tenant_id, incident_id, version_no);
create index if not exists idx_incident_report_latest
  on incident_report(tenant_id, incident_id, created_at desc);
create index if not exists idx_incident_report_type
  on incident_report(tenant_id, incident_id, report_type);

-- =============================================================================
-- Seed Data: Default Approval Policies
-- =============================================================================

insert into approval_policy (id, tenant_id, risk_level, required_approvals, require_comment, enabled)
select 'ap_global_low', null, 'low', 0, false, true
where not exists (select 1 from approval_policy where tenant_id is null and risk_level = 'low');

insert into approval_policy (id, tenant_id, risk_level, required_approvals, require_comment, enabled)
select 'ap_global_medium', null, 'medium', 1, false, true
where not exists (select 1 from approval_policy where tenant_id is null and risk_level = 'medium');

insert into approval_policy (id, tenant_id, risk_level, required_approvals, require_comment, enabled)
select 'ap_global_high', null, 'high', 1, true, true
where not exists (select 1 from approval_policy where tenant_id is null and risk_level = 'high');

insert into approval_policy (id, tenant_id, risk_level, required_approvals, require_comment, enabled)
select 'ap_global_critical', null, 'critical', 2, true, true
where not exists (select 1 from approval_policy where tenant_id is null and risk_level = 'critical');
