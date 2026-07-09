-- Phase 3: Enterprise work record schema.
--
-- Current baseline:
--   - Old work-record tables are public.wr_template / public.wr_template_field / public.wr_record.
--   - Old records only bind template_id, not template_version_id.
--   - This migration creates work_record schema and migrates old public.wr_* data into it.
--
-- Important:
--   - Do NOT drop public.wr_* in this phase.
--   - New code after Phase 3 must target work_record.wr_*.
--   - Old public.wr_* remain as rollback/reference tables until a later cleanup phase.

create schema if not exists work_record;

-- ---------------------------------------------------------------------------
-- 1. Trigger helpers
-- ---------------------------------------------------------------------------

create or replace function work_record.touch_updated_at()
returns trigger
language plpgsql
as $$
begin
  new.updated_at = now();
  return new;
end;
$$;

create or replace function work_record.reject_physical_delete()
returns trigger
language plpgsql
as $$
begin
  raise exception 'physical delete is forbidden on %.%; use enabled=false or deleted_at instead',
    tg_table_schema, tg_table_name;
end;
$$;

create or replace function work_record.reject_field_code_update()
returns trigger
language plpgsql
as $$
begin
  if old.field_code is distinct from new.field_code then
    raise exception 'field_code is immutable once created: % -> %', old.field_code, new.field_code;
  end if;
  return new;
end;
$$;

-- Platform dictionary is shared platform data. It must be soft-disabled, not deleted.
create or replace function public.reject_platform_dict_item_delete()
returns trigger
language plpgsql
as $$
begin
  raise exception 'physical delete is forbidden on platform_dict_item; use enabled=false instead';
end;
$$;

drop trigger if exists trg_platform_dict_item_no_delete on public.platform_dict_item;
create trigger trg_platform_dict_item_no_delete
before delete on public.platform_dict_item
for each row execute function public.reject_platform_dict_item_delete();

-- ---------------------------------------------------------------------------
-- 2. Template metadata
-- ---------------------------------------------------------------------------

create table if not exists work_record.wr_template (
  id varchar(64) primary key,
  tenant_id varchar(64) not null references public.tenant(id) on delete cascade,

  code varchar(64) not null,
  name varchar(128) not null,
  description text,

  status varchar(32) not null default 'draft',
  enabled boolean not null default true,

  current_version_id varchar(64),

  draft_schema_json jsonb not null default '{}'::jsonb,
  draft_designer_json jsonb not null default '{}'::jsonb,

  created_by varchar(64) not null,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  deleted_at timestamptz,

  constraint ck_wr_template_status
    check (status in ('draft', 'published', 'disabled', 'archived')),
  constraint uq_wr_template_tenant_code unique (tenant_id, code)
);

comment on table work_record.wr_template is '工作记录模板元信息。草稿在 draft_schema_json / draft_designer_json 中，发布后进入 wr_template_version。';
comment on column work_record.wr_template.current_version_id is '当前发布版本，Phase 3 建表后通过 alter table 加 FK。';

-- ---------------------------------------------------------------------------
-- 3. Template version
-- ---------------------------------------------------------------------------

create table if not exists work_record.wr_template_version (
  id varchar(64) primary key,
  tenant_id varchar(64) not null references public.tenant(id) on delete cascade,
  template_id varchar(64) not null references work_record.wr_template(id) on delete cascade,

  version_no integer not null,
  version_name varchar(128),

  schema_json jsonb not null default '{}'::jsonb,
  designer_json jsonb not null default '{}'::jsonb,

  field_index_json jsonb not null default '[]'::jsonb,

  published_by varchar(64) not null,
  published_at timestamptz not null default now(),
  created_at timestamptz not null default now(),

  constraint ck_wr_template_version_no_positive check (version_no > 0),
  constraint uq_wr_template_version_no unique (tenant_id, template_id, version_no)
);

alter table work_record.wr_template
  drop constraint if exists fk_wr_template_current_version;

alter table work_record.wr_template
  add constraint fk_wr_template_current_version
  foreign key (current_version_id)
  references work_record.wr_template_version(id);

