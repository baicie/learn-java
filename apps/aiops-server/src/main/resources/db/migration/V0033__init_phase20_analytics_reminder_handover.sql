-- Phase 20.4: analytics permissions, missing-record reminders, notifications and shift handover.

create table if not exists work_record.wr_reminder_rule (
    id varchar(64) primary key,
    tenant_id varchar(64) not null,
    name varchar(160) not null,
    template_id varchar(64) not null,
    target_type varchar(24) not null,
    target_json jsonb not null default '{}'::jsonb,
    cutoff_time time not null,
    time_zone varchar(64) not null,
    enabled boolean not null default true,
    created_by varchar(64) not null,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    constraint uq_wr_reminder_rule_tenant_id unique (tenant_id, id),
    constraint fk_wr_reminder_template foreign key (tenant_id, template_id)
        references work_record.wr_template(tenant_id, id) on delete cascade,
    constraint fk_wr_reminder_creator foreign key (tenant_id, created_by)
        references public.sys_user(tenant_id, id) on delete restrict,
    constraint ck_wr_reminder_target_type check (target_type in ('users', 'role')),
    constraint ck_wr_reminder_target_json check (jsonb_typeof(target_json) = 'object'),
    constraint ck_wr_reminder_timezone check (char_length(time_zone) between 1 and 64)
);

create index if not exists idx_wr_reminder_rule_due
    on work_record.wr_reminder_rule(enabled, cutoff_time) where enabled = true;

create table if not exists work_record.wr_notification (
    id varchar(64) primary key,
    tenant_id varchar(64) not null,
    user_id varchar(64) not null,
    notification_type varchar(48) not null,
    title varchar(255) not null,
    content text not null,
    resource_type varchar(64),
    resource_id varchar(64),
    dedupe_key varchar(192) not null,
    created_at timestamptz not null default now(),
    read_at timestamptz,
    constraint fk_wr_notification_user foreign key (tenant_id, user_id)
        references public.sys_user(tenant_id, id) on delete cascade,
    constraint uq_wr_notification_dedupe unique (tenant_id, user_id, dedupe_key)
);

create index if not exists idx_wr_notification_user_unread
    on work_record.wr_notification(tenant_id, user_id, created_at desc)
    where read_at is null;

create table if not exists work_record.wr_handover (
    id varchar(64) primary key,
    tenant_id varchar(64) not null,
    from_user_id varchar(64) not null,
    to_user_id varchar(64) not null,
    shift_start timestamptz not null,
    shift_end timestamptz not null,
    status varchar(24) not null default 'draft',
    summary text not null,
    record_ids_json jsonb not null default '[]'::jsonb,
    relation_ids_json jsonb not null default '[]'::jsonb,
    created_by varchar(64) not null,
    created_at timestamptz not null default now(),
    accepted_at timestamptz,
    completed_at timestamptz,
    row_version integer not null default 1,
    constraint uq_wr_handover_tenant_id unique (tenant_id, id),
    constraint fk_wr_handover_from_user foreign key (tenant_id, from_user_id)
        references public.sys_user(tenant_id, id) on delete restrict,
    constraint fk_wr_handover_to_user foreign key (tenant_id, to_user_id)
        references public.sys_user(tenant_id, id) on delete restrict,
    constraint fk_wr_handover_creator foreign key (tenant_id, created_by)
        references public.sys_user(tenant_id, id) on delete restrict,
    constraint ck_wr_handover_users check (from_user_id <> to_user_id),
    constraint ck_wr_handover_period check (shift_end > shift_start),
    constraint ck_wr_handover_status
        check (status in ('draft', 'submitted', 'accepted', 'completed', 'cancelled')),
    constraint ck_wr_handover_summary check (char_length(summary) between 1 and 10000),
    constraint ck_wr_handover_records check (jsonb_typeof(record_ids_json) = 'array'),
    constraint ck_wr_handover_relations check (jsonb_typeof(relation_ids_json) = 'array'),
    constraint ck_wr_handover_version check (row_version > 0)
);

create index if not exists idx_wr_handover_receiver
    on work_record.wr_handover(tenant_id, to_user_id, status, created_at desc);
create index if not exists idx_wr_handover_sender
    on work_record.wr_handover(tenant_id, from_user_id, created_at desc);

insert into iam.permission_definition(
    permission_code, module_code, permission_name, description, risk_level,
    dependencies_json, sort_order, enabled)
values
    ('work-record:analytics', 'work-record', '查看工作记录统计', '查看统计报表和工作量分析',
     'normal', '["work-record:read:self"]'::jsonb, 340, true),
    ('work-record:reminder:manage', 'work-record', '管理日报提醒', '配置日报缺失提醒规则',
     'high', '["work-record:template:read"]'::jsonb, 350, true),
    ('work-record:handover', 'work-record', '管理值班交接', '创建、接收和完成值班交接',
     'normal', '["work-record:read:self"]'::jsonb, 360, true)
on conflict (permission_code) do update set
    permission_name = excluded.permission_name,
    description = excluded.description,
    risk_level = excluded.risk_level,
    dependencies_json = excluded.dependencies_json,
    sort_order = excluded.sort_order,
    enabled = true,
    updated_at = now();

insert into iam.permission(
    permission_code, permission_name, module_code, resource_type, description, enabled)
select permission_code, permission_name, module_code, 'ACTION', description, true
from iam.permission_definition
where permission_code in (
    'work-record:analytics', 'work-record:reminder:manage', 'work-record:handover')
on conflict (permission_code) do update set
    permission_name = excluded.permission_name,
    description = excluded.description,
    enabled = true,
    updated_at = now();

insert into iam.role_permission(tenant_id, role_code, permission_code)
select r.tenant_id, r.role_code, p.permission_code
from iam.role_definition r
cross join iam.permission p
where r.role_code in ('system_admin', 'record_admin')
  and r.deleted_at is null
  and p.permission_code in (
      'work-record:analytics', 'work-record:reminder:manage', 'work-record:handover')
on conflict do nothing;

insert into iam.role_permission(tenant_id, role_code, permission_code)
select r.tenant_id, r.role_code, p.permission_code
from iam.role_definition r
cross join iam.permission p
where r.role_code = 'normal_user'
  and r.deleted_at is null
  and p.permission_code = 'work-record:handover'
on conflict do nothing;
