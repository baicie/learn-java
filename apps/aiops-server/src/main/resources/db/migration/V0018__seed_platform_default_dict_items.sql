-- Phase 2 fix: seed default dictionary items.
-- Idempotent for existing tenants.

-- record_type
insert into platform_dict_item(
  id, tenant_id, dict_type_id, item_label, item_value, color, icon, description,
  system_builtin, enabled, sort_order, extra_json, created_by
)
select concat('di-', md5(t.id || ':record-type-daily')), t.id, d.id,
       '日常记录', 'daily', null, null, '日常工作记录',
       true, true, 10, '{}'::jsonb, 'system'
from tenant t
join platform_dict_type d on d.tenant_id = t.id and d.dict_code = 'record_type'
where not exists (
  select 1 from platform_dict_item i
  where i.tenant_id = t.id and i.dict_type_id = d.id and i.item_value = 'daily'
);

insert into platform_dict_item(
  id, tenant_id, dict_type_id, item_label, item_value, color, icon, description,
  system_builtin, enabled, sort_order, extra_json, created_by
)
select concat('di-', md5(t.id || ':record-type-fault')), t.id, d.id,
       '故障记录', 'fault', null, null, '故障处理记录',
       true, true, 20, '{}'::jsonb, 'system'
from tenant t
join platform_dict_type d on d.tenant_id = t.id and d.dict_code = 'record_type'
where not exists (
  select 1 from platform_dict_item i
  where i.tenant_id = t.id and i.dict_type_id = d.id and i.item_value = 'fault'
);

insert into platform_dict_item(
  id, tenant_id, dict_type_id, item_label, item_value, color, icon, description,
  system_builtin, enabled, sort_order, extra_json, created_by
)
select concat('di-', md5(t.id || ':record-type-inspection')), t.id, d.id,
       '巡检记录', 'inspection', null, null, '巡检工作记录',
       true, true, 30, '{}'::jsonb, 'system'
from tenant t
join platform_dict_type d on d.tenant_id = t.id and d.dict_code = 'record_type'
where not exists (
  select 1 from platform_dict_item i
  where i.tenant_id = t.id and i.dict_type_id = d.id and i.item_value = 'inspection'
);

insert into platform_dict_item(
  id, tenant_id, dict_type_id, item_label, item_value, color, icon, description,
  system_builtin, enabled, sort_order, extra_json, created_by
)
select concat('di-', md5(t.id || ':record-type-change')), t.id, d.id,
       '变更记录', 'change', null, null, '变更处理记录',
       true, true, 40, '{}'::jsonb, 'system'
from tenant t
join platform_dict_type d on d.tenant_id = t.id and d.dict_code = 'record_type'
where not exists (
  select 1 from platform_dict_item i
  where i.tenant_id = t.id and i.dict_type_id = d.id and i.item_value = 'change'
);

-- record_status
insert into platform_dict_item(
  id, tenant_id, dict_type_id, item_label, item_value, color, icon, description,
  system_builtin, enabled, sort_order, extra_json, created_by
)
select concat('di-', md5(t.id || ':record-status-draft')), t.id, d.id,
       '草稿', 'draft', null, null, '草稿状态',
       true, true, 10, '{}'::jsonb, 'system'
from tenant t
join platform_dict_type d on d.tenant_id = t.id and d.dict_code = 'record_status'
where not exists (
  select 1 from platform_dict_item i
  where i.tenant_id = t.id and i.dict_type_id = d.id and i.item_value = 'draft'
);

insert into platform_dict_item(
  id, tenant_id, dict_type_id, item_label, item_value, color, icon, description,
  system_builtin, enabled, sort_order, extra_json, created_by
)
select concat('di-', md5(t.id || ':record-status-done')), t.id, d.id,
       '已完成', 'done', null, null, '已完成状态',
       true, true, 20, '{}'::jsonb, 'system'
