-- Phase 20.2: direct-to-object-storage upload sessions and import/export permissions.

create unique index if not exists uq_sys_user_tenant_id
    on public.sys_user(tenant_id, id);

create table if not exists work_record.wr_upload_session (
    id varchar(64) primary key,
    tenant_id varchar(64) not null,
    requested_by varchar(64) not null,
    purpose varchar(32) not null,
    object_key varchar(512) not null,
    original_file_name varchar(255) not null,
    content_type varchar(128) not null,
    declared_size_bytes bigint not null,
    status varchar(24) not null default 'prepared',
    expires_at timestamptz not null,
    consumed_at timestamptz,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    constraint uq_wr_upload_session_tenant_id unique (tenant_id, id),
    constraint uq_wr_upload_session_object unique (tenant_id, object_key),
    constraint fk_wr_upload_session_tenant foreign key (tenant_id)
        references public.tenant(id) on delete restrict,
    constraint fk_wr_upload_session_user foreign key (tenant_id, requested_by)
        references public.sys_user(tenant_id, id) on delete restrict,
    constraint ck_wr_upload_session_purpose
        check (purpose in ('excel_import', 'attachment')),
    constraint ck_wr_upload_session_status
        check (status in ('prepared', 'consumed', 'expired')),
    constraint ck_wr_upload_session_size
        check (declared_size_bytes > 0 and declared_size_bytes <= 20971520),
    constraint ck_wr_upload_session_expiry
        check (expires_at > created_at),
    constraint ck_wr_upload_session_consumed
        check ((status = 'consumed' and consumed_at is not null)
            or (status <> 'consumed' and consumed_at is null))
);

create index idx_wr_upload_session_expiry
    on work_record.wr_upload_session(status, expires_at);

insert into iam.permission_definition(
    permission_code, module_code, permission_name, description, risk_level,
    dependencies_json, sort_order, enabled)
values
    ('work-record:import', 'work-record', '导入工作记录', '通过 Excel 批量导入工作记录',
     'high', '["work-record:write"]'::jsonb, 270, true),
    ('work-record:export:async', 'work-record', '异步导出工作记录',
     '创建并下载异步导出任务', 'sensitive', '["work-record:export"]'::jsonb, 280, true)
on conflict (permission_code) do update
set module_code = excluded.module_code,
    permission_name = excluded.permission_name,
    description = excluded.description,
    risk_level = excluded.risk_level,
    dependencies_json = excluded.dependencies_json,
    sort_order = excluded.sort_order,
    enabled = true,
    updated_at = now();

insert into iam.permission(
    permission_code, permission_name, module_code, resource_type, description, enabled)
select permission_code, permission_name, module_code, 'ACTION', description, true
from iam.permission_definition
where permission_code in ('work-record:import', 'work-record:export:async')
on conflict (permission_code) do update
set permission_name = excluded.permission_name,
    module_code = excluded.module_code,
    resource_type = excluded.resource_type,
    description = excluded.description,
    enabled = true,
    updated_at = now();

insert into iam.role_permission(tenant_id, role_code, permission_code)
select r.tenant_id, r.role_code, p.permission_code
from iam.role_definition r
cross join iam.permission p
where r.role_code in ('system_admin', 'record_admin', 'normal_user')
  and r.deleted_at is null
  and p.permission_code in ('work-record:import', 'work-record:export:async')
on conflict do nothing;
