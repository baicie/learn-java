-- Phase 5.0: Runbook recommendation and AutomationPlan draft.
-- This phase only creates draft plans. It does not execute any action.

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
  updated_at timestamptz not null default now(),
  constraint ck_runbook_risk_level
    check (risk_level in ('low', 'medium', 'high', 'critical'))
);

create index if not exists idx_runbook_tenant_enabled
  on runbook(tenant_id, enabled);

create index if not exists idx_runbook_category
  on runbook(category);

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
  created_at timestamptz not null default now(),
  constraint ck_runbook_step_template_action_type
    check (action_type in ('manual', 'shell', 'ansible', 'http')),
  constraint ck_runbook_step_template_target_type
    check (target_type in ('human', 'host', 'service', 'cluster')),
  constraint uq_runbook_step_template_sequence
    unique (runbook_id, sequence_no)
);

create index if not exists idx_runbook_step_template_runbook
  on runbook_step_template(runbook_id, sequence_no);

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
  updated_at timestamptz not null default now(),
  constraint ck_automation_plan_status
    check (status in ('draft', 'superseded', 'cancelled')),
  constraint ck_automation_plan_risk_level
    check (risk_level in ('low', 'medium', 'high', 'critical'))
);

create index if not exists idx_automation_plan_tenant_incident_created
  on automation_plan(tenant_id, incident_id, created_at desc);

create index if not exists idx_automation_plan_incident
  on automation_plan(incident_id);

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
  created_at timestamptz not null default now(),
  constraint ck_automation_plan_step_action_type
    check (action_type in ('manual', 'shell', 'ansible', 'http')),
  constraint ck_automation_plan_step_target_type
    check (target_type in ('human', 'host', 'service', 'cluster')),
  constraint ck_automation_plan_step_status
    check (status in ('pending', 'skipped')),
  constraint uq_automation_plan_step_sequence
    unique (plan_id, sequence_no)
);

create index if not exists idx_automation_plan_step_plan
  on automation_plan_step(plan_id, sequence_no);