from tenant t
join platform_dict_type d on d.tenant_id = t.id and d.dict_code = 'record_status'
where not exists (
  select 1 from platform_dict_item i
  where i.tenant_id = t.id and i.dict_type_id = d.id and i.item_value = 'done'
);

insert into platform_dict_item(
  id, tenant_id, dict_type_id, item_label, item_value, color, icon, description,
  system_builtin, enabled, sort_order, extra_json, created_by
)
select concat('di-', md5(t.id || ':record-status-archived')), t.id, d.id,
       '已归档', 'archived', null, null, '已归档状态',
       true, true, 30, '{}'::jsonb, 'system'
from tenant t
join platform_dict_type d on d.tenant_id = t.id and d.dict_code = 'record_status'
where not exists (
  select 1 from platform_dict_item i
  where i.tenant_id = t.id and i.dict_type_id = d.id and i.item_value = 'archived'
);

-- record_priority
insert into platform_dict_item(
  id, tenant_id, dict_type_id, item_label, item_value, color, icon, description,
  system_builtin, enabled, sort_order, extra_json, created_by
)
select concat('di-', md5(t.id || ':priority-p0')), t.id, d.id,
       'P0', 'P0', null, null, '最高优先级',
       true, true, 10, '{}'::jsonb, 'system'
from tenant t
join platform_dict_type d on d.tenant_id = t.id and d.dict_code = 'record_priority'
where not exists (
  select 1 from platform_dict_item i
  where i.tenant_id = t.id and i.dict_type_id = d.id and i.item_value = 'P0'
);

insert into platform_dict_item(
  id, tenant_id, dict_type_id, item_label, item_value, color, icon, description,
  system_builtin, enabled, sort_order, extra_json, created_by
)
select concat('di-', md5(t.id || ':priority-p1')), t.id, d.id,
       'P1', 'P1', null, null, '高优先级',
       true, true, 20, '{}'::jsonb, 'system'
from tenant t
join platform_dict_type d on d.tenant_id = t.id and d.dict_code = 'record_priority'
where not exists (
  select 1 from platform_dict_item i
  where i.tenant_id = t.id and i.dict_type_id = d.id and i.item_value = 'P1'
);

insert into platform_dict_item(
  id, tenant_id, dict_type_id, item_label, item_value, color, icon, description,
  system_builtin, enabled, sort_order, extra_json, created_by
)
select concat('di-', md5(t.id || ':priority-p2')), t.id, d.id,
       'P2', 'P2', null, null, '中优先级',
       true, true, 30, '{}'::jsonb, 'system'
from tenant t
join platform_dict_type d on d.tenant_id = t.id and d.dict_code = 'record_priority'
where not exists (
  select 1 from platform_dict_item i
  where i.tenant_id = t.id and i.dict_type_id = d.id and i.item_value = 'P2'
);

insert into platform_dict_item(
  id, tenant_id, dict_type_id, item_label, item_value, color, icon, description,
  system_builtin, enabled, sort_order, extra_json, created_by
)
select concat('di-', md5(t.id || ':priority-p3')), t.id, d.id,
       'P3', 'P3', null, null, '低优先级',
       true, true, 40, '{}'::jsonb, 'system'
from tenant t
join platform_dict_type d on d.tenant_id = t.id and d.dict_code = 'record_priority'
where not exists (
  select 1 from platform_dict_item i
  where i.tenant_id = t.id and i.dict_type_id = d.id and i.item_value = 'P3'
);

-- env_type
insert into platform_dict_item(
  id, tenant_id, dict_type_id, item_label, item_value, color, icon, description,
  system_builtin, enabled, sort_order, extra_json, created_by
)
select concat('di-', md5(t.id || ':env-prod')), t.id, d.id,
       '生产', 'prod', null, null, '生产环境',
       true, true, 10, '{}'::jsonb, 'system'