comment on table work_record.wr_template_version is '已发布模板版本。记录必须绑定 template_version_id，保证历史记录按旧模板展示。';

-- ---------------------------------------------------------------------------
-- 4. Template field index
-- ---------------------------------------------------------------------------

create table if not exists work_record.wr_template_field (
  id varchar(64) primary key,
  tenant_id varchar(64) not null references public.tenant(id) on delete cascade,

  template_id varchar(64) not null references work_record.wr_template(id) on delete cascade,
  template_version_id varchar(64) not null references work_record.wr_template_version(id) on delete cascade,

  field_name varchar(128) not null,
  field_code varchar(128) not null,
  field_type varchar(32) not null,

  required boolean not null default false,
  default_value text,

  option_source varchar(32) not null default 'static',
  dict_code varchar(128),
  options_json jsonb not null default '[]'::jsonb,

  schema_path varchar(512),

  list_visible boolean not null default false,
  filterable boolean not null default false,
  exportable boolean not null default true,
  statistical boolean not null default false,

  sort_order integer not null default 0,
  enabled boolean not null default true,

  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),

  constraint ck_wr_template_field_code
    check (field_code ~ '^[a-zA-Z][a-zA-Z0-9_]{0,63}$'),

  constraint ck_wr_template_field_type
    check (field_type in (
      'text',
      'textarea',
      'number',
      'date',
      'datetime',
      'select',
      'multi_select',
      'user',
      'boolean'
    )),

  constraint ck_wr_template_field_option_source
    check (option_source in ('static', 'dict')),

  constraint ck_wr_template_field_dict_code
    check (
      (option_source = 'dict' and dict_code is not null and length(trim(dict_code)) > 0)
      or
      (option_source = 'static')
    ),

  constraint uq_wr_template_field_code
    unique (tenant_id, template_version_id, field_code)
);

comment on table work_record.wr_template_field is '模板版本字段索引。用于查询、导出、统计、后端校验。字段禁用只能 enabled=false。';
comment on column work_record.wr_template_field.field_code is '字段编码。创建后不可修改。';

-- ---------------------------------------------------------------------------
-- 5. Work record
-- ---------------------------------------------------------------------------

create table if not exists work_record.wr_record (
  id varchar(64) primary key,
  tenant_id varchar(64) not null references public.tenant(id) on delete cascade,

  template_id varchar(64) not null references work_record.wr_template(id),
  template_version_id varchar(64) not null references work_record.wr_template_version(id),

  title varchar(255) not null,
  status varchar(32) not null default 'draft',

  owner_id varchar(64),
  creator_id varchar(64) not null,
  record_time timestamptz not null,

  builtin_data_json jsonb not null default '{}'::jsonb,
  custom_data_json jsonb not null default '{}'::jsonb,

  row_version integer not null default 1,

  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  deleted_at timestamptz,

  constraint ck_wr_record_status
    check (status in ('draft', 'processing', 'done', 'archived'))
);

comment on table work_record.wr_record is '工作记录主表。删除必须走 deleted_at。记录必须绑定 template_version_id。';

-- ---------------------------------------------------------------------------
-- 6. Record snapshot
-- ---------------------------------------------------------------------------

create table if not exists work_record.wr_record_snapshot (
  id varchar(64) primary key,
  tenant_id varchar(64) not null references public.tenant(id) on delete cascade,
  record_id varchar(64) not null references work_record.wr_record(id) on delete cascade,

  template_id varchar(64) not null references work_record.wr_template(id),
  template_version_id varchar(64) not null references work_record.wr_template_version(id),

  snapshot_no integer not null,

  title varchar(255) not null,
  status varchar(32) not null,

  owner_id varchar(64),
  creator_id varchar(64) not null,
  record_time timestamptz not null,

  builtin_data_json jsonb not null default '{}'::jsonb,
  custom_data_json jsonb not null default '{}'::jsonb,

  reason varchar(128) not null default 'initial',
  actor_id varchar(64) not null default 'system',
  created_at timestamptz not null default now(),

  constraint ck_wr_record_snapshot_no_positive check (snapshot_no > 0),
  constraint uq_wr_record_snapshot_no unique (tenant_id, record_id, snapshot_no)
);

