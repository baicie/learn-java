-- Phase 4.4: Agent run trace + eval result observability.
-- Each ai_diagnosis row produced by the Python agent can attach one
-- agent_run, with detail steps in agent_run_step and per-check eval rows
-- in agent_eval_result. These are persisted by Java aiops-server after
-- the agent returns response.raw.agentRun / response.raw.agentEval.

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

create index if not exists idx_agent_run_tenant_incident_created
  on agent_run(tenant_id, incident_id, created_at desc);

create index if not exists idx_agent_run_trace
  on agent_run(trace_id);

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

create index if not exists idx_agent_run_step_run_sequence
  on agent_run_step(run_id, sequence_no);

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

create index if not exists idx_agent_eval_result_run
  on agent_eval_result(run_id);
