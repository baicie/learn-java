create table if not exists alert_ingest_rule (
  id varchar(64) primary key,
  tenant_id varchar(64) not null references tenant(id) on delete cascade,
  name varchar(128) not null,
  source varchar(64) not null,
  enabled boolean not null default true,
  severity_mapping jsonb not null default '{}'::jsonb,
  label_mapping jsonb not null default '{}'::jsonb,
  default_env varchar(64),
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

create table if not exists alert_dedup_rule (
  id varchar(64) primary key,
  tenant_id varchar(64) not null references tenant(id) on delete cascade,
  name varchar(128) not null,
  source varchar(64) not null,
  fingerprint_template varchar(512) not null,
  enabled boolean not null default true,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

create index if not exists idx_alert_ingest_rule_tenant_source on alert_ingest_rule(tenant_id, source, enabled);
create index if not exists idx_alert_dedup_rule_tenant_source on alert_dedup_rule(tenant_id, source, enabled);
