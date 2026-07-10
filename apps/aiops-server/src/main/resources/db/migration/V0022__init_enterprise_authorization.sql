create schema if not exists iam;

create table if not exists iam.permission (
    permission_code varchar(128) primary key,
    permission_name varchar(128) not null,
    module_code varchar(64) not null,
    resource_type varchar(32) not null default 'ACTION',
    description varchar(512),
    system_builtin boolean not null default true,
    enabled boolean not null default true,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),

    constraint ck_iam_permission_resource_type
        check (resource_type in ('MENU', 'ACTION', 'API'))
);

create table if not exists iam.role_definition (
    role_code varchar(64) primary key,
    role_name varchar(128) not null,
    description varchar(512),
    system_builtin boolean not null default true,
    enabled boolean not null default true,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);

create table if not exists iam.role_permission (
    role_code varchar(64) not null,
    permission_code varchar(128) not null,
    created_at timestamptz not null default now(),

    primary key (role_code, permission_code),

    constraint fk_iam_role_permission_role
        foreign key (role_code)
        references iam.role_definition(role_code)
        on delete cascade,

    constraint fk_iam_role_permission_permission
        foreign key (permission_code)
        references iam.permission(permission_code)
        on delete cascade
);

create table if not exists iam.role_data_scope (
    role_code varchar(64) not null,
    resource_code varchar(64) not null,
    scope_type varchar(32) not null,
    created_at timestamptz not null default now(),

    primary key (role_code, resource_code),

    constraint fk_iam_role_scope_role
        foreign key (role_code)
        references iam.role_definition(role_code)
        on delete cascade,

    constraint ck_iam_role_scope_type
        check (scope_type in ('SELF', 'ALL'))
);

create table if not exists iam.user_role (
    tenant_id varchar(64) not null,
    user_id varchar(64) not null,
    role_code varchar(64) not null,
    created_at timestamptz not null default now(),
    created_by varchar(64) not null default 'system',

    primary key (tenant_id, user_id, role_code),

    constraint fk_iam_user_role_role
        foreign key (role_code)
        references iam.role_definition(role_code)
        on delete restrict
);

create index if not exists idx_iam_user_role_lookup
    on iam.user_role(tenant_id, user_id);

create index if not exists idx_iam_role_permission_permission
    on iam.role_permission(permission_code);

insert into iam.permission(
    permission_code,
    permission_name,
    module_code,
    resource_type,
    description
)
values
    (
        'platform:dict:read',
        '读取字典',
        'platform',
        'API',
        '查看字典类型和字典项'
    ),
    (
        'platform:dict:write',
        '维护字典',
        'platform',
        'ACTION',
        '创建和修改字典类型及字典项'
    ),
    (
        'platform:calendar:read',
        '读取工作日历',
        'platform',
        'API',
        '查看工作日历'
    ),
    (
        'platform:calendar:write',
        '维护工作日历',
        'platform',
        'ACTION',
        '创建和修改工作日历'
    ),
    (
        'platform:calendar:import',
        '导入工作日历',
        'platform',
        'ACTION',
        '批量导入工作日历'
    ),
    (
        'work-record:template:read',
        '读取工作记录模板',
        'work-record',
        'API',
        '查看模板及模板版本'
    ),
    (
        'work-record:template:write',
        '维护工作记录模板',
        'work-record',
        'ACTION',
        '创建、编辑、发布、启停和归档模板'
    ),
    (
        'work-record:read:self',
        '读取本人工作记录',
        'work-record',
        'API',
        '查看本人创建或负责的工作记录'
    ),
    (
        'work-record:read:all',
        '读取全部工作记录',
        'work-record',
        'API',
        '查看当前租户全部工作记录'
    ),
    (
        'work-record:write',
        '编辑工作记录',
        'work-record',
        'ACTION',
        '创建或编辑权限范围内的工作记录'
    ),
    (
        'work-record:delete',
        '删除工作记录',
        'work-record',
        'ACTION',
        '删除权限范围内的工作记录'
    ),
    (
        'work-record:export',
        '导出工作记录',
        'work-record',
        'ACTION',
        '导出权限范围内的工作记录'
    )
