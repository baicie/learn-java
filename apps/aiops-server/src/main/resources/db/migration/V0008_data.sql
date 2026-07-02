/* jooq:ignore */

-- Data initialization + procedural migration.
-- Only runs via Flyway (PostgreSQL); jOOQ codegen uses V0008 DDL only.

-- Initialize platform_menu_item data.
insert into platform_menu_item(id, module_id, path, title, icon, permission_code, sort_order)
values
('menu-workbench', 'platform', '/app/workbench', '工作台', 'layout-dashboard', 'incident:read', 10),
('menu-alerts', 'alert', '/app/alerts', '告警中心', 'bell', 'alert:read', 20),
('menu-incidents', 'incident', '/app/incidents', '故障中心', 'siren', 'incident:read', 30),
('menu-evidence', 'evidence', '/app/evidence', '证据中心', 'database', 'incident:read', 40),
('menu-reports', 'report', '/app/reports', '报告中心', 'file-text', 'incident:read', 50),
('menu-datasources', 'datasource', '/app/datasources', '数据源中心', 'plug', 'datasource:read', 60),
('menu-users', 'platform', '/app/platform/users', '用户管理', 'users', 'admin:manage', 900),
('menu-roles', 'platform', '/app/platform/roles', '角色权限', 'shield', 'admin:manage', 910),
('menu-modules', 'platform', '/app/modules', '模块管理', 'blocks', 'admin:manage', 920),
('menu-audit', 'audit', '/app/audit', '审计日志', 'scroll-text', 'audit:read', 930)
on conflict(module_id, path) do nothing;

-- Procedural migration: backfill tenant_id and add FK constraint.
do $$
declare
  default_tenant_id varchar(64);
begin
  select id into default_tenant_id
  from tenant
  order by created_at asc
  limit 1;

  if default_tenant_id is null
     and exists (select 1 from sys_workspace where tenant_id is null) then
    raise exception 'Cannot migrate sys_workspace: tenant table is empty. Ensure tenant data exists before running this migration.';
  end if;

  update sys_workspace
  set tenant_id = default_tenant_id
  where tenant_id is null;
end $$;

-- Make NOT NULL after backfill.
alter table sys_workspace alter column tenant_id set not null;

-- Conditional FK constraint (DO block needed for IF NOT EXISTS check).
do $$
begin
  if not exists (
    select 1
    from pg_constraint
    where conname = 'fk_sys_workspace_tenant'
      and conrelid = 'sys_workspace'::regclass
  ) then
    alter table sys_workspace
      add constraint fk_sys_workspace_tenant
      foreign key (tenant_id) references tenant(id) on delete cascade;
  end if;
end $$;
