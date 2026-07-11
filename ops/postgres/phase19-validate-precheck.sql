-- Phase 19 precheck：在把 NOT VALID 约束升级为 VALIDATE 之前，
-- 确认所有现有行都不违反约束。任何一行违规都会让后续 VALIDATE 失败，
-- 因此必须先把违例行清理掉。
--
-- 必须先执行本脚本，才能执行 phase19-validate-constraints.sql。

\set ON_ERROR_STOP on

do $$
begin
    if exists (
        select 1
        from work_record.wr_template
        where draft_schema_json is not null
          and (
              jsonb_typeof(draft_schema_json) <> 'object'
              or octet_length(draft_schema_json::text) > 1048576
          )
    ) then
        raise exception 'wr_template contains invalid draft_schema_json';
    end if;

    if exists (
        select 1
        from work_record.wr_template
        where draft_designer_json is not null
          and octet_length(draft_designer_json::text) > 1048576
    ) then
        raise exception 'wr_template contains oversized draft_designer_json';
    end if;

    if exists (
        select 1
        from work_record.wr_template_version
        where jsonb_typeof(schema_json) <> 'object'
           or octet_length(schema_json::text) > 1048576
           or octet_length(designer_json::text) > 1048576
           or octet_length(field_index_json::text) > 1048576
    ) then
        raise exception 'wr_template_version contains invalid JSON payload';
    end if;

    if exists (
        select 1
        from work_record.wr_record
        where jsonb_typeof(builtin_data_json) <> 'object'
           or jsonb_typeof(custom_data_json) <> 'object'
           or octet_length(builtin_data_json::text) > 131072
           or octet_length(custom_data_json::text) > 262144
    ) then
        raise exception 'wr_record contains invalid JSON payload';
    end if;
end
$$;
