-- Phase 4: AI diagnosis agent output.
-- Each row is one LangGraph graph run, persisted by Java aiops-server after
-- the agent returns a structured DiagnoseResponse. The latest row is what the
-- UI displays under "AI Diagnosis Agent".

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

create index if not exists idx_ai_diagnosis_tenant_incident_created
  on ai_diagnosis(tenant_id, incident_id, created_at desc);

create index if not exists idx_ai_diagnosis_incident
  on ai_diagnosis(incident_id);
