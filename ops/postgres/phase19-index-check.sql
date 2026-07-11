-- Phase 19：检查 work_record 模式下的关键索引是否存在且有效。
-- 任意索引缺失或 indisvalid=false/indisready=false，脚本直接 raise exception 失败。

\set ON_ERROR_STOP on

do $$
declare
    missing text[];
begin
    select array_agg(required.name)
      into missing
    from (
        values
            ('idx_wr_record_tenant_time_live'),
            ('idx_wr_record_tenant_created_live'),
            ('idx_wr_record_tenant_template_time_live'),
            ('idx_wr_record_tenant_version_time_live'),
            ('idx_wr_record_tenant_status_time_live'),
            ('idx_wr_record_tenant_owner_time_live'),
            ('idx_wr_record_tenant_creator_time_live'),
            ('idx_wr_record_title_trgm_live'),
            ('idx_wr_record_custom_jsonb_live'),
            ('idx_wr_template_tenant_updated_live'),
            ('idx_wr_field_tenant_version_sort')
    ) required(name)
    where not exists (
        select 1
          from pg_class index_class
          join pg_namespace namespace
            on namespace.oid = index_class.relnamespace
          join pg_index index_state
            on index_state.indexrelid = index_class.oid
         where namespace.nspname = 'work_record'
           and index_class.relname = required.name
           and index_state.indisvalid = true
           and index_state.indisready = true
    );

    if missing is not null then
        raise exception 'missing or invalid Phase 19 indexes: %', missing;
    end if;
end
$$;
