-- Phase 3: RCA rule engine and evidence chain.
-- Stores RCA analyses produced by the rules engine; per-incident history is
-- append-only and the latest row is the one shown to users.

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

create index if not exists idx_rca_analysis_tenant_incident_created
  on rca_analysis(tenant_id, incident_id, created_at desc);

create index if not exists idx_rca_analysis_incident
  on rca_analysis(incident_id);
