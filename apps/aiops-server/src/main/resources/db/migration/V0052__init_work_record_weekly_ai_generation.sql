alter table work_record.wr_ai_generation
    drop constraint if exists ck_wr_ai_generation_type;

alter table work_record.wr_ai_generation
    add constraint ck_wr_ai_generation_type
    check (generation_type in ('record_summary', 'weekly_report', 'monthly_report'));

alter table work_record.wr_ai_generation
    drop constraint if exists ck_wr_ai_generation_resource;

alter table work_record.wr_ai_generation
    add constraint ck_wr_ai_generation_resource
    check (resource_type in ('record', 'tenant_week', 'tenant_month'));

alter table work_record.wr_ai_generation
    drop constraint if exists ck_wr_ai_generation_period;

alter table work_record.wr_ai_generation
    add constraint ck_wr_ai_generation_period
    check (
      (generation_type = 'weekly_report'
        and resource_type = 'tenant_week'
        and period_start is not null
        and period_end is not null
        and period_end = period_start + 6)
      or (generation_type = 'monthly_report'
        and resource_type = 'tenant_month'
        and period_start is not null
        and period_end is not null)
      or (generation_type = 'record_summary'
        and resource_type = 'record'
        and period_start is null
        and period_end is null)
    );

update iam.permission_definition
set permission_name = '生成工作记录 AI 内容',
    description = '生成记录摘要、周报和月报草稿',
    updated_at = now()
where permission_code = 'work-record:ai:generate';

update iam.permission
set permission_name = '生成工作记录 AI 内容',
    description = '生成记录摘要、周报和月报草稿',
    updated_at = now()
where permission_code = 'work-record:ai:generate';
