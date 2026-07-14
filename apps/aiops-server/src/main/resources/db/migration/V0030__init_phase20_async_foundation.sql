-- Phase 20.0: lease-safe outbox delivery and tenant-scoped asynchronous task foundation.

alter table automation_outbox
    add column if not exists available_at timestamptz;

alter table automation_outbox
    add column if not exists lease_until timestamptz;

alter table automation_outbox
    add column if not exists idempotency_key varchar(128);

update automation_outbox
   set available_at = coalesce(created_at, now())
 where available_at is null;

alter table automation_outbox
    alter column available_at set default now();

alter table automation_outbox
    alter column available_at set not null;

create unique index if not exists uq_automation_outbox_idempotency
    on automation_outbox (target_app, job_name, idempotency_key)
    where idempotency_key is not null;

create index if not exists idx_automation_outbox_claim
    on automation_outbox (target_app, status, available_at, created_at)
    where status = 'pending';

create index if not exists idx_automation_outbox_lease
    on automation_outbox (target_app, lease_until)
    where status = 'processing';

create table if not exists work_record.wr_async_job (
    id varchar(64) primary key,
    tenant_id varchar(64) not null,
    job_type varchar(48) not null,
    status varchar(24) not null default 'queued',
    requested_by varchar(64) not null,
    request_json jsonb not null default '{}'::jsonb,
    result_json jsonb not null default '{}'::jsonb,
    source_object_key varchar(512),
    total_count integer not null default 0,
    processed_count integer not null default 0,
    success_count integer not null default 0,
    failure_count integer not null default 0,
    result_object_key varchar(512),
    result_file_name varchar(255),
    result_content_type varchar(128),
    error_object_key varchar(512),
    idempotency_key varchar(128),
    error_message varchar(2000),
    row_version bigint not null default 0,
    started_at timestamptz,
    finished_at timestamptz,
    expires_at timestamptz,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    constraint fk_wr_async_job_tenant foreign key (tenant_id)
        references public.tenant(id) on delete restrict,
    constraint uq_wr_async_job_tenant_id unique (tenant_id, id),
    constraint ck_wr_async_job_type check
        (job_type in ('excel_import', 'excel_export', 'report_export', 'ai_summary', 'ai_monthly_report')),
    constraint ck_wr_async_job_status check
        (status in ('queued', 'processing', 'succeeded', 'partially_succeeded', 'failed', 'cancelled', 'expired')),
    constraint ck_wr_async_job_counts check
        (total_count >= 0 and processed_count >= 0 and success_count >= 0 and failure_count >= 0
         and processed_count <= total_count and success_count + failure_count <= processed_count),
    constraint ck_wr_async_job_request_json check (jsonb_typeof(request_json) = 'object'),
    constraint ck_wr_async_job_result_json check (jsonb_typeof(result_json) = 'object')
);

create unique index if not exists uq_wr_async_job_idempotency
    on work_record.wr_async_job (tenant_id, job_type, idempotency_key)
    where idempotency_key is not null;

create index if not exists idx_wr_async_job_tenant_created
    on work_record.wr_async_job (tenant_id, created_at desc, id desc);

create index if not exists idx_wr_async_job_tenant_status
    on work_record.wr_async_job (tenant_id, status, created_at desc);

create table if not exists work_record.wr_async_job_item (
    id varchar(64) primary key,
    tenant_id varchar(64) not null,
    job_id varchar(64) not null,
    item_key varchar(160) not null,
    row_number integer,
    status varchar(24) not null default 'pending',
    resource_id varchar(64),
    error_code varchar(96),
    error_message varchar(2000),
    detail_json jsonb not null default '{}'::jsonb,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    constraint fk_wr_async_job_item_job foreign key (tenant_id, job_id)
        references work_record.wr_async_job(tenant_id, id) on delete cascade,
    constraint uq_wr_async_job_item unique (job_id, item_key),
    constraint ck_wr_async_job_item_row check (row_number is null or row_number > 0),
    constraint ck_wr_async_job_item_status check
        (status in ('pending', 'succeeded', 'failed', 'skipped')),
    constraint ck_wr_async_job_item_detail check (jsonb_typeof(detail_json) = 'object')
);

create index if not exists idx_wr_async_job_item_job_status
    on work_record.wr_async_job_item (tenant_id, job_id, status, row_number);
