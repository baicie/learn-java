-- Phase Z7: incident markdown report generation and storage.

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

create unique index if not exists uk_incident_report_version
  on incident_report(tenant_id, incident_id, version_no);

create index if not exists idx_incident_report_latest
  on incident_report(tenant_id, incident_id, created_at desc);

create index if not exists idx_incident_report_type
  on incident_report(tenant_id, incident_id, report_type);
