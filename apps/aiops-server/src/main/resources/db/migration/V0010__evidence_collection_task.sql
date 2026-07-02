create table if not exists evidence_collection_task (
  id varchar(64) primary key,
  tenant_id varchar(64) not null references tenant(id) on delete cascade,
  incident_id varchar(64) not null references incident(id) on delete cascade,
  collector_key varchar(128) not null,
  status varchar(32) not null default 'pending',
  request_json jsonb not null default '{}'::jsonb,
  result_json jsonb not null default '{}'::jsonb,
  error_message text,
  started_at timestamptz,
  finished_at timestamptz,
  created_at timestamptz not null default now()
);

create index if not exists idx_evidence_task_incident
  on evidence_collection_task(tenant_id, incident_id, created_at desc);

create index if not exists idx_evidence_task_status
  on evidence_collection_task(tenant_id, status, created_at desc);