comment on table work_record.wr_record_snapshot is '工作记录快照。用于历史回放、编辑前后对比、审计追溯。';

-- ---------------------------------------------------------------------------
-- 7. Record audit event
-- ---------------------------------------------------------------------------

create table if not exists work_record.wr_record_audit_event (
  id varchar(64) primary key,
  tenant_id varchar(64) not null references public.tenant(id) on delete cascade,

  record_id varchar(64),
  template_id varchar(64),

  resource_type varchar(64) not null,
  resource_id varchar(64) not null,
  action varchar(128) not null,

  actor_id varchar(64) not null default 'system',

  before_json jsonb not null default '{}'::jsonb,
  after_json jsonb not null default '{}'::jsonb,
  detail_json jsonb not null default '{}'::jsonb,

  created_at timestamptz not null default now()
);

comment on table work_record.wr_record_audit_event is '工作记录模块内审计事件。平台全局 audit 之外，保留模块级结构化事件。';

-- ---------------------------------------------------------------------------
-- 8. Indexes
-- ---------------------------------------------------------------------------

create index if not exists idx_wr_template_tenant_status
  on work_record.wr_template(tenant_id, status, enabled)
  where deleted_at is null;

create index if not exists idx_wr_template_tenant_current_version
  on work_record.wr_template(tenant_id, current_version_id)
  where current_version_id is not null;

create index if not exists idx_wr_template_version_template
  on work_record.wr_template_version(tenant_id, template_id, version_no desc);

create index if not exists idx_wr_template_field_version_sort
  on work_record.wr_template_field(tenant_id, template_version_id, enabled, sort_order);

create index if not exists idx_wr_template_field_filterable
  on work_record.wr_template_field(tenant_id, template_version_id, field_code)
  where filterable = true and enabled = true;

create index if not exists idx_wr_template_field_exportable
  on work_record.wr_template_field(tenant_id, template_version_id, field_code)
  where exportable = true and enabled = true;

create index if not exists idx_wr_record_tenant_time
  on work_record.wr_record(tenant_id, record_time desc)
  where deleted_at is null;

create index if not exists idx_wr_record_tenant_status_time
  on work_record.wr_record(tenant_id, status, record_time desc)
  where deleted_at is null;

create index if not exists idx_wr_record_tenant_owner_time
  on work_record.wr_record(tenant_id, owner_id, record_time desc)
  where deleted_at is null;

create index if not exists idx_wr_record_tenant_creator_time
  on work_record.wr_record(tenant_id, creator_id, record_time desc)
  where deleted_at is null;

create index if not exists idx_wr_record_template_version
  on work_record.wr_record(tenant_id, template_version_id, record_time desc)
  where deleted_at is null;

create index if not exists idx_wr_record_builtin_data_gin
  on work_record.wr_record
  using gin (builtin_data_json jsonb_path_ops);

create index if not exists idx_wr_record_custom_data_gin
  on work_record.wr_record
  using gin (custom_data_json jsonb_path_ops);

create index if not exists idx_wr_record_snapshot_record
  on work_record.wr_record_snapshot(tenant_id, record_id, snapshot_no desc);

create index if not exists idx_wr_audit_resource
  on work_record.wr_record_audit_event(tenant_id, resource_type, resource_id, created_at desc);

create index if not exists idx_wr_audit_record
  on work_record.wr_record_audit_event(tenant_id, record_id, created_at desc)
  where record_id is not null;

-- ---------------------------------------------------------------------------
-- 9. Triggers
-- ---------------------------------------------------------------------------

drop trigger if exists trg_wr_template_touch_updated_at on work_record.wr_template;
create trigger trg_wr_template_touch_updated_at
before update on work_record.wr_template
for each row execute function work_record.touch_updated_at();

drop trigger if exists trg_wr_template_field_touch_updated_at on work_record.wr_template_field;
create trigger trg_wr_template_field_touch_updated_at
before update on work_record.wr_template_field
for each row execute function work_record.touch_updated_at();

