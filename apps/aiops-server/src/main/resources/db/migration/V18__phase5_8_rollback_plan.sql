-- Phase 5.8: Rollback Plan.
-- Rollback is never automatic. A rollback plan must be created, submitted,
-- approved, and then bound to a rollback execution_run.

create table if not exists rollback_plan (
  id varchar(64) primary key,
  tenant_id varchar(64) not null references tenant(id) on delete cascade,
  incident_id varchar(64) not null,
  source_plan_id varchar(64) not null,
  source_execution_id varchar(64) not null references execution_run(id) on delete cascade,
  status varchar(32) not null default 'draft',
  risk_level varchar(32) not null default 'high',
  reason text,
  required_approvals int not null default 1,
  approved_count int not null default 0,
  rejected_count int not null default 0,
  created_by varchar(64) not null,
  submitted_by varchar(64),
  submitted_at timestamptz,
  decided_at timestamptz,
  approval_snapshot jsonb not null default '{}'::jsonb,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  constraint ck_rollback_plan_status
    check (status in (
      'draft',
      'pending_approval',
      'approved',
      'rejected',
      'executing',
      'succeeded',
      'failed',
      'cancelled'
    )),
  constraint ck_rollback_plan_risk_level
    check (risk_level in ('low', 'medium', 'high', 'critical')),
  constraint ck_rollback_plan_required_approvals
    check (required_approvals >= 1 and required_approvals <= 5)
);

create index if not exists idx_rollback_plan_tenant_source_execution
  on rollback_plan(tenant_id, source_execution_id, created_at desc);

create index if not exists idx_rollback_plan_tenant_status
  on rollback_plan(tenant_id, status, created_at desc);

create table if not exists rollback_plan_step (
  id varchar(64) primary key,
  tenant_id varchar(64) not null references tenant(id) on delete cascade,
  rollback_plan_id varchar(64) not null references rollback_plan(id) on delete cascade,
  source_step_id varchar(64),
  step_order int not null,
  title varchar(240) not null,
  description text,
  action_type varchar(64) not null,
  target_type varchar(64) not null,
  action_payload jsonb not null default '{}'::jsonb,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  constraint uq_rollback_plan_step_order unique (tenant_id, rollback_plan_id, step_order)
);

create index if not exists idx_rollback_plan_step_plan
  on rollback_plan_step(tenant_id, rollback_plan_id, step_order);

create table if not exists rollback_decision (
  id varchar(64) primary key,
  tenant_id varchar(64) not null references tenant(id) on delete cascade,
  rollback_plan_id varchar(64) not null references rollback_plan(id) on delete cascade,
  reviewer varchar(64) not null,
  decision varchar(16) not null,
  comment text,
  created_at timestamptz not null default now(),
  constraint ck_rollback_decision
    check (decision in ('approve', 'reject')),
  constraint uq_rollback_decision_reviewer
    unique (tenant_id, rollback_plan_id, reviewer)
);

create index if not exists idx_rollback_decision_plan
  on rollback_decision(tenant_id, rollback_plan_id, created_at);

alter table execution_run
  add column execution_kind varchar(32) not null default 'normal';

alter table execution_run
  add column rollback_plan_id varchar(64);

alter table execution_run
  add column rollback_of_execution_id varchar(64);

alter table execution_run
  add constraint ck_execution_run_kind
    check (execution_kind in ('normal', 'rollback'));

create index idx_execution_run_rollback_plan
  on execution_run(tenant_id, rollback_plan_id);

create index idx_execution_run_rollback_of
  on execution_run(tenant_id, rollback_of_execution_id);