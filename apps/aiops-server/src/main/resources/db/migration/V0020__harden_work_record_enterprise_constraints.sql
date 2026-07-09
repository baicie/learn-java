-- Phase 3 hardening:
-- 1. Enforce tenant/template/version consistency with composite FKs.
-- 2. Forbid physical delete for template/version/snapshot/audit.
-- 3. Rebuild template_version.field_index_json from wr_template_field.
-- 4. Normalize migrated option_source / dict_code for old dirty data.

-- ---------------------------------------------------------------------------
-- 1. Helper: add unique constraint if missing
-- ---------------------------------------------------------------------------

do $$
begin
  if not exists (
    select 1 from pg_constraint
    where conname = 'uq_wr_template_tenant_id'
      and conrelid = 'work_record.wr_template'::regclass
  ) then
    alter table work_record.wr_template
      add constraint uq_wr_template_tenant_id unique (tenant_id, id);
  end if;

  if not exists (
    select 1 from pg_constraint
    where conname = 'uq_wr_template_version_tenant_id'
      and conrelid = 'work_record.wr_template_version'::regclass
  ) then
    alter table work_record.wr_template_version
      add constraint uq_wr_template_version_tenant_id unique (tenant_id, id);
  end if;

  if not exists (
    select 1 from pg_constraint
    where conname = 'uq_wr_template_version_tenant_template_id'
      and conrelid = 'work_record.wr_template_version'::regclass
  ) then
    alter table work_record.wr_template_version
      add constraint uq_wr_template_version_tenant_template_id
      unique (tenant_id, template_id, id);
  end if;

  if not exists (
    select 1 from pg_constraint
    where conname = 'uq_wr_record_tenant_id'
      and conrelid = 'work_record.wr_record'::regclass
  ) then
    alter table work_record.wr_record
      add constraint uq_wr_record_tenant_id unique (tenant_id, id);
  end if;
end $$;

-- ---------------------------------------------------------------------------
-- 2. Normalize dirty migrated field option data
-- ---------------------------------------------------------------------------

update work_record.wr_template_field
   set option_source = 'static',
       dict_code = null,
       updated_at = now()
 where option_source not in ('static', 'dict')
    or option_source is null
    or (option_source = 'dict' and (dict_code is null or length(trim(dict_code)) = 0));

-- ---------------------------------------------------------------------------
-- 3. Drop weak single-column FKs and add composite consistency FKs
-- ---------------------------------------------------------------------------

alter table work_record.wr_template
  drop constraint if exists fk_wr_template_current_version;

do $$
begin
  if not exists (
    select 1 from pg_constraint
    where conname = 'fk_wr_template_current_version_same_template'
      and conrelid = 'work_record.wr_template'::regclass
  ) then
    alter table work_record.wr_template
      add constraint fk_wr_template_current_version_same_template
      foreign key (tenant_id, id, current_version_id)
      references work_record.wr_template_version(tenant_id, template_id, id);
  end if;
end $$;

do $$
begin
  if not exists (
    select 1 from pg_constraint
    where conname = 'fk_wr_template_field_template_same_tenant'
      and conrelid = 'work_record.wr_template_field'::regclass
  ) then
    alter table work_record.wr_template_field
      add constraint fk_wr_template_field_template_same_tenant
      foreign key (tenant_id, template_id)
      references work_record.wr_template(tenant_id, id);
  end if;

  if not exists (
    select 1 from pg_constraint
    where conname = 'fk_wr_template_field_version_same_template'
      and conrelid = 'work_record.wr_template_field'::regclass
  ) then
    alter table work_record.wr_template_field
      add constraint fk_wr_template_field_version_same_template
      foreign key (tenant_id, template_id, template_version_id)
      references work_record.wr_template_version(tenant_id, template_id, id);
  end if;
end $$;

do $$
begin
  if not exists (
    select 1 from pg_constraint
    where conname = 'fk_wr_record_template_same_tenant'
      and conrelid = 'work_record.wr_record'::regclass
  ) then
    alter table work_record.wr_record
      add constraint fk_wr_record_template_same_tenant
      foreign key (tenant_id, template_id)
      references work_record.wr_template(tenant_id, id);
  end if;

  if not exists (
    select 1 from pg_constraint
    where conname = 'fk_wr_record_version_same_template'
      and conrelid = 'work_record.wr_record'::regclass
  ) then
    alter table work_record.wr_record
      add constraint fk_wr_record_version_same_template
      foreign key (tenant_id, template_id, template_version_id)
      references work_record.wr_template_version(tenant_id, template_id, id);
  end if;
end $$;

