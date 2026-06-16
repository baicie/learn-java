-- Phase 4.3: Evidence stores for AI diagnosis.
-- log_event mirrors structured application logs; change_event captures manual or
-- automated changes. Both are queryable by the agent evidence API to enrich the
-- AI diagnosis with timeline-aligned signal.

create table if not exists log_event (
  id varchar(64) primary key,
  tenant_id varchar(64) not null references tenant(id),
  asset_id varchar(64),
  service_name varchar(255),
  severity varchar(32) not null default 'info',
  message text not null,
  attributes jsonb not null default '{}'::jsonb,
  occurred_at timestamptz not null,
  created_at timestamptz not null default now()
);

create index if not exists idx_log_event_tenant_asset_time
  on log_event(tenant_id, asset_id, occurred_at desc);

create index if not exists idx_log_event_tenant_service_time
  on log_event(tenant_id, service_name, occurred_at desc);

create index if not exists idx_log_event_tenant_severity_time
  on log_event(tenant_id, severity, occurred_at desc);

create table if not exists change_event (
  id varchar(64) primary key,
  tenant_id varchar(64) not null references tenant(id),
  asset_id varchar(64),
  service_name varchar(255),
  change_type varchar(64) not null,
  title varchar(512) not null,
  description text,
  source varchar(64) not null default 'manual',
  operator varchar(128),
  risk_level varchar(32) not null default 'medium',
  attributes jsonb not null default '{}'::jsonb,
  occurred_at timestamptz not null,
  created_at timestamptz not null default now()
);

create index if not exists idx_change_event_tenant_asset_time
  on change_event(tenant_id, asset_id, occurred_at desc);

create index if not exists idx_change_event_tenant_service_time
  on change_event(tenant_id, service_name, occurred_at desc);

create index if not exists idx_change_event_tenant_time
  on change_event(tenant_id, occurred_at desc);
