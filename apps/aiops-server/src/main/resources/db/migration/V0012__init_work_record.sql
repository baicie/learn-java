-- 工作记录模块与平台字典表
-- Phase WR-0
--
-- 所有表位于 public schema，命名带 wr_/platform_dict_ 前缀。
-- 跨模块关联仅指向 public.tenant、public.platform_dict_type 和平台自身表，
-- 避免 jOOQ H2 模拟无法解析 PostgreSQL 跨 schema 外键。

create table if not exists platform_dict_type (
  id varchar(64) primary key,
  tenant_id varchar(64) not null references tenant(id) on delete cascade,
  dict_code varchar(128) not null,
  dict_name varchar(128) not null,
  description text,
  system_builtin boolean not null default false,
  enabled boolean not null default true,
  sort_order integer not null default 0,
  created_by varchar(64),
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  unique (tenant_id, dict_code)
);

create table if not exists platform_dict_item (
  id varchar(64) primary key,
  tenant_id varchar(64) not null references tenant(id) on delete cascade,
  dict_type_id varchar(64) not null references platform_dict_type(id) on delete cascade,
  item_label varchar(128) not null,
  item_value varchar(128) not null,
  color varchar(32),
  icon varchar(64),
  description text,
  system_builtin boolean not null default false,
  enabled boolean not null default true,
  sort_order integer not null default 0,
  extra_json jsonb not null default '{}'::jsonb,
  created_by varchar(64),
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  unique (tenant_id, dict_type_id, item_value)
);

create table if not exists wr_template (
  id varchar(64) primary key,
  tenant_id varchar(64) not null references tenant(id) on delete cascade,
  name varchar(128) not null,
  code varchar(64) not null,
  description text,
  enabled boolean not null default true,
  schema_json jsonb not null default '{}'::jsonb,
  created_by varchar(64) not null,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  unique (tenant_id, code)
);

create table if not exists wr_template_field (
  id varchar(64) primary key,
  tenant_id varchar(64) not null references tenant(id) on delete cascade,
  template_id varchar(64) not null references wr_template(id) on delete cascade,
  field_name varchar(128) not null,
  field_code varchar(128) not null,
  field_type varchar(32) not null,
  required boolean not null default false,
  default_value text,
  option_source varchar(32) not null default 'static',
  dict_code varchar(128),
  options_json jsonb not null default '[]'::jsonb,
  list_visible boolean not null default false,
  filterable boolean not null default false,
  statistical boolean not null default false,
  sort_order integer not null default 0,
  enabled boolean not null default true,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  unique (tenant_id, template_id, field_code)
);

create table if not exists wr_record (
  id varchar(64) primary key,
  tenant_id varchar(64) not null references tenant(id) on delete cascade,
  template_id varchar(64) not null references wr_template(id),
  title varchar(255) not null,
  status varchar(32) not null default 'draft',
  owner_id varchar(64),
  creator_id varchar(64) not null,
  record_time timestamptz not null,
  builtin_data_json jsonb not null default '{}'::jsonb,
  custom_data_json jsonb not null default '{}'::jsonb,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  deleted_at timestamptz
);

create index if not exists idx_dict_type_tenant_code on platform_dict_type(tenant_id, dict_code);
create index if not exists idx_dict_item_type_sort on platform_dict_item(dict_type_id, enabled, sort_order);
create index if not exists idx_wr_template_tenant_enabled on wr_template(tenant_id, enabled);
create index if not exists idx_wr_field_template_sort on wr_template_field(template_id, enabled, sort_order);
create index if not exists idx_wr_record_tenant_time on wr_record(tenant_id, record_time desc);
create index if not exists idx_wr_record_tenant_owner on wr_record(tenant_id, owner_id);

-- 菜单 icon key 必须落在 web/console/src/lib/menu.ts 的 MENU_ICON_MAP 中。
insert into platform_menu_item (id, module_id, parent_id, path, title, icon, permission_code, sort_order, enabled)
select v.id, v.module_id, v.parent_id, v.path, v.title, v.icon, v.permission_code, v.sort_order, v.enabled
from (
  values
    ('menu-work-record', 'work-record', null, '/app/work-records', '工作记录', 'scroll-text', 'work-record:read', 300, true),
    ('menu-work-record-list', 'work-record', 'menu-work-record', '/app/work-records?view=list', '记录列表', 'file-text', 'work-record:read', 310, true),
    ('menu-work-record-designer', 'work-record', 'menu-work-record', '/app/work-records/designer', '表单设计', 'blocks', 'work-record:template:write', 320, true),
    ('menu-platform-dictionaries', 'platform', null, '/app/platform/dictionaries', '字典管理', 'database', 'platform:dict:write', 430, true)
) as v(id, module_id, parent_id, path, title, icon, permission_code, sort_order, enabled)
where not exists (
  select 1 from platform_menu_item m where m.id = v.id
);
