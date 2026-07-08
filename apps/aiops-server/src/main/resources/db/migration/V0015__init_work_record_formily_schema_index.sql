-- 工作记录 Phase WR-4：template designer_json 与 field schema_path
--
-- 背景：
--   Phase WR-1 已存在 schema_json 与 wr_template_field 索引表。
--   Phase WR-4 将"门户原生字段设计器 + Formily-compatible schema"作为权威设计态，
--   并同步 wr_template_field 字段索引，因此需补充两列：
--     - wr_template.designer_json：设计器本地状态（面板展开、字段顺序 UI 草稿），
--       不作为运行态真相源，schema_json 仍是权威。
--     - wr_template_field.schema_path：字段在 schema.properties 中的位置，
--       便于后续通过 path 直接定位 JSONB 路径。设计器把字段放在 properties.<code>
--       下时 path 等于 .code；如果未来加入嵌套，必须先记录 path。

alter table wr_template
  add column if not exists designer_json jsonb not null default '{}'::jsonb;

alter table wr_template_field
  add column if not exists schema_path varchar(512);

-- 默认值：现有字段的 schema_path 默认为 '.code' 形式（properties.<code>）。
-- 由于模板与字段都强 tenant-bound，update 用子查询以保证更新顺序稳定。
update wr_template_field
   set schema_path = '.properties.' || field_code
 where schema_path is null;
