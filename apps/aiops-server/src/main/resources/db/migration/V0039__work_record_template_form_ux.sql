-- Phase 21: work-record template form UX contract (after asset migrations).

alter table work_record.wr_template
  add column if not exists is_default boolean not null default false;

create unique index if not exists uq_wr_template_tenant_default
  on work_record.wr_template(tenant_id)
  where is_default = true and deleted_at is null;

comment on column work_record.wr_template.is_default is
  '租户的新建记录默认模板；每个租户最多一个。';

alter table work_record.wr_template_field
  add column if not exists column_span integer not null default 2,
  add column if not exists validation_json jsonb not null default '{}'::jsonb;

-- Repair field indexes published by the old designer, which allowed a dictionary
-- source to remain after changing an option field into a non-option field.
update work_record.wr_template_field
   set option_source = 'static',
       dict_code = null
 where field_type not in ('select', 'multi_select')
   and (option_source <> 'static' or dict_code is not null);

alter table work_record.wr_template_field
  drop constraint if exists ck_wr_template_field_column_span;

alter table work_record.wr_template_field
  add constraint ck_wr_template_field_column_span check (column_span in (1, 2));

alter table work_record.wr_template_field
  drop constraint if exists ck_wr_template_field_option_field;

alter table work_record.wr_template_field
  add constraint ck_wr_template_field_option_field
  check (
    field_type in ('select', 'multi_select')
    or (option_source = 'static' and dict_code is null)
  );

comment on column work_record.wr_template_field.column_span is
  '字段在两列表单中的占位宽度：1=半宽，2=整行。';

comment on column work_record.wr_template_field.validation_json is
  '随模板版本冻结的标准字段校验规则，不引用可变的中心规则。';
