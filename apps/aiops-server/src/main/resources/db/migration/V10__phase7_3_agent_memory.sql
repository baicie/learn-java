-- Phase 7.3: Agent Memory.
-- Controlled, tenant-scoped, auditable memory.
-- This phase does not add execution capability and does not call runner.

create table if not exists agent_memory (
  id varchar(64) primary key,
  tenant_id varchar(64) not null references tenant(id) on delete cascade,
  scope_type varchar(64) not null default 'tenant',
  scope_id varchar(128),
  memory_type varchar(64) not null,
  source_type varchar(64) not null default 'diagnosis',
  source_id varchar(128),
  title varchar(240) not null,
  content text not null,
  tags jsonb not null default '[]'::jsonb,
  confidence numeric(6,4) not null default 0,
  status varchar(32) not null default 'active',
  created_by varchar(64) not null,
  expires_at timestamptz,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),

  constraint ck_agent_memory_scope_type
    check (scope_type in ('tenant', 'service', 'incident', 'asset')),
  constraint ck_agent_memory_type
    check (memory_type in (
      'incident_summary',
      'root_cause_pattern',
      'service_behavior',
      'runbook_hint',
      'safety_note'
    )),
  constraint ck_agent_memory_source_type
    check (source_type in ('diagnosis', 'postmortem', 'incident_case', 'manual')),
  constraint ck_agent_memory_status
    check (status in ('active', 'archived')),
  constraint ck_agent_memory_confidence
    check (confidence >= 0 and confidence <= 1)
);

create index if not exists idx_agent_memory_scope
  on agent_memory(tenant_id, scope_type, scope_id, status, created_at desc);

create index if not exists idx_agent_memory_type
  on agent_memory(tenant_id, memory_type, status, created_at desc);

create index if not exists idx_agent_memory_source
  on agent_memory(tenant_id, source_type, source_id);

create table if not exists agent_memory_event (
  id varchar(64) primary key,
  tenant_id varchar(64) not null references tenant(id) on delete cascade,
  memory_id varchar(64) references agent_memory(id) on delete cascade,
  event_type varchar(64) not null,
  summary text not null,
  actor varchar(64) not null,
  metadata jsonb not null default '{}'::jsonb,
  created_at timestamptz not null default now(),

  constraint ck_agent_memory_event_type
    check (event_type in ('created', 'searched', 'archived', 'rejected_by_policy'))
);

create index if not exists idx_agent_memory_event_memory
  on agent_memory_event(tenant_id, memory_id, created_at desc);

create index if not exists idx_agent_memory_event_type
  on agent_memory_event(tenant_id, event_type, created_at desc);
