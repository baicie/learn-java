-- 工作记录 schema 容器（Phase WR-1 §6.15.3 预备 + ADR 0004 阶段性决议）
--
-- 本次仅创建 work_record schema 容器；所有 wr_* 表当前仍在 public schema，由
-- V0012 创建并由 V0015 扩展。事实标准是 public.wr_*，与 jOOQ DDLDatabase 的
-- unqualifiedSchema=public 一致。
--
-- 真实迁移到 work_record schema 推迟到独立 Phase WR-S1（见 ADR 0004），届时：
--   1. ALTER TABLE ... SET SCHEMA work_record（wr_template / wr_template_field / wr_record）
--   2. 同步所有 Repository / Initializer / Test 代码的 SQL 加 work_record. 前缀
--   3. 更新 jOOQ DDLDatabase / codegen 配置
--   4. 补集成测试断言 schema 切换后表与代码一致
--
-- 在 Phase WR-S1 落地之前，新代码必须继续使用裸 wr_* 表名以匹配 V0012 事实标准。
-- 禁止"裸表名 + work_record. 前缀"混合使用（ADR 0004 决策 4）。

create schema if not exists work_record;

-- 占位查询（Flyway 要求迁移文件非空）
select 1;
