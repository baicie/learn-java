-- Phase 19：生产化数据边界。
-- NOT VALID 会立即约束新写入和被更新的行，
-- 但不会因为已有历史脏数据阻断升级。
-- 历史数据清理后再执行 ops/postgres/phase19-validate-constraints.sql。

alter table work_record.wr_template
    drop constraint if exists
    ck_wr_template_draft_schema_object;

alter table work_record.wr_template
    add constraint
    ck_wr_template_draft_schema_object
    check (
        jsonb_typeof(draft_schema_json) = 'object'
    )
    not valid;

alter table work_record.wr_template
    drop constraint if exists
    ck_wr_template_draft_schema_size;

alter table work_record.wr_template
    add constraint
    ck_wr_template_draft_schema_size
    check (
        octet_length(draft_schema_json::text)
            <= 1048576
    )
    not valid;

alter table work_record.wr_template
    drop constraint if exists
    ck_wr_template_draft_designer_size;

alter table work_record.wr_template
    add constraint
    ck_wr_template_draft_designer_size
    check (
        octet_length(draft_designer_json::text)
            <= 1048576
    )
    not valid;

alter table work_record.wr_template_version
    drop constraint if exists
    ck_wr_template_version_schema_object;

alter table work_record.wr_template_version
    add constraint
    ck_wr_template_version_schema_object
    check (
        jsonb_typeof(schema_json) = 'object'
    )
    not valid;

alter table work_record.wr_template_version
    drop constraint if exists
    ck_wr_template_version_schema_size;

alter table work_record.wr_template_version
    add constraint
    ck_wr_template_version_schema_size
    check (
        octet_length(schema_json::text)
            <= 1048576
    )
    not valid;

alter table work_record.wr_template_version
    drop constraint if exists
    ck_wr_template_version_designer_size;

alter table work_record.wr_template_version
    add constraint
    ck_wr_template_version_designer_size
    check (
        octet_length(designer_json::text)
            <= 1048576
    )
    not valid;

alter table work_record.wr_template_version
    drop constraint if exists
    ck_wr_template_version_field_index_size;

alter table work_record.wr_template_version
    add constraint
    ck_wr_template_version_field_index_size
    check (
        octet_length(field_index_json::text)
            <= 1048576
    )
    not valid;

alter table work_record.wr_record
    drop constraint if exists
    ck_wr_record_builtin_object;

alter table work_record.wr_record
    add constraint
    ck_wr_record_builtin_object
    check (
        jsonb_typeof(builtin_data_json) = 'object'
    )
    not valid;

alter table work_record.wr_record
    drop constraint if exists
    ck_wr_record_builtin_size;

alter table work_record.wr_record
    add constraint
    ck_wr_record_builtin_size
    check (
        octet_length(builtin_data_json::text)
            <= 131072
    )
    not valid;

alter table work_record.wr_record
    drop constraint if exists
    ck_wr_record_custom_object;

alter table work_record.wr_record
    add constraint
    ck_wr_record_custom_object
    check (
        jsonb_typeof(custom_data_json) = 'object'
    )
    not valid;

alter table work_record.wr_record
    drop constraint if exists
    ck_wr_record_custom_size;

alter table work_record.wr_record
    add constraint
    ck_wr_record_custom_size
    check (
        octet_length(custom_data_json::text)
            <= 262144
    )
    not valid;