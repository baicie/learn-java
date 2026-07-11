-- ops/postgres/phase19-role-timeouts.sql
--
-- Phase 19 数据库角色超时配置：
--   statement_timeout            单条语句最大执行时长
--   lock_timeout                 等待锁的最大时长
--   idle_in_transaction_session_timeout  事务空闲最大时长（防止应用忘记提交/回滚）
--
-- 推荐：
--   statement_timeout = '5s'         单条 SQL 最长 5 秒
--   lock_timeout     = '2s'          锁等待最长 2 秒
--   idle_in_transaction_session_timeout = '30s'
--
-- 必须以超级用户（如 postgres）执行：
--
--   psql -v ON_ERROR_STOP=on \
--        -f ops/postgres/phase19-role-timeouts.sql
--
-- 执行后可使用下列命令验证：
--
--   select rolname, rolconfig
--     from pg_roles
--    where rolname = 'aegisops_app';

\set ON_ERROR_STOP on

do $$
declare
    target_role text := current_setting('phase19.target_role', true);
begin
    if target_role is null or target_role = '' then
        target_role := 'aegisops_app';
        raise notice 'using default role: %', target_role;
    end if;

    if not exists (select 1 from pg_roles where rolname = target_role) then
        raise exception 'role "%" does not exist; create it before running this script', target_role;
    end if;

    execute format('alter role %I set statement_timeout = %L',
                   target_role, '5s');
    execute format('alter role %I set lock_timeout = %L',
                   target_role, '2s');
    execute format('alter role %I set idle_in_transaction_session_timeout = %L',
                   target_role, '30s');
end
$$;
