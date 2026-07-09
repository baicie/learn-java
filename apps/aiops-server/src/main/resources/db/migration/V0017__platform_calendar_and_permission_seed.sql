-- Phase 2: platform dictionary hardening, calendar, permission seed.
-- Keep tables in public schema for current project baseline.

-- 1. Permission code registry.
create table if not exists platform_permission_code (
  id varchar(64) primary key,
  permission_code varchar(128) not null unique,
  permission_name varchar(128) not null,
  module_code varchar(64) not null,
  description text,
  enabled boolean not null default true,
  sort_order integer not null default 0,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

-- 2. Default role grant registry.
-- This table is a seed contract table. Existing RBAC implementation can sync from it.
create table if not exists platform_default_role_grant (
  id varchar(64) primary key,
  role_code varchar(64) not null,
  permission_code varchar(128) not null references platform_permission_code(permission_code),
  enabled boolean not null default true,
  created_at timestamptz not null default now(),
  unique (role_code, permission_code)
);

-- 3. Work calendar.
create table if not exists platform_calendar (
  id varchar(64) primary key,
  tenant_id varchar(64) not null references tenant(id) on delete cascade,
  calendar_code varchar(64) not null,
  calendar_name varchar(128) not null,
  region_code varchar(32) not null default 'CN',
  timezone varchar(64) not null default 'Asia/Shanghai',
  year integer not null,
  enabled boolean not null default true,
  source_type varchar(32) not null default 'manual',
  description text,
  created_by varchar(64),
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  unique (tenant_id, calendar_code)
);

create table if not exists platform_calendar_day (
  id varchar(64) primary key,
  tenant_id varchar(64) not null references tenant(id) on delete cascade,
  calendar_id varchar(64) not null references platform_calendar(id) on delete cascade,
  calendar_date date not null,
  day_of_week integer not null,
  day_type varchar(32) not null,
  is_workday boolean not null,
  holiday_code varchar(64),
  holiday_name varchar(128),
  source_type varchar(32) not null default 'manual',
  remark text,
  created_by varchar(64),
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  unique (tenant_id, calendar_id, calendar_date)
);

create index if not exists idx_platform_calendar_tenant_year
  on platform_calendar(tenant_id, year, enabled);

create index if not exists idx_platform_calendar_day_date
  on platform_calendar_day(tenant_id, calendar_id, calendar_date);

create index if not exists idx_platform_calendar_day_workday
  on platform_calendar_day(tenant_id, calendar_id, is_workday, calendar_date);

-- 4. Permission seeds using INSERT ... SELECT ... WHERE NOT EXISTS pattern.
insert into platform_permission_code(
  id, permission_code, permission_name, module_code, description, sort_order
)
select v.id, v.permission_code, v.permission_name, v.module_code, v.description, v.sort_order
from (
  values
    ('perm-platform-dict-read', 'platform:dict:read', '查看字典', 'platform', '查看平台字典类型和字典项', 100),
    ('perm-platform-dict-write', 'platform:dict:write', '维护字典', 'platform', '新增、编辑、禁用字典类型和字典项', 110),
    ('perm-platform-calendar-read', 'platform:calendar:read', '查看工作日历', 'platform', '查看工作日历和日期', 120),
    ('perm-platform-calendar-write', 'platform:calendar:write', '维护工作日历', 'platform', '新增日历、覆盖日期', 130),
    ('perm-platform-calendar-import', 'platform:calendar:import', '导入工作日历', 'platform', '导入 CSV 工作日历', 140),
    ('perm-work-record-template-read', 'work-record:template:read', '查看工作记录模板', 'work-record', '查看工作记录模板', 200),
    ('perm-work-record-template-write', 'work-record:template:write', '维护工作记录模板', 'work-record', '维护工作记录模板', 210),
    ('perm-work-record-read-self', 'work-record:read:self', '查看自己的工作记录', 'work-record', '查看自己创建或负责的记录', 220),
    ('perm-work-record-read-all', 'work-record:read:all', '查看全部工作记录', 'work-record', '查看全部工作记录', 230),
    ('perm-work-record-write', 'work-record:write', '维护工作记录', 'work-record', '新增和编辑工作记录', 240),
    ('perm-work-record-delete', 'work-record:delete', '删除工作记录', 'work-record', '软删除工作记录', 250),
    ('perm-work-record-export', 'work-record:export', '导出工作记录', 'work-record', '导出工作记录', 260)
) as v(id, permission_code, permission_name, module_code, description, sort_order)
where not exists (
  select 1 from platform_permission_code p where p.permission_code = v.permission_code
);

-- 5. Default role grants.
insert into platform_default_role_grant(id, role_code, permission_code)
select v.id, v.role_code, v.permission_code
from (
  values
    ('grant-admin-platform-dict-read', 'system_admin', 'platform:dict:read'),
    ('grant-admin-platform-dict-write', 'system_admin', 'platform:dict:write'),
    ('grant-admin-platform-calendar-read', 'system_admin', 'platform:calendar:read'),
    ('grant-admin-platform-calendar-write', 'system_admin', 'platform:calendar:write'),
    ('grant-admin-platform-calendar-import', 'system_admin', 'platform:calendar:import'),
    ('grant-admin-wr-template-read', 'system_admin', 'work-record:template:read'),
    ('grant-admin-wr-template-write', 'system_admin', 'work-record:template:write'),
    ('grant-admin-wr-read-self', 'system_admin', 'work-record:read:self'),
    ('grant-admin-wr-read-all', 'system_admin', 'work-record:read:all'),
    ('grant-admin-wr-write', 'system_admin', 'work-record:write'),
    ('grant-admin-wr-delete', 'system_admin', 'work-record:delete'),
    ('grant-admin-wr-export', 'system_admin', 'work-record:export'),
    ('grant-record-admin-platform-dict-read', 'record_admin', 'platform:dict:read'),
    ('grant-record-admin-platform-calendar-read', 'record_admin', 'platform:calendar:read'),
    ('grant-record-admin-wr-template-read', 'record_admin', 'work-record:template:read'),
    ('grant-record-admin-wr-template-write', 'record_admin', 'work-record:template:write'),
    ('grant-record-admin-wr-read-all', 'record_admin', 'work-record:read:all'),
    ('grant-record-admin-wr-write', 'record_admin', 'work-record:write'),
    ('grant-record-admin-wr-delete', 'record_admin', 'work-record:delete'),
    ('grant-record-admin-wr-export', 'record_admin', 'work-record:export'),
    ('grant-normal-user-wr-read-self', 'normal_user', 'work-record:read:self'),
    ('grant-normal-user-wr-write', 'normal_user', 'work-record:write'),
    ('grant-readonly-wr-read-self', 'readonly_user', 'work-record:read:self')
) as v(id, role_code, permission_code)
where not exists (
  select 1 from platform_default_role_grant g
  where g.role_code = v.role_code and g.permission_code = v.permission_code
);

-- 6. Default dictionary hardening using INSERT ... SELECT ... WHERE NOT EXISTS pattern.
insert into platform_dict_type(
  id, tenant_id, dict_code, dict_name, description, system_builtin, enabled, sort_order, created_by
)
select concat('dict-', t.id, '-record-type'), t.id, 'record_type', '工作记录类型',
       '工作记录类型字典', true, true, 100, 'system'
from tenant t
where not exists (
  select 1 from platform_dict_type d
  where d.tenant_id = t.id and d.dict_code = 'record_type'
);

insert into platform_dict_type(
  id, tenant_id, dict_code, dict_name, description, system_builtin, enabled, sort_order, created_by
)
select concat('dict-', t.id, '-record-status'), t.id, 'record_status', '工作记录状态',
       '工作记录状态字典', true, true, 110, 'system'
from tenant t
where not exists (
  select 1 from platform_dict_type d
  where d.tenant_id = t.id and d.dict_code = 'record_status'
);

insert into platform_dict_type(
  id, tenant_id, dict_code, dict_name, description, system_builtin, enabled, sort_order, created_by
)
select concat('dict-', t.id, '-record-priority'), t.id, 'record_priority', '优先级',
       '工作记录优先级字典', true, true, 120, 'system'
from tenant t
where not exists (
  select 1 from platform_dict_type d
  where d.tenant_id = t.id and d.dict_code = 'record_priority'
);

insert into platform_dict_type(
  id, tenant_id, dict_code, dict_name, description, system_builtin, enabled, sort_order, created_by
)
select concat('dict-', t.id, '-env-type'), t.id, 'env_type', '环境类型',
       '环境类型字典', true, true, 130, 'system'
from tenant t
where not exists (
  select 1 from platform_dict_type d
  where d.tenant_id = t.id and d.dict_code = 'env_type'
);

insert into platform_dict_type(
  id, tenant_id, dict_code, dict_name, description, system_builtin, enabled, sort_order, created_by
)
select concat('dict-', t.id, '-yes-no'), t.id, 'yes_no', '是否',
       '是否字典', true, true, 140, 'system'
from tenant t
where not exists (
  select 1 from platform_dict_type d
  where d.tenant_id = t.id and d.dict_code = 'yes_no'
);

insert into platform_dict_type(
  id, tenant_id, dict_code, dict_name, description, system_builtin, enabled, sort_order, created_by
)
select concat('dict-', t.id, '-process-result'), t.id, 'process_result', '处理结果',
       '处理结果字典', true, true, 150, 'system'
from tenant t
where not exists (
  select 1 from platform_dict_type d
  where d.tenant_id = t.id and d.dict_code = 'process_result'
);
