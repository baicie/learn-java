\set ON_ERROR_STOP on

-- 清理上次 CREATE INDEX CONCURRENTLY 失败留下的同名无效索引。
-- 如果不清理，create index ... if not exists 会直接跳过，
-- 导致索引永远处于 indisvalid=false 状态而健康检查却返回 UP。
select format(
    'drop index concurrently if exists %I.%I;',
    namespace.nspname,
    index_class.relname
)
from pg_class index_class
join pg_namespace namespace
  on namespace.oid = index_class.relnamespace
join pg_index index_state
  on index_state.indexrelid = index_class.oid
where namespace.nspname = 'work_record'
  and index_class.relname in (
      'idx_wr_record_tenant_time_live',
      'idx_wr_record_tenant_created_live',
      'idx_wr_record_tenant_template_time_live',
      'idx_wr_record_tenant_version_time_live',
      'idx_wr_record_tenant_status_time_live',
      'idx_wr_record_tenant_owner_time_live',
      'idx_wr_record_tenant_creator_time_live',
      'idx_wr_record_title_trgm_live',
      'idx_wr_record_custom_jsonb_live',
      'idx_wr_template_tenant_updated_live',
      'idx_wr_field_tenant_version_sort'
  )
  and (
      index_state.indisvalid = false
      or index_state.indisready = false
  )
\gexec

create extension if not exists pg_trgm;

create index concurrently if not exists
idx_wr_record_tenant_time_live
on work_record.wr_record (
    tenant_id,
    record_time desc,
    created_at desc,
    id desc
)
where deleted_at is null;

create index concurrently if not exists
idx_wr_record_tenant_created_live
on work_record.wr_record (
    tenant_id,
    created_at desc,
    id desc
)
where deleted_at is null;

create index concurrently if not exists
idx_wr_record_tenant_template_time_live
on work_record.wr_record (
    tenant_id,
    template_id,
    record_time desc,
    created_at desc,
    id desc
)
where deleted_at is null;

create index concurrently if not exists
idx_wr_record_tenant_version_time_live
on work_record.wr_record (
    tenant_id,
    template_version_id,
    record_time desc,
    created_at desc,
    id desc
)
where deleted_at is null;

create index concurrently if not exists
idx_wr_record_tenant_status_time_live
on work_record.wr_record (
    tenant_id,
    status,
    record_time desc,
    created_at desc,
    id desc
)
where deleted_at is null;

create index concurrently if not exists
idx_wr_record_tenant_owner_time_live
on work_record.wr_record (
    tenant_id,
    owner_id,
    record_time desc,
    created_at desc,
    id desc
)
where deleted_at is null;

create index concurrently if not exists
idx_wr_record_tenant_creator_time_live
on work_record.wr_record (
    tenant_id,
    creator_id,
    record_time desc,
    created_at desc,
    id desc
)
where deleted_at is null;
create index concurrently if not exists
idx_wr_record_title_trgm_live
on work_record.wr_record
using gin (
    title gin_trgm_ops
)
where deleted_at is null;

create index concurrently if not exists
idx_wr_record_custom_jsonb_live
on work_record.wr_record
using gin (
    custom_data_json jsonb_path_ops
)
where deleted_at is null;

create index concurrently if not exists
idx_wr_template_tenant_updated_live
on work_record.wr_template (
    tenant_id,
    updated_at desc,
    id desc
)
where deleted_at is null;

create index concurrently if not exists
idx_wr_field_tenant_version_sort
on work_record.wr_template_field (
    tenant_id,
    template_version_id,
    enabled,
    sort_order,
    field_code
);

analyze work_record.wr_record;
analyze work_record.wr_template;
analyze work_record.wr_template_version;
analyze work_record.wr_template_field;
