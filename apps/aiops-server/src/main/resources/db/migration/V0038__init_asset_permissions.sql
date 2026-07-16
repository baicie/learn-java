-- Phase 1 asset source foundation: register resource management permissions in tenant IAM.
insert into iam.permission_definition(
  permission_code,module_code,permission_name,description,risk_level,
  dependencies_json,sort_order,enabled
) values
  ('asset:read','asset','读取资源','查看资源、来源身份和资源关系','normal','[]'::jsonb,210,true),
  ('asset:write','asset','维护资源','创建、编辑、归档资源并维护资源关系','sensitive','["asset:read"]'::jsonb,220,true),
  ('asset:import','asset','导入资源','预览并确认 CSV 资源批量导入','high','["asset:write"]'::jsonb,230,true)
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
where permission_code in('asset:read','asset:write','asset:import')
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
  and p.permission_code in('asset:read','asset:write','asset:import')
on conflict do nothing;
