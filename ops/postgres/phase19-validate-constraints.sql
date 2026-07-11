-- Phase 19：在历史脏数据被清理干净后，
-- 把 NOT VALID 约束升级为真正生效的约束。
-- 必须先执行 phase19-validate-precheck.sql 确认无违例行。

alter table work_record.wr_template
    validate constraint ck_wr_template_draft_schema_object;

alter table work_record.wr_template
    validate constraint ck_wr_template_draft_schema_size;

alter table work_record.wr_template
    validate constraint ck_wr_template_draft_designer_size;

alter table work_record.wr_template_version
    validate constraint ck_wr_template_version_schema_object;

alter table work_record.wr_template_version
    validate constraint ck_wr_template_version_schema_size;

alter table work_record.wr_template_version
    validate constraint ck_wr_template_version_designer_size;

alter table work_record.wr_template_version
    validate constraint ck_wr_template_version_field_index_size;

alter table work_record.wr_record
    validate constraint ck_wr_record_builtin_object;

alter table work_record.wr_record
    validate constraint ck_wr_record_builtin_size;

alter table work_record.wr_record
    validate constraint ck_wr_record_custom_object;

alter table work_record.wr_record
    validate constraint ck_wr_record_custom_size;