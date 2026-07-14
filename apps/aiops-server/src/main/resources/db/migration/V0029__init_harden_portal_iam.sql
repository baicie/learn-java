-- Phase 20.0: make the existing Portal IAM implementation tenant-safe and executable.

do $body$
declare
    default_tenant_id varchar(64);
begin

insert into tenant(id, code, name, status)
values ('default', 'default', 'Default Tenant', 'active')
on conflict (code) do nothing;

select id into strict default_tenant_id
from tenant
where code = 'default';

alter table sys_user add column if not exists deleted_at timestamptz;
alter table sys_user drop constraint if exists sys_user_username_key;
drop index if exists uk_sys_user_tenant_username_lower;
create unique index uk_sys_user_tenant_username_lower
    on sys_user(tenant_id, lower(username))
    where deleted_at is null;

alter table iam.user_role
    drop constraint if exists fk_iam_user_role_role;
alter table iam.role_permission
    drop constraint if exists fk_iam_role_permission_role;
alter table iam.role_data_scope
    drop constraint if exists fk_iam_role_scope_role;

alter table iam.role_definition add column if not exists tenant_id varchar(64);
alter table iam.role_definition add column if not exists row_version integer not null default 1;
alter table iam.role_definition add column if not exists deleted_at timestamptz;
update iam.role_definition set tenant_id = default_tenant_id where tenant_id is null;
alter table iam.role_definition alter column tenant_id set not null;
alter table iam.role_definition drop constraint if exists role_definition_pkey;
alter table iam.role_definition
    add primary key (tenant_id, role_code);
alter table iam.role_definition
    add constraint fk_iam_role_definition_tenant
    foreign key (tenant_id) references tenant(id) on delete cascade;

insert into iam.role_definition(
    tenant_id, role_code, role_name, description, system_builtin, enabled,
    row_version, created_at, updated_at)
select
    t.id, r.role_code, r.role_name, r.description, r.system_builtin, r.enabled,
    1, r.created_at, r.updated_at
from tenant t
join iam.role_definition r on r.tenant_id = default_tenant_id
where t.id <> default_tenant_id
on conflict (tenant_id, role_code) do nothing;

alter table iam.role_permission add column if not exists tenant_id varchar(64);
update iam.role_permission set tenant_id = default_tenant_id where tenant_id is null;
alter table iam.role_permission alter column tenant_id set not null;
alter table iam.role_permission drop constraint if exists role_permission_pkey;
alter table iam.role_permission
    add primary key (tenant_id, role_code, permission_code);

insert into iam.role_permission(tenant_id, role_code, permission_code, created_at)
select r.tenant_id, p.role_code, p.permission_code, p.created_at
from iam.role_definition r
join iam.role_permission p
  on p.tenant_id = default_tenant_id and p.role_code = r.role_code
where r.tenant_id <> default_tenant_id
on conflict do nothing;

alter table iam.role_permission
    add constraint fk_iam_role_permission_role
    foreign key (tenant_id, role_code)
    references iam.role_definition(tenant_id, role_code)
    on delete cascade;

alter table iam.role_data_scope add column if not exists tenant_id varchar(64);
update iam.role_data_scope set tenant_id = default_tenant_id where tenant_id is null;
alter table iam.role_data_scope alter column tenant_id set not null;
alter table iam.role_data_scope drop constraint if exists role_data_scope_pkey;
alter table iam.role_data_scope
    add primary key (tenant_id, role_code, resource_code);

insert into iam.role_data_scope(tenant_id, role_code, resource_code, scope_type, created_at)
select r.tenant_id, s.role_code, s.resource_code, s.scope_type, s.created_at
from iam.role_definition r
join iam.role_data_scope s
  on s.tenant_id = default_tenant_id and s.role_code = r.role_code
where r.tenant_id <> default_tenant_id
on conflict do nothing;

alter table iam.role_data_scope
    add constraint fk_iam_role_scope_role
    foreign key (tenant_id, role_code)
    references iam.role_definition(tenant_id, role_code)
    on delete cascade;

