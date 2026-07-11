-- Phase 19：列出 work_record 模式的关键索引是否存在，
-- 用于运维脚本或运维文档中对照生产环境。

select
    schemaname,
    tablename,
    indexname
from pg_indexes
where schemaname = 'work_record'
  and indexname in (
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
order by tablename, indexname;