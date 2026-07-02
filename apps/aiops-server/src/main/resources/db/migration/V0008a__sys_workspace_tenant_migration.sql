-- Procedural migration: backfill tenant_id and add FK constraint.
-- Only runs via Flyway (PostgreSQL); jOOQ codegen uses V0008 DDL only.
do $$
declare
  default_tenant_id varchar(64);
begin
  select id into default_tenant_id
  from tenant
  order by created_at asc
  limit 1;

  if default_tenant_id is null
     and exists (select 1 from sys_workspace where tenant_id is null) then
    raise exception 'Cannot migrate sys_workspace: tenant table is empty. Ensure tenant data exists before running this migration.';
  end if;

  update sys_workspace
  set tenant_id = default_tenant_id
  where tenant_id is null;
end $$;

-- Make NOT NULL after backfill.
alter table sys_workspace alter column tenant_id set not null;

-- Conditional FK constraint (DO block needed for IF NOT EXISTS check).
do $$
begin
  if not exists (
    select 1
    from pg_constraint
    where conname = 'fk_sys_workspace_tenant'
      and conrelid = 'sys_workspace'::regclass
  ) then
    alter table sys_workspace
      add constraint fk_sys_workspace_tenant
      foreign key (tenant_id) references tenant(id) on delete cascade;
  end if;
end $$;