alter table iam.user_role
    add constraint fk_iam_user_role_role
    foreign key (tenant_id, role_code)
    references iam.role_definition(tenant_id, role_code)
    on delete restrict;

alter table iam.role_data_scope_v2
    add constraint fk_iam_role_scope_v2_role
    foreign key (tenant_id, role_code)
    references iam.role_definition(tenant_id, role_code)
    on delete cascade;

insert into iam.permission(
    permission_code, permission_name, module_code, resource_type, description)
select
    permission_code,
    permission_name,
    module_code,
    'ACTION',
    description
from iam.permission_definition
where permission_code in (
    'platform:user:read',
    'platform:user:write',
    'platform:user:status',
    'platform:user:assign-role',
    'platform:user:reset-password',
    'platform:role:read',
    'platform:role:write'
)
on conflict (permission_code) do update
set permission_name = excluded.permission_name,
    module_code = excluded.module_code,
    description = excluded.description,
    enabled = true,
    updated_at = now();

insert into iam.role_permission(tenant_id, role_code, permission_code)
select r.tenant_id, r.role_code, p.permission_code
from iam.role_definition r
cross join iam.permission p
where r.role_code = 'system_admin'
  and r.deleted_at is null
  and p.permission_code in (
      'platform:user:read',
      'platform:user:write',
      'platform:user:status',
      'platform:user:assign-role',
      'platform:user:reset-password',
      'platform:role:read',
      'platform:role:write'
  )
on conflict do nothing;

end
$body$;

insert into iam.role_data_scope_v2(
    tenant_id, role_code, resource_code, scope_type, scope_json,
    created_at, updated_at)
select
    s.tenant_id, s.role_code, s.resource_code, s.scope_type, '{}'::jsonb,
    s.created_at, now()
from iam.role_data_scope s
on conflict (tenant_id, role_code, resource_code) do update
set scope_type = excluded.scope_type,
    updated_at = now();

create or replace function iam.seed_tenant_builtin_roles()
returns trigger
language plpgsql
as $function$
declare
    default_tenant_id varchar(64);
begin
    select id into default_tenant_id from tenant where code = 'default';
    if default_tenant_id is null or new.id = default_tenant_id then
        return new;
    end if;

    insert into iam.role_definition(
        tenant_id, role_code, role_name, description, system_builtin, enabled,
        row_version, created_at, updated_at)
    select
        new.id, r.role_code, r.role_name, r.description, true, r.enabled,
        1, now(), now()
    from iam.role_definition r
    where r.tenant_id = default_tenant_id
      and r.system_builtin = true
      and r.deleted_at is null
    on conflict (tenant_id, role_code) do nothing;

    insert into iam.role_permission(tenant_id, role_code, permission_code, created_at)
    select new.id, p.role_code, p.permission_code, now()
    from iam.role_permission p
    join iam.role_definition r
      on r.tenant_id = new.id and r.role_code = p.role_code
    where p.tenant_id = default_tenant_id
    on conflict do nothing;

    insert into iam.role_data_scope(
        tenant_id, role_code, resource_code, scope_type, created_at)
    select new.id, s.role_code, s.resource_code, s.scope_type, now()
    from iam.role_data_scope s
    join iam.role_definition r
      on r.tenant_id = new.id and r.role_code = s.role_code
    where s.tenant_id = default_tenant_id
    on conflict do nothing;

    insert into iam.role_data_scope_v2(
        tenant_id, role_code, resource_code, scope_type, scope_json,
        created_at, updated_at)
    select
        new.id, s.role_code, s.resource_code, s.scope_type, s.scope_json,
        now(), now()
    from iam.role_data_scope_v2 s
    join iam.role_definition r
      on r.tenant_id = new.id and r.role_code = s.role_code
    where s.tenant_id = default_tenant_id
    on conflict do nothing;

    return new;
end
$function$;

drop trigger if exists trg_seed_tenant_builtin_roles on tenant;
create trigger trg_seed_tenant_builtin_roles
after insert on tenant
for each row execute function iam.seed_tenant_builtin_roles();
