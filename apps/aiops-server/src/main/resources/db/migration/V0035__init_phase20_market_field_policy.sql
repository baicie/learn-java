-- Phase 20.6: immutable template packages and version-scoped field policies.

create table if not exists work_record.wr_market_package (
    id varchar(64) primary key,
    publisher_tenant_id varchar(64) not null references public.tenant(id) on delete restrict,
    source_template_id varchar(64),
    package_code varchar(96) not null unique,
    name varchar(160) not null,
    summary varchar(500),
    category varchar(64) not null,
    visibility varchar(24) not null default 'private',
    status varchar(24) not null default 'draft',
    latest_version_id varchar(64),
    install_count bigint not null default 0,
    created_by varchar(64) not null,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    constraint ck_wr_market_visibility check (visibility in ('private','tenant','public')),
    constraint ck_wr_market_status check (status in ('draft','published','withdrawn')),
    constraint ck_wr_market_install_count check (install_count >= 0)
);

create table if not exists work_record.wr_market_package_version (
    id varchar(64) primary key,
    package_id varchar(64) not null references work_record.wr_market_package(id) on delete cascade,
    version_no integer not null,
    version_name varchar(128),
    package_json jsonb not null,
    checksum varchar(64) not null,
    published_by varchar(64) not null,
    created_at timestamptz not null default now(),
    constraint uq_wr_market_package_version unique (package_id, version_no),
    constraint ck_wr_market_package_json check (jsonb_typeof(package_json)='object'),
    constraint ck_wr_market_checksum check (checksum ~ '^[0-9a-f]{64}$')
);
alter table work_record.wr_market_package
    add constraint fk_wr_market_latest_version foreign key (latest_version_id)
    references work_record.wr_market_package_version(id) deferrable initially deferred;

create table if not exists work_record.wr_market_install (
    id varchar(64) primary key,
    tenant_id varchar(64) not null references public.tenant(id) on delete cascade,
    package_id varchar(64) not null references work_record.wr_market_package(id),
    package_version_id varchar(64) not null references work_record.wr_market_package_version(id),
    installed_template_id varchar(64) not null,
    installed_by varchar(64) not null,
    created_at timestamptz not null default now(),
    constraint fk_wr_market_install_template foreign key (tenant_id, installed_template_id)
        references work_record.wr_template(tenant_id, id) on delete restrict,
    constraint fk_wr_market_installer foreign key (tenant_id, installed_by)
        references public.sys_user(tenant_id, id) on delete restrict,
    constraint uq_wr_market_install unique (tenant_id, package_version_id, installed_template_id)
);

create table if not exists work_record.wr_field_policy (
    id varchar(64) primary key,
    tenant_id varchar(64) not null,
    template_version_id varchar(64) not null,
    field_code varchar(64) not null,
    read_roles_json jsonb not null default '[]'::jsonb,
    write_roles_json jsonb not null default '[]'::jsonb,
    mask_mode varchar(24) not null default 'none',
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    constraint fk_wr_field_policy_version foreign key (tenant_id, template_version_id)
        references work_record.wr_template_version(tenant_id, id) on delete cascade,
    constraint uq_wr_field_policy unique (tenant_id, template_version_id, field_code),
    constraint ck_wr_field_policy_read_roles check (jsonb_typeof(read_roles_json)='array'),
    constraint ck_wr_field_policy_write_roles check (jsonb_typeof(write_roles_json)='array'),
    constraint ck_wr_field_policy_mask_mode check (mask_mode in ('none','full','partial'))
);
create index if not exists idx_wr_field_policy_version
    on work_record.wr_field_policy(tenant_id, template_version_id);

insert into iam.permission_definition(
    permission_code,module_code,permission_name,description,risk_level,
    dependencies_json,sort_order,enabled)
values
 ('work-record:market:publish','work-record','发布模板市场包','发布不可变模板包','sensitive',
  '["work-record:template:write"]'::jsonb,390,true),
 ('work-record:market:install','work-record','安装模板市场包','校验并安装模板包','high',
  '["work-record:template:write"]'::jsonb,400,true)
on conflict(permission_code) do update set permission_name=excluded.permission_name,
 description=excluded.description,risk_level=excluded.risk_level,
 dependencies_json=excluded.dependencies_json,sort_order=excluded.sort_order,
 enabled=true,updated_at=now();
insert into iam.permission(permission_code,permission_name,module_code,resource_type,description,enabled)
select permission_code,permission_name,module_code,'ACTION',description,true
from iam.permission_definition where permission_code in(
 'work-record:market:publish','work-record:market:install')
on conflict(permission_code) do update set permission_name=excluded.permission_name,
 description=excluded.description,enabled=true,updated_at=now();
insert into iam.role_permission(tenant_id,role_code,permission_code)
select r.tenant_id,r.role_code,p.permission_code from iam.role_definition r cross join iam.permission p
where r.role_code in('system_admin','record_admin') and r.deleted_at is null
and p.permission_code in('work-record:market:publish','work-record:market:install')
on conflict do nothing;
