alter table log_event add column if not exists source varchar(64) not null default 'manual';
alter table log_event add column if not exists source_event_id varchar(256);
alter table log_event add column if not exists trace_id varchar(128);
create unique index if not exists uq_log_event_source on log_event(tenant_id, source, source_event_id) where source_event_id is not null;
create index if not exists idx_log_event_trace on log_event(tenant_id, trace_id, occurred_at desc) where trace_id is not null;

alter table change_event add column if not exists source_event_id varchar(256);
create unique index if not exists uq_change_event_source on change_event(tenant_id, source, source_event_id) where source_event_id is not null;

create table trace_event (
  id varchar(64) primary key,
  tenant_id varchar(64) not null references tenant(id),
  asset_id varchar(64) references asset(id) on delete set null,
  datasource_id varchar(64) references datasource(id) on delete set null,
  source_event_id varchar(256) not null,
  service_name varchar(255) not null,
  trace_id varchar(128) not null,
  span_id varchar(128),
  attributes jsonb not null default '{}'::jsonb,
  occurred_at timestamptz not null,
  created_at timestamptz not null default now(),
  unique(tenant_id, datasource_id, source_event_id)
);
create index idx_trace_event_entity_time on trace_event(tenant_id, asset_id, service_name, occurred_at desc);
create index idx_trace_event_trace on trace_event(tenant_id, trace_id);

create table telemetry_metric (
  id varchar(64) primary key,
  tenant_id varchar(64) not null references tenant(id),
  asset_id varchar(64) references asset(id) on delete set null,
  datasource_id varchar(64) references datasource(id) on delete set null,
  source_event_id varchar(256) not null,
  service_name varchar(255) not null,
  metric_name varchar(255) not null,
  metric_value numeric not null,
  attributes jsonb not null default '{}'::jsonb,
  occurred_at timestamptz not null,
  created_at timestamptz not null default now(),
  unique(tenant_id, datasource_id, source_event_id)
);
create index idx_telemetry_metric_entity_time on telemetry_metric(tenant_id, asset_id, service_name, occurred_at desc);

create table service_catalog (
  id varchar(64) primary key,
  tenant_id varchar(64) not null references tenant(id),
  asset_id varchar(64) not null references asset(id) on delete cascade,
  owner_team varchar(128),
  repository_url varchar(512),
  runbook_id varchar(64) references runbook(id) on delete set null,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  unique(tenant_id, asset_id)
);

create table rum_event (
  id varchar(64) primary key,
  tenant_id varchar(64) not null references tenant(id),
  asset_id varchar(64) references asset(id) on delete set null,
  datasource_id varchar(64) references datasource(id) on delete set null,
  source_event_id varchar(256) not null,
  event_type varchar(32) not null,
  page varchar(512) not null,
  session_id varchar(128),
  user_hash varchar(64),
  error_message text,
  trace_id varchar(128),
  vital_name varchar(64),
  vital_value numeric,
  attributes jsonb not null default '{}'::jsonb,
  occurred_at timestamptz not null,
  created_at timestamptz not null default now(),
  unique(tenant_id, datasource_id, source_event_id)
);
create index idx_rum_event_entity_time on rum_event(tenant_id, asset_id, page, occurred_at desc);
create index idx_rum_event_trace on rum_event(tenant_id, trace_id) where trace_id is not null;

create unique index if not exists uq_asset_relation_source
  on asset_relation(tenant_id, from_asset_id, to_asset_id, relation_type, source);
