-- Automation outbox table for cross-app dispatch.
-- Replaces direct server → worker calls with an async outbox pattern.
-- All three apps (server / worker / runner) share this table.
-- Worker OutboxPoller picks pending rows and routes by (target_app, job_name).
-- This is the infrastructure layer of the Phase 5 three-app调度链.
--
-- Dependency: V0001 (tenant FK), V0002 (asset, datasource), V0003 (incident, alert).

create table if not exists automation_outbox (
  id                    varchar(64)  primary key,
  tenant_id             varchar(64)  references tenant(id),

  -- Routing
  target_app            varchar(32)  not null,   -- 'worker' | 'server' | 'runner'
  job_name              varchar(64)  not null,   -- e.g. 'zabbix-sync' | 'incident-aggregate'
                                            --     'trigger-rca-diagnosis' | 'postmortem-draft'

  -- Payload
  payload               jsonb        not null default '{}'::jsonb,

  -- Lifecycle
  status                varchar(32)  not null default 'pending',
  -- 'pending'    : queued, not yet picked
  -- 'processing' : locked by a poller (prevents double-pick)
  -- 'done'       : completed successfully
  -- 'failed'     : exhausted retries

  retry_count           int          not null default 0,
  max_retries          int          not null default 3,
  error_message        text,

  -- Audit
  created_at            timestamptz  not null default now(),
  updated_at           timestamptz  not null default now(),
  processed_at          timestamptz
);

-- Poller picks: pending rows ordered by creation time
create index if not exists idx_outbox_pending on automation_outbox(target_app, status, created_at asc);

-- Completed rows: clean-up / audit
create index if not exists idx_outbox_done on automation_outbox(status, processed_at asc);