drop trigger if exists trg_wr_record_touch_updated_at on work_record.wr_record;
create trigger trg_wr_record_touch_updated_at
before update on work_record.wr_record
for each row execute function work_record.touch_updated_at();

drop trigger if exists trg_wr_template_field_code_immutable on work_record.wr_template_field;
create trigger trg_wr_template_field_code_immutable
before update of field_code on work_record.wr_template_field
for each row execute function work_record.reject_field_code_update();

drop trigger if exists trg_wr_template_field_no_delete on work_record.wr_template_field;
create trigger trg_wr_template_field_no_delete
before delete on work_record.wr_template_field
for each row execute function work_record.reject_physical_delete();

drop trigger if exists trg_wr_record_no_delete on work_record.wr_record;
create trigger trg_wr_record_no_delete
before delete on work_record.wr_record
for each row execute function work_record.reject_physical_delete();

-- ---------------------------------------------------------------------------
-- 10. Migrate old public.wr_template
-- ---------------------------------------------------------------------------

insert into work_record.wr_template(
  id,
  tenant_id,
  code,
  name,
  description,
  status,
  enabled,
  draft_schema_json,
  draft_designer_json,
  created_by,
  created_at,
  updated_at
)
select
  t.id,
  t.tenant_id,
  t.code,
  t.name,
  t.description,
  case when t.enabled then 'published' else 'disabled' end,
  t.enabled,
  coalesce(t.schema_json, '{}'::jsonb),
  coalesce(t.designer_json, '{}'::jsonb),
  t.created_by,
  t.created_at,
  t.updated_at
from public.wr_template t
where not exists (
  select 1
  from work_record.wr_template nt
  where nt.id = t.id
);

-- ---------------------------------------------------------------------------
-- 11. Migrate template version v1 from old public.wr_template
-- ---------------------------------------------------------------------------

insert into work_record.wr_template_version(
  id,
  tenant_id,
  template_id,
  version_no,
  version_name,
  schema_json,
  designer_json,
  field_index_json,
  published_by,
  published_at,
  created_at
)
select
  concat('tv-', md5(t.id || ':v1')),
  t.tenant_id,
  t.id,
  1,
  'v1',
  coalesce(t.schema_json, '{}'::jsonb),
  coalesce(t.designer_json, '{}'::jsonb),
  '[]'::jsonb,
  t.created_by,
  t.updated_at,
  t.created_at
from public.wr_template t
where not exists (
  select 1
  from work_record.wr_template_version v
  where v.tenant_id = t.tenant_id
    and v.template_id = t.id
    and v.version_no = 1
);

update work_record.wr_template t
   set current_version_id = v.id
  from work_record.wr_template_version v
 where v.tenant_id = t.tenant_id
   and v.template_id = t.id
   and v.version_no = 1
   and t.current_version_id is null;

-- ---------------------------------------------------------------------------
-- 12. Migrate field index
-- ---------------------------------------------------------------------------

insert into work_record.wr_template_field(
  id,
  tenant_id,
  template_id,
  template_version_id,
  field_name,
  field_code,
  field_type,
  required,
  default_value,
  option_source,
  dict_code,
  options_json,
  schema_path,
  list_visible,
  filterable,
  exportable,
  statistical,
  sort_order,
  enabled,
  created_at,
  updated_at
)
select
  concat('tf-', md5(f.id)),
  f.tenant_id,
  f.template_id,
  v.id,
  f.field_name,
  f.field_code,
  case
    when f.field_type = 'text' then 'text'
    when f.field_type = 'textarea' then 'textarea'
    when f.field_type = 'number' then 'number'
    when f.field_type = 'date' then 'date'
    when f.field_type = 'datetime' then 'datetime'
    when f.field_type = 'select' then 'select'
    when f.field_type = 'multi_select' then 'multi_select'
    when f.field_type = 'user' then 'user'
    when f.field_type = 'boolean' then 'boolean'
    else 'text'
  end,
  f.required,
  f.default_value,
  coalesce(f.option_source, 'static'),
  f.dict_code,
  coalesce(f.options_json, '[]'::jsonb),
  coalesce(f.schema_path, '.properties.' || f.field_code),
  f.list_visible,
  f.filterable,
  coalesce(f.exportable, true),
  f.statistical,
  f.sort_order,
  f.enabled,
  f.created_at,
  f.updated_at
