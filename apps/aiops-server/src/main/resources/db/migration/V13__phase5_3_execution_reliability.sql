-- Phase 5.3: execution reliability.
-- Adds runner lease, heartbeat, timeout, retry and artifact metadata.

alter table execution_run
  drop constraint if exists ck_execution_run_status;

alter table execution_run
  add constraint ck_execution_run_status
    check (status in ('queued', 'running', 'succeeded', 'failed', 'cancelled', 'timeout'));

alter table execution_step
  drop constraint if exists ck_execution_step_status;

alter table execution_step
  add constraint ck_execution_step_status
    check (status in ('queued', 'running', 'succeeded', 'failed', 'skipped', 'cancelled', 'timeout'));

alter table execution_run
  add column attempt int not null default 1;

alter table execution_run
  add column max_attempts int not null default 1;

alter table execution_run
  add column retry_of_execution_id varchar(64) references execution_run(id) on delete set null;

alter table execution_run
  add column lease_until timestamptz;

alter table execution_run
  add column heartbeat_at timestamptz;

alter table execution_run
  add column timeout_seconds int not null default 1800;

alter table execution_run
  add constraint ck_execution_run_attempt
    check (attempt >= 1 and max_attempts >= 1 and attempt <= max_attempts);

alter table execution_run
  add constraint ck_execution_run_timeout_seconds
    check (timeout_seconds >= 30 and timeout_seconds <= 86400);

alter table execution_step
  add column attempt int not null default 1;

alter table execution_step
  add column timeout_seconds int not null default 300;

alter table execution_step
  add column artifact_count int not null default 0;

alter table execution_step
  add constraint ck_execution_step_attempt
    check (attempt >= 1);

alter table execution_step
  add constraint ck_execution_step_timeout_seconds
    check (timeout_seconds >= 1 and timeout_seconds <= 86400);

alter table execution_step
  add constraint ck_execution_step_artifact_count
    check (artifact_count >= 0);

create index idx_execution_run_running_lease
  on execution_run(status, lease_until);

create index idx_execution_run_retry_of
  on execution_run(retry_of_execution_id);

create table execution_artifact (
  id varchar(64) primary key,
  tenant_id varchar(64) not null references tenant(id) on delete cascade,
  execution_id varchar(64) not null references execution_run(id) on delete cascade,
  step_id varchar(64) references execution_step(id) on delete cascade,
  artifact_type varchar(32) not null default 'text',
  name varchar(160) not null,
  content text,
  metadata jsonb not null default '{}'::jsonb,
  created_at timestamptz not null default now(),
  constraint ck_execution_artifact_type
    check (artifact_type in ('text', 'json', 'log'))
);

create index idx_execution_artifact_execution
  on execution_artifact(execution_id, created_at asc);

create index idx_execution_artifact_step
  on execution_artifact(step_id, created_at asc);