from tenant t
join platform_dict_type d on d.tenant_id = t.id and d.dict_code = 'env_type'
where not exists (
  select 1 from platform_dict_item i
  where i.tenant_id = t.id and i.dict_type_id = d.id and i.item_value = 'prod'
);

insert into platform_dict_item(
  id, tenant_id, dict_type_id, item_label, item_value, color, icon, description,
  system_builtin, enabled, sort_order, extra_json, created_by
)
select concat('di-', md5(t.id || ':env-staging')), t.id, d.id,
       '预发', 'staging', null, null, '预发环境',
       true, true, 20, '{}'::jsonb, 'system'
from tenant t
join platform_dict_type d on d.tenant_id = t.id and d.dict_code = 'env_type'
where not exists (
  select 1 from platform_dict_item i
  where i.tenant_id = t.id and i.dict_type_id = d.id and i.item_value = 'staging'
);

insert into platform_dict_item(
  id, tenant_id, dict_type_id, item_label, item_value, color, icon, description,
  system_builtin, enabled, sort_order, extra_json, created_by
)
select concat('di-', md5(t.id || ':env-test')), t.id, d.id,
       '测试', 'test', null, null, '测试环境',
       true, true, 30, '{}'::jsonb, 'system'
from tenant t
join platform_dict_type d on d.tenant_id = t.id and d.dict_code = 'env_type'
where not exists (
  select 1 from platform_dict_item i
  where i.tenant_id = t.id and i.dict_type_id = d.id and i.item_value = 'test'
);

-- yes_no
insert into platform_dict_item(
  id, tenant_id, dict_type_id, item_label, item_value, color, icon, description,
  system_builtin, enabled, sort_order, extra_json, created_by
)
select concat('di-', md5(t.id || ':yes')), t.id, d.id,
       '是', 'yes', null, null, null,
       true, true, 10, '{}'::jsonb, 'system'
from tenant t
join platform_dict_type d on d.tenant_id = t.id and d.dict_code = 'yes_no'
where not exists (
  select 1 from platform_dict_item i
  where i.tenant_id = t.id and i.dict_type_id = d.id and i.item_value = 'yes'
);

insert into platform_dict_item(
  id, tenant_id, dict_type_id, item_label, item_value, color, icon, description,
  system_builtin, enabled, sort_order, extra_json, created_by
)
select concat('di-', md5(t.id || ':no')), t.id, d.id,
       '否', 'no', null, null, null,
       true, true, 20, '{}'::jsonb, 'system'
from tenant t
join platform_dict_type d on d.tenant_id = t.id and d.dict_code = 'yes_no'
where not exists (
  select 1 from platform_dict_item i
  where i.tenant_id = t.id and i.dict_type_id = d.id and i.item_value = 'no'
);

-- process_result
insert into platform_dict_item(
  id, tenant_id, dict_type_id, item_label, item_value, color, icon, description,
  system_builtin, enabled, sort_order, extra_json, created_by
)
select concat('di-', md5(t.id || ':process-success')), t.id, d.id,
       '已解决', 'resolved', null, null, null,
       true, true, 10, '{}'::jsonb, 'system'
from tenant t
join platform_dict_type d on d.tenant_id = t.id and d.dict_code = 'process_result'
where not exists (
  select 1 from platform_dict_item i
  where i.tenant_id = t.id and i.dict_type_id = d.id and i.item_value = 'resolved'
);

insert into platform_dict_item(
  id, tenant_id, dict_type_id, item_label, item_value, color, icon, description,
  system_builtin, enabled, sort_order, extra_json, created_by
)
select concat('di-', md5(t.id || ':process-follow-up')), t.id, d.id,
       '需跟进', 'follow_up', null, null, null,
       true, true, 20, '{}'::jsonb, 'system'
from tenant t
join platform_dict_type d on d.tenant_id = t.id and d.dict_code = 'process_result'
where not exists (
  select 1 from platform_dict_item i
  where i.tenant_id = t.id and i.dict_type_id = d.id and i.item_value = 'follow_up'
);
