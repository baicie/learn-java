create table if not exists tenant (
  id varchar(64) primary key,
  code varchar(64) not null unique,
  name varchar(128) not null,
  status varchar(32) not null default 'active',
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

create table if not exists sys_user (
  id varchar(64) primary key,
  tenant_id varchar(64) not null references tenant(id),
  username varchar(64) not null unique,
  display_name varchar(128) not null,
  email varchar(128),
  password_hash varchar(255) not null,
  status varchar(32) not null default 'active',
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

create table if not exists sys_role (
  id varchar(64) primary key,
  code varchar(64) not null unique,
  name varchar(128) not null,
  created_at timestamptz not null default now()
);

create table if not exists sys_permission (
  id varchar(64) primary key,
  code varchar(128) not null unique,
  name varchar(128) not null,
  created_at timestamptz not null default now()
);

create table if not exists sys_user_role (
  user_id varchar(64) not null references sys_user(id) on delete cascade,
  role_id varchar(64) not null references sys_role(id) on delete cascade,
  primary key(user_id, role_id)
);

create table if not exists sys_role_permission (
  role_id varchar(64) not null references sys_role(id) on delete cascade,
  permission_id varchar(64) not null references sys_permission(id) on delete cascade,
  primary key(role_id, permission_id)
);

create table if not exists datasource (
  id varchar(64) primary key,
  tenant_id varchar(64) not null references tenant(id),
  type varchar(64) not null,
  name varchar(128) not null,
  status varchar(32) not null default 'inactive',
  config_json jsonb not null default '{}'::jsonb,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

create table if not exists asset (
  id varchar(64) primary key,
  tenant_id varchar(64) not null references tenant(id),
  asset_type varchar(64) not null,
  name varchar(256) not null,
  display_name varchar(256),
  source varchar(64) not null default 'manual',
  source_id varchar(256),
  env varchar(64),
  ip varchar(64),
  tags jsonb not null default '{}'::jsonb,
  status varchar(32) not null default 'active',
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

create index if not exists idx_asset_tenant_type on asset(tenant_id, asset_type);

create table if not exists asset_relation (
  id varchar(64) primary key,
  tenant_id varchar(64) not null references tenant(id),
  from_asset_id varchar(64) not null references asset(id) on delete cascade,
  to_asset_id varchar(64) not null references asset(id) on delete cascade,
  relation_type varchar(64) not null,
  confidence numeric(5,4) not null default 1.0,
  source varchar(64) not null default 'manual',
  created_at timestamptz not null default now()
);

create table if not exists alert_event (
  id varchar(64) primary key,
  tenant_id varchar(64) not null references tenant(id),
  source varchar(64) not null,
  source_event_id varchar(256),
  severity varchar(32) not null default 'info',
  title varchar(512) not null,
  description text,
  asset_id varchar(64),
  entity_type varchar(64),
  entity_name varchar(256),
  labels jsonb not null default '{}'::jsonb,
  starts_at timestamptz not null default now(),
  ends_at timestamptz,
  status varchar(32) not null default 'open',
  raw_payload jsonb not null default '{}'::jsonb,
  fingerprint varchar(512) not null,
  created_at timestamptz not null default now()
);

create index if not exists idx_alert_event_tenant_starts on alert_event(tenant_id, starts_at desc);
create index if not exists idx_alert_event_fingerprint on alert_event(tenant_id, fingerprint);

create table if not exists incident (
  id varchar(64) primary key,
  tenant_id varchar(64) not null references tenant(id),
  title varchar(512) not null,
  summary text,
  severity varchar(32) not null default 'low',
  status varchar(32) not null default 'open',
  source varchar(64) not null default 'system',
  primary_asset_id varchar(64),
  suspected_root_cause text,
  confidence numeric(5,4),
  impact_score numeric(10,4) not null default 0,
  started_at timestamptz not null default now(),
  detected_at timestamptz not null default now(),
  resolved_at timestamptz,
  owner_user_id varchar(64),
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

create index if not exists idx_incident_tenant_started on incident(tenant_id, started_at desc);

create table if not exists incident_event (
  id varchar(64) primary key,
  incident_id varchar(64) not null references incident(id) on delete cascade,
  event_type varchar(64) not null,
  event_id varchar(64) not null,
  relation_type varchar(64) not null default 'related',
  occurred_at timestamptz not null default now()
);

create table if not exists incident_timeline (
  id varchar(64) primary key,
  incident_id varchar(64) not null references incident(id) on delete cascade,
  event_time timestamptz not null,
  event_type varchar(64) not null,
  title varchar(512) not null,
  description text,
  source varchar(64) not null default 'system',
  payload jsonb not null default '{}'::jsonb
);

create table if not exists audit_log (
  id varchar(64) primary key,
  tenant_id varchar(64),
  actor_user_id varchar(64),
  action varchar(128) not null,
  target_type varchar(64),
  target_id varchar(64),
  detail_json jsonb not null default '{}'::jsonb,
  created_at timestamptz not null default now()
);

create index if not exists idx_audit_log_created on audit_log(created_at desc);
