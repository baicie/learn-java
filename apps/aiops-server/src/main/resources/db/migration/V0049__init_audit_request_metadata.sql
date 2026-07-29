alter table public.audit_log
    add column if not exists request_id varchar(128);

alter table public.audit_log
    add column if not exists ip text;

alter table public.audit_log
    add column if not exists user_agent text;

create index if not exists idx_audit_log_request_history
    on public.audit_log(tenant_id, request_id, created_at desc)
    where request_id is not null;
