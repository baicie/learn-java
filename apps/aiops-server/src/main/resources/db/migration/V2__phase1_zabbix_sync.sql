create table if not exists datasource_sync_run (
  id varchar(64) primary key,
  tenant_id varchar(64) not null references tenant(id),
  datasource_id varchar(64) not null references datasource(id) on delete cascade,
  sync_type varchar(64) not null default 'manual',
  status varchar(32) not null default 'running',
  message text,
  stats_json jsonb not null default '{}'::jsonb,
  started_at timestamptz not null default now(),
  finished_at timestamptz,
  created_by varchar(64)
);

alter table datasource add column if not exists last_sync_at timestamptz;

create index if not exists idx_datasource_sync_run_ds_started
  on datasource_sync_run(datasource_id, started_at desc);

create unique index if not exists uq_asset_tenant_source_source_id
  on asset(tenant_id, source, source_id)
  where source_id is not null;

create unique index if not exists uq_alert_tenant_source_source_event_id
  on alert_event(tenant_id, source, source_event_id)
  where source_event_id is not null;
