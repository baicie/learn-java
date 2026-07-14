-- Phase 20.7: approval workflow and SLA tracking.
alter table work_record.wr_record drop constraint if exists ck_wr_record_status;
alter table work_record.wr_record add constraint ck_wr_record_status check(status in(
 'draft','processing','pending_approval','rejected','done','archived')) not valid;

create table if not exists work_record.wr_approval_definition(
 id varchar(64) primary key,tenant_id varchar(64) not null,template_id varchar(64) not null,
 name varchar(160) not null,version_no integer not null,trigger_status varchar(32) not null default 'done',
 enabled boolean not null default true,created_by varchar(64) not null,created_at timestamptz not null default now(),
 constraint fk_wr_approval_def_template foreign key(tenant_id,template_id)
  references work_record.wr_template(tenant_id,id) on delete cascade,
 constraint uq_wr_approval_definition_version unique(tenant_id,template_id,version_no));
create table if not exists work_record.wr_approval_step(
 id varchar(64) primary key,definition_id varchar(64) not null references work_record.wr_approval_definition(id) on delete cascade,
 step_no integer not null,name varchar(160) not null,approver_type varchar(24) not null,
 approver_value varchar(128) not null,approval_mode varchar(24) not null default 'any',timeout_minutes integer,
 constraint uq_wr_approval_step_no unique(definition_id,step_no),
 constraint ck_wr_approval_approver_type check(approver_type in('user','role','record_owner')),
 constraint ck_wr_approval_mode check(approval_mode in('any','all')),
 constraint ck_wr_approval_timeout check(timeout_minutes is null or timeout_minutes>0));
create table if not exists work_record.wr_approval_instance(
 id varchar(64) primary key,tenant_id varchar(64) not null,record_id varchar(64) not null,
 definition_id varchar(64) not null references work_record.wr_approval_definition(id),
 status varchar(24) not null default 'pending',current_step_no integer not null default 1,
 started_by varchar(64) not null,started_at timestamptz not null default now(),finished_at timestamptz,row_version integer not null default 1,
 constraint fk_wr_approval_record foreign key(tenant_id,record_id) references work_record.wr_record(tenant_id,id),
 constraint ck_wr_approval_instance_status check(status in('pending','approved','rejected','cancelled')));
create unique index uq_wr_approval_active_instance on work_record.wr_approval_instance(tenant_id,record_id) where status='pending';
create table if not exists work_record.wr_approval_task(
 id varchar(64) primary key,tenant_id varchar(64) not null,instance_id varchar(64) not null references work_record.wr_approval_instance(id) on delete cascade,
 step_no integer not null,assignee_type varchar(24) not null,assignee_value varchar(128) not null,status varchar(24) not null default 'pending',
 acted_by varchar(64),action_comment varchar(1000),due_at timestamptz,acted_at timestamptz,created_at timestamptz not null default now(),
 constraint ck_wr_approval_task_status check(status in('pending','approved','rejected','cancelled','expired')));
create index idx_wr_approval_task_pending on work_record.wr_approval_task(tenant_id,status,due_at,created_at);

create table if not exists work_record.wr_sla_policy(
 id varchar(64) primary key,tenant_id varchar(64) not null,template_id varchar(64) not null,name varchar(160) not null,
 start_event varchar(32) not null,stop_event varchar(32) not null,target_minutes integer not null,
 calendar_aware boolean not null default false,severity varchar(24) not null default 'warning',enabled boolean not null default true,
 created_by varchar(64) not null,created_at timestamptz not null default now(),updated_at timestamptz not null default now(),
 constraint fk_wr_sla_template foreign key(tenant_id,template_id) references work_record.wr_template(tenant_id,id) on delete cascade,
 constraint ck_wr_sla_target check(target_minutes>0),constraint ck_wr_sla_severity check(severity in('info','warning','critical')));
create table if not exists work_record.wr_sla_instance(
 id varchar(64) primary key,tenant_id varchar(64) not null,record_id varchar(64) not null,policy_id varchar(64) not null references work_record.wr_sla_policy(id),
 status varchar(24) not null default 'running',started_at timestamptz not null,due_at timestamptz not null,stopped_at timestamptz,breached_at timestamptz,
 elapsed_minutes integer,row_version integer not null default 1,
 constraint fk_wr_sla_record foreign key(tenant_id,record_id) references work_record.wr_record(tenant_id,id),
 constraint uq_wr_sla_instance unique(tenant_id,record_id,policy_id),
 constraint ck_wr_sla_instance_status check(status in('running','met','breached','cancelled')));
create index idx_wr_sla_due on work_record.wr_sla_instance(tenant_id,status,due_at) where status='running';
create table if not exists work_record.wr_sla_event(
 id varchar(64) primary key,tenant_id varchar(64) not null,instance_id varchar(64) not null references work_record.wr_sla_instance(id) on delete cascade,
 event_type varchar(48) not null,detail_json jsonb not null default '{}'::jsonb,event_at timestamptz not null default now(),
 constraint ck_wr_sla_event_detail check(jsonb_typeof(detail_json)='object'));

insert into iam.permission_definition(permission_code,module_code,permission_name,description,risk_level,dependencies_json,sort_order,enabled) values
 ('work-record:approval:manage','work-record','管理审批流','配置工作记录审批定义','sensitive','["work-record:template:write"]'::jsonb,410,true),
 ('work-record:approval:act','work-record','处理审批任务','批准或拒绝工作记录','high','["work-record:read:self"]'::jsonb,420,true),
 ('work-record:sla:manage','work-record','管理 SLA','配置工作记录 SLA','sensitive','["work-record:template:write"]'::jsonb,430,true)
on conflict(permission_code) do update set permission_name=excluded.permission_name,description=excluded.description,risk_level=excluded.risk_level,
 dependencies_json=excluded.dependencies_json,sort_order=excluded.sort_order,enabled=true,updated_at=now();
insert into iam.permission(permission_code,permission_name,module_code,resource_type,description,enabled)
select permission_code,permission_name,module_code,'ACTION',description,true from iam.permission_definition
where permission_code in('work-record:approval:manage','work-record:approval:act','work-record:sla:manage')
on conflict(permission_code) do update set permission_name=excluded.permission_name,description=excluded.description,enabled=true,updated_at=now();
insert into iam.role_permission(tenant_id,role_code,permission_code)
select r.tenant_id,r.role_code,p.permission_code from iam.role_definition r cross join iam.permission p
where r.role_code in('system_admin','record_admin') and r.deleted_at is null
and p.permission_code in('work-record:approval:manage','work-record:approval:act','work-record:sla:manage') on conflict do nothing;
insert into iam.role_permission(tenant_id,role_code,permission_code)
select r.tenant_id,r.role_code,p.permission_code from iam.role_definition r cross join iam.permission p
where r.role_code='normal_user' and r.deleted_at is null and p.permission_code='work-record:approval:act' on conflict do nothing;
