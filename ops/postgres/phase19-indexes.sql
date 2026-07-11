\set ON_ERROR_STOP on

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
