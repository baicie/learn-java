alter table asset add column if not exists description text;
alter table asset add column if not exists site varchar(128);
alter table asset add column if not exists owner_team varchar(128);
alter table asset add column if not exists criticality varchar(32) not null default 'normal';
alter table asset add column if not exists last_seen_at timestamptz;
alter table asset add column if not exists deleted_at timestamptz;
alter table asset add column if not exists version bigint not null default 0;

create table asset_source_link (
  id varchar(64) primary key,
  tenant_id varchar(64) not null references tenant(id),
  asset_id varchar(64) not null references asset(id) on delete cascade,
  source_type varchar(64) not null,
  source_instance_id varchar(128) not null,
  datasource_id varchar(64) references datasource(id) on delete set null,
  external_id varchar(256) not null,
  ingestion_channel varchar(32) not null,
  sync_status varchar(32) not null default 'active',
  raw_payload jsonb not null default '{}'::jsonb,
  first_seen_at timestamptz not null default now(),
  last_seen_at timestamptz not null default now(),
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  constraint ck_asset_source_link_channel
    check (ingestion_channel in ('manual', 'csv', 'webhook', 'sync')),
  constraint ck_asset_source_link_status
    check (sync_status in ('active', 'stale', 'missing', 'error', 'archived'))
);

create unique index uq_asset_source_link_external
  on asset_source_link(tenant_id, source_type, source_instance_id, external_id);
create index idx_asset_source_link_asset
  on asset_source_link(tenant_id, asset_id);
create index idx_asset_source_link_datasource
  on asset_source_link(tenant_id, datasource_id, last_seen_at desc);

create table asset_identity (
  id varchar(64) primary key,
  tenant_id varchar(64) not null references tenant(id),
  asset_id varchar(64) not null references asset(id) on delete cascade,
  source_link_id varchar(64) references asset_source_link(id) on delete set null,
  identity_type varchar(64) not null,
  scope_key varchar(256) not null default 'global',
  identity_value varchar(512) not null,
  normalized_value varchar(512) not null,
  strength varchar(16) not null,
  verified boolean not null default false,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  constraint ck_asset_identity_strength check (strength in ('strong', 'weak'))
);

create unique index uq_asset_identity_strong
  on asset_identity(tenant_id, identity_type, scope_key, normalized_value)
  where strength = 'strong';
create index idx_asset_identity_asset
  on asset_identity(tenant_id, asset_id);
create index idx_asset_identity_weak_lookup
  on asset_identity(tenant_id, identity_type, scope_key, normalized_value)
  where strength = 'weak';

create table asset_import_job (
  id varchar(64) primary key,
  tenant_id varchar(64) not null references tenant(id),
  source_instance_id varchar(128) not null,
  file_name varchar(256) not null,
  content_sha256 varchar(64) not null,
  status varchar(32) not null default 'previewed',
  total_rows integer not null default 0,
  valid_rows integer not null default 0,
  invalid_rows integer not null default 0,
  created_rows integer not null default 0,
  updated_rows integer not null default 0,
  conflict_rows integer not null default 0,
  created_by varchar(64) not null,
  confirmed_by varchar(64),
  confirmed_at timestamptz,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  constraint ck_asset_import_job_status
    check (status in ('previewed', 'confirmed', 'running', 'success', 'partial', 'failed', 'cancelled')),
  constraint uq_asset_import_job_checksum
    unique (tenant_id, source_instance_id, content_sha256)
);

create table asset_import_row (
  id varchar(64) primary key,
  tenant_id varchar(64) not null references tenant(id),
  job_id varchar(64) not null references asset_import_job(id) on delete cascade,
  row_number integer not null,
  external_id varchar(256),
  normalized_payload jsonb not null default '{}'::jsonb,
  validation_status varchar(32) not null,
  resolution_action varchar(32),
  resolved_asset_id varchar(64) references asset(id) on delete set null,
  error_codes jsonb not null default '[]'::jsonb,
  created_at timestamptz not null default now(),
  constraint uq_asset_import_row_number unique (tenant_id, job_id, row_number),
  constraint ck_asset_import_row_validation
    check (validation_status in ('valid', 'invalid', 'conflict')),
  constraint ck_asset_import_row_action
    check (resolution_action is null or resolution_action in ('create', 'update', 'link', 'skip'))
);

create index idx_asset_import_job_tenant_created
  on asset_import_job(tenant_id, created_at desc);
create index idx_asset_import_row_job_status
  on asset_import_row(tenant_id, job_id, validation_status, row_number);

create index if not exists idx_asset_active_tenant_type
  on asset(tenant_id, asset_type, updated_at desc)
  where deleted_at is null;
