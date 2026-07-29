alter table datasource_sync_run
    add column if not exists lease_until timestamptz;

alter table datasource_sync_run
    add column if not exists claim_token varchar(64);

alter table datasource_sync_run
    add column if not exists datasource_updated_at timestamptz;

alter table automation_outbox
    add column if not exists claim_token varchar(64);

update datasource_sync_run r
   set lease_until = coalesce(r.lease_until, now() + interval '5 minutes'),
       datasource_updated_at = coalesce(r.datasource_updated_at, d.updated_at)
  from datasource d
 where r.status = 'running'
   and d.tenant_id = r.tenant_id
   and d.id = r.datasource_id;

create index if not exists idx_datasource_sync_run_running_lease
    on datasource_sync_run (lease_until)
    where status = 'running';

create index if not exists idx_datasource_sync_run_active
    on datasource_sync_run (tenant_id, datasource_id, started_at, id)
    where status in ('pending', 'running');
