-- Phase 5.8: Execution Report & Audit.
-- This phase does not add new execution capability.
-- It stores execution reports, verification records, and execution audit events.

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
  updated_at timestamptz not null default now(),
  constraint ck_execution_report_type
    check (report_type in ('standard', 'rollback', 'audit')),
  constraint ck_execution_report_status
    check (status in ('generated', 'superseded'))
);

create index if not exists idx_execution_report_execution
  on execution_report(tenant_id, execution_id, generated_at desc);

create table if not exists execution_report_section (
  id varchar(64) primary key,
  tenant_id varchar(64) not null references tenant(id) on delete cascade,
  report_id varchar(64) not null references execution_report(id) on delete cascade,
  section_order int not null,
  section_type varchar(64) not null,
  title varchar(240) not null,
  content text not null,
  metadata jsonb not null default '{}'::jsonb,
  created_at timestamptz not null default now(),
  constraint uq_execution_report_section_order
    unique (tenant_id, report_id, section_order)
);

create index if not exists idx_execution_report_section_report
  on execution_report_section(tenant_id, report_id, section_order);

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
  created_at timestamptz not null default now(),
  constraint ck_execution_verification_type
    check (verification_type in ('before', 'after', 'manual', 'post')),
  constraint ck_execution_verification_status
    check (status in ('passed', 'failed', 'warn', 'skipped'))
);

create index if not exists idx_execution_verification_execution
  on execution_verification(tenant_id, execution_id, created_at);

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

create index if not exists idx_execution_audit_event_execution
  on execution_audit_event(tenant_id, execution_id, created_at);
