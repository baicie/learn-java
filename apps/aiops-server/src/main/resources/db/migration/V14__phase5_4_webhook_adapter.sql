-- Phase 5.4: Webhook Adapter.
-- Webhook execution is performed only by aiops-runner.
-- Live webhook is disabled by default and guarded by policy.

create table if not exists webhook_connector (
  id varchar(64) primary key,
  tenant_id varchar(64) not null references tenant(id) on delete cascade,
  name varchar(160) not null,
  description text,
  base_url varchar(512) not null,
  default_method varchar(16) not null default 'POST',
  default_headers jsonb not null default '{}'::jsonb,
  sensitive_headers jsonb not null default '["authorization", "x-api-key", "x-token", "cookie"]'::jsonb,
  enabled boolean not null default true,
  created_by varchar(64) not null,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  constraint ck_webhook_connector_method
    check (default_method in ('GET', 'POST', 'PUT', 'PATCH', 'DELETE'))
);

create index if not exists idx_webhook_connector_tenant_enabled
  on webhook_connector(tenant_id, enabled);

create unique index if not exists uq_webhook_connector_tenant_name
  on webhook_connector(tenant_id, name);

create table if not exists webhook_execution_policy (
  id varchar(64) primary key,
  tenant_id varchar(64) not null references tenant(id) on delete cascade,
  connector_id varchar(64) not null references webhook_connector(id) on delete cascade,
  allow_live boolean not null default false,
  allowed_hosts jsonb not null default '[]'::jsonb,
  allowed_methods jsonb not null default '["POST"]'::jsonb,
  block_private_ip boolean not null default true,
  block_localhost boolean not null default true,
  block_metadata_ip boolean not null default true,
  max_body_bytes int not null default 32768,
  timeout_millis int not null default 5000,
  enabled boolean not null default true,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  constraint ck_webhook_policy_max_body_bytes
    check (max_body_bytes >= 0 and max_body_bytes <= 1048576),
  constraint ck_webhook_policy_timeout_millis
    check (timeout_millis >= 100 and timeout_millis <= 60000)
);

create unique index if not exists uq_webhook_policy_connector
  on webhook_execution_policy(connector_id);

create index if not exists idx_webhook_policy_tenant_enabled
  on webhook_execution_policy(tenant_id, enabled);
