alter table work_record.wr_ai_generation
    add column if not exists provider_run_id varchar(128),
    add column if not exists provider_workflow_id varchar(128),
    add column if not exists provider_workflow_version varchar(128),
    add column if not exists provider_duration_ms bigint,
    add column if not exists provider_total_tokens bigint,
    add column if not exists warnings_json jsonb not null default '[]'::jsonb,
    add column if not exists fallback_reason varchar(128);

alter table work_record.wr_ai_generation
    add constraint ck_wr_ai_generation_warnings
    check (jsonb_typeof(warnings_json) = 'array');
