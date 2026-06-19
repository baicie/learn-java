-- Phase 6.0: Postmortem Report.
-- This phase does not add execution capability.
-- It generates structured incident postmortem reports from existing incident,
-- RCA, AI diagnosis, execution, rollback, and timeline data.

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
  updated_at timestamptz not null default now(),
  constraint ck_postmortem_report_status
    check (status in ('draft', 'generated', 'reviewed', 'archived')),
  constraint ck_postmortem_report_severity
    check (severity is null or severity in ('info', 'low', 'medium', 'high', 'critical'))
);

create index if not exists idx_postmortem_report_incident
  on postmortem_report(tenant_id, incident_id, generated_at desc);

create index if not exists idx_postmortem_report_status
  on postmortem_report(tenant_id, status, generated_at desc);

create table if not exists postmortem_section (
  id varchar(64) primary key,
  tenant_id varchar(64) not null references tenant(id) on delete cascade,
  postmortem_id varchar(64) not null references postmortem_report(id) on delete cascade,
  section_order int not null,
  section_type varchar(64) not null,
  title varchar(240) not null,
  content text not null,
  metadata jsonb not null default '{}'::jsonb,
  created_at timestamptz not null default now(),
  constraint uq_postmortem_section_order
    unique (tenant_id, postmortem_id, section_order)
);

create index if not exists idx_postmortem_section_postmortem
  on postmortem_section(tenant_id, postmortem_id, section_order);

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
  updated_at timestamptz not null default now(),
  constraint ck_postmortem_action_item_priority
    check (priority in ('low', 'medium', 'high', 'critical')),
  constraint ck_postmortem_action_item_status
    check (status in ('open', 'in_progress', 'done', 'cancelled')),
  constraint ck_postmortem_action_item_source_type
    check (source_type in ('manual', 'rca', 'ai_diagnosis', 'execution', 'rollback'))
);

create index if not exists idx_postmortem_action_item_postmortem
  on postmortem_action_item(tenant_id, postmortem_id, created_at);

create index if not exists idx_postmortem_action_item_owner_status
  on postmortem_action_item(tenant_id, owner, status);
