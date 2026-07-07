-- 工作记录独立 schema 占位（Phase WR-1 §6.15.3）
--
-- 本次先创建 schema 容器，为后续物理迁移预留空间。
-- 当前所有 wr_* 表仍在 public schema，由 V0012 创建。
-- 物理迁移（ALTER TABLE SET SCHEMA）待 Phase 2 执行，届时：
--   1. 将 public.wr_* 迁移到 work_record.wr_*
--   2. Repository SQL 改为带 work_record. 前缀
--   3. jOOQ codegen 同步更新支持多 schema 合并
--
-- jOOQ DDLDatabase 当前使用 unqualifiedSchema=public，所有 wr_* 表在 public schema
-- 下被正确解析和生成代码。待物理迁移完成后重新评估 codegen 配置。

create schema if not exists work_record;

-- 占位查询（Flyway 要求迁移文件非空）
select 1;
