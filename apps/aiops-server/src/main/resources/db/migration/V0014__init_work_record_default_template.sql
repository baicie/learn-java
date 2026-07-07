-- 工作记录默认模板 seed（Phase WR-1，§6.15.4）
-- seed 模板使用特殊共享 tenant '__seed__'。该租户在 V0015 由 backend
-- PlatformDictBootstrap 保证存在；这里的 migration 仅声明建表后的默认模板元数据。
-- 说明：因 platform_dict_type 强 FK 指向 tenant(id)，默认字典 seed 由 backend
-- DataInitializer 在首次启动时插入，避免跨迁移阶段写入没有真实租户的数据。

-- 占位 migration：保留 V0014 编号，避免破坏现有部署的版本号连续性。
select 1;