on conflict (permission_code) do update
set permission_name = excluded.permission_name,
    module_code = excluded.module_code,
    resource_type = excluded.resource_type,
    description = excluded.description,
    system_builtin = true,
    enabled = true,
    updated_at = now();

insert into iam.role_definition(
    role_code,
    role_name,
    description
)
values
    (
        'system_admin',
        '系统管理员',
        '拥有系统和工作记录模块的全部权限'
    ),
    (
        'record_admin',
        '记录管理员',
        '管理工作记录、模板、字典和工作日历'
    ),
    (
        'normal_user',
        '普通用户',
        '填写并维护本人工作记录'
    ),
    (
        'readonly_user',
        '只读用户',
        '只读本人工作记录和基础配置'
    )
on conflict (role_code) do update
set role_name = excluded.role_name,
    description = excluded.description,
    system_builtin = true,
    enabled = true,
    updated_at = now();

-- 系统管理员：本阶段所有权限。
insert into iam.role_permission(role_code, permission_code)
select 'system_admin', permission_code
from iam.permission
where permission_code in (
    'platform:dict:read',
    'platform:dict:write',
    'platform:calendar:read',
    'platform:calendar:write',
    'platform:calendar:import',
    'work-record:template:read',
    'work-record:template:write',
    'work-record:read:self',
    'work-record:read:all',
    'work-record:write',
    'work-record:delete',
    'work-record:export'
)
on conflict do nothing;

-- 记录管理员。
insert into iam.role_permission(role_code, permission_code)
select 'record_admin', permission_code
from iam.permission
where permission_code in (
    'platform:dict:read',
    'platform:dict:write',
    'platform:calendar:read',
    'platform:calendar:write',
    'platform:calendar:import',
    'work-record:template:read',
    'work-record:template:write',
    'work-record:read:self',
    'work-record:read:all',
    'work-record:write',
    'work-record:delete',
    'work-record:export'
)
on conflict do nothing;

-- 普通用户。
insert into iam.role_permission(role_code, permission_code)
select 'normal_user', permission_code
from iam.permission
where permission_code in (
    'platform:dict:read',
    'platform:calendar:read',
    'work-record:template:read',
    'work-record:read:self',
    'work-record:write'
)
on conflict do nothing;

-- 只读用户。
insert into iam.role_permission(role_code, permission_code)
select 'readonly_user', permission_code
from iam.permission
where permission_code in (
    'platform:dict:read',
    'platform:calendar:read',
    'work-record:template:read',
    'work-record:read:self'
)
on conflict do nothing;

insert into iam.role_data_scope(
    role_code,
    resource_code,
    scope_type
)
values
    ('system_admin', 'work-record', 'ALL'),
    ('record_admin', 'work-record', 'ALL'),
    ('normal_user', 'work-record', 'SELF'),
    ('readonly_user', 'work-record', 'SELF')
on conflict (role_code, resource_code) do update
set scope_type = excluded.scope_type;

-- 兼容旧 sys_user.role 字段。
-- admin -> system_admin
-- operator -> normal_user
-- viewer / readonly -> readonly_user
do $migration$
begin
    if to_regclass('public.sys_user') is not null
       and exists (
           select 1
           from information_schema.columns
           where table_schema = 'public'
             and table_name = 'sys_user'
             and column_name = 'role'
       )
       and exists (
           select 1
           from information_schema.columns
           where table_schema = 'public'
             and table_name = 'sys_user'
             and column_name = 'tenant_id'
       )
    then
        execute $sql$
            insert into iam.user_role(
                tenant_id,
                user_id,
                role_code,
                created_by
            )
            select
                tenant_id,
                id,
                case lower(role)
                    when 'admin' then 'system_admin'
                    when 'system_admin' then 'system_admin'
                    when 'operator' then 'normal_user'
                    when 'normal_user' then 'normal_user'
                    when 'viewer' then 'readonly_user'
                    when 'readonly' then 'readonly_user'
                    when 'readonly_user' then 'readonly_user'
                    else 'normal_user'
                end,
                'phase-13-migration'
            from public.sys_user
            where tenant_id is not null
              and id is not null
            on conflict do nothing
        $sql$;
    end if;
end
$migration$;
