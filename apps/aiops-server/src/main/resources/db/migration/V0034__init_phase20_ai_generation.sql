-- Phase 20.5: auditable AI work-record summaries and monthly reports.

create table if not exists work_record.wr_ai_generation (
    id varchar(64) primary key,
    tenant_id varchar(64) not null,
    generation_type varchar(32) not null,
    resource_type varchar(32) not null,
    resource_id varchar(64) not null,
    period_start date,
    period_end date,
    status varchar(24) not null default 'queued',
    prompt_version varchar(64) not null,
    input_hash varchar(64) not null,
    input_json jsonb not null default '{}'::jsonb,
    output_markdown text,
    provider varchar(128),
    model varchar(128),
    requested_by varchar(64) not null,
    reviewed_by varchar(64),
    reviewed_at timestamptz,
    created_at timestamptz not null default now(),
    finished_at timestamptz,
    constraint fk_wr_ai_requested_by foreign key (tenant_id, requested_by)
        references public.sys_user(tenant_id, id) on delete restrict,
    constraint fk_wr_ai_reviewed_by foreign key (tenant_id, reviewed_by)
        references public.sys_user(tenant_id, id) on delete restrict,
    constraint ck_wr_ai_generation_type
        check (generation_type in ('record_summary', 'monthly_report')),
    constraint ck_wr_ai_generation_resource
        check (resource_type in ('record', 'tenant_month')),
    constraint ck_wr_ai_generation_status
        check (status in ('queued', 'running', 'success', 'failed', 'accepted', 'rejected')),
    constraint ck_wr_ai_generation_input check (jsonb_typeof(input_json) = 'object'),
    constraint ck_wr_ai_generation_hash check (input_hash ~ '^[0-9a-f]{64}$'),
    constraint ck_wr_ai_generation_period
        check ((generation_type = 'monthly_report' and period_start is not null and period_end is not null)
            or generation_type = 'record_summary')
);

create unique index if not exists uq_wr_ai_generation_input
    on work_record.wr_ai_generation(
        tenant_id, generation_type, resource_type, resource_id, input_hash)
    where status in ('queued', 'running', 'success', 'accepted');
create index if not exists idx_wr_ai_generation_resource
    on work_record.wr_ai_generation(tenant_id, resource_type, resource_id, created_at desc);

insert into iam.permission_definition(
    permission_code, module_code, permission_name, description, risk_level,
    dependencies_json, sort_order, enabled)
values
    ('work-record:ai:generate', 'work-record', '生成工作记录 AI 内容',
     '生成记录摘要和月报草稿', 'high', '["work-record:read:self"]'::jsonb, 370, true),
    ('work-record:ai:review', 'work-record', '审核工作记录 AI 内容',
     '接受或拒绝 AI 生成草稿', 'sensitive', '["work-record:ai:generate"]'::jsonb, 380, true)
on conflict (permission_code) do update set
    permission_name=excluded.permission_name, description=excluded.description,
    risk_level=excluded.risk_level, dependencies_json=excluded.dependencies_json,
    sort_order=excluded.sort_order, enabled=true, updated_at=now();

insert into iam.permission(
    permission_code, permission_name, module_code, resource_type, description, enabled)
select permission_code, permission_name, module_code, 'ACTION', description, true
from iam.permission_definition
where permission_code in ('work-record:ai:generate', 'work-record:ai:review')
on conflict (permission_code) do update set
    permission_name=excluded.permission_name, description=excluded.description,
    enabled=true, updated_at=now();

insert into iam.role_permission(tenant_id, role_code, permission_code)
select r.tenant_id, r.role_code, p.permission_code
from iam.role_definition r cross join iam.permission p
where r.role_code in ('system_admin', 'record_admin') and r.deleted_at is null
  and p.permission_code in ('work-record:ai:generate', 'work-record:ai:review')
on conflict do nothing;
