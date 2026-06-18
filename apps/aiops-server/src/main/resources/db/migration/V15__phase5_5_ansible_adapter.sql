-- Phase 5.5: Ansible Adapter.
-- This phase stores Ansible resources and generates dry-run previews only.
-- It does not execute ansible-playbook.

create table if not exists ansible_inventory (
  id varchar(64) primary key,
  tenant_id varchar(64) not null references tenant(id) on delete cascade,
  name varchar(160) not null,
  description text,
  inventory_type varchar(32) not null default 'inline',
  inline_inventory text,
  file_ref varchar(512),
  enabled boolean not null default true,
  created_by varchar(64) not null,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  constraint ck_ansible_inventory_type
    check (inventory_type in ('inline', 'file_ref'))
);

create unique index if not exists uq_ansible_inventory_tenant_name
  on ansible_inventory(tenant_id, name);

create index if not exists idx_ansible_inventory_tenant_enabled
  on ansible_inventory(tenant_id, enabled);

create table if not exists ansible_playbook (
  id varchar(64) primary key,
  tenant_id varchar(64) not null references tenant(id) on delete cascade,
  name varchar(160) not null,
  description text,
  playbook_ref varchar(512),
  playbook_content text,
  variables_schema jsonb not null default '{}'::jsonb,
  allowed_tags jsonb not null default '[]'::jsonb,
  enabled boolean not null default true,
  created_by varchar(64) not null,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

create unique index if not exists uq_ansible_playbook_tenant_name
  on ansible_playbook(tenant_id, name);

create index if not exists idx_ansible_playbook_tenant_enabled
  on ansible_playbook(tenant_id, enabled);

create table if not exists ansible_execution_policy (
  id varchar(64) primary key,
  tenant_id varchar(64) not null references tenant(id) on delete cascade,
  playbook_id varchar(64) not null references ansible_playbook(id) on delete cascade,
  allow_live boolean not null default false,
  default_check_mode boolean not null default true,
  allowed_inventory_ids jsonb not null default '[]'::jsonb,
  allowed_extra_vars jsonb not null default '[]'::jsonb,
  max_extra_vars_bytes int not null default 32768,
  timeout_seconds int not null default 1800,
  enabled boolean not null default true,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  constraint ck_ansible_policy_max_extra_vars_bytes
    check (max_extra_vars_bytes >= 0 and max_extra_vars_bytes <= 1048576),
  constraint ck_ansible_policy_timeout_seconds
    check (timeout_seconds >= 30 and timeout_seconds <= 86400)
);

create unique index if not exists uq_ansible_policy_playbook
  on ansible_execution_policy(playbook_id);

create index if not exists idx_ansible_policy_tenant_enabled
  on ansible_execution_policy(tenant_id, enabled);
