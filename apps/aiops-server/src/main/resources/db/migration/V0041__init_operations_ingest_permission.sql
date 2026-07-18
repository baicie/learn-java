-- Register the complete AIOps permission catalog used by Portal IAM and protect telemetry ingest.
insert into iam.permission_definition(
  permission_code,module_code,permission_name,description,risk_level,
  dependencies_json,sort_order,enabled
) values
  ('datasource:read','datasource','读取数据源','查看数据源与同步记录','normal','[]'::jsonb,100,true),
  ('datasource:write','datasource','维护数据源','创建、测试和同步数据源','sensitive','["datasource:read"]'::jsonb,110,true),
  ('datasource:ingest','datasource','摄取可观测数据','写入 OTel、RUM 与变更事件','high','[]'::jsonb,120,true),
  ('asset:read','asset','读取资源','查看资源、来源身份和资源关系','normal','[]'::jsonb,210,true),
  ('asset:write','asset','维护资源','创建、编辑、归档资源并维护资源关系','sensitive','["asset:read"]'::jsonb,220,true),
  ('asset:import','asset','导入资源','预览并确认 CSV 资源批量导入','high','["asset:write"]'::jsonb,230,true),
  ('alert:read','alert','读取告警','查看告警事件','normal','[]'::jsonb,300,true),
  ('alert:write','alert','维护告警','摄取和修改告警事件','sensitive','["alert:read"]'::jsonb,310,true),
  ('incident:read','incident','读取 Incident','查看 Incident、时间线与证据','normal','[]'::jsonb,400,true),
  ('incident:write','incident','维护 Incident','更新 Incident 状态、时间线与复盘','sensitive','["incident:read"]'::jsonb,410,true),
  ('incident:diagnose','incident','诊断 Incident','执行 RCA 与 AI 诊断','sensitive','["incident:read"]'::jsonb,420,true),
  ('runbook:read','runbook','读取 Runbook','查看 Runbook 与执行步骤','normal','[]'::jsonb,500,true),
  ('runbook:write','runbook','维护 Runbook','创建、编辑和禁用 Runbook','high','["runbook:read"]'::jsonb,510,true),
  ('automation:read','automation','读取自动化','查看自动化任务、日志与报告','normal','[]'::jsonb,600,true),
  ('automation:approve','automation','审批自动化','审批自动化与回滚任务','critical','["automation:read"]'::jsonb,610,true),
  ('automation:execute','automation','执行自动化','创建和执行自动化与回滚任务','critical','["automation:read"]'::jsonb,620,true),
  ('audit:read','audit','读取审计','查看安全与操作审计日志','sensitive','[]'::jsonb,700,true),
  ('admin:manage','admin','系统管理','管理租户、用户、权限和系统配置','critical','[]'::jsonb,800,true)
on conflict(permission_code) do update set
  module_code=excluded.module_code,
  permission_name=excluded.permission_name,
  description=excluded.description,
  risk_level=excluded.risk_level,
  dependencies_json=excluded.dependencies_json,
  sort_order=excluded.sort_order,
  enabled=true,
  updated_at=now();

insert into iam.permission(
  permission_code,permission_name,module_code,resource_type,description,enabled
)
select permission_code,permission_name,module_code,'ACTION',description,true
from iam.permission_definition
where module_code in('datasource','asset','alert','incident','runbook','automation','audit','admin')
on conflict(permission_code) do update set
  permission_name=excluded.permission_name,
  module_code=excluded.module_code,
  resource_type=excluded.resource_type,
  description=excluded.description,
  enabled=true,
  updated_at=now();

insert into iam.role_permission(tenant_id,role_code,permission_code)
select r.tenant_id,r.role_code,p.permission_code
from iam.role_definition r
cross join iam.permission p
where r.role_code='system_admin'
  and r.deleted_at is null
  and p.module_code in('datasource','asset','alert','incident','runbook','automation','audit','admin')
on conflict do nothing;
