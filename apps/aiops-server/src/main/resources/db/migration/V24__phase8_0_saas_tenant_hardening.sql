-- Phase 8.0: SaaS Multi-tenant Hardening.
-- Adds baseline tenant quota policy and security event audit.
-- This phase does not add runner execution capability.

create table if not exists tenant_quota_policy (
  id varchar(64) primary key,
  tenant_id varchar(64) not null references tenant(id) on delete cascade,
  status varchar(32) not null default 'active',

  public_api_requests_per_minute int not null default 600,
  internal_agent_requests_per_minute int not null default 1200,

  max_active_incidents int not null default 1000,
  max_agent_memories int not null default 10000,
  max_kb_documents int not null default 10000,
  max_monthly_ai_diagnoses int not null default 100000,

  created_by varchar(64) not null default 'system',
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),

  constraint uq_tenant_quota_policy unique (tenant_id),
  constraint ck_tenant_quota_policy_status
    check (status in ('active', 'disabled')),
  constraint ck_tenant_quota_public_api_rpm
    check (public_api_requests_per_minute > 0),
  constraint ck_tenant_quota_internal_agent_rpm
    check (internal_agent_requests_per_minute > 0)
);

create index if not exists idx_tenant_quota_policy_status
  on tenant_quota_policy(tenant_id, status);

create table if not exists tenant_security_event (
  id varchar(64) primary key,
  tenant_id varchar(64),
  event_type varchar(64) not null,
  severity varchar(32) not null default 'medium',
  actor varchar(128) not null default 'unknown',
  request_path varchar(512),
  remote_addr varchar(128),
  summary text not null,
  metadata jsonb not null default '{}'::jsonb,
  created_at timestamptz not null default now(),

  constraint ck_tenant_security_event_type
    check (event_type in (
      'tenant_missing',
      'internal_auth_failed',
      'rate_limited',
      'quota_exceeded',
      'cross_tenant_denied',
      'internal_auth_succeeded'
    )),
  constraint ck_tenant_security_event_severity
    check (severity in ('low', 'medium', 'high', 'critical'))
);

create index if not exists idx_tenant_security_event_tenant
  on tenant_security_event(tenant_id, created_at desc);

create index if not exists idx_tenant_security_event_type
  on tenant_security_event(event_type, created_at desc);
