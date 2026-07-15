-- Phase 20.3: work-record comments, attachments, and related operational objects.

create table if not exists work_record.wr_comment (
    id varchar(64) primary key,
    tenant_id varchar(64) not null,
    record_id varchar(64) not null,
    content text not null,
    mentions_json jsonb not null default '[]'::jsonb,
    created_by varchar(64) not null,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    deleted_at timestamptz,
    row_version integer not null default 1,
    constraint uq_wr_comment_tenant_id unique (tenant_id, id),
    constraint fk_wr_comment_record foreign key (tenant_id, record_id)
        references work_record.wr_record(tenant_id, id) on delete cascade,
    constraint fk_wr_comment_user foreign key (tenant_id, created_by)
        references public.sys_user(tenant_id, id) on delete restrict,
    constraint ck_wr_comment_content check (char_length(content) between 1 and 4000),
    constraint ck_wr_comment_mentions check (jsonb_typeof(mentions_json) = 'array'),
    constraint ck_wr_comment_version check (row_version > 0)
);

create index if not exists idx_wr_comment_record_time
    on work_record.wr_comment(tenant_id, record_id, created_at, id)
    where deleted_at is null;

create table if not exists work_record.wr_attachment (
    id varchar(64) primary key,
    tenant_id varchar(64) not null,
    record_id varchar(64) not null,
    upload_id varchar(64) not null,
    object_key varchar(512) not null,
    file_name varchar(255) not null,
    content_type varchar(128) not null,
    size_bytes bigint not null,
    sha256 varchar(64),
    status varchar(24) not null default 'pending_scan',
    uploaded_by varchar(64) not null,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    deleted_at timestamptz,
    constraint uq_wr_attachment_tenant_id unique (tenant_id, id),
    constraint uq_wr_attachment_object_key unique (tenant_id, object_key),
    constraint uq_wr_attachment_upload unique (tenant_id, upload_id),
    constraint fk_wr_attachment_record foreign key (tenant_id, record_id)
        references work_record.wr_record(tenant_id, id) on delete cascade,
    constraint fk_wr_attachment_upload foreign key (tenant_id, upload_id)
        references work_record.wr_upload_session(tenant_id, id) on delete restrict,
    constraint fk_wr_attachment_user foreign key (tenant_id, uploaded_by)
        references public.sys_user(tenant_id, id) on delete restrict,
    constraint ck_wr_attachment_size check (size_bytes > 0 and size_bytes <= 20971520),
    constraint ck_wr_attachment_sha256
        check (sha256 is null or sha256 ~ '^[0-9a-f]{64}$'),
    constraint ck_wr_attachment_status
        check (status in ('pending_scan', 'ready', 'quarantined', 'deleted')),
    constraint ck_wr_attachment_ready_hash
        check (status <> 'ready' or sha256 is not null)
);

create index if not exists idx_wr_attachment_record
    on work_record.wr_attachment(tenant_id, record_id, created_at desc)
    where deleted_at is null;

create table if not exists work_record.wr_record_relation (
    id varchar(64) primary key,
    tenant_id varchar(64) not null,
    record_id varchar(64) not null,
    relation_type varchar(32) not null,
    target_id varchar(64) not null,
    target_title varchar(255),
    target_status varchar(64),
    snapshot_json jsonb not null default '{}'::jsonb,
    created_by varchar(64) not null,
    created_at timestamptz not null default now(),
    constraint uq_wr_record_relation_tenant_id unique (tenant_id, id),
    constraint uq_wr_record_relation unique (tenant_id, record_id, relation_type, target_id),
    constraint fk_wr_record_relation_record foreign key (tenant_id, record_id)
        references work_record.wr_record(tenant_id, id) on delete cascade,
    constraint fk_wr_record_relation_user foreign key (tenant_id, created_by)
        references public.sys_user(tenant_id, id) on delete restrict,
    constraint ck_wr_record_relation_type
        check (relation_type in ('alert', 'inspection', 'incident')),
    constraint ck_wr_record_relation_snapshot check (jsonb_typeof(snapshot_json) = 'object')
);

create index if not exists idx_wr_record_relation_target
    on work_record.wr_record_relation(tenant_id, relation_type, target_id);

insert into iam.permission_definition(
    permission_code, module_code, permission_name, description, risk_level,
    dependencies_json, sort_order, enabled)
values
    ('work-record:comment', 'work-record', '评论工作记录', '创建及维护本人评论',
     'normal', '["work-record:read:self"]'::jsonb, 290, true),
    ('work-record:comment:moderate', 'work-record', '管理工作记录评论', '维护租户内所有评论',
     'sensitive', '["work-record:comment"]'::jsonb, 300, true),
    ('work-record:attachment', 'work-record', '管理工作记录附件', '上传及维护本人附件',
     'high', '["work-record:write"]'::jsonb, 310, true),
    ('work-record:attachment:moderate', 'work-record', '管理全部工作记录附件',
     '维护租户内所有附件', 'sensitive', '["work-record:attachment"]'::jsonb, 320, true),
    ('work-record:relation', 'work-record', '关联运维对象', '关联告警、巡检和事件',
     'high', '["work-record:write"]'::jsonb, 330, true)
on conflict (permission_code) do update
set module_code = excluded.module_code,
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
    'work-record:comment', 'work-record:comment:moderate',
    'work-record:attachment', 'work-record:attachment:moderate', 'work-record:relation')
on conflict (permission_code) do update
set permission_name = excluded.permission_name,
    module_code = excluded.module_code,
    resource_type = excluded.resource_type,
    description = excluded.description,
    enabled = true,
    updated_at = now();

insert into iam.role_permission(tenant_id, role_code, permission_code)
select r.tenant_id, r.role_code, p.permission_code
from iam.role_definition r
cross join iam.permission p
where r.role_code in ('system_admin', 'record_admin', 'normal_user')
  and r.deleted_at is null
  and p.permission_code in ('work-record:comment', 'work-record:attachment', 'work-record:relation')
on conflict do nothing;

insert into iam.role_permission(tenant_id, role_code, permission_code)
select r.tenant_id, r.role_code, p.permission_code
from iam.role_definition r
cross join iam.permission p
where r.role_code in ('system_admin', 'record_admin')
  and r.deleted_at is null
  and p.permission_code in ('work-record:comment:moderate', 'work-record:attachment:moderate')
on conflict do nothing;
