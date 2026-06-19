-- Phase 6.1: Incident Case Library.
-- This phase converts postmortem reports into reusable structured incident cases.
-- It does not add vector search, embeddings, or agent memory.
-- does not add execution capability.

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
  updated_at timestamptz not null default now(),
  constraint ck_incident_case_status
    check (status in ('draft', 'published', 'archived')),
  constraint ck_incident_case_severity
    check (severity is null or severity in ('info', 'low', 'medium', 'high', 'critical')),
  constraint ck_incident_case_quality_score
    check (quality_score >= 0 and quality_score <= 100)
);

create unique index if not exists uq_incident_case_source_postmortem
  on incident_case(tenant_id, source_postmortem_id);

create index if not exists idx_incident_case_incident
  on incident_case(tenant_id, incident_id, created_at desc);

create index if not exists idx_incident_case_status_quality
  on incident_case(tenant_id, status, quality_score desc, created_at desc);

create table if not exists incident_case_symptom (
  id varchar(64) primary key,
  tenant_id varchar(64) not null references tenant(id) on delete cascade,
  case_id varchar(64) not null references incident_case(id) on delete cascade,
  symptom_type varchar(64) not null,
  name varchar(160) not null,
  description text,
  created_at timestamptz not null default now()
);

create index if not exists idx_incident_case_symptom_case
  on incident_case_symptom(tenant_id, case_id, created_at);

create table if not exists incident_case_resolution_step (
  id varchar(64) primary key,
  tenant_id varchar(64) not null references tenant(id) on delete cascade,
  case_id varchar(64) not null references incident_case(id) on delete cascade,
  step_order int not null,
  title varchar(240) not null,
  description text,
  action_type varchar(64),
  source_ref_id varchar(64),
  created_at timestamptz not null default now(),
  constraint uq_incident_case_resolution_step_order
    unique (tenant_id, case_id, step_order)
);

create index if not exists idx_incident_case_resolution_step_case
  on incident_case_resolution_step(tenant_id, case_id, step_order);

create table if not exists incident_case_tag (
  id varchar(64) primary key,
  tenant_id varchar(64) not null references tenant(id) on delete cascade,
  case_id varchar(64) not null references incident_case(id) on delete cascade,
  tag varchar(64) not null,
  created_at timestamptz not null default now(),
  constraint uq_incident_case_tag unique (tenant_id, case_id, tag)
);

create index if not exists idx_incident_case_tag_tag
  on incident_case_tag(tenant_id, tag);
