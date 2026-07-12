-- V0028: Portal IAM 管理平面 - 用户状态、权限目录、角色数据范围与冲突约束
-- 目标：
--   1. 强化 sys_user（status / 锁定 / 失败计数 / 版本号 / 唯一 username）。
--   2. 引入 iam.permission_definition：带 risk_level / dependencies 的权限目录。
--   3. 强化 iam.role_data_scope：tenant_id + scope_json 元数据。
--   4. 与现有 iam.permission / iam.role_definition 共存：permission_definition
--      是"目录"，permission 是"权限快照"；doc02 § 4 PermissionNormalizer 消费的是
--      permission_definition，因此不替换任何现有数据。
--
-- 所有 ALTER TABLE 子句用 DO $$ BEGIN ... END $$ 包起来，仅在列不存在时 ALTER，
-- 这样 Flyway 之后的 jOOQ codegen 通过 prepare-jooq-ddl.mjs 的 do/declare/perform
-- skipStarters 直接忽略整段，编译期就不会触发 jOOQ DDL parser。

do $body$
begin
    if not exists (
        select 1 from information_schema.columns
        where table_name = 'sys_user' and column_name = 'status'
    ) then
        alter table sys_user add column status varchar(32) not null default 'active';
    end if;

    if not exists (
        select 1 from information_schema.columns
        where table_name = 'sys_user' and column_name = 'locked_until'
    ) then
        alter table sys_user add column locked_until timestamptz;
    end if;

    if not exists (
        select 1 from information_schema.columns
        where table_name = 'sys_user' and column_name = 'failed_login_count'
    ) then
        alter table sys_user add column failed_login_count integer not null default 0;
    end if;

    if not exists (
        select 1 from information_schema.columns
        where table_name = 'sys_user' and column_name = 'last_login_at'
    ) then
        alter table sys_user add column last_login_at timestamptz;
    end if;

    if not exists (
        select 1 from information_schema.columns
        where table_name = 'sys_user' and column_name = 'password_changed_at'
    ) then
        alter table sys_user add column password_changed_at timestamptz;
    end if;

    if not exists (
        select 1 from information_schema.columns
        where table_name = 'sys_user' and column_name = 'row_version'
    ) then
        alter table sys_user add column row_version integer not null default 1;
    end if;
end
$body$;

create unique index if not exists uk_sys_user_tenant_username_lower
    on sys_user(tenant_id, lower(username));

create index if not exists idx_sys_user_tenant_status_created
    on sys_user(tenant_id, status, created_at desc, id desc);

create table if not exists iam.permission_definition (
    permission_code varchar(128) primary key,
    module_code varchar(64) not null,
    permission_name varchar(128) not null,
    description varchar(500),
    risk_level varchar(32) not null default 'normal',
    dependencies_json jsonb not null default '[]'::jsonb,
    sort_order integer not null default 0,
    enabled boolean not null default true,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    constraint ck_permission_risk
        check (risk_level in ('normal', 'sensitive', 'high', 'critical')),
    constraint ck_permission_dependencies_array
        check (jsonb_typeof(dependencies_json) = 'array')
);

create table if not exists iam.role_data_scope_v2 (
    tenant_id varchar(64) not null,
    role_code varchar(64) not null,
    resource_code varchar(64) not null,
    scope_type varchar(32) not null,
    scope_json jsonb not null default '{}'::jsonb,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    primary key (tenant_id, role_code, resource_code),
    constraint ck_role_scope_v2_type
        check (scope_type in ('ALL', 'SELF', 'DEPARTMENT', 'CUSTOM')),
    constraint ck_role_scope_v2_json_object
        check (jsonb_typeof(scope_json) = 'object')
);

create index if not exists idx_user_role_tenant_role
    on iam.user_role(tenant_id, role_code, user_id);

do $body$
begin
    perform 1;
end
$body$;

insert into iam.permission_definition(
    permission_code, module_code, permission_name, risk_level,
    dependencies_json, sort_order
)
values
    ('platform:user:read', 'platform-user', '查看用户', 'sensitive', '[]'::jsonb, 10),
    ('platform:user:write', 'platform-user', '创建和编辑用户', 'high',
     '["platform:user:read"]'::jsonb, 20),
    ('platform:user:status', 'platform-user', '启用或禁用用户', 'critical',
     '["platform:user:read"]'::jsonb, 30),
    ('platform:user:assign-role', 'platform-user', '分配角色', 'critical',
     '["platform:user:read","platform:role:read"]'::jsonb, 40),
    ('platform:user:reset-password', 'platform-user', '重置密码', 'critical',
     '["platform:user:read"]'::jsonb, 50),
    ('platform:role:read', 'platform-role', '查看角色', 'sensitive', '[]'::jsonb, 10),
    ('platform:role:write', 'platform-role', '管理角色权限', 'critical',
     '["platform:role:read"]'::jsonb, 20)
on conflict (permission_code) do update
set module_code = excluded.module_code,
    permission_name = excluded.permission_name,
    risk_level = excluded.risk_level,
    dependencies_json = excluded.dependencies_json,
    sort_order = excluded.sort_order,
    updated_at = now();