do $$
begin
  if not exists (
    select 1 from pg_constraint
    where conname = 'fk_wr_record_snapshot_record_same_tenant'
      and conrelid = 'work_record.wr_record_snapshot'::regclass
  ) then
    alter table work_record.wr_record_snapshot
      add constraint fk_wr_record_snapshot_record_same_tenant
      foreign key (tenant_id, record_id)
      references work_record.wr_record(tenant_id, id);
  end if;

  if not exists (
    select 1 from pg_constraint
    where conname = 'fk_wr_record_snapshot_template_same_tenant'
      and conrelid = 'work_record.wr_record_snapshot'::regclass
  ) then
    alter table work_record.wr_record_snapshot
      add constraint fk_wr_record_snapshot_template_same_tenant
      foreign key (tenant_id, template_id)
      references work_record.wr_template(tenant_id, id);
  end if;

  if not exists (
    select 1 from pg_constraint
    where conname = 'fk_wr_record_snapshot_version_same_template'
      and conrelid = 'work_record.wr_record_snapshot'::regclass
  ) then
    alter table work_record.wr_record_snapshot
      add constraint fk_wr_record_snapshot_version_same_template
      foreign key (tenant_id, template_id, template_version_id)
      references work_record.wr_template_version(tenant_id, template_id, id);
  end if;
end $$;

do $$
begin
  if not exists (
    select 1 from pg_constraint
    where conname = 'fk_wr_audit_record_same_tenant'
      and conrelid = 'work_record.wr_record_audit_event'::regclass
  ) then
    alter table work_record.wr_record_audit_event
      add constraint fk_wr_audit_record_same_tenant
      foreign key (tenant_id, record_id)
      references work_record.wr_record(tenant_id, id);
  end if;

  if not exists (
    select 1 from pg_constraint
    where conname = 'fk_wr_audit_template_same_tenant'
      and conrelid = 'work_record.wr_record_audit_event'::regclass
  ) then
    alter table work_record.wr_record_audit_event
      add constraint fk_wr_audit_template_same_tenant
      foreign key (tenant_id, template_id)
      references work_record.wr_template(tenant_id, id);
  end if;
end $$;

-- ---------------------------------------------------------------------------
-- 4. Forbid physical delete on enterprise work-record tables
-- ---------------------------------------------------------------------------

drop trigger if exists trg_wr_template_no_delete on work_record.wr_template;
create trigger trg_wr_template_no_delete
before delete on work_record.wr_template
for each row execute function work_record.reject_physical_delete();

drop trigger if exists trg_wr_template_version_no_delete on work_record.wr_template_version;
create trigger trg_wr_template_version_no_delete
before delete on work_record.wr_template_version
for each row execute function work_record.reject_physical_delete();

drop trigger if exists trg_wr_record_snapshot_no_delete on work_record.wr_record_snapshot;
create trigger trg_wr_record_snapshot_no_delete
before delete on work_record.wr_record_snapshot
for each row execute function work_record.reject_physical_delete();

drop trigger if exists trg_wr_record_audit_event_no_delete on work_record.wr_record_audit_event;
create trigger trg_wr_record_audit_event_no_delete
before delete on work_record.wr_record_audit_event
for each row execute function work_record.reject_physical_delete();

-- ---------------------------------------------------------------------------
-- 5. Rebuild field_index_json from actual field index table
-- ---------------------------------------------------------------------------

update work_record.wr_template_version v
   set field_index_json = coalesce((
     select jsonb_agg(
       jsonb_build_object(
         'fieldId', f.id,
         'fieldName', f.field_name,
         'fieldCode', f.field_code,
         'fieldType', f.field_type,
         'required', f.required,
         'optionSource', f.option_source,
         'dictCode', f.dict_code,
         'schemaPath', f.schema_path,
         'listVisible', f.list_visible,
         'filterable', f.filterable,
         'exportable', f.exportable,
         'statistical', f.statistical,
         'sortOrder', f.sort_order,
         'enabled', f.enabled
       )
       order by f.sort_order asc, f.created_at asc
     )
     from work_record.wr_template_field f
     where f.tenant_id = v.tenant_id
       and f.template_id = v.template_id
       and f.template_version_id = v.id
   ), '[]'::jsonb);

-- ---------------------------------------------------------------------------
-- 6. Useful consistency indexes
-- ---------------------------------------------------------------------------

create index if not exists idx_wr_template_version_current_lookup
  on work_record.wr_template_version(tenant_id, template_id, id);

create index if not exists idx_wr_record_tenant_template_version_status
  on work_record.wr_record(tenant_id, template_id, template_version_id, status, record_time desc)
  where deleted_at is null;