from public.wr_template_field f
join work_record.wr_template_version v
  on v.tenant_id = f.tenant_id
 and v.template_id = f.template_id
 and v.version_no = 1
where f.field_code ~ '^[a-zA-Z][a-zA-Z0-9_]{0,63}$'
  and not exists (
    select 1
    from work_record.wr_template_field nf
    where nf.tenant_id = f.tenant_id
      and nf.template_version_id = v.id
      and nf.field_code = f.field_code
  );

-- ---------------------------------------------------------------------------
-- 13. Migrate records and bind them to template version v1
-- ---------------------------------------------------------------------------

insert into work_record.wr_record(
  id,
  tenant_id,
  template_id,
  template_version_id,
  title,
  status,
  owner_id,
  creator_id,
  record_time,
  builtin_data_json,
  custom_data_json,
  row_version,
  created_at,
  updated_at,
  deleted_at
)
select
  r.id,
  r.tenant_id,
  r.template_id,
  v.id,
  r.title,
  case
    when r.status in ('draft', 'processing', 'done', 'archived') then r.status
    when r.status = 'completed' then 'done'
    else 'draft'
  end,
  r.owner_id,
  r.creator_id,
  r.record_time,
  coalesce(r.builtin_data_json, '{}'::jsonb),
  coalesce(r.custom_data_json, '{}'::jsonb),
  1,
  r.created_at,
  r.updated_at,
  r.deleted_at
from public.wr_record r
join work_record.wr_template_version v
  on v.tenant_id = r.tenant_id
 and v.template_id = r.template_id
 and v.version_no = 1
where not exists (
  select 1
  from work_record.wr_record nr
  where nr.id = r.id
);

-- ---------------------------------------------------------------------------
-- 14. Create initial snapshots for migrated records
-- ---------------------------------------------------------------------------

insert into work_record.wr_record_snapshot(
  id,
  tenant_id,
  record_id,
  template_id,
  template_version_id,
  snapshot_no,
  title,
  status,
  owner_id,
  creator_id,
  record_time,
  builtin_data_json,
  custom_data_json,
  reason,
  actor_id,
  created_at
)
select
  concat('snap-', md5(r.id || ':1')),
  r.tenant_id,
  r.id,
  r.template_id,
  r.template_version_id,
  1,
  r.title,
  r.status,
  r.owner_id,
  r.creator_id,
  r.record_time,
  r.builtin_data_json,
  r.custom_data_json,
  'migration',
  'system',
  r.created_at
from work_record.wr_record r
where not exists (
  select 1
  from work_record.wr_record_snapshot s
  where s.tenant_id = r.tenant_id
    and s.record_id = r.id
    and s.snapshot_no = 1
);

-- ---------------------------------------------------------------------------
-- 15. Create migration audit events
-- ---------------------------------------------------------------------------

insert into work_record.wr_record_audit_event(
  id,
  tenant_id,
  record_id,
  template_id,
  resource_type,
  resource_id,
  action,
  actor_id,
  before_json,
  after_json,
  detail_json,
  created_at
)
select
  concat('audit-', md5(r.id || ':migration')),
  r.tenant_id,
  r.id,
  r.template_id,
  'work_record',
  r.id,
  'work_record.record.migrated',
  'system',
  '{}'::jsonb,
  jsonb_build_object(
    'id', r.id,
    'templateId', r.template_id,
    'templateVersionId', r.template_version_id,
    'status', r.status
  ),
  jsonb_build_object(
    'source', 'public.wr_record',
    'target', 'work_record.wr_record'
  ),
  now()
from work_record.wr_record r
where not exists (
  select 1
  from work_record.wr_record_audit_event e
  where e.id = concat('audit-', md5(r.id || ':migration'))
);
