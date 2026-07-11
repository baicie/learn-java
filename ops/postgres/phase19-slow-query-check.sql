-- ops/postgres/phase19-slow-query-check.sql
--
-- Phase 19 慢查询体检：基于 pg_stat_statements 输出 wr_record / wr_template 相关
-- 命中频次、平均耗时、调用次数最高的 SQL，便于发现索引缺失或写法不优。
--
-- 前置：必须先在目标库执行
--
--   create extension if not exists pg_stat_statements;
--
-- 用法：
--
--   psql -v ON_ERROR_STOP=on \
--        -f ops/postgres/phase19-slow-query-check.sql
--
-- 输出按 mean_exec_time desc 排序的前 30 条，列依次为：
--
--   calls             调用次数
--   total_ms          该 SQL 累计耗时（毫秒）
--   mean_ms           平均耗时（毫秒）
--   rows              平均返回行数
--   query             截取前 1000 字符的 SQL 文本
--
-- 应用数据库角色应同时配置：
--
--   alter role aegisops_app set statement_timeout     = '5s';
--   alter role aegisops_app set lock_timeout         = '2s';
--   alter role aegisops_app set idle_in_transaction_session_timeout = '30s';

\set ON_ERROR_STOP on

-- 1. 慢查询 Top 30（仅工作记录模块相关表）
select
    calls,
    round(total_exec_time::numeric, 2)               as total_ms,
    round(mean_exec_time::numeric, 2)                as mean_ms,
    round((100.0 * total_exec_time / nullif(sum(total_exec_time) over (), 0))::numeric, 2)
                                                      as pct_of_total,
    rows::bigint                                      as rows,
    left(query, 1000)                                 as query
from pg_stat_statements
where query ilike '%work_record.wr_%'
   or query ilike '%public.audit_log%'
order by mean_exec_time desc
limit 30;

-- 2. 整体调用次数最高 Top 10：可能存在无谓循环调用
select
    calls,
    round(mean_exec_time::numeric, 2) as mean_ms,
    left(query, 1000) as query
from pg_stat_statements
order by calls desc
limit 10;

-- 3. 失效索引检查：与 phase19-index-check.sql 互为补充
select
    namespace.nspname as schema_name,
    index_class.relname as index_name,
    index_state.indisvalid,
    index_state.indisready,
    pg_size_pretty(pg_relation_size(index_class.oid)) as size
from pg_class index_class
join pg_namespace namespace
  on namespace.oid = index_class.relnamespace
join pg_index index_state
  on index_state.indexrelid = index_class.oid
where namespace.nspname = 'work_record'
  and (index_state.indisvalid = false
       or index_state.indisready = false)
order by index_class.relname;

-- 4. 未使用索引建议：heap scan 占比过高且索引从未被使用
select
    schemaname || '.' || indexrelname as index_name,
    idx_scan as scans,
    pg_size_pretty(pg_relation_size(indexrelid)) as size
from pg_stat_user_indexes
where schemaname in ('work_record', 'public')
  and idx_scan = 0
order by pg_relation_size(indexrelid) desc
limit 20;
