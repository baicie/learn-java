alter table sys_workspace add column if not exists tenant_id varchar(64);

create table if not exists sys_workspace_member (
  id varchar(64) primary key,
  tenant_id varchar(64) not null references tenant(id) on delete cascade,
  workspace_id varchar(64) not null references sys_workspace(id) on delete cascade,
  user_id varchar(64) not null references sys_user(id) on delete cascade,
  role varchar(32) not null default 'member',
  created_at timestamptz not null default now(),
  unique(workspace_id, user_id)
);

create table if not exists platform_menu_item (
  id varchar(64) primary key,
  module_id varchar(64) not null,
  parent_id varchar(64),
  path varchar(256) not null,
  title varchar(128) not null,
  icon varchar(64),
  permission_code varchar(128),
  sort_order int not null default 100,
  enabled boolean not null default true,
  created_at timestamptz not null default now(),
  unique(module_id, path)
);

create index if not exists idx_workspace_member_tenant_user on sys_workspace_member(tenant_id, user_id);
create index if not exists idx_platform_menu_enabled_sort on platform_menu_item(enabled, sort_order);
