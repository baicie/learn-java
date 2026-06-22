-- Phase 8.1: Plugin System.
-- Safe plugin descriptor, tenant enablement, frontend manifest and agent tool allowlist.
-- This phase does not dynamically load code and does not add execution capability.

create table if not exists plugin_descriptor (
  id varchar(64) primary key,
  plugin_key varchar(128) not null,
  name varchar(160) not null,
  version varchar(64) not null,
  description text,
  provider varchar(128) not null default 'builtin',
  status varchar(32) not null default 'active',
  manifest_json jsonb not null default '{}'::jsonb,
  capabilities_json jsonb not null default '{}'::jsonb,
  created_by varchar(64) not null default 'system',
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),

  constraint uq_plugin_descriptor_key_version unique (plugin_key, version),
  constraint ck_plugin_descriptor_status check (status in ('active', 'disabled', 'deprecated'))
);

create index if not exists idx_plugin_descriptor_key
  on plugin_descriptor(plugin_key);

create index if not exists idx_plugin_descriptor_status
  on plugin_descriptor(status, created_at desc);

create table if not exists tenant_plugin (
  id varchar(64) primary key,
  tenant_id varchar(64) not null references tenant(id) on delete cascade,
  plugin_id varchar(64) not null references plugin_descriptor(id) on delete cascade,
  status varchar(32) not null default 'enabled',
  config_json jsonb not null default '{}'::jsonb,
  enabled_by varchar(64) not null default 'system',
  enabled_at timestamptz not null default now(),
  disabled_by varchar(64),
  disabled_at timestamptz,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),

  constraint uq_tenant_plugin unique (tenant_id, plugin_id),
  constraint ck_tenant_plugin_status check (status in ('enabled', 'disabled'))
);

create index if not exists idx_tenant_plugin_tenant_status
  on tenant_plugin(tenant_id, status, created_at desc);

create table if not exists tenant_plugin_tool_policy (
  id varchar(64) primary key,
  tenant_id varchar(64) not null references tenant(id) on delete cascade,
  tenant_plugin_id varchar(64) not null references tenant_plugin(id) on delete cascade,
  plugin_id varchar(64) not null references plugin_descriptor(id) on delete cascade,
  tool_key varchar(128) not null,
  status varchar(32) not null default 'allowed',
  risk_level varchar(32) not null default 'low',
  created_by varchar(64) not null default 'system',
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),

  constraint uq_tenant_plugin_tool_policy unique (tenant_id, tenant_plugin_id, tool_key),
  constraint ck_tenant_plugin_tool_policy_status check (status in ('allowed', 'denied')),
  constraint ck_tenant_plugin_tool_policy_risk check (risk_level in ('low', 'medium', 'high', 'critical'))
);

create index if not exists idx_tenant_plugin_tool_policy_lookup
  on tenant_plugin_tool_policy(tenant_id, tool_key, status);

create table if not exists plugin_event (
  id varchar(64) primary key,
  tenant_id varchar(64),
  plugin_id varchar(64),
  tenant_plugin_id varchar(64),
  event_type varchar(64) not null,
  summary text not null,
  actor varchar(64) not null default 'system',
  metadata jsonb not null default '{}'::jsonb,
  created_at timestamptz not null default now(),

  constraint ck_plugin_event_type check (event_type in (
    'plugin_registered',
    'plugin_enabled',
    'plugin_disabled',
    'tool_allowed',
    'tool_denied',
    'tool_authorized',
    'tool_denied_by_policy',
    'manifest_requested'
  ))
);

create index if not exists idx_plugin_event_tenant
  on plugin_event(tenant_id, created_at desc);

create index if not exists idx_plugin_event_plugin
  on plugin_event(plugin_id, created_at desc);
