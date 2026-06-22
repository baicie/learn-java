-- Phase Z4: Zabbix evidence collector.
-- diagnosis_evidence stores structured evidence collected from Zabbix
-- (and future sources: prometheus / otel / rum / log) for an incident.
-- evidence_key is unique per (tenant, incident) so re-collecting the
-- same evidence is idempotent (ON CONFLICT DO UPDATE).

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
  confidence numeric(6, 4) not null default 0,
  payload_json jsonb not null default '{}'::jsonb,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

create unique index if not exists uk_diagnosis_evidence_key
  on diagnosis_evidence(tenant_id, incident_id, evidence_key);

create index if not exists idx_diagnosis_evidence_incident
  on diagnosis_evidence(tenant_id, incident_id, created_at desc);

create index if not exists idx_diagnosis_evidence_type
  on diagnosis_evidence(tenant_id, incident_id, evidence_type);
