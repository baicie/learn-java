create table if not exists inspection_task (
  id varchar(64) primary key,
  tenant_id varchar(64) not null references tenant(id) on delete cascade,
  name varchar(160) not null,
  target_type varchar(64) not null,
  target_query jsonb not null default '{}'::jsonb,
  template_key varchar(128) not null,
  enabled boolean not null default true,
  created_by varchar(64) not null default 'system',
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

create table if not exists inspection_run (
  id varchar(64) primary key,
  tenant_id varchar(64) not null references tenant(id) on delete cascade,
  task_id varchar(64) not null references inspection_task(id) on delete cascade,
  status varchar(32) not null default 'running',
  summary text,
  started_at timestamptz not null default now(),
  finished_at timestamptz,
  created_at timestamptz not null default now()
);

create table if not exists inspection_item_result (
  id varchar(64) primary key,
  tenant_id varchar(64) not null references tenant(id) on delete cascade,
  run_id varchar(64) not null references inspection_run(id) on delete cascade,
  item_key varchar(128) not null,
  title varchar(256) not null,
  status varchar(32) not null,
  severity varchar(32) not null default 'info',
  detail text,
  evidence_json jsonb not null default '{}'::jsonb,
  created_at timestamptz not null default now()
);

create index if not exists idx_inspection_task_tenant on inspection_task(tenant_id, created_at desc);
create index if not exists idx_inspection_run_tenant_task on inspection_run(tenant_id, task_id, started_at desc);
create index if not exists idx_inspection_item_run on inspection_item_result(run_id, status, severity);
