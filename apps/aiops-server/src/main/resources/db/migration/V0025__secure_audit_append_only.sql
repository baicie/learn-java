-- Phase 14 修补：禁止应用会话通过 SET app.audit_maintenance = 'on' 绕过 append-only 触发器。
-- 原 V0024 函数仅在 current_setting('app.audit_maintenance', true) = 'on' 时放行，
-- 任何能执行 SET LOCAL 的应用连接都可以借此修改或删除审计记录。
-- 该 Flyway 迁移由 DBA 在维护窗口内运行，普通应用连接不应能绕过。

create or replace function
public.prevent_audit_log_mutation()
returns trigger
language plpgsql
as $$
begin
    raise exception 'audit_log is append-only';
end;
$$;

comment on function
public.prevent_audit_log_mutation()
is
'audit_log update/delete is unconditionally forbidden. '
'DBA-owned maintenance must temporarily disable the trigger '
'trg_audit_log_immutable on public.audit_log under a controlled '
'migration; application connections cannot bypass this rule.';