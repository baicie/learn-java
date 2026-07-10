-- Phase 14: 审计与变更追踪 - 升级 audit_log 支持 before/after/detail 分离
-- 与迁移稳定后停写 work_record.wr_record_audit_event，但保留作为兼容只读表

alter table public.audit_log
    add column if not exists before_json jsonb;

alter table public.audit_log
    add column if not exists after_json jsonb;

update public.audit_log
set before_json = '{}'::jsonb
where before_json is null;

update public.audit_log
set after_json = '{}'::jsonb
where after_json is null;

update public.audit_log
set detail_json = '{}'::jsonb
where detail_json is null;

alter table public.audit_log
    alter column before_json set default '{}'::jsonb,
    alter column before_json set not null,
    alter column after_json set default '{}'::jsonb,
    alter column after_json set not null,
    alter column detail_json set default '{}'::jsonb,
    alter column detail_json set not null;

alter table public.audit_log
    drop constraint if exists ck_audit_log_before_json_object;

alter table public.audit_log
    add constraint ck_audit_log_before_json_object
        check (jsonb_typeof(before_json) = 'object');

alter table public.audit_log
    drop constraint if exists ck_audit_log_after_json_object;

alter table public.audit_log
    add constraint ck_audit_log_after_json_object
        check (jsonb_typeof(after_json) = 'object');

alter table public.audit_log
    drop constraint if exists ck_audit_log_detail_json_object;

alter table public.audit_log
    add constraint ck_audit_log_detail_json_object
        check (jsonb_typeof(detail_json) = 'object');

create index if not exists idx_audit_log_resource_history
    on public.audit_log(
        tenant_id,
        target_type,
        target_id,
        created_at desc,
        id desc
    );

create index if not exists idx_audit_log_actor_history
    on public.audit_log(
        tenant_id,
        actor_user_id,
        created_at desc
    );

create index if not exists idx_audit_log_action_history
    on public.audit_log(
        tenant_id,
        action,
        created_at desc
    );

-- 将旧工作记录审计迁入通用审计表。
do $migration$
begin
    if to_regclass(
        'work_record.wr_record_audit_event'
    ) is not null then
        execute $sql$
            insert into public.audit_log(
                id,
                tenant_id,
                actor_user_id,
                action,
                target_type,
                target_id,
                before_json,
                after_json,
                detail_json,
                created_at
            )
            select
                id,
                tenant_id,
                actor_id,
                action,
                resource_type,
                resource_id,
                coalesce(before_json, '{}'::jsonb),
                coalesce(after_json, '{}'::jsonb),
                coalesce(detail_json, '{}'::jsonb),
                created_at
            from work_record.wr_record_audit_event
            on conflict (id) do nothing
        $sql$;
    end if;
end
$migration$;

-- 审计事件只允许追加。
create or replace function
public.prevent_audit_log_mutation()
returns trigger
language plpgsql
as $$
begin
    if current_setting(
        'app.audit_maintenance',
        true
    ) = 'on' then
        if tg_op = 'DELETE' then
            return old;
        end if;
        return new;
    end if;

    raise exception 'audit_log is append-only';
end;
$$;

drop trigger if exists
trg_audit_log_immutable
on public.audit_log;

create trigger trg_audit_log_immutable
before update or delete
on public.audit_log
for each row
execute function
public.prevent_audit_log_mutation();