-- 工作记录字段增加 exportable 标志（Phase WR-7 收尾）
--
-- 背景：
--   Phase WR-5 引入 4 位权限位（view/edit/require/export）但字段表只存
--   filterable / listVisible / statistical 三个布尔位。WorkRecordExportService
--   此前只校验 `work-record:export` 权限位，对字段粒度"该字段能不能导出"无任何约束。
--   审查报告 P0-2 要求补齐字段级 exportable。
--
-- 设计：
--   新增列 exportable boolean not null default true：
--     - 旧字段默认 true（保持现有导出能力，不破坏现有数据）
--     - WorkRecordExportService 按 exportable=false 过滤自定义 columns
--     - WorkRecordQueryService 在 RecordListColumn / RecordListFilterField 中返回 exportable
--     - 前端 ExportRecordsDialog 过滤掉 exportable=false 的字段
--
-- 与现有权限位的关系：
--   - "能否进入导出流程"由 work-record:export 权限位控制（不变化）
--   - "具体哪些字段能导出"由本列控制（新增）

alter table wr_template_field
  add column if not exists exportable boolean not null default true;
