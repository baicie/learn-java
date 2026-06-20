-- Phase 6.3: Agent Eval & Prompt Regression.
-- This phase stores deterministic eval datasets/runs/results.
-- It does not call real LLM by default and does not add execution capability.

create table if not exists agent_eval_dataset (
  id varchar(64) primary key,
  tenant_id varchar(64) not null references tenant(id) on delete cascade,
  name varchar(160) not null,
  description text,
  status varchar(32) not null default 'draft',
  created_by varchar(64) not null,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  constraint ck_agent_eval_dataset_status
    check (status in ('draft', 'active', 'archived'))
);

create index if not exists idx_agent_eval_dataset_status
  on agent_eval_dataset(tenant_id, status, created_at desc);

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
  updated_at timestamptz not null default now(),
  constraint ck_agent_eval_case_source_type
    check (source_type in ('manual', 'incident_case', 'postmortem')),
  constraint ck_agent_eval_case_severity
    check (severity is null or severity in ('info', 'low', 'medium', 'high', 'critical'))
);

create index if not exists idx_agent_eval_case_dataset
  on agent_eval_case(tenant_id, dataset_id, enabled, created_at);

create index if not exists idx_agent_eval_case_source
  on agent_eval_case(tenant_id, source_type, source_id);

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
  updated_at timestamptz not null default now(),
  constraint ck_agent_prompt_profile_status
    check (status in ('active', 'archived')),
  constraint uq_agent_prompt_profile_name_version
    unique (tenant_id, name, version)
);

create index if not exists idx_agent_prompt_profile_status
  on agent_prompt_profile(tenant_id, status, created_at desc);

create table if not exists agent_eval_run (
  id varchar(64) primary key,
  tenant_id varchar(64) not null references tenant(id) on delete cascade,
  dataset_id varchar(64) not null references agent_eval_dataset(id) on delete cascade,
  prompt_profile_id varchar(64) references agent_prompt_profile(id) on delete set null,
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
  updated_at timestamptz not null default now(),
  constraint ck_agent_eval_run_mode
    check (mode in ('mock', 'offline_latest_diagnosis')),
  constraint ck_agent_eval_run_status
    check (status in ('running', 'succeeded', 'failed'))
);

create index if not exists idx_agent_eval_run_dataset
  on agent_eval_run(tenant_id, dataset_id, created_at desc);

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
  created_at timestamptz not null default now(),
  constraint uq_agent_eval_case_result unique (tenant_id, run_id, case_id)
);

create index if not exists idx_agent_eval_case_result_run
  on agent_eval_case_result(tenant_id, run_id, score desc);
