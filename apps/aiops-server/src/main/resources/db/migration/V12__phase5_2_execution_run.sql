-- Phase 5.2: execution run and runner.
-- aiops-server only creates queued runs.
-- aiops-runner consumes queued runs.

alter table automation_plan
  drop constraint if exists ck_automation_plan_status;

alter table automation_plan
  add constraint ck_automation_plan_status
    check (status in (
      'draft',
      'pending_approval',
      'approved',
      'rejected',
      'superseded',
      'cancelled',
      'executing',
      'succeeded',
      'failed'
    ));

create table if not exists execution_run (
  id varchar(64) primary key,
  tenant_id varchar(64) not null references tenant(id) on delete cascade,
  incident_id varchar(64) not null references incident(id) on delete cascade,
  plan_id varchar(64) not null references automation_plan(id) on delete cascade,
  status varchar(32) not null default 'queued',
  mode varchar(32) not null default 'dry_run',
  requested_by varchar(64) not null,
  runner_id varchar(128),
  started_at timestamptz,
  finished_at timestamptz,
  error_message text,
  summary text,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  constraint ck_execution_run_status
    check (status in ('queued', 'running', 'succeeded', 'failed', 'cancelled')),
  constraint ck_execution_run_mode
    check (mode in ('dry_run', 'live'))
);

create index if not exists idx_execution_run_tenant_plan_created
  on execution_run(tenant_id, plan_id, created_at desc);

create index if not exists idx_execution_run_status_created
  on execution_run(status, created_at asc);

create unique index if not exists uq_execution_run_active_plan
  on execution_run(plan_id)
  where status in ('queued', 'running');

create table if not exists execution_step (
  id varchar(64) primary key,
  tenant_id varchar(64) not null references tenant(id) on delete cascade,
  execution_id varchar(64) not null references execution_run(id) on delete cascade,
  plan_step_id varchar(64) not null references automation_plan_step(id) on delete cascade,
  sequence_no int not null,
  name varchar(160) not null,
  action_type varchar(32) not null,
  target_type varchar(32) not null,
  status varchar(32) not null default 'queued',
  action_payload jsonb not null default '{}'::jsonb,
  command_snapshot text,
  output text,
  error_message text,
  started_at timestamptz,
  finished_at timestamptz,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  constraint ck_execution_step_status
    check (status in ('queued', 'running', 'succeeded', 'failed', 'skipped', 'cancelled')),
  constraint uq_execution_step_sequence
    unique (execution_id, sequence_no)
);

create index if not exists idx_execution_step_execution_sequence
  on execution_step(execution_id, sequence_no);

create index if not exists idx_execution_step_status
  on execution_step(status);
