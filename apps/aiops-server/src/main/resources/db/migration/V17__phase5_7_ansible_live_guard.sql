-- Phase 5.7: Ansible live execution with approval guard.
-- Allows controlled ansible-playbook live execution when:
-- 1. execution_run.mode = live
-- 2. approval snapshot is present
-- 3. ansible policy allow_live = true
-- 4. live risk level is allowed
-- 5. runner live-enabled = true

alter table execution_run
  add column approval_id varchar(64),
  add column approval_snapshot jsonb not null default '{}'::jsonb,
  add column plan_risk_level varchar(32),
  add column live_guard_passed_at timestamptz;

create index idx_execution_run_approval
  on execution_run(tenant_id, approval_id);

create table ansible_credential_ref (
  id varchar(64) primary key,
  tenant_id varchar(64) not null references tenant(id) on delete cascade,
  name varchar(160) not null,
  description text,
  credential_type varchar(32) not null default 'ssh_key',
  secret_ref varchar(512) not null,
  enabled boolean not null default true,
  created_by varchar(64) not null,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  constraint ck_ansible_credential_type
    check (credential_type in ('ssh_key', 'password', 'token', 'vault_ref')));

create unique index uq_ansible_credential_ref_tenant_name
  on ansible_credential_ref(tenant_id, name);

create index idx_ansible_credential_ref_tenant_enabled
  on ansible_credential_ref(tenant_id, enabled);

alter table ansible_execution_policy
  add column live_requires_approval boolean not null default true,
  add column allowed_live_risk_levels jsonb not null default '["low", "medium"]'::jsonb,
  add column allowed_credential_ref_ids jsonb not null default '[]'::jsonb,
  add column stdout_stderr_masking_enabled boolean not null default true,
  add column live_timeout_seconds int not null default 1800;

alter table ansible_execution_policy
  add constraint ck_ansible_policy_live_timeout_seconds
    check (live_timeout_seconds >= 30 and live_timeout_seconds <= 86400);

create index idx_ansible_policy_live
  on ansible_execution_policy(tenant_id, allow_live, live_requires_approval, enabled);
