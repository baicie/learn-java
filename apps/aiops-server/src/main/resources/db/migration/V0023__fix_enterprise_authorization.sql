-- V0023: 修复 Phase 13 企业授权 - 补充 AIOps 旧权限、ops_operator 角色和旧角色迁移
-- 修复：P0-3 旧用户角色迁移, P0-4 全站旧权限丢失

-- 1. 补齐已有 AIOps API 所依赖的权限码
insert into iam.permission(
    permission_code,
    permission_name,
    module_code,
    resource_type,
    description,
    enabled,
    created_at,
    updated_at
)
values
    ('datasource:read', '读取数据源', 'datasource', 'API', '查看数据源', true, now(), now()),
    ('datasource:write', '维护数据源', 'datasource', 'ACTION', '创建和修改数据源', true, now(), now()),
    ('asset:read', '读取资产', 'asset', 'API', '查看资产', true, now(), now()),
    ('alert:read', '读取告警', 'alert', 'API', '查看告警', true, now(), now()),
    ('alert:write', '维护告警', 'alert', 'ACTION', '修改告警', true, now(), now()),
    ('incident:read', '读取事件', 'incident', 'API', '查看事件', true, now(), now()),
    ('incident:write', '维护事件', 'incident', 'ACTION', '修改事件', true, now(), now()),
    ('incident:diagnose', '诊断事件', 'incident', 'ACTION', '执行事件诊断', true, now(), now()),
    ('runbook:read', '读取 Runbook', 'runbook', 'API', '查看 Runbook', true, now(), now()),
    ('runbook:write', '维护 Runbook', 'runbook', 'ACTION', '创建和修改 Runbook', true, now(), now()),
    ('automation:read', '读取自动化', 'automation', 'API', '查看自动化任务', true, now(), now()),
    ('automation:approve', '审批自动化', 'automation', 'ACTION', '审批自动化任务', true, now(), now()),
    ('automation:execute', '执行自动化', 'automation', 'ACTION', '执行自动化任务', true, now(), now()),
    ('audit:read', '读取审计', 'audit', 'API', '查看审计日志', true, now(), now()),
    ('admin:manage', '系统管理', 'admin', 'ACTION', '管理用户、角色和系统配置', true, now(), now())
on conflict (permission_code) do update
set permission_name = excluded.permission_name,
    module_code = excluded.module_code,
    resource_type = excluded.resource_type,
    description = excluded.description,
    enabled = true,
    updated_at = now();

-- 2. 兼容旧 operator，创建 ops_operator 角色
-- 注意：不能直接映射为 record_admin，因为会扩大其 read:all、export、template:write 等权限
insert into iam.role_definition(
    role_code,
    role_name,
    description,
    system_builtin,
    enabled,
    created_at,
    updated_at
)
values (
    'ops_operator',
    '运维操作员（兼容角色）',
    '兼容升级前 operator 权限，禁止在新租户中主动分配',
    true,
    true,
    now(),
    now()
)
on conflict (role_code) do update
set role_name = excluded.role_name,
    description = excluded.description,
    enabled = true,
    updated_at = now();

-- 3. 系统管理员获得现有完整权限
insert into iam.role_permission(role_code, permission_code)
select 'system_admin', permission_code
from iam.permission
where enabled = true
on conflict do nothing;

-- 4. 旧 operator 保持升级前权限，不扩大权限
insert into iam.role_permission(role_code, permission_code)
select 'ops_operator', permission_code
from iam.permission
where permission_code in (
    'datasource:read',
    'asset:read',
    'alert:read',
    'alert:write',
    'incident:read',
    'incident:write',
    'incident:diagnose',
    'runbook:read',
    'automation:read',
    'automation:execute',
    'audit:read',
    'platform:dict:read',
    'platform:calendar:read',
    'work-record:template:read',
    'work-record:read:self',
    'work-record:write',
    'work-record:delete'
)
on conflict do nothing;

-- 5. ops_operator 对 work-record 的数据范围是 SELF
insert into iam.role_data_scope(
    role_code,
    resource_code,
    scope_type,
    created_at
)
values ('ops_operator', 'work-record', 'SELF', now())
on conflict (role_code, resource_code) do update
set scope_type = excluded.scope_type;

-- 6. 从真实旧表结构迁移角色
-- 读取 sys_user -> sys_user_role -> sys_role 映射到 iam.user_role
with mapped_roles as (
    select distinct
        u.tenant_id,
        u.id as user_id,
        case lower(r.code)
            when 'admin' then 'system_admin'
            when 'operator' then 'ops_operator'
            when 'viewer' then 'readonly_user'
            when 'readonly' then 'readonly_user'
            when 'readonly_user' then 'readonly_user'
            when 'normal_user' then 'normal_user'
            when 'record_admin' then 'record_admin'
            when 'system_admin' then 'system_admin'
            else null
        end as new_role_code
    from public.sys_user u
    join public.sys_user_role ur on ur.user_id = u.id
    join public.sys_role r on r.id = ur.role_id
    where u.tenant_id is not null
)
insert into iam.user_role(
    tenant_id,
    user_id,
    role_code,
    created_by,
    created_at
)
select
    tenant_id,
    user_id,
    new_role_code,
    'phase-13-corrective-migration',
    now()
from mapped_roles
where new_role_code is not null
on conflict do nothing;
