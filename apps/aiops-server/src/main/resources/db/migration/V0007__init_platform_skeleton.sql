create table if not exists platform_module (
  id varchar(64) primary key,
  module_id varchar(64) not null unique,
  name varchar(128) not null,
  version varchar(32) not null,
  enabled boolean not null default true,
  health_status varchar(32) not null default 'UNKNOWN',
  config_json text not null default '{}',
  created_at timestamptz not null default now()
);

create table if not exists sys_workspace (
  id varchar(64) primary key,
  code varchar(64) not null unique,
  name varchar(128) not null,
  enabled boolean not null default true,
  created_at timestamptz not null default now()
);

create table if not exists sys_audit_log (
  id varchar(64) primary key,
  actor varchar(128) not null,
  action varchar(128) not null,
  resource_type varchar(64) not null,
  resource_id varchar(128),
  detail_json text not null default '{}',
  created_at timestamptz not null default now()
);

create index if not exists idx_platform_module_module_id on platform_module(module_id);
create index if not exists idx_platform_module_health_status on platform_module(health_status);
create index if not exists idx_sys_workspace_code on sys_workspace(code);
create index if not exists idx_sys_audit_log_actor on sys_audit_log(actor);
create index if not exists idx_sys_audit_log_action on sys_audit_log(action);
create index if not exists idx_sys_audit_log_created on sys_audit_log(created_at desc);

insert into sys_workspace (id, code, name, enabled, created_at)
select 'wksp-default', 'default', '默认空间', true, now()
where not exists (select 1 from sys_workspace where code = 'default');

insert into platform_module (id, module_id, name, version, enabled, health_status, created_at)
select 'mod-platform', 'platform', '平台底座', '1.0.0', true, 'HEALTHY', now()
where not exists (select 1 from platform_module where module_id = 'platform');
