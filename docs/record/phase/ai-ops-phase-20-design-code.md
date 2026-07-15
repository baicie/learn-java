---
title: AegisOps Phase 20 后续增强详细设计与完整代码方案
type: phase
status: deprecated
phase: work-record-20
owner: ai
created: 2026-07-14
updated: 2026-07-14
related: []
---

# AegisOps Phase 20：后续增强详细设计与完整代码方案

> 本文仅保留为历史设计材料，不可直接执行。当前单一真相源为
> [`docs/record/phase/20/`](./20/00-phase-20-execution-baseline.md) 下的拆分计划；迁移编号、Portal
> 目录和 IAM 表结构均以拆分计划与当前代码为准。

> 基线：`baicie/ai-ops`，分支 `feat/record-doc-portal`，提交 `43d16315cc4b68cc705177d2c8faba1d8e42644e`。
>
> 重要前置：该提交仅修复 RCA lambda 参数遮蔽，Phase 19 审查中识别的生产阻断项尚未合入。Phase 20 开发分支必须先合入 Phase 19 P0 修复，再开始数据库迁移。

## 1. 结论

Phase 20 的 17 项能力不能作为一个提交一次完成。它们共享文件存储、异步任务、通知、关联对象、字段策略、审批和 SLA 等基础设施，若并行硬写会导致：

- 同一种异步任务出现多套状态机；
- 评论、附件、审批、SLA 各自重复实现时间线；
- AI 月报与统计报表读取口径不一致；
- 字段级权限只在前端隐藏，后端查询、导出、AI 输入仍泄露字段；
- 大文件和大导出继续占用 Server 内存；
- Worker 重试造成重复导入、重复提醒和重复 AI 计费。

因此 Phase 20 拆成八个可独立验收的子阶段：

| 子阶段 | 能力                                         |
| ------ | -------------------------------------------- |
| 20.0   | Phase 19 P0 修复、outbox 租约与幂等升级      |
| 20.1   | 通用异步任务、MinIO 对象存储、任务中心       |
| 20.2   | Excel 导入、异步导出                         |
| 20.3   | 评论时间线、附件、关联告警/巡检/事件         |
| 20.4   | 统计报表、工作量分析、日报缺失提醒、值班交接 |
| 20.5   | AI 自动总结、AI 月报生成                     |
| 20.6   | 模板市场、字段级权限                         |
| 20.7   | 审批流、SLA                                  |
| 20.8   | Portal 收口、企业 E2E、性能与生产验收        |

## 2. 总体架构

新增一个 Maven 模块：

```text
modules/aiops-work-record-extension
```

它是模块化单体中的扩展域，不是微服务：

```text
Portal
  │
  ▼
aiops-server
  ├── aiops-work-record             核心模板/版本/记录/查询/同步导出
  ├── aiops-work-record-extension   导入/异步任务/评论/附件/统计/AI/审批/SLA
  ├── aiops-platform                字典/工作日历
  ├── aiops-alert                   告警
  ├── aiops-inspection              巡检
  ├── aiops-incident                事件
  └── aiops-ai-client               Java → Python Agent
          │
          ├── PostgreSQL
          ├── MinIO
          └── automation_outbox
                    │
                    ▼
               aiops-worker
                    │
                    ▼
               aiops-agent
```

依赖规则：

```text
work-record-extension -> work-record
work-record-extension -> common/security/audit/platform/user/ai-client
work-record           -X-> work-record-extension
api                    -X-> infrastructure
application            -X-> api/infrastructure
```

审批和 SLA 如需改变记录状态，只能调用核心模块新增的 `WorkRecordLifecyclePort`，扩展模块不得直接更新 `wr_record`。

## 3. 文件结构

```text
modules/aiops-work-record-extension/
├── pom.xml
├── src/main/java/io/aegisops/workrecord/extension/
│   ├── api/
│   │   ├── WorkRecordAsyncJobController.java
│   │   ├── WorkRecordCommentController.java
│   │   ├── WorkRecordAttachmentController.java
│   │   ├── WorkRecordRelationController.java
│   │   ├── WorkRecordAnalyticsController.java
│   │   ├── WorkRecordReminderController.java
│   │   ├── WorkRecordHandoverController.java
│   │   ├── WorkRecordAiController.java
│   │   ├── WorkRecordMarketController.java
│   │   ├── WorkRecordApprovalController.java
│   │   └── WorkRecordSlaController.java
│   ├── application/
│   │   ├── command/
│   │   ├── port/
│   │   └── service/
│   ├── domain/model/
│   └── infrastructure/
│       ├── jdbc/
│       └── storage/
└── src/test/java/io/aegisops/workrecord/extension/

apps/aiops-worker/src/main/java/io/aegisops/worker/job/workrecord/
├── WorkRecordImportValidateJob.java
├── WorkRecordImportCommitJob.java
├── WorkRecordAsyncExportJob.java
├── WorkRecordMissingReminderJob.java
├── WorkRecordAiSummaryJob.java
├── WorkRecordAiMonthlyReportJob.java
└── WorkRecordSlaScanJob.java

web/portal/src/components/work-records/extensions/
├── api.ts
├── types.ts
├── async-jobs/
├── import/
├── comments/
├── attachments/
├── relations/
├── analytics/
├── handover/
├── ai/
├── market/
├── approval/
└── sla/
```

## 4. 数据库迁移

### 4.1 V0028：Outbox 增强与异步任务

```sql
-- apps/aiops-server/src/main/resources/db/migration/
-- V0028__phase20_async_job_and_outbox.sql

alter table automation_outbox
    add column if not exists available_at timestamptz;

alter table automation_outbox
    add column if not exists lease_until timestamptz;

alter table automation_outbox
    add column if not exists idempotency_key varchar(160);

update automation_outbox
set available_at = coalesce(available_at, created_at, now())
where available_at is null;

alter table automation_outbox
    alter column available_at set default now();

alter table automation_outbox
    alter column available_at set not null;

create unique index if not exists
idx_automation_outbox_idempotency
on automation_outbox(target_app, job_name, idempotency_key)
where idempotency_key is not null;

create index if not exists
idx_automation_outbox_claim
on automation_outbox(target_app, status, available_at, created_at);

create index if not exists
idx_automation_outbox_expired_lease
on automation_outbox(target_app, lease_until)
where status = 'processing';

create table if not exists work_record.wr_async_job (
    id varchar(64) primary key,
    tenant_id varchar(64) not null,
    job_type varchar(48) not null,
    status varchar(32) not null default 'queued',
    requested_by varchar(64) not null,
    request_json jsonb not null default '{}'::jsonb,
    result_json jsonb not null default '{}'::jsonb,
    source_object_key varchar(512),
    result_object_key varchar(512),
    result_file_name varchar(255),
    result_content_type varchar(128),
    total_count integer not null default 0,
    processed_count integer not null default 0,
    success_count integer not null default 0,
    failure_count integer not null default 0,
    error_message text,
    idempotency_key varchar(160),
    row_version integer not null default 1,
    created_at timestamptz not null default now(),
    started_at timestamptz,
    finished_at timestamptz,
    expires_at timestamptz,

    constraint ck_wr_async_job_type
        check (job_type in (
            'import_validate',
            'import_commit',
            'async_export',
            'missing_reminder',
            'ai_record_summary',
            'ai_monthly_report',
            'sla_scan'
        )),

    constraint ck_wr_async_job_status
        check (status in (
            'queued',
            'running',
            'validated',
            'success',
            'partial_success',
            'failed',
            'cancelled',
            'expired'
        )),

    constraint ck_wr_async_job_request_object
        check (jsonb_typeof(request_json) = 'object'),

    constraint ck_wr_async_job_result_object
        check (jsonb_typeof(result_json) = 'object'),

    constraint ck_wr_async_job_progress
        check (
            total_count >= 0
            and processed_count >= 0
            and success_count >= 0
            and failure_count >= 0
            and processed_count <= total_count
            and success_count + failure_count <= processed_count
        )
);

create unique index if not exists
uk_wr_async_job_idempotency
on work_record.wr_async_job(tenant_id, job_type, idempotency_key)
where idempotency_key is not null;

create index if not exists
idx_wr_async_job_tenant_created
on work_record.wr_async_job(tenant_id, created_at desc, id desc);

create index if not exists
idx_wr_async_job_tenant_status
on work_record.wr_async_job(tenant_id, status, created_at desc);

create table if not exists work_record.wr_async_job_item (
    id varchar(64) primary key,
    tenant_id varchar(64) not null,
    job_id varchar(64) not null
        references work_record.wr_async_job(id)
        on delete cascade,
    item_key varchar(160) not null,
    row_number integer,
    status varchar(24) not null,
    resource_id varchar(64),
    error_code varchar(96),
    error_message text,
    detail_json jsonb not null default '{}'::jsonb,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),

    constraint uk_wr_async_job_item
        unique (job_id, item_key),

    constraint ck_wr_async_job_item_status
        check (status in ('pending', 'success', 'failed', 'skipped')),

    constraint ck_wr_async_job_item_detail
        check (jsonb_typeof(detail_json) = 'object')
);

create index if not exists
idx_wr_async_job_item_job_status
on work_record.wr_async_job_item(job_id, status, row_number);
```

### 4.2 V0029：评论、附件、关联对象

```sql
-- V0029__phase20_collaboration.sql

create table if not exists work_record.wr_comment (
    id varchar(64) primary key,
    tenant_id varchar(64) not null,
    record_id varchar(64) not null
        references work_record.wr_record(id)
        on delete cascade,
    content text not null,
    mentions_json jsonb not null default '[]'::jsonb,
    created_by varchar(64) not null,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    deleted_at timestamptz,
    row_version integer not null default 1,

    constraint ck_wr_comment_content
        check (char_length(content) between 1 and 4000),

    constraint ck_wr_comment_mentions
        check (jsonb_typeof(mentions_json) = 'array')
);

create index if not exists
idx_wr_comment_record_time
on work_record.wr_comment(tenant_id, record_id, created_at, id)
where deleted_at is null;

create table if not exists work_record.wr_attachment (
    id varchar(64) primary key,
    tenant_id varchar(64) not null,
    record_id varchar(64) not null
        references work_record.wr_record(id)
        on delete cascade,
    object_key varchar(512) not null,
    file_name varchar(255) not null,
    content_type varchar(128) not null,
    size_bytes bigint not null,
    sha256 varchar(64) not null,
    status varchar(24) not null default 'ready',
    uploaded_by varchar(64) not null,
    created_at timestamptz not null default now(),
    deleted_at timestamptz,

    constraint uk_wr_attachment_object_key
        unique (object_key),

    constraint ck_wr_attachment_size
        check (size_bytes > 0 and size_bytes <= 20971520),

    constraint ck_wr_attachment_sha256
        check (sha256 ~ '^[0-9a-f]{64}$'),

    constraint ck_wr_attachment_status
        check (status in ('ready', 'quarantined', 'deleted'))
);

create index if not exists
idx_wr_attachment_record
on work_record.wr_attachment(tenant_id, record_id, created_at desc)
where deleted_at is null;

create table if not exists work_record.wr_record_relation (
    id varchar(64) primary key,
    tenant_id varchar(64) not null,
    record_id varchar(64) not null
        references work_record.wr_record(id)
        on delete cascade,
    relation_type varchar(32) not null,
    target_id varchar(64) not null,
    target_title varchar(255),
    target_status varchar(64),
    snapshot_json jsonb not null default '{}'::jsonb,
    created_by varchar(64) not null,
    created_at timestamptz not null default now(),

    constraint uk_wr_record_relation
        unique (tenant_id, record_id, relation_type, target_id),

    constraint ck_wr_record_relation_type
        check (relation_type in ('alert', 'inspection', 'incident')),

    constraint ck_wr_record_relation_snapshot
        check (jsonb_typeof(snapshot_json) = 'object')
);

create index if not exists
idx_wr_record_relation_target
on work_record.wr_record_relation(tenant_id, relation_type, target_id);
```

### 4.3 V0030：提醒、通知、值班交接

```sql
-- V0030__phase20_reminder_handover.sql

create table if not exists work_record.wr_reminder_rule (
    id varchar(64) primary key,
    tenant_id varchar(64) not null,
    name varchar(160) not null,
    template_id varchar(64) not null
        references work_record.wr_template(id),
    target_type varchar(24) not null,
    target_json jsonb not null default '{}'::jsonb,
    cutoff_time time not null,
    time_zone varchar(64) not null,
    enabled boolean not null default true,
    created_by varchar(64) not null,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),

    constraint ck_wr_reminder_target_type
        check (target_type in ('users', 'role')),

    constraint ck_wr_reminder_target_json
        check (jsonb_typeof(target_json) = 'object')
);

create index if not exists
idx_wr_reminder_rule_due
on work_record.wr_reminder_rule(enabled, time_zone, cutoff_time);

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

    constraint uk_wr_notification_dedupe
        unique (tenant_id, user_id, dedupe_key)
);

create index if not exists
idx_wr_notification_user_unread
on work_record.wr_notification(tenant_id, user_id, read_at, created_at desc);

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

    constraint ck_wr_handover_period
        check (shift_end > shift_start),

    constraint ck_wr_handover_status
        check (status in ('draft', 'submitted', 'accepted', 'completed', 'cancelled')),

    constraint ck_wr_handover_records
        check (jsonb_typeof(record_ids_json) = 'array'),

    constraint ck_wr_handover_relations
        check (jsonb_typeof(relation_ids_json) = 'array')
);

create index if not exists
idx_wr_handover_receiver
on work_record.wr_handover(tenant_id, to_user_id, status, created_at desc);
```

### 4.4 V0031：AI 生成结果

```sql
-- V0031__phase20_ai_generation.sql

create table if not exists work_record.wr_ai_generation (
    id varchar(64) primary key,
    tenant_id varchar(64) not null,
    generation_type varchar(32) not null,
    resource_type varchar(32) not null,
    resource_id varchar(64) not null,
    period_start date,
    period_end date,
    status varchar(24) not null default 'queued',
    prompt_version varchar(64) not null,
    input_hash varchar(64) not null,
    input_json jsonb not null default '{}'::jsonb,
    output_markdown text,
    provider varchar(128),
    model varchar(128),
    requested_by varchar(64) not null,
    reviewed_by varchar(64),
    reviewed_at timestamptz,
    created_at timestamptz not null default now(),
    finished_at timestamptz,

    constraint ck_wr_ai_generation_type
        check (generation_type in ('record_summary', 'monthly_report')),

    constraint ck_wr_ai_generation_resource
        check (resource_type in ('record', 'tenant_month')),

    constraint ck_wr_ai_generation_status
        check (status in ('queued', 'running', 'success', 'failed', 'accepted', 'rejected')),

    constraint ck_wr_ai_generation_input
        check (jsonb_typeof(input_json) = 'object'),

    constraint ck_wr_ai_generation_hash
        check (input_hash ~ '^[0-9a-f]{64}$')
);

create unique index if not exists
uk_wr_ai_generation_input
on work_record.wr_ai_generation(
    tenant_id,
    generation_type,
    resource_type,
    resource_id,
    input_hash
)
where status in ('queued', 'running', 'success', 'accepted');

create index if not exists
idx_wr_ai_generation_resource
on work_record.wr_ai_generation(
    tenant_id,
    resource_type,
    resource_id,
    created_at desc
);
```

### 4.5 V0032：模板市场与字段级权限

```sql
-- V0032__phase20_market_field_policy.sql

create table if not exists work_record.wr_market_package (
    id varchar(64) primary key,
    publisher_tenant_id varchar(64),
    source_template_id varchar(64),
    package_code varchar(96) not null,
    name varchar(160) not null,
    summary varchar(500),
    category varchar(64) not null,
    visibility varchar(24) not null default 'private',
    status varchar(24) not null default 'draft',
    latest_version_id varchar(64),
    install_count bigint not null default 0,
    created_by varchar(64) not null,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),

    constraint uk_wr_market_package_code
        unique (package_code),

    constraint ck_wr_market_visibility
        check (visibility in ('private', 'tenant', 'public')),

    constraint ck_wr_market_status
        check (status in ('draft', 'published', 'withdrawn'))
);

create table if not exists work_record.wr_market_package_version (
    id varchar(64) primary key,
    package_id varchar(64) not null
        references work_record.wr_market_package(id)
        on delete cascade,
    version_no integer not null,
    version_name varchar(128),
    package_json jsonb not null,
    checksum varchar(64) not null,
    published_by varchar(64) not null,
    created_at timestamptz not null default now(),

    constraint uk_wr_market_package_version
        unique (package_id, version_no),

    constraint ck_wr_market_package_json
        check (jsonb_typeof(package_json) = 'object'),

    constraint ck_wr_market_checksum
        check (checksum ~ '^[0-9a-f]{64}$')
);

alter table work_record.wr_market_package
    add constraint fk_wr_market_latest_version
    foreign key (latest_version_id)
    references work_record.wr_market_package_version(id)
    deferrable initially deferred;

create table if not exists work_record.wr_market_install (
    id varchar(64) primary key,
    tenant_id varchar(64) not null,
    package_id varchar(64) not null
        references work_record.wr_market_package(id),
    package_version_id varchar(64) not null
        references work_record.wr_market_package_version(id),
    installed_template_id varchar(64) not null
        references work_record.wr_template(id),
    installed_by varchar(64) not null,
    created_at timestamptz not null default now(),

    constraint uk_wr_market_install
        unique (tenant_id, package_version_id, installed_template_id)
);

create table if not exists work_record.wr_field_policy (
    id varchar(64) primary key,
    tenant_id varchar(64) not null,
    template_version_id varchar(64) not null
        references work_record.wr_template_version(id)
        on delete cascade,
    field_code varchar(64) not null,
    read_roles_json jsonb not null default '[]'::jsonb,
    write_roles_json jsonb not null default '[]'::jsonb,
    mask_mode varchar(24) not null default 'none',
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),

    constraint uk_wr_field_policy
        unique (tenant_id, template_version_id, field_code),

    constraint ck_wr_field_policy_read_roles
        check (jsonb_typeof(read_roles_json) = 'array'),

    constraint ck_wr_field_policy_write_roles
        check (jsonb_typeof(write_roles_json) = 'array'),

    constraint ck_wr_field_policy_mask_mode
        check (mask_mode in ('none', 'full', 'partial'))
);

create index if not exists
idx_wr_field_policy_version
on work_record.wr_field_policy(tenant_id, template_version_id);
```

### 4.6 V0033：审批流

```sql
-- V0033__phase20_approval.sql

create table if not exists work_record.wr_approval_definition (
    id varchar(64) primary key,
    tenant_id varchar(64) not null,
    template_id varchar(64) not null
        references work_record.wr_template(id),
    name varchar(160) not null,
    version_no integer not null,
    trigger_status varchar(32) not null default 'done',
    enabled boolean not null default true,
    created_by varchar(64) not null,
    created_at timestamptz not null default now(),

    constraint uk_wr_approval_definition_version
        unique (tenant_id, template_id, version_no)
);

create table if not exists work_record.wr_approval_step (
    id varchar(64) primary key,
    definition_id varchar(64) not null
        references work_record.wr_approval_definition(id)
        on delete cascade,
    step_no integer not null,
    name varchar(160) not null,
    approver_type varchar(24) not null,
    approver_value varchar(128) not null,
    approval_mode varchar(24) not null default 'any',
    timeout_minutes integer,

    constraint uk_wr_approval_step_no
        unique (definition_id, step_no),

    constraint ck_wr_approval_approver_type
        check (approver_type in ('user', 'role', 'record_owner')),

    constraint ck_wr_approval_mode
        check (approval_mode in ('any', 'all')),

    constraint ck_wr_approval_timeout
        check (timeout_minutes is null or timeout_minutes > 0)
);

create table if not exists work_record.wr_approval_instance (
    id varchar(64) primary key,
    tenant_id varchar(64) not null,
    record_id varchar(64) not null
        references work_record.wr_record(id),
    definition_id varchar(64) not null
        references work_record.wr_approval_definition(id),
    status varchar(24) not null default 'pending',
    current_step_no integer not null default 1,
    started_by varchar(64) not null,
    started_at timestamptz not null default now(),
    finished_at timestamptz,
    row_version integer not null default 1,

    constraint ck_wr_approval_instance_status
        check (status in ('pending', 'approved', 'rejected', 'cancelled'))
);

create unique index if not exists
uk_wr_approval_active_instance
on work_record.wr_approval_instance(tenant_id, record_id)
where status = 'pending';

create table if not exists work_record.wr_approval_task (
    id varchar(64) primary key,
    tenant_id varchar(64) not null,
    instance_id varchar(64) not null
        references work_record.wr_approval_instance(id)
        on delete cascade,
    step_no integer not null,
    assignee_type varchar(24) not null,
    assignee_value varchar(128) not null,
    status varchar(24) not null default 'pending',
    acted_by varchar(64),
    action_comment varchar(1000),
    due_at timestamptz,
    acted_at timestamptz,
    created_at timestamptz not null default now(),

    constraint ck_wr_approval_task_status
        check (status in ('pending', 'approved', 'rejected', 'cancelled', 'expired'))
);

create index if not exists
idx_wr_approval_task_pending
on work_record.wr_approval_task(tenant_id, status, due_at, created_at);
```

### 4.7 V0034：SLA

```sql
-- V0034__phase20_sla.sql

create table if not exists work_record.wr_sla_policy (
    id varchar(64) primary key,
    tenant_id varchar(64) not null,
    template_id varchar(64) not null
        references work_record.wr_template(id),
    name varchar(160) not null,
    start_event varchar(32) not null,
    stop_event varchar(32) not null,
    target_minutes integer not null,
    calendar_aware boolean not null default false,
    severity varchar(24) not null default 'warning',
    enabled boolean not null default true,
    created_by varchar(64) not null,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),

    constraint ck_wr_sla_target
        check (target_minutes > 0),

    constraint ck_wr_sla_severity
        check (severity in ('info', 'warning', 'critical'))
);

create table if not exists work_record.wr_sla_instance (
    id varchar(64) primary key,
    tenant_id varchar(64) not null,
    record_id varchar(64) not null
        references work_record.wr_record(id),
    policy_id varchar(64) not null
        references work_record.wr_sla_policy(id),
    status varchar(24) not null default 'running',
    started_at timestamptz not null,
    due_at timestamptz not null,
    stopped_at timestamptz,
    breached_at timestamptz,
    elapsed_minutes integer,
    row_version integer not null default 1,

    constraint uk_wr_sla_instance
        unique (tenant_id, record_id, policy_id),

    constraint ck_wr_sla_instance_status
        check (status in ('running', 'met', 'breached', 'cancelled'))
);

create index if not exists
idx_wr_sla_due
on work_record.wr_sla_instance(tenant_id, status, due_at)
where status = 'running';

create table if not exists work_record.wr_sla_event (
    id varchar(64) primary key,
    tenant_id varchar(64) not null,
    instance_id varchar(64) not null
        references work_record.wr_sla_instance(id)
        on delete cascade,
    event_type varchar(48) not null,
    detail_json jsonb not null default '{}'::jsonb,
    event_at timestamptz not null default now(),

    constraint ck_wr_sla_event_detail
        check (jsonb_typeof(detail_json) = 'object')
);
```

### 4.8 V0035：权限初始化

```sql
-- V0035__phase20_permissions.sql

insert into iam.permission(
    permission_code,
    permission_name,
    module_code,
    resource_type,
    description
)
values
    ('work-record:import', '导入工作记录', 'work-record', 'ACTION', '通过 Excel 批量导入工作记录'),
    ('work-record:export:async', '异步导出工作记录', 'work-record', 'ACTION', '创建和下载异步导出任务'),
    ('work-record:comment', '评论工作记录', 'work-record', 'ACTION', '创建和维护本人评论'),
    ('work-record:comment:moderate', '管理工作记录评论', 'work-record', 'ACTION', '删除任意工作记录评论'),
    ('work-record:attachment', '管理本人附件', 'work-record', 'ACTION', '上传、下载和删除本人附件'),
    ('work-record:attachment:moderate', '管理全部附件', 'work-record', 'ACTION', '删除任意工作记录附件'),
    ('work-record:relation', '关联运维对象', 'work-record', 'ACTION', '关联告警、巡检和事件'),
    ('work-record:analytics', '查看工作记录统计', 'work-record', 'API', '查看统计报表和工作量分析'),
    ('work-record:reminder:manage', '管理日报提醒', 'work-record', 'ACTION', '配置日报缺失提醒'),
    ('work-record:handover', '值班交接', 'work-record', 'ACTION', '创建、接收和完成值班交接'),
    ('work-record:ai:generate', '生成 AI 工作记录内容', 'work-record', 'ACTION', '生成记录总结和月报草稿'),
    ('work-record:ai:review', '审核 AI 工作记录内容', 'work-record', 'ACTION', '接受或拒绝 AI 生成结果'),
    ('work-record:market:read', '查看模板市场', 'work-record', 'API', '浏览模板市场'),
    ('work-record:market:install', '安装市场模板', 'work-record', 'ACTION', '从市场安装模板'),
    ('work-record:market:publish', '发布市场模板', 'work-record', 'ACTION', '发布或撤回模板包'),
    ('work-record:field-policy:manage', '管理字段权限', 'work-record', 'ACTION', '配置字段读写角色和脱敏'),
    ('work-record:approval:manage', '管理审批流', 'work-record', 'ACTION', '配置审批定义'),
    ('work-record:approval:act', '审批工作记录', 'work-record', 'ACTION', '处理待审批任务'),
    ('work-record:sla:read', '查看工作记录 SLA', 'work-record', 'API', '查看记录 SLA 状态'),
    ('work-record:sla:manage', '管理工作记录 SLA', 'work-record', 'ACTION', '配置 SLA 策略')
on conflict (permission_code) do update
set permission_name = excluded.permission_name,
    module_code = excluded.module_code,
    resource_type = excluded.resource_type,
    description = excluded.description,
    enabled = true,
    updated_at = now();

insert into iam.role_permission(role_code, permission_code)
select role_code, permission_code
from (
    values ('system_admin'), ('record_admin')
) roles(role_code)
cross join iam.permission permission
where permission.permission_code like 'work-record:%'
on conflict do nothing;

insert into iam.role_permission(role_code, permission_code)
select 'normal_user', permission_code
from iam.permission
where permission_code in (
    'work-record:import',
    'work-record:export:async',
    'work-record:comment',
    'work-record:attachment',
    'work-record:relation',
    'work-record:handover',
    'work-record:ai:generate',
    'work-record:market:read',
    'work-record:market:install',
    'work-record:sla:read'
)
on conflict do nothing;

insert into iam.role_permission(role_code, permission_code)
select 'readonly_user', permission_code
from iam.permission
where permission_code in (
    'work-record:market:read',
    'work-record:sla:read'
)
on conflict do nothing;
```

`PermissionCodes.java` 同步增加以上常量，并建立 `PHASE_20_PERMISSIONS` 集合；`ALL_PERMISSIONS` 合并 Phase 13 与 Phase 20，避免 `requireKnown()` 拒绝新权限。

## 5. Maven 模块

### 5.1 根 pom.xml

在 `<modules>` 中加入：

```xml
<module>modules/aiops-work-record-extension</module>
```

在 `<properties>` 中加入：

```xml
<apache-poi.version>5.3.0</apache-poi.version>
<minio.version>8.5.17</minio.version>
```

### 5.2 modules/aiops-work-record-extension/pom.xml

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
  <modelVersion>4.0.0</modelVersion>

  <parent>
    <groupId>io.aegisops</groupId>
    <artifactId>aegisops</artifactId>
    <version>0.1.0-SNAPSHOT</version>
    <relativePath>../../pom.xml</relativePath>
  </parent>

  <artifactId>aiops-work-record-extension</artifactId>
  <packaging>jar</packaging>

  <dependencies>
    <dependency>
      <groupId>io.aegisops</groupId>
      <artifactId>aiops-ai-client</artifactId>
      <version>${project.version}</version>
    </dependency>
    <dependency>
      <groupId>io.aegisops</groupId>
      <artifactId>aiops-audit</artifactId>
      <version>${project.version}</version>
    </dependency>
    <dependency>
      <groupId>io.aegisops</groupId>
      <artifactId>aiops-common</artifactId>
      <version>${project.version}</version>
    </dependency>
    <dependency>
      <groupId>io.aegisops</groupId>
      <artifactId>aiops-platform</artifactId>
      <version>${project.version}</version>
    </dependency>
    <dependency>
      <groupId>io.aegisops</groupId>
      <artifactId>aiops-security</artifactId>
      <version>${project.version}</version>
    </dependency>
    <dependency>
      <groupId>io.aegisops</groupId>
      <artifactId>aiops-user</artifactId>
      <version>${project.version}</version>
    </dependency>
    <dependency>
      <groupId>io.aegisops</groupId>
      <artifactId>aiops-work-record</artifactId>
      <version>${project.version}</version>
    </dependency>

    <dependency>
      <groupId>io.minio</groupId>
      <artifactId>minio</artifactId>
      <version>${minio.version}</version>
    </dependency>
    <dependency>
      <groupId>org.apache.poi</groupId>
      <artifactId>poi-ooxml</artifactId>
      <version>${apache-poi.version}</version>
    </dependency>
    <dependency>
      <groupId>org.springframework.boot</groupId>
      <artifactId>spring-boot-starter-jdbc</artifactId>
    </dependency>
    <dependency>
      <groupId>org.springframework.boot</groupId>
      <artifactId>spring-boot-starter-validation</artifactId>
    </dependency>
    <dependency>
      <groupId>org.springframework.boot</groupId>
      <artifactId>spring-boot-starter-web</artifactId>
    </dependency>

    <dependency>
      <groupId>com.tngtech.archunit</groupId>
      <artifactId>archunit-junit5</artifactId>
      <version>1.4.2</version>
      <scope>test</scope>
    </dependency>
    <dependency>
      <groupId>org.postgresql</groupId>
      <artifactId>postgresql</artifactId>
      <scope>test</scope>
    </dependency>
    <dependency>
      <groupId>org.springframework.boot</groupId>
      <artifactId>spring-boot-starter-test</artifactId>
      <scope>test</scope>
    </dependency>
    <dependency>
      <groupId>org.springframework.security</groupId>
      <artifactId>spring-security-test</artifactId>
      <scope>test</scope>
    </dependency>
    <dependency>
      <groupId>org.testcontainers</groupId>
      <artifactId>junit-jupiter</artifactId>
      <scope>test</scope>
    </dependency>
    <dependency>
      <groupId>org.testcontainers</groupId>
      <artifactId>postgresql</artifactId>
      <scope>test</scope>
    </dependency>
  </dependencies>

  <build>
    <plugins>
      <plugin>
        <groupId>org.apache.maven.plugins</groupId>
        <artifactId>maven-failsafe-plugin</artifactId>
      </plugin>
    </plugins>
  </build>
</project>
```

`apps/aiops-server/pom.xml` 和 `apps/aiops-worker/pom.xml` 均加入：

```xml
<dependency>
  <groupId>io.aegisops</groupId>
  <artifactId>aiops-work-record-extension</artifactId>
  <version>${project.version}</version>
</dependency>
```

## 6. Phase 20.0：Outbox 必须先升级

当前 Worker 的 `tick()` 在一个事务中执行整个 Job；大导出或 AI 调用会长时间占用事务。Phase 20 必须改为“短事务 claim、事务外执行、短事务回写”。

### 6.1 OutboxMessage.java

```java
package io.aegisops.common.outbox;

import java.time.OffsetDateTime;
import java.util.Map;

public record OutboxMessage(
    String tenantId,
    String targetApp,
    String jobName,
    Map<String, Object> payload,
    String idempotencyKey,
    int maxRetries,
    OffsetDateTime availableAt) {

  public OutboxMessage {
    if (targetApp == null || targetApp.isBlank()) {
      throw new IllegalArgumentException("targetApp is required");
    }
    if (jobName == null || jobName.isBlank()) {
      throw new IllegalArgumentException("jobName is required");
    }
    payload = payload == null ? Map.of() : Map.copyOf(payload);
    maxRetries = Math.max(1, maxRetries);
    availableAt = availableAt == null ? OffsetDateTime.now() : availableAt;
  }
}
```

### 6.2 完整替换 OutboxWriter.java

```java
package io.aegisops.common.outbox;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OutboxWriter {

  private final JdbcTemplate jdbc;
  private final ObjectMapper objectMapper;

  public OutboxWriter(JdbcTemplate jdbc, ObjectMapper objectMapper) {
    this.jdbc = jdbc;
    this.objectMapper = objectMapper;
  }

  @Transactional(propagation = Propagation.REQUIRED)
  public String enqueue(String targetApp, String jobName, Map<String, Object> payload) {
    Object tenant = payload == null ? null : payload.get("tenantId");
    String tenantId = tenant == null ? null : String.valueOf(tenant);
    return enqueue(
        new OutboxMessage(
            tenantId,
            targetApp,
            jobName,
            payload,
            null,
            3,
            null));
  }

  @Transactional(propagation = Propagation.REQUIRED)
  public String enqueue(OutboxMessage message) {
    String id = "outbox_" + UUID.randomUUID().toString().replace("-", "");
    String payloadJson = serialize(message.payload());

    int inserted =
        jdbc.update(
            """
            insert into automation_outbox(
                id,
                tenant_id,
                target_app,
                job_name,
                payload,
                status,
                retry_count,
                max_retries,
                available_at,
                idempotency_key,
                created_at,
                updated_at
            )
            values (
                ?, ?, ?, ?, ?::jsonb,
                'pending', 0, ?, ?, ?, now(), now()
            )
            on conflict (target_app, job_name, idempotency_key)
            where idempotency_key is not null
            do nothing
            """,
            id,
            blankToNull(message.tenantId()),
            message.targetApp(),
            message.jobName(),
            payloadJson,
            message.maxRetries(),
            message.availableAt(),
            blankToNull(message.idempotencyKey()));

    if (inserted == 1) {
      return id;
    }

    return jdbc.queryForObject(
        """
        select id
        from automation_outbox
        where target_app = ?
          and job_name = ?
          and idempotency_key = ?
        """,
        String.class,
        message.targetApp(),
        message.jobName(),
        message.idempotencyKey());
  }

  private String serialize(Map<String, Object> payload) {
    try {
      return objectMapper.writeValueAsString(payload);
    } catch (JsonProcessingException ex) {
      throw new IllegalStateException("failed to serialize outbox payload", ex);
    }
  }

  private String blankToNull(String value) {
    return value == null || value.isBlank() ? null : value;
  }
}
```

### 6.3 完整替换 OutboxProperties.java

```java
package io.aegisops.worker.outbox;

import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "aiops.outbox")
public record OutboxProperties(
    boolean enabled,
    @Min(100) long pollDelayMs,
    @Min(1) int batchSize,
    String targetApp,
    @Min(10) long leaseSeconds) {

  public OutboxProperties {
    if (targetApp == null || targetApp.isBlank()) {
      targetApp = "worker";
    }
    if (leaseSeconds < 10) {
      leaseSeconds = 300;
    }
  }
}
```

Worker 配置增加：

```yaml
aiops:
  outbox:
    lease-seconds: ${AIOPS_OUTBOX_LEASE_SECONDS:300}
```

### 6.4 完整替换 OutboxRepository.java

```java
package io.aegisops.worker.outbox;

import io.aegisops.persistence.jooq.public_.tables.records.AutomationOutboxRecord;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

public interface OutboxRepository {

  List<AutomationOutboxRecord> claimNextPending(
      String targetApp,
      int batchSize,
      Duration leaseDuration);

  Optional<AutomationOutboxRecord> findById(String id);

  boolean markDone(String id, OffsetDateTime processedAt);

  boolean recordFailure(String id, String errorMessage);

  int resetExpiredProcessing(String targetApp, OffsetDateTime now);
}
```

### 6.5 完整替换 JooqOutboxRepository.java

```java
package io.aegisops.worker.outbox;

import static io.aegisops.persistence.jooq.public_.Tables.AUTOMATION_OUTBOX;

import io.aegisops.persistence.jooq.public_.tables.records.AutomationOutboxRecord;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.jooq.DSLContext;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Repository;

@Repository
public class JooqOutboxRepository implements OutboxRepository {

  private final DSLContext dsl;

  public JooqOutboxRepository(DSLContext dsl) {
    this.dsl = dsl;
  }

  @Override
  public List<AutomationOutboxRecord> claimNextPending(
      String targetApp,
      int batchSize,
      Duration leaseDuration) {
    OffsetDateTime now = OffsetDateTime.now();
    OffsetDateTime leaseUntil = now.plus(leaseDuration);

    return dsl.transactionResult(
        configuration -> {
          DSLContext tx = DSL.using(configuration);
          List<String> ids =
              tx.select(AUTOMATION_OUTBOX.ID)
                  .from(AUTOMATION_OUTBOX)
                  .where(AUTOMATION_OUTBOX.TARGET_APP.eq(targetApp))
                  .and(AUTOMATION_OUTBOX.STATUS.eq("pending"))
                  .and(AUTOMATION_OUTBOX.AVAILABLE_AT.le(now))
                  .orderBy(
                      AUTOMATION_OUTBOX.AVAILABLE_AT.asc(),
                      AUTOMATION_OUTBOX.CREATED_AT.asc())
                  .limit(batchSize)
                  .forUpdate()
                  .skipLocked()
                  .fetch(AUTOMATION_OUTBOX.ID);

          if (ids.isEmpty()) {
            return List.of();
          }

          return tx.update(AUTOMATION_OUTBOX)
              .set(AUTOMATION_OUTBOX.STATUS, "processing")
              .set(AUTOMATION_OUTBOX.LEASE_UNTIL, leaseUntil)
              .set(AUTOMATION_OUTBOX.UPDATED_AT, now)
              .where(AUTOMATION_OUTBOX.ID.in(ids))
              .returning()
              .fetch();
        });
  }

  @Override
  public Optional<AutomationOutboxRecord> findById(String id) {
    return Optional.ofNullable(
        dsl.selectFrom(AUTOMATION_OUTBOX)
            .where(AUTOMATION_OUTBOX.ID.eq(id))
            .fetchOne());
  }

  @Override
  public boolean markDone(String id, OffsetDateTime processedAt) {
    return dsl.update(AUTOMATION_OUTBOX)
            .set(AUTOMATION_OUTBOX.STATUS, "done")
            .set(AUTOMATION_OUTBOX.PROCESSED_AT, processedAt)
            .set(AUTOMATION_OUTBOX.LEASE_UNTIL, (OffsetDateTime) null)
            .set(AUTOMATION_OUTBOX.ERROR_MESSAGE, (String) null)
            .set(AUTOMATION_OUTBOX.UPDATED_AT, OffsetDateTime.now())
            .where(AUTOMATION_OUTBOX.ID.eq(id))
            .execute()
        == 1;
  }

  @Override
  public boolean recordFailure(String id, String errorMessage) {
    return dsl.transactionResult(
        configuration -> {
          DSLContext tx = DSL.using(configuration);
          AutomationOutboxRecord row =
              tx.selectFrom(AUTOMATION_OUTBOX)
                  .where(AUTOMATION_OUTBOX.ID.eq(id))
                  .forUpdate()
                  .fetchOne();

          if (row == null) {
            return false;
          }

          int retry = row.getRetryCount() + 1;
          boolean exhausted = retry >= row.getMaxRetries();
          long delaySeconds = Math.min(300L, 1L << Math.min(retry, 8));

          return tx.update(AUTOMATION_OUTBOX)
                  .set(AUTOMATION_OUTBOX.RETRY_COUNT, retry)
                  .set(AUTOMATION_OUTBOX.STATUS, exhausted ? "failed" : "pending")
                  .set(AUTOMATION_OUTBOX.AVAILABLE_AT, OffsetDateTime.now().plusSeconds(delaySeconds))
                  .set(AUTOMATION_OUTBOX.LEASE_UNTIL, (OffsetDateTime) null)
                  .set(AUTOMATION_OUTBOX.ERROR_MESSAGE, truncate(errorMessage))
                  .set(AUTOMATION_OUTBOX.UPDATED_AT, OffsetDateTime.now())
                  .where(AUTOMATION_OUTBOX.ID.eq(id))
                  .execute()
              == 1;
        });
  }

  @Override
  public int resetExpiredProcessing(String targetApp, OffsetDateTime now) {
    return dsl.update(AUTOMATION_OUTBOX)
        .set(AUTOMATION_OUTBOX.STATUS, "pending")
        .set(AUTOMATION_OUTBOX.LEASE_UNTIL, (OffsetDateTime) null)
        .set(AUTOMATION_OUTBOX.AVAILABLE_AT, now)
        .set(AUTOMATION_OUTBOX.UPDATED_AT, now)
        .where(AUTOMATION_OUTBOX.TARGET_APP.eq(targetApp))
        .and(AUTOMATION_OUTBOX.STATUS.eq("processing"))
        .and(AUTOMATION_OUTBOX.LEASE_UNTIL.lt(now))
        .execute();
  }

  private String truncate(String value) {
    if (value == null) {
      return null;
    }
    return value.length() <= 2000 ? value : value.substring(0, 2000);
  }
}
```

### 6.6 完整替换 OutboxPoller.java

```java
package io.aegisops.worker.outbox;

import io.aegisops.persistence.jooq.public_.tables.records.AutomationOutboxRecord;
import io.aegisops.worker.job.JobResult;
import io.aegisops.worker.job.OutboxJob;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class OutboxPoller {

  private static final Logger LOGGER = LoggerFactory.getLogger(OutboxPoller.class);

  private final OutboxRepository repository;
  private final OutboxProperties properties;
  private final Map<String, OutboxJob> jobsByName;

  public OutboxPoller(
      OutboxRepository repository,
      OutboxProperties properties,
      List<OutboxJob> jobs) {
    this.repository = repository;
    this.properties = properties;

    Map<String, OutboxJob> mapped = new HashMap<>();
    for (OutboxJob job : jobs) {
      OutboxJob previous = mapped.put(job.jobName(), job);
      if (previous != null) {
        throw new IllegalStateException("duplicate outbox job: " + job.jobName());
      }
    }
    this.jobsByName = Map.copyOf(mapped);
  }

  public Map<String, OutboxJob> jobsByName() {
    return jobsByName;
  }

  public int tick() {
    if (!properties.enabled()) {
      return 0;
    }

    repository.resetExpiredProcessing(properties.targetApp(), OffsetDateTime.now());

    List<AutomationOutboxRecord> claimed =
        repository.claimNextPending(
            properties.targetApp(),
            properties.batchSize(),
            Duration.ofSeconds(properties.leaseSeconds()));

    int completed = 0;
    for (AutomationOutboxRecord row : claimed) {
      JobResult result = execute(row);
      if (result.isSuccess()) {
        repository.markDone(row.getId(), OffsetDateTime.now());
        completed++;
      } else {
        repository.recordFailure(row.getId(), result.reason());
      }
    }
    return completed;
  }

  private JobResult execute(AutomationOutboxRecord row) {
    OutboxJob job = jobsByName.get(row.getJobName());
    if (job == null) {
      return JobResult.failure("UNKNOWN_JOB:" + row.getJobName());
    }

    try {
      return job.handle(row);
    } catch (RuntimeException ex) {
      LOGGER.warn("outbox job failed: job={}, row={}", row.getJobName(), row.getId(), ex);
      return JobResult.failure(ex.getClass().getSimpleName() + ":" + ex.getMessage());
    }
  }
}
```

## 7. Phase 20.1：通用异步任务与 MinIO

### 7.1 AsyncJobType.java

```java
package io.aegisops.workrecord.extension.domain.model;

public enum AsyncJobType {
  IMPORT_VALIDATE("work-record-import-validate"),
  IMPORT_COMMIT("work-record-import-commit"),
  ASYNC_EXPORT("work-record-async-export"),
  MISSING_REMINDER("work-record-missing-reminder"),
  AI_RECORD_SUMMARY("work-record-ai-record-summary"),
  AI_MONTHLY_REPORT("work-record-ai-monthly-report"),
  SLA_SCAN("work-record-sla-scan");

  private final String outboxJobName;

  AsyncJobType(String outboxJobName) {
    this.outboxJobName = outboxJobName;
  }

  public String value() {
    return name().toLowerCase();
  }

  public String outboxJobName() {
    return outboxJobName;
  }

  public static AsyncJobType from(String value) {
    for (AsyncJobType type : values()) {
      if (type.value().equalsIgnoreCase(value)) {
        return type;
      }
    }
    throw new IllegalArgumentException("unknown async job type: " + value);
  }
}
```

### 7.2 AsyncJobStatus.java

```java
package io.aegisops.workrecord.extension.domain.model;

public enum AsyncJobStatus {
  QUEUED,
  RUNNING,
  VALIDATED,
  SUCCESS,
  PARTIAL_SUCCESS,
  FAILED,
  CANCELLED,
  EXPIRED;

  public String value() {
    return name().toLowerCase();
  }

  public boolean terminal() {
    return this == SUCCESS
        || this == PARTIAL_SUCCESS
        || this == FAILED
        || this == CANCELLED
        || this == EXPIRED;
  }
}
```

### 7.3 WorkRecordAsyncJob.java

```java
package io.aegisops.workrecord.extension.domain.model;

import java.time.OffsetDateTime;

public record WorkRecordAsyncJob(
    String id,
    String tenantId,
    AsyncJobType jobType,
    AsyncJobStatus status,
    String requestedBy,
    String requestJson,
    String resultJson,
    String sourceObjectKey,
    String resultObjectKey,
    String resultFileName,
    String resultContentType,
    int totalCount,
    int processedCount,
    int successCount,
    int failureCount,
    String errorMessage,
    String idempotencyKey,
    int rowVersion,
    OffsetDateTime createdAt,
    OffsetDateTime startedAt,
    OffsetDateTime finishedAt,
    OffsetDateTime expiresAt) {}
```

### 7.4 CreateAsyncJobCommand.java

```java
package io.aegisops.workrecord.extension.application.command;

import io.aegisops.workrecord.extension.domain.model.AsyncJobType;
import java.time.OffsetDateTime;

public record CreateAsyncJobCommand(
    String tenantId,
    AsyncJobType jobType,
    String requestedBy,
    String requestJson,
    String sourceObjectKey,
    String idempotencyKey,
    OffsetDateTime availableAt) {

  public CreateAsyncJobCommand {
    if (tenantId == null || tenantId.isBlank()) {
      throw new IllegalArgumentException("tenantId is required");
    }
    if (jobType == null) {
      throw new IllegalArgumentException("jobType is required");
    }
    if (requestedBy == null || requestedBy.isBlank()) {
      throw new IllegalArgumentException("requestedBy is required");
    }
    requestJson = requestJson == null || requestJson.isBlank() ? "{}" : requestJson;
  }
}
```

### 7.5 AsyncJobRepository.java

```java
package io.aegisops.workrecord.extension.application.port;

import io.aegisops.workrecord.extension.application.command.CreateAsyncJobCommand;
import io.aegisops.workrecord.extension.domain.model.AsyncJobStatus;
import io.aegisops.workrecord.extension.domain.model.WorkRecordAsyncJob;
import java.util.List;
import java.util.Optional;

public interface AsyncJobRepository {

  WorkRecordAsyncJob create(String id, CreateAsyncJobCommand command);

  Optional<WorkRecordAsyncJob> find(String tenantId, String jobId);

  List<WorkRecordAsyncJob> list(String tenantId, String requestedBy, int limit);

  boolean markRunning(String tenantId, String jobId);

  boolean updateProgress(String tenantId, String jobId, JobProgress progress);

  boolean complete(String tenantId, String jobId, JobCompletion completion);

  boolean fail(String tenantId, String jobId, String message);

  boolean transition(
      String tenantId,
      String jobId,
      AsyncJobStatus expected,
      AsyncJobStatus target);

  record JobProgress(
      int totalCount,
      int processedCount,
      int successCount,
      int failureCount) {}

  record JobCompletion(
      AsyncJobStatus status,
      String resultJson,
      String resultObjectKey,
      String resultFileName,
      String resultContentType,
      JobProgress progress) {}
}
```

### 7.6 JdbcAsyncJobRepository.java

```java
package io.aegisops.workrecord.extension.infrastructure.jdbc;

import io.aegisops.workrecord.extension.application.command.CreateAsyncJobCommand;
import io.aegisops.workrecord.extension.application.port.AsyncJobRepository;
import io.aegisops.workrecord.extension.domain.model.AsyncJobStatus;
import io.aegisops.workrecord.extension.domain.model.AsyncJobType;
import io.aegisops.workrecord.extension.domain.model.WorkRecordAsyncJob;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcAsyncJobRepository implements AsyncJobRepository {

  private static final String COLUMNS =
      """
      id, tenant_id, job_type, status, requested_by,
      request_json::text, result_json::text,
      source_object_key, result_object_key,
      result_file_name, result_content_type,
      total_count, processed_count, success_count, failure_count,
      error_message, idempotency_key, row_version,
      created_at, started_at, finished_at, expires_at
      """;

  private final NamedParameterJdbcTemplate jdbc;

  public JdbcAsyncJobRepository(NamedParameterJdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  public WorkRecordAsyncJob create(String id, CreateAsyncJobCommand command) {
    jdbc.update(
        """
        insert into work_record.wr_async_job(
            id, tenant_id, job_type, status, requested_by,
            request_json, source_object_key, idempotency_key, expires_at
        )
        values (
            :id, :tenantId, :jobType, 'queued', :requestedBy,
            cast(:requestJson as jsonb), :sourceObjectKey, :idempotencyKey,
            now() + interval '30 days'
        )
        """,
        Map.of(
            "id", id,
            "tenantId", command.tenantId(),
            "jobType", command.jobType().value(),
            "requestedBy", command.requestedBy(),
            "requestJson", command.requestJson(),
            "sourceObjectKey", nullable(command.sourceObjectKey()),
            "idempotencyKey", nullable(command.idempotencyKey())));

    return find(command.tenantId(), id).orElseThrow();
  }

  @Override
  public Optional<WorkRecordAsyncJob> find(String tenantId, String jobId) {
    List<WorkRecordAsyncJob> rows =
        jdbc.query(
            "select " + COLUMNS
                + " from work_record.wr_async_job"
                + " where tenant_id=:tenantId and id=:id",
            Map.of("tenantId", tenantId, "id", jobId),
            (rs, rowNum) -> map(rs));
    return rows.stream().findFirst();
  }

  @Override
  public List<WorkRecordAsyncJob> list(String tenantId, String requestedBy, int limit) {
    Map<String, Object> params = new java.util.HashMap<>();
    params.put("tenantId", tenantId);
    params.put("requestedBy", nullable(requestedBy));
    params.put("limit", Math.min(Math.max(limit, 1), 200));

    return jdbc.query(
        "select " + COLUMNS
            + " from work_record.wr_async_job"
            + " where tenant_id=:tenantId"
            + " and (:requestedBy is null or requested_by=:requestedBy)"
            + " order by created_at desc, id desc limit :limit",
        params,
        (rs, rowNum) -> map(rs));
  }

  @Override
  public boolean markRunning(String tenantId, String jobId) {
    return jdbc.update(
            """
            update work_record.wr_async_job
            set status='running', started_at=coalesce(started_at, now()),
                row_version=row_version+1
            where tenant_id=:tenantId and id=:id and status='queued'
            """,
            Map.of("tenantId", tenantId, "id", jobId))
        == 1;
  }

  @Override
  public boolean updateProgress(String tenantId, String jobId, JobProgress progress) {
    return jdbc.update(
            """
            update work_record.wr_async_job
            set total_count=:total,
                processed_count=:processed,
                success_count=:success,
                failure_count=:failure,
                row_version=row_version+1
            where tenant_id=:tenantId and id=:id and status='running'
            """,
            Map.of(
                "tenantId", tenantId,
                "id", jobId,
                "total", progress.totalCount(),
                "processed", progress.processedCount(),
                "success", progress.successCount(),
                "failure", progress.failureCount()))
        == 1;
  }

  @Override
  public boolean complete(String tenantId, String jobId, JobCompletion completion) {
    JobProgress progress = completion.progress();
    java.util.Map<String, Object> params = new java.util.HashMap<>();
    params.put("tenantId", tenantId);
    params.put("id", jobId);
    params.put("status", completion.status().value());
    params.put("resultJson", completion.resultJson());
    params.put("objectKey", completion.resultObjectKey());
    params.put("fileName", completion.resultFileName());
    params.put("contentType", completion.resultContentType());
    params.put("total", progress.totalCount());
    params.put("processed", progress.processedCount());
    params.put("success", progress.successCount());
    params.put("failure", progress.failureCount());

    return jdbc.update(
            """
            update work_record.wr_async_job
            set status=:status,
                result_json=cast(:resultJson as jsonb),
                result_object_key=:objectKey,
                result_file_name=:fileName,
                result_content_type=:contentType,
                total_count=:total,
                processed_count=:processed,
                success_count=:success,
                failure_count=:failure,
                error_message=null,
                finished_at=now(),
                row_version=row_version+1
            where tenant_id=:tenantId and id=:id and status='running'
            """,
            params)
        == 1;
  }

  @Override
  public boolean fail(String tenantId, String jobId, String message) {
    return jdbc.update(
            """
            update work_record.wr_async_job
            set status='failed', error_message=:message,
                finished_at=now(), row_version=row_version+1
            where tenant_id=:tenantId and id=:id
              and status in ('queued', 'running')
            """,
            Map.of(
                "tenantId", tenantId,
                "id", jobId,
                "message", truncate(message)))
        == 1;
  }

  @Override
  public boolean transition(
      String tenantId,
      String jobId,
      AsyncJobStatus expected,
      AsyncJobStatus target) {
    return jdbc.update(
            """
            update work_record.wr_async_job
            set status=:target, row_version=row_version+1
            where tenant_id=:tenantId and id=:id and status=:expected
            """,
            Map.of(
                "tenantId", tenantId,
                "id", jobId,
                "expected", expected.value(),
                "target", target.value()))
        == 1;
  }

  private WorkRecordAsyncJob map(ResultSet rs) throws SQLException {
    return new WorkRecordAsyncJob(
        rs.getString("id"),
        rs.getString("tenant_id"),
        AsyncJobType.from(rs.getString("job_type")),
        AsyncJobStatus.valueOf(rs.getString("status").toUpperCase()),
        rs.getString("requested_by"),
        rs.getString("request_json"),
        rs.getString("result_json"),
        rs.getString("source_object_key"),
        rs.getString("result_object_key"),
        rs.getString("result_file_name"),
        rs.getString("result_content_type"),
        rs.getInt("total_count"),
        rs.getInt("processed_count"),
        rs.getInt("success_count"),
        rs.getInt("failure_count"),
        rs.getString("error_message"),
        rs.getString("idempotency_key"),
        rs.getInt("row_version"),
        rs.getObject("created_at", OffsetDateTime.class),
        rs.getObject("started_at", OffsetDateTime.class),
        rs.getObject("finished_at", OffsetDateTime.class),
        rs.getObject("expires_at", OffsetDateTime.class));
  }

  private Object nullable(String value) {
    return value == null || value.isBlank() ? null : value;
  }

  private String truncate(String value) {
    if (value == null) {
      return "unknown job failure";
    }
    return value.length() <= 2000 ? value : value.substring(0, 2000);
  }
}
```

### 7.7 AsyncJobService.java

```java
package io.aegisops.workrecord.extension.application.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.aegisops.common.id.Ids;
import io.aegisops.common.outbox.OutboxMessage;
import io.aegisops.common.outbox.OutboxWriter;
import io.aegisops.security.UserPrincipal;
import io.aegisops.workrecord.extension.application.command.CreateAsyncJobCommand;
import io.aegisops.workrecord.extension.application.port.AsyncJobRepository;
import io.aegisops.workrecord.extension.domain.model.WorkRecordAsyncJob;
import java.time.OffsetDateTime;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AsyncJobService {

  private final AsyncJobRepository repository;
  private final OutboxWriter outboxWriter;
  private final ObjectMapper objectMapper;

  public AsyncJobService(
      AsyncJobRepository repository,
      OutboxWriter outboxWriter,
      ObjectMapper objectMapper) {
    this.repository = repository;
    this.outboxWriter = outboxWriter;
    this.objectMapper = objectMapper;
  }

  @Transactional
  public WorkRecordAsyncJob create(CreateAsyncJobCommand command) {
    String jobId = Ids.newId();
    WorkRecordAsyncJob job = repository.create(jobId, command);

    outboxWriter.enqueue(
        new OutboxMessage(
            command.tenantId(),
            "worker",
            command.jobType().outboxJobName(),
            Map.of(
                "tenantId", command.tenantId(),
                "jobId", jobId),
            "work-record-job:" + jobId + ":" + command.jobType().value(),
            5,
            command.availableAt()));

    return job;
  }

  public WorkRecordAsyncJob requireReadable(
      String tenantId,
      String jobId,
      UserPrincipal user) {
    WorkRecordAsyncJob job =
        repository
            .find(tenantId, jobId)
            .orElseThrow(() -> new IllegalArgumentException("async job not found"));

    boolean readAll = user != null && user.hasPermission("work-record:read:all");
    boolean owner = user != null && user.id().equals(job.requestedBy());
    if (!readAll && !owner) {
      throw new org.springframework.security.access.AccessDeniedException(
          "not allowed to read this async job");
    }
    return job;
  }

  public String writeJson(Object value) {
    try {
      return objectMapper.writeValueAsString(value);
    } catch (Exception ex) {
      throw new IllegalArgumentException("invalid async job request", ex);
    }
  }
}
```

## 8. 对象存储

### 8.1 ObjectStoragePort.java

```java
package io.aegisops.workrecord.extension.application.port;

import java.io.InputStream;
import java.time.Duration;

public interface ObjectStoragePort {

  StoredObject put(
      PutObjectCommand command,
      InputStream input);

  StoredObject putUnknownLength(
      String objectKey,
      String contentType,
      InputStream input,
      long maxBytes);

  InputStream get(String objectKey);

  String presignedGet(String objectKey, Duration duration);

  void delete(String objectKey);

  record PutObjectCommand(
      String objectKey,
      String contentType,
      long sizeBytes) {}

  record StoredObject(
      String objectKey,
      long sizeBytes,
      String sha256) {}
}
```

### 8.2 ObjectStorageProperties.java

```java
package io.aegisops.workrecord.extension.infrastructure.storage;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "aiops.object-storage")
public record ObjectStorageProperties(
    String endpoint,
    String accessKey,
    String secretKey,
    String bucket,
    long attachmentMaxBytes,
    int downloadUrlExpirySeconds) {

  public ObjectStorageProperties {
    endpoint = blank(endpoint, "http://localhost:9002");
    accessKey = blank(accessKey, "minioadmin");
    secretKey = blank(secretKey, "minioadmin");
    bucket = blank(bucket, "aegisops-work-record");
    attachmentMaxBytes =
        attachmentMaxBytes <= 0
            ? 20L * 1024L * 1024L
            : attachmentMaxBytes;
    downloadUrlExpirySeconds =
        downloadUrlExpirySeconds <= 0
            ? 300
            : downloadUrlExpirySeconds;
  }

  public static ObjectStorageProperties defaults() {
    return new ObjectStorageProperties(null, null, null, null, 0, 0, null);
  }

  private static String blank(String value, String fallback) {
    return value == null || value.isBlank() ? fallback : value;
  }
}
```

### 8.3 MinioObjectStorageAdapter.java

```java
package io.aegisops.workrecord.extension.infrastructure.storage;

import io.aegisops.workrecord.extension.application.port.ObjectStoragePort;
import io.minio.BucketExistsArgs;
import io.minio.GetObjectArgs;
import io.minio.GetPresignedObjectUrlArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import io.minio.http.Method;
import jakarta.annotation.PostConstruct;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.HexFormat;
import org.springframework.stereotype.Component;

@Component
public class MinioObjectStorageAdapter implements ObjectStoragePort {

  private static final long UNKNOWN_PART_SIZE = 10L * 1024L * 1024L;

  private final ObjectStorageProperties properties;
  private final MinioClient client;

  public MinioObjectStorageAdapter(ObjectStorageProperties properties) {
    this.properties = properties;
    this.client =
        MinioClient.builder()
            .endpoint(properties.endpoint())
            .credentials(properties.accessKey(), properties.secretKey())
            .build();
  }

  @PostConstruct
  void ensureBucket() {
    try {
      boolean exists =
          client.bucketExists(
              BucketExistsArgs.builder()
                  .bucket(properties.bucket())
                  .build());
      if (!exists) {
        client.makeBucket(
            MakeBucketArgs.builder()
                .bucket(properties.bucket())
                .build());
      }
    } catch (Exception ex) {
      throw new IllegalStateException(
          "failed to initialize object storage bucket",
          ex);
    }
  }

  @Override
  public StoredObject put(
      PutObjectCommand command,
      InputStream source) {
    if (command.sizeBytes() <= 0) {
      throw new IllegalArgumentException("object size must be positive");
    }
    return store(
        command.objectKey(),
        command.contentType(),
        source,
        command.sizeBytes(),
        command.sizeBytes());
  }

  @Override
  public StoredObject putUnknownLength(
      String objectKey,
      String contentType,
      InputStream source,
      long maxBytes) {
    if (maxBytes < 1) {
      throw new IllegalArgumentException("maxBytes must be positive");
    }
    return store(
        objectKey,
        contentType,
        source,
        -1,
        maxBytes);
  }

  private StoredObject store(
      String objectKey,
      String contentType,
      InputStream source,
      long declaredSize,
      long maxBytes) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      CountingBoundedInputStream bounded =
          new CountingBoundedInputStream(source, maxBytes);
      try (DigestInputStream input = new DigestInputStream(bounded, digest)) {
        PutObjectArgs.Builder builder =
            PutObjectArgs.builder()
                .bucket(properties.bucket())
                .object(objectKey)
                .contentType(contentType);
        if (declaredSize >= 0) {
          builder.stream(input, declaredSize, -1);
        } else {
          builder.stream(input, -1, UNKNOWN_PART_SIZE);
        }
        client.putObject(builder.build());
      }

      if (declaredSize >= 0 && bounded.count() != declaredSize) {
        deleteQuietly(objectKey);
        throw new IllegalArgumentException(
            "object size does not match declared size");
      }

      return new StoredObject(
          objectKey,
          bounded.count(),
          HexFormat.of().formatHex(digest.digest()));
    } catch (IllegalArgumentException ex) {
      throw ex;
    } catch (Exception ex) {
      deleteQuietly(objectKey);
      throw new IllegalStateException("failed to store object", ex);
    }
  }

  @Override
  public InputStream get(String objectKey) {
    try {
      return client.getObject(
          GetObjectArgs.builder()
              .bucket(properties.bucket())
              .object(objectKey)
              .build());
    } catch (Exception ex) {
      throw new IllegalStateException("failed to read object", ex);
    }
  }

  @Override
  public String presignedGet(String objectKey, Duration duration) {
    try {
      return client.getPresignedObjectUrl(
          GetPresignedObjectUrlArgs.builder()
              .method(Method.GET)
              .bucket(properties.bucket())
              .object(objectKey)
              .expiry(Math.toIntExact(duration.toSeconds()))
              .build());
    } catch (Exception ex) {
      throw new IllegalStateException("failed to create download url", ex);
    }
  }

  @Override
  public void delete(String objectKey) {
    try {
      client.removeObject(
          RemoveObjectArgs.builder()
              .bucket(properties.bucket())
              .object(objectKey)
              .build());
    } catch (Exception ex) {
      throw new IllegalStateException("failed to delete object", ex);
    }
  }

  private void deleteQuietly(String objectKey) {
    try {
      client.removeObject(
          RemoveObjectArgs.builder()
              .bucket(properties.bucket())
              .object(objectKey)
              .build());
    } catch (Exception ignored) {
      // Object may not have been created yet.
    }
  }

  private static final class CountingBoundedInputStream
      extends FilterInputStream {

    private final long maxBytes;
    private long count;

    private CountingBoundedInputStream(
        InputStream delegate,
        long maxBytes) {
      super(delegate);
      this.maxBytes = maxBytes;
    }

    @Override
    public int read() throws IOException {
      int value = super.read();
      if (value >= 0) {
        increment(1);
      }
      return value;
    }

    @Override
    public int read(byte[] buffer, int offset, int length)
        throws IOException {
      int read = super.read(buffer, offset, length);
      if (read > 0) {
        increment(read);
      }
      return read;
    }

    private void increment(long value) {
      count += value;
      if (count > maxBytes) {
        throw new IllegalArgumentException(
            "object exceeds maximum size of " + maxBytes + " bytes");
      }
    }

    private long count() {
      return count;
    }
  }
}
```

### 8.4 StorageConfiguration.java

```java
package io.aegisops.workrecord.extension.infrastructure.storage;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(ObjectStorageProperties.class)
public class StorageConfiguration {}
```

配置：

```yaml
aiops:
  object-storage:
    endpoint: ${AIOPS_MINIO_ENDPOINT:http://localhost:9002}
    access-key: ${AIOPS_MINIO_ACCESS_KEY:minioadmin}
    secret-key: ${AIOPS_MINIO_SECRET_KEY:minioadmin}
    bucket: ${AIOPS_MINIO_BUCKET:aegisops-work-record}
    attachment-max-bytes: ${AIOPS_ATTACHMENT_MAX_BYTES:20971520}
    download-url-expiry-seconds: 300
```

---

## 9. Phase 20.2：Excel 导入与异步导出

### 9.1 依赖调整

`modules/aiops-work-record-extension/pom.xml` 增加：

```xml
<dependency>
  <groupId>org.apache.poi</groupId>
  <artifactId>poi-ooxml</artifactId>
  <version>5.4.1</version>
</dependency>

<dependency>
  <groupId>org.springframework.boot</groupId>
  <artifactId>spring-boot-starter-validation</artifactId>
</dependency>
```

`apps/aiops-worker/pom.xml` 增加：

```xml
<dependency>
  <groupId>io.aegisops</groupId>
  <artifactId>aiops-work-record</artifactId>
  <version>${project.version}</version>
</dependency>

<dependency>
  <groupId>io.aegisops</groupId>
  <artifactId>aiops-work-record-extension</artifactId>
  <version>${project.version}</version>
</dependency>
```

Worker 配置必须显式声明运行角色，避免加载 Server Controller：

```yaml
aiops:
  runtime:
    app: worker
```

所有 Phase 20 Controller 统一增加：

```java
@ConditionalOnProperty(
    prefix = "aiops.runtime",
    name = "app",
    havingValue = "server",
    matchIfMissing = true)
```

### 9.2 ExcelImportRequest.java

```java
package io.aegisops.workrecord.extension.application.command;

import java.time.OffsetDateTime;

public record ExcelImportRequest(
    String templateId,
    String templateVersionId,
    String sourceObjectKey,
    String originalFileName,
    String defaultStatus,
    String defaultOwnerId,
    OffsetDateTime defaultRecordTime,
    boolean stopOnError) {}
```

### 9.3 AsyncExportRequest.java

```java
package io.aegisops.workrecord.extension.application.command;

import io.aegisops.workrecord.application.command.RecordDynamicFilter;
import java.time.OffsetDateTime;
import java.util.List;

public record AsyncExportRequest(
    String templateId,
    String templateVersionId,
    List<String> statuses,
    String keyword,
    OffsetDateTime recordTimeFrom,
    OffsetDateTime recordTimeTo,
    String creatorId,
    String ownerId,
    List<RecordDynamicFilter> dynamicFilters,
    List<String> columns,
    String sortBy,
    String sortDir,
    String quickView,
    Integer workdayCount) {

  public AsyncExportRequest {
    statuses = statuses == null ? List.of() : List.copyOf(statuses);
    dynamicFilters = dynamicFilters == null ? List.of() : List.copyOf(dynamicFilters);
    columns = columns == null ? List.of() : List.copyOf(columns);
  }
}
```

### 9.4 ImportedRecordRow.java

```java
package io.aegisops.workrecord.extension.application.model;

import java.time.OffsetDateTime;
import java.util.Map;

public record ImportedRecordRow(
    int rowNumber,
    String title,
    String status,
    String ownerId,
    OffsetDateTime recordTime,
    Map<String, Object> customData) {

  public ImportedRecordRow {
    customData = customData == null ? Map.of() : Map.copyOf(customData);
  }
}
```

### 9.5 ImportRowFailure.java

```java
package io.aegisops.workrecord.extension.application.model;

public record ImportRowFailure(
    int rowNumber,
    String fieldCode,
    String errorCode,
    String message) {}
```

### 9.6 ExcelImportParser.java

```java
package io.aegisops.workrecord.extension.application.service;

import io.aegisops.workrecord.domain.model.FieldType;
import io.aegisops.workrecord.domain.model.WorkRecordField;
import io.aegisops.workrecord.extension.application.model.ImportedRecordRow;
import java.io.InputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Component;

@Component
public class ExcelImportParser {

  private static final int MAX_ROWS = 20_000;
  private static final int MAX_COLUMNS = 300;

  private final DataFormatter formatter = new DataFormatter();

  public List<ImportedRecordRow> parse(
      InputStream input,
      List<WorkRecordField> fields,
      ImportDefaults defaults) {
    try (Workbook workbook = new XSSFWorkbook(input)) {
      if (workbook.getNumberOfSheets() == 0) {
        throw new IllegalArgumentException("Excel workbook has no sheet");
      }

      Sheet sheet = workbook.getSheetAt(0);
      Row header = sheet.getRow(sheet.getFirstRowNum());
      if (header == null) {
        throw new IllegalArgumentException("Excel header row is required");
      }

      Map<Integer, ColumnBinding> bindings = bindHeader(header, fields);
      int lastRow = sheet.getLastRowNum();
      if (lastRow - header.getRowNum() > MAX_ROWS) {
        throw new IllegalArgumentException("Excel rows exceed " + MAX_ROWS);
      }

      List<ImportedRecordRow> result = new ArrayList<>();
      for (int index = header.getRowNum() + 1; index <= lastRow; index++) {
        Row row = sheet.getRow(index);
        if (row == null || isBlank(row)) {
          continue;
        }
        result.add(parseRow(row, bindings, defaults));
      }
      return List.copyOf(result);
    } catch (IllegalArgumentException ex) {
      throw ex;
    } catch (Exception ex) {
      throw new IllegalArgumentException("failed to parse Excel import", ex);
    }
  }

  private Map<Integer, ColumnBinding> bindHeader(
      Row header,
      List<WorkRecordField> fields) {
    if (header.getLastCellNum() > MAX_COLUMNS) {
      throw new IllegalArgumentException("Excel columns exceed " + MAX_COLUMNS);
    }

    Map<String, WorkRecordField> fieldsByCode = new HashMap<>();
    for (WorkRecordField field : fields) {
      fieldsByCode.put(field.fieldCode(), field);
    }

    Map<Integer, ColumnBinding> result = new LinkedHashMap<>();
    for (int index = 0; index < header.getLastCellNum(); index++) {
      String name = text(header.getCell(index)).trim();
      if (name.isEmpty()) {
        continue;
      }

      ColumnBinding binding = builtinBinding(name);
      if (binding == null) {
        WorkRecordField field = fieldsByCode.get(name);
        if (field == null) {
          throw new IllegalArgumentException("unknown Excel column: " + name);
        }
        binding = ColumnBinding.custom(field);
      }
      result.put(index, binding);
    }

    boolean hasTitle = result.values().stream().anyMatch(ColumnBinding::title);
    if (!hasTitle) {
      throw new IllegalArgumentException("Excel column title is required");
    }
    return Map.copyOf(result);
  }

  private ImportedRecordRow parseRow(
      Row row,
      Map<Integer, ColumnBinding> bindings,
      ImportDefaults defaults) {
    String title = null;
    String status = defaults.defaultStatus();
    String ownerId = defaults.defaultOwnerId();
    OffsetDateTime recordTime = defaults.defaultRecordTime();
    Map<String, Object> custom = new LinkedHashMap<>();

    for (Map.Entry<Integer, ColumnBinding> entry : bindings.entrySet()) {
      Cell cell = row.getCell(entry.getKey());
      ColumnBinding binding = entry.getValue();
      Object value = value(cell, binding.field());

      if (binding.title()) {
        title = value == null ? null : String.valueOf(value).trim();
      } else if (binding.status()) {
        status = value == null ? status : String.valueOf(value).trim();
      } else if (binding.owner()) {
        ownerId = value == null ? ownerId : String.valueOf(value).trim();
      } else if (binding.recordTime()) {
        recordTime = value == null ? recordTime : toOffsetDateTime(value);
      } else if (binding.field() != null && value != null) {
        custom.put(binding.field().fieldCode(), value);
      }
    }

    if (title == null || title.isBlank()) {
      throw new ImportRowException(row.getRowNum() + 1, "title", "title is required");
    }
    if (recordTime == null) {
      throw new ImportRowException(
          row.getRowNum() + 1,
          "recordTime",
          "recordTime is required");
    }

    return new ImportedRecordRow(
        row.getRowNum() + 1,
        title,
        status,
        emptyToNull(ownerId),
        recordTime,
        custom);
  }

  private Object value(Cell cell, WorkRecordField field) {
    if (cell == null || cell.getCellType() == CellType.BLANK) {
      return null;
    }
    if (field == null) {
      return builtinValue(cell);
    }

    return switch (field.fieldType()) {
      case NUMBER -> number(cell);
      case BOOLEAN -> bool(cell);
      case DATE -> date(cell).toString();
      case DATETIME -> datetime(cell).toString();
      case MULTI_SELECT -> multi(text(cell));
      default -> text(cell).trim();
    };
  }

  private Object builtinValue(Cell cell) {
    if (DateUtil.isCellDateFormatted(cell)) {
      return cell.getLocalDateTimeCellValue().atOffset(ZoneOffset.UTC);
    }
    return text(cell).trim();
  }

  private BigDecimal number(Cell cell) {
    if (cell.getCellType() == CellType.NUMERIC) {
      return BigDecimal.valueOf(cell.getNumericCellValue()).stripTrailingZeros();
    }
    try {
      return new BigDecimal(text(cell).trim());
    } catch (NumberFormatException ex) {
      throw new IllegalArgumentException("invalid number: " + text(cell), ex);
    }
  }

  private boolean bool(Cell cell) {
    if (cell.getCellType() == CellType.BOOLEAN) {
      return cell.getBooleanCellValue();
    }
    String value = text(cell).trim().toLowerCase();
    return switch (value) {
      case "true", "1", "yes", "y", "是" -> true;
      case "false", "0", "no", "n", "否" -> false;
      default -> throw new IllegalArgumentException("invalid boolean: " + value);
    };
  }

  private LocalDate date(Cell cell) {
    if (DateUtil.isCellDateFormatted(cell)) {
      return cell.getLocalDateTimeCellValue().toLocalDate();
    }
    return LocalDate.parse(text(cell).trim());
  }

  private OffsetDateTime datetime(Cell cell) {
    if (DateUtil.isCellDateFormatted(cell)) {
      return cell.getLocalDateTimeCellValue().atOffset(ZoneOffset.UTC);
    }
    return OffsetDateTime.parse(text(cell).trim());
  }

  private OffsetDateTime toOffsetDateTime(Object value) {
    if (value instanceof OffsetDateTime dateTime) {
      return dateTime;
    }
    return OffsetDateTime.parse(String.valueOf(value));
  }

  private List<String> multi(String raw) {
    if (raw == null || raw.isBlank()) {
      return List.of();
    }
    return java.util.Arrays.stream(raw.split("[,;，；]"))
        .map(String::trim)
        .filter(value -> !value.isEmpty())
        .distinct()
        .toList();
  }

  private boolean isBlank(Row row) {
    for (int index = row.getFirstCellNum(); index < row.getLastCellNum(); index++) {
      Cell cell = row.getCell(index);
      if (cell != null && !text(cell).isBlank()) {
        return false;
      }
    }
    return true;
  }

  private String text(Cell cell) {
    return cell == null ? "" : formatter.formatCellValue(cell);
  }

  private ColumnBinding builtinBinding(String name) {
    return switch (name) {
      case "title" -> ColumnBinding.titleBinding();
      case "status" -> ColumnBinding.statusBinding();
      case "ownerId" -> ColumnBinding.ownerBinding();
      case "recordTime" -> ColumnBinding.recordTimeBinding();
      default -> null;
    };
  }

  private String emptyToNull(String value) {
    return value == null || value.isBlank() ? null : value;
  }

  public record ImportDefaults(
      String defaultStatus,
      String defaultOwnerId,
      OffsetDateTime defaultRecordTime) {}

  private record ColumnBinding(
      boolean title,
      boolean status,
      boolean owner,
      boolean recordTime,
      WorkRecordField field) {

    static ColumnBinding titleBinding() {
      return new ColumnBinding(true, false, false, false, null);
    }

    static ColumnBinding statusBinding() {
      return new ColumnBinding(false, true, false, false, null);
    }

    static ColumnBinding ownerBinding() {
      return new ColumnBinding(false, false, true, false, null);
    }

    static ColumnBinding recordTimeBinding() {
      return new ColumnBinding(false, false, false, true, null);
    }

    static ColumnBinding custom(WorkRecordField field) {
      return new ColumnBinding(false, false, false, false, field);
    }
  }

  public static final class ImportRowException extends IllegalArgumentException {
    private final int rowNumber;
    private final String fieldCode;

    public ImportRowException(int rowNumber, String fieldCode, String message) {
      super(message);
      this.rowNumber = rowNumber;
      this.fieldCode = fieldCode;
    }

    public int rowNumber() {
      return rowNumber;
    }

    public String fieldCode() {
      return fieldCode;
    }
  }
}
```

### 9.7 WorkRecordBatchWritePort.java

该 Port 放在 `aiops-work-record`，供可信 Worker 使用；不能让 Worker 直接绕过领域校验写表。

```java
package io.aegisops.workrecord.application.port;

import io.aegisops.workrecord.application.command.CreateRecordCommand;
import io.aegisops.workrecord.domain.model.WorkRecord;

public interface WorkRecordBatchWritePort {

  WorkRecord createValidated(
      String tenantId,
      CreateRecordCommand command,
      String actorId);
}
```

### 9.8 WorkRecordBatchWriteService.java

```java
package io.aegisops.workrecord.application.service;

import io.aegisops.workrecord.application.command.CreateRecordCommand;
import io.aegisops.workrecord.application.port.WorkRecordBatchWritePort;
import io.aegisops.workrecord.domain.model.WorkRecord;
import org.springframework.stereotype.Service;

@Service
public class WorkRecordBatchWriteService implements WorkRecordBatchWritePort {

  private final WorkRecordTrustedMutationService trustedMutationService;

  public WorkRecordBatchWriteService(
      WorkRecordTrustedMutationService trustedMutationService) {
    this.trustedMutationService = trustedMutationService;
  }

  @Override
  public WorkRecord createValidated(
      String tenantId,
      CreateRecordCommand command,
      String actorId) {
    return trustedMutationService.create(tenantId, command, actorId);
  }
}
```

`WorkRecordTrustedMutationService` 必须复用 `WorkRecordService.create` 中的版本解析、字段校验、字典校验、用户校验与审计逻辑，只移除 HTTP 权限检查。推荐将当前 `WorkRecordService.create` 重构为：

```java
public WorkRecord create(
    String tenantId,
    CreateRecordCommand command,
    UserPrincipal user) {
  permissionService.requireCreate(user);
  return trustedMutationService.create(
      tenantId,
      command,
      user.id());
}
```

### 9.9 WorkRecordImportProcessor.java

```java
package io.aegisops.workrecord.extension.application.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.aegisops.workrecord.application.command.CreateRecordCommand;
import io.aegisops.workrecord.application.port.WorkRecordBatchWritePort;
import io.aegisops.workrecord.application.port.WorkRecordFieldIndexRepository;
import io.aegisops.workrecord.domain.model.WorkRecordField;
import io.aegisops.workrecord.extension.application.command.ExcelImportRequest;
import io.aegisops.workrecord.extension.application.model.ImportRowFailure;
import io.aegisops.workrecord.extension.application.model.ImportedRecordRow;
import io.aegisops.workrecord.extension.application.port.AsyncJobRepository;
import io.aegisops.workrecord.extension.application.port.ObjectStoragePort;
import io.aegisops.workrecord.extension.domain.AsyncJobStatus;
import io.aegisops.workrecord.extension.domain.JobCompletion;
import io.aegisops.workrecord.extension.domain.JobProgress;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class WorkRecordImportProcessor {

  private final AsyncJobRepository jobs;
  private final ObjectStoragePort storage;
  private final WorkRecordFieldIndexRepository fields;
  private final WorkRecordBatchWritePort batchWriter;
  private final ExcelImportParser parser;
  private final ObjectMapper objectMapper;

  public WorkRecordImportProcessor(
      AsyncJobRepository jobs,
      ObjectStoragePort storage,
      WorkRecordFieldIndexRepository fields,
      WorkRecordBatchWritePort batchWriter,
      ExcelImportParser parser,
      ObjectMapper objectMapper) {
    this.jobs = jobs;
    this.storage = storage;
    this.fields = fields;
    this.batchWriter = batchWriter;
    this.parser = parser;
    this.objectMapper = objectMapper;
  }

  public void process(String tenantId, String jobId) {
    var job = jobs.find(tenantId, jobId)
        .orElseThrow(() -> new IllegalArgumentException("import job not found"));
    if (!jobs.markRunning(tenantId, jobId)) {
      return;
    }

    try {
      ExcelImportRequest request =
          objectMapper.readValue(job.requestJson(), ExcelImportRequest.class);
      List<WorkRecordField> versionFields =
          fields.listByVersion(tenantId, request.templateVersionId());

      List<ImportedRecordRow> rows;
      try (InputStream input = storage.get(request.sourceObjectKey())) {
        rows = parser.parse(
            input,
            versionFields,
            new ExcelImportParser.ImportDefaults(
                request.defaultStatus(),
                request.defaultOwnerId(),
                request.defaultRecordTime()));
      }

      List<ImportRowFailure> failures = new ArrayList<>();
      int success = 0;
      for (ImportedRecordRow row : rows) {
        try {
          batchWriter.createValidated(
              tenantId,
              new CreateRecordCommand(
                  request.templateId(),
                  request.templateVersionId(),
                  row.title(),
                  row.status(),
                  row.ownerId(),
                  row.recordTime(),
                  "{}",
                  objectMapper.writeValueAsString(row.customData())),
              job.requestedBy());
          success++;
        } catch (RuntimeException ex) {
          failures.add(
              new ImportRowFailure(
                  row.rowNumber(),
                  null,
                  ex.getClass().getSimpleName(),
                  safeMessage(ex)));
          if (request.stopOnError()) {
            break;
          }
        }

        jobs.updateProgress(
            tenantId,
            jobId,
            new JobProgress(rows.size(), success + failures.size(), success, failures.size()));
      }

      Map<String, Object> result = new LinkedHashMap<>();
      result.put("failures", failures);
      result.put("successCount", success);
      result.put("failureCount", failures.size());

      JobProgress progress =
          new JobProgress(rows.size(), success + failures.size(), success, failures.size());
      jobs.complete(
          tenantId,
          jobId,
          new JobCompletion(
              failures.isEmpty()
                  ? AsyncJobStatus.SUCCEEDED
                  : AsyncJobStatus.PARTIAL_SUCCESS,
              objectMapper.writeValueAsString(result),
              null,
              null,
              null,
              progress));
    } catch (Exception ex) {
      jobs.fail(tenantId, jobId, safeMessage(ex));
      throw new IllegalStateException("work-record import failed", ex);
    }
  }

  private String safeMessage(Throwable ex) {
    String value = ex.getMessage();
    if (value == null || value.isBlank()) {
      return ex.getClass().getSimpleName();
    }
    return value.length() <= 500 ? value : value.substring(0, 500);
  }
}
```

### 9.10 WorkRecordImportJob.java

```java
package io.aegisops.worker.job;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.aegisops.persistence.jooq.public_.tables.records.AutomationOutboxRecord;
import io.aegisops.workrecord.extension.application.service.WorkRecordImportProcessor;
import org.springframework.stereotype.Component;

@Component
public class WorkRecordImportJob implements OutboxJob {

  public static final String JOB_NAME = "work-record-import";

  private final WorkRecordImportProcessor processor;
  private final ObjectMapper objectMapper;

  public WorkRecordImportJob(
      WorkRecordImportProcessor processor,
      ObjectMapper objectMapper) {
    this.processor = processor;
    this.objectMapper = objectMapper;
  }

  @Override
  public String jobName() {
    return JOB_NAME;
  }

  @Override
  public JobResult handle(AutomationOutboxRecord row) {
    try {
      JsonNode payload = objectMapper.readTree(row.getPayload().data());
      processor.process(
          required(payload, "tenantId"),
          required(payload, "jobId"));
      return JobResult.success();
    } catch (RuntimeException ex) {
      return JobResult.failure(ex.getClass().getSimpleName());
    } catch (Exception ex) {
      return JobResult.failure("INVALID_PAYLOAD");
    }
  }

  private String required(JsonNode payload, String name) {
    String value = payload.path(name).asText();
    if (value.isBlank()) {
      throw new IllegalArgumentException(name + " is required");
    }
    return value;
  }
}
```

### 9.11 WorkRecordImportService.java

```java
package io.aegisops.workrecord.extension.application.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.aegisops.security.UserPrincipal;
import io.aegisops.workrecord.extension.application.command.CreateAsyncJobCommand;
import io.aegisops.workrecord.extension.application.command.ExcelImportRequest;
import io.aegisops.workrecord.extension.application.port.ObjectStoragePort;
import io.aegisops.workrecord.extension.domain.AsyncJobType;
import java.io.InputStream;
import java.time.OffsetDateTime;
import org.springframework.stereotype.Service;

@Service
public class WorkRecordImportService {

  private final ObjectStoragePort storage;
  private final AsyncJobService asyncJobs;
  private final ObjectMapper objectMapper;

  public WorkRecordImportService(
      ObjectStoragePort storage,
      AsyncJobService asyncJobs,
      ObjectMapper objectMapper) {
    this.storage = storage;
    this.asyncJobs = asyncJobs;
    this.objectMapper = objectMapper;
  }

  public String submit(
      String tenantId,
      ImportSubmission submission,
      UserPrincipal user) {
    requirePermission(user, "work-record:import");
    validateFile(submission);

    String jobId = io.aegisops.common.id.Ids.newId();
    String objectKey =
        tenantId + "/imports/" + jobId + "/source.xlsx";

    storage.put(
        new ObjectStoragePort.PutObjectCommand(
            objectKey,
            submission.contentType(),
            submission.sizeBytes()),
        submission.input());

    ExcelImportRequest request =
        new ExcelImportRequest(
            submission.templateId(),
            submission.templateVersionId(),
            objectKey,
            submission.originalFileName(),
            submission.defaultStatus(),
            submission.defaultOwnerId(),
            submission.defaultRecordTime(),
            submission.stopOnError());

    try {
      return asyncJobs.create(
          tenantId,
          new CreateAsyncJobCommand(
              jobId,
              AsyncJobType.RECORD_IMPORT,
              objectMapper.writeValueAsString(request),
              objectKey,
              "import:" + tenantId + ":" + jobId,
              OffsetDateTime.now().plusDays(7)),
          user,
          "work-record-import");
    } catch (Exception ex) {
      storage.delete(objectKey);
      throw new IllegalStateException("failed to create import job", ex);
    }
  }

  private void validateFile(ImportSubmission submission) {
    if (submission.sizeBytes() < 1 || submission.sizeBytes() > 20L * 1024L * 1024L) {
      throw new IllegalArgumentException("Excel file must be between 1 byte and 20 MiB");
    }
    String fileName = submission.originalFileName();
    if (fileName == null || !fileName.toLowerCase().endsWith(".xlsx")) {
      throw new IllegalArgumentException("only .xlsx is supported");
    }
  }

  private void requirePermission(UserPrincipal user, String permission) {
    if (user == null || !user.hasPermission(permission)) {
      throw new org.springframework.security.access.AccessDeniedException(
          "not allowed to import work records");
    }
  }

  public record ImportSubmission(
      String templateId,
      String templateVersionId,
      String originalFileName,
      String contentType,
      long sizeBytes,
      InputStream input,
      String defaultStatus,
      String defaultOwnerId,
      OffsetDateTime defaultRecordTime,
      boolean stopOnError) {}
}
```

### 9.12 WorkRecordImportController.java

```java
package io.aegisops.workrecord.extension.api;

import io.aegisops.common.api.ApiResponse;
import io.aegisops.common.tenant.TenantContext;
import io.aegisops.security.UserPrincipal;
import io.aegisops.workrecord.extension.application.service.WorkRecordImportService;
import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.Map;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/work-record/imports")
@ConditionalOnProperty(
    prefix = "aiops.runtime",
    name = "app",
    havingValue = "server",
    matchIfMissing = true)
public class WorkRecordImportController {

  private final WorkRecordImportService service;

  public WorkRecordImportController(WorkRecordImportService service) {
    this.service = service;
  }

  @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  @PreAuthorize("hasAuthority('work-record:import')")
  public ApiResponse<Map<String, String>> submit(
      @RequestPart("file") MultipartFile file,
      @RequestParam String templateId,
      @RequestParam String templateVersionId,
      @RequestParam(defaultValue = "draft") String defaultStatus,
      @RequestParam(required = false) String defaultOwnerId,
      @RequestParam(required = false) OffsetDateTime defaultRecordTime,
      @RequestParam(defaultValue = "false") boolean stopOnError,
      @AuthenticationPrincipal UserPrincipal user)
      throws IOException {
    String jobId =
        service.submit(
            TenantContext.requireTenantId(),
            new WorkRecordImportService.ImportSubmission(
                templateId,
                templateVersionId,
                file.getOriginalFilename(),
                file.getContentType(),
                file.getSize(),
                file.getInputStream(),
                defaultStatus,
                defaultOwnerId,
                defaultRecordTime,
                stopOnError),
            user);
    return ApiResponse.ok(Map.of("jobId", jobId));
  }
}
```

### 9.13 WorkRecordAsyncExportProcessor.java

异步导出不能直接调用现有返回 `byte[]` 的同步导出方法，否则仍会把完整文件留在内存中。将现有列解析、字典 label、历史版本字段映射抽到 `WorkRecordExportDatasetService`，再由同步和异步两个 Writer 复用。

```java
package io.aegisops.workrecord.extension.application.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.aegisops.security.UserPrincipal;
import io.aegisops.workrecord.application.command.RecordQuery;
import io.aegisops.workrecord.application.service.WorkRecordExportDatasetService;
import io.aegisops.workrecord.extension.application.command.AsyncExportRequest;
import io.aegisops.workrecord.extension.application.port.AsyncJobRepository;
import io.aegisops.workrecord.extension.application.port.ObjectStoragePort;
import io.aegisops.workrecord.extension.domain.AsyncJobStatus;
import io.aegisops.workrecord.extension.domain.JobCompletion;
import io.aegisops.workrecord.extension.domain.JobProgress;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.springframework.stereotype.Service;

@Service
public class WorkRecordAsyncExportProcessor {

  private final AsyncJobRepository jobs;
  private final WorkRecordExportDatasetService datasets;
  private final ObjectStoragePort storage;
  private final ObjectMapper objectMapper;
  private final ExecutorService writerExecutor = Executors.newVirtualThreadPerTaskExecutor();

  public WorkRecordAsyncExportProcessor(
      AsyncJobRepository jobs,
      WorkRecordExportDatasetService datasets,
      ObjectStoragePort storage,
      ObjectMapper objectMapper) {
    this.jobs = jobs;
    this.datasets = datasets;
    this.storage = storage;
    this.objectMapper = objectMapper;
  }

  public void process(
      String tenantId,
      String jobId,
      UserPrincipal principal) {
    var job = jobs.find(tenantId, jobId)
        .orElseThrow(() -> new IllegalArgumentException("export job not found"));
    if (!jobs.markRunning(tenantId, jobId)) {
      return;
    }

    String objectKey = tenantId + "/exports/" + jobId + "/records.csv";
    String fileName = "work-records-" + jobId + ".csv";

    try {
      AsyncExportRequest request =
          objectMapper.readValue(job.requestJson(), AsyncExportRequest.class);
      RecordQuery query = toQuery(request, principal.id());
      var dataset = datasets.prepare(
          tenantId,
          query,
          request.columns(),
          principal,
          100_000);

      try (PipedInputStream input = new PipedInputStream(128 * 1024);
          PipedOutputStream output = new PipedOutputStream(input)) {
        var writerFuture = writerExecutor.submit(() -> {
          try (var writer =
              new java.io.BufferedWriter(
                  new java.io.OutputStreamWriter(output, StandardCharsets.UTF_8))) {
            writer.write('\ufeff');
            datasets.writeCsv(dataset, writer, progress ->
                jobs.updateProgress(tenantId, jobId, progress));
          } catch (Exception ex) {
            throw new IllegalStateException(ex);
          }
        });

        storage.putUnknownLength(
            objectKey,
            "text/csv;charset=UTF-8",
            input,
            100L * 1024L * 1024L);
        writerFuture.get();
      }

      JobProgress progress =
          new JobProgress(
              dataset.totalCount(),
              dataset.totalCount(),
              dataset.totalCount(),
              0);
      jobs.complete(
          tenantId,
          jobId,
          new JobCompletion(
              AsyncJobStatus.SUCCEEDED,
              objectMapper.writeValueAsString(Map.of("rowCount", dataset.totalCount())),
              objectKey,
              fileName,
              "text/csv;charset=UTF-8",
              progress));
    } catch (Exception ex) {
      storage.delete(objectKey);
      jobs.fail(tenantId, jobId, safe(ex));
      throw new IllegalStateException("async export failed", ex);
    }
  }

  private RecordQuery toQuery(AsyncExportRequest request, String userId) {
    return new RecordQuery(
        1,
        500,
        request.templateId(),
        request.templateVersionId(),
        request.statuses(),
        request.keyword(),
        request.recordTimeFrom(),
        request.recordTimeTo(),
        request.creatorId(),
        request.ownerId(),
        false,
        userId,
        request.dynamicFilters(),
        request.sortBy(),
        request.sortDir(),
        request.quickView(),
        request.workdayCount());
  }

  private String safe(Throwable ex) {
    String value = ex.getMessage();
    return value == null ? ex.getClass().getSimpleName() : value.substring(0, Math.min(500, value.length()));
  }
}
```

`ObjectStoragePort` 增加：

```java
StoredObject putUnknownLength(
    String objectKey,
    String contentType,
    InputStream input,
    long maxBytes);
```

MinIO 实现必须在读取流时累计字节并在超过 `maxBytes` 后中断，不能把未知长度流无限写入。

### 9.14 WorkRecordAsyncExportJob.java

```java
package io.aegisops.worker.job;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.aegisops.persistence.jooq.public_.tables.records.AutomationOutboxRecord;
import io.aegisops.security.UserPrincipalFactory;
import io.aegisops.user.UserService;
import io.aegisops.workrecord.extension.application.service.WorkRecordAsyncExportProcessor;
import org.springframework.stereotype.Component;

@Component
public class WorkRecordAsyncExportJob implements OutboxJob {

  public static final String JOB_NAME = "work-record-export";

  private final WorkRecordAsyncExportProcessor processor;
  private final UserService users;
  private final UserPrincipalFactory principals;
  private final ObjectMapper objectMapper;

  public WorkRecordAsyncExportJob(
      WorkRecordAsyncExportProcessor processor,
      UserService users,
      UserPrincipalFactory principals,
      ObjectMapper objectMapper) {
    this.processor = processor;
    this.users = users;
    this.principals = principals;
    this.objectMapper = objectMapper;
  }

  @Override
  public String jobName() {
    return JOB_NAME;
  }

  @Override
  public JobResult handle(AutomationOutboxRecord row) {
    try {
      JsonNode payload = objectMapper.readTree(row.getPayload().data());
      String tenantId = required(payload, "tenantId");
      String jobId = required(payload, "jobId");
      String requestedBy = required(payload, "requestedBy");
      var user = users.getById(requestedBy);
      if (!tenantId.equals(user.tenantId())) {
        return JobResult.failure("TENANT_MISMATCH");
      }
      var principal = principals.create(user);
      if (!principal.hasPermission("work-record:export")) {
        return JobResult.failure("EXPORT_PERMISSION_REVOKED");
      }
      processor.process(tenantId, jobId, principal);
      return JobResult.success();
    } catch (RuntimeException ex) {
      return JobResult.failure(ex.getClass().getSimpleName());
    } catch (Exception ex) {
      return JobResult.failure("INVALID_PAYLOAD");
    }
  }

  private String required(JsonNode payload, String name) {
    String value = payload.path(name).asText();
    if (value.isBlank()) {
      throw new IllegalArgumentException(name + " is required");
    }
    return value;
  }
}
```

异步执行时重新读取用户授权，确保用户提交任务后权限被撤销时，Worker 不会继续导出。

### 9.15 AsyncJobController.java

```java
package io.aegisops.workrecord.extension.api;

import io.aegisops.common.api.ApiResponse;
import io.aegisops.common.tenant.TenantContext;
import io.aegisops.security.UserPrincipal;
import io.aegisops.workrecord.extension.application.command.AsyncExportRequest;
import io.aegisops.workrecord.extension.application.command.CreateAsyncJobCommand;
import io.aegisops.workrecord.extension.application.service.AsyncJobService;
import io.aegisops.workrecord.extension.application.port.ObjectStoragePort;
import io.aegisops.workrecord.extension.domain.AsyncJobType;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/work-record/jobs")
@ConditionalOnProperty(
    prefix = "aiops.runtime",
    name = "app",
    havingValue = "server",
    matchIfMissing = true)
public class AsyncJobController {

  private final AsyncJobService jobs;
  private final ObjectStoragePort storage;

  public AsyncJobController(
      AsyncJobService jobs,
      ObjectStoragePort storage) {
    this.jobs = jobs;
    this.storage = storage;
  }

  @PostMapping("/exports")
  @PreAuthorize("hasAuthority('work-record:export:async')")
  public ApiResponse<Map<String, String>> export(
      @RequestBody AsyncExportRequest request,
      @AuthenticationPrincipal UserPrincipal user) {
    String tenantId = TenantContext.requireTenantId();
    String jobId = io.aegisops.common.id.Ids.newId();
    String created =
        jobs.createJson(
            tenantId,
            new CreateAsyncJobCommand(
                jobId,
                AsyncJobType.RECORD_EXPORT,
                request,
                null,
                "export:" + tenantId + ":" + user.id() + ":" + jobId,
                OffsetDateTime.now().plusDays(7)),
            user,
            "work-record-export");
    return ApiResponse.ok(Map.of("jobId", created));
  }

  @GetMapping
  public ApiResponse<List<?>> list(
      @AuthenticationPrincipal UserPrincipal user) {
    String tenantId = TenantContext.requireTenantId();
    String requestedBy = user.hasPermission("work-record:read:all") ? null : user.id();
    return ApiResponse.ok(jobs.list(tenantId, requestedBy, 100));
  }

  @GetMapping("/{jobId}")
  public ApiResponse<?> get(
      @PathVariable String jobId,
      @AuthenticationPrincipal UserPrincipal user) {
    return ApiResponse.ok(
        jobs.getVisible(
            TenantContext.requireTenantId(),
            jobId,
            user));
  }

  @GetMapping("/{jobId}/download-url")
  public ApiResponse<Map<String, String>> download(
      @PathVariable String jobId,
      @AuthenticationPrincipal UserPrincipal user) {
    var job = jobs.getVisible(TenantContext.requireTenantId(), jobId, user);
    if (!job.status().downloadable() || job.resultObjectKey() == null) {
      throw new IllegalStateException("job result is not downloadable");
    }
    return ApiResponse.ok(
        Map.of(
            "url",
            storage.presignedGet(job.resultObjectKey(), Duration.ofMinutes(5)),
            "fileName",
            job.resultFileName()));
  }
}
```

### 9.16 ExcelImportParserTest.java

```java
package io.aegisops.workrecord.extension.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.aegisops.workrecord.domain.model.FieldType;
import io.aegisops.workrecord.domain.model.OptionSource;
import io.aegisops.workrecord.domain.model.WorkRecordField;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.time.OffsetDateTime;
import java.util.List;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

class ExcelImportParserTest {

  private final ExcelImportParser parser = new ExcelImportParser();

  @Test
  void parsesBuiltinAndDynamicColumns() throws Exception {
    byte[] content;
    try (var workbook = new XSSFWorkbook();
        var output = new ByteArrayOutputStream()) {
      var sheet = workbook.createSheet("records");
      var header = sheet.createRow(0);
      header.createCell(0).setCellValue("title");
      header.createCell(1).setCellValue("recordTime");
      header.createCell(2).setCellValue("hours");
      var row = sheet.createRow(1);
      row.createCell(0).setCellValue("完成发布");
      row.createCell(1).setCellValue("2026-07-11T10:00:00+08:00");
      row.createCell(2).setCellValue(2.5);
      workbook.write(output);
      content = output.toByteArray();
    }

    var rows =
        parser.parse(
            new ByteArrayInputStream(content),
            List.of(field("hours", FieldType.NUMBER)),
            new ExcelImportParser.ImportDefaults(
                "draft",
                null,
                OffsetDateTime.parse("2026-07-11T00:00:00+08:00")));

    assertThat(rows).hasSize(1);
    assertThat(rows.getFirst().title()).isEqualTo("完成发布");
    assertThat(rows.getFirst().customData().get("hours").toString())
        .isEqualTo("2.5");
  }

  @Test
  void rejectsUnknownColumn() throws Exception {
    byte[] content;
    try (var workbook = new XSSFWorkbook();
        var output = new ByteArrayOutputStream()) {
      var sheet = workbook.createSheet("records");
      var header = sheet.createRow(0);
      header.createCell(0).setCellValue("title");
      header.createCell(1).setCellValue("unknown_field");
      workbook.write(output);
      content = output.toByteArray();
    }

    assertThatThrownBy(
            () ->
                parser.parse(
                    new ByteArrayInputStream(content),
                    List.of(),
                    new ExcelImportParser.ImportDefaults(
                        "draft",
                        null,
                        OffsetDateTime.now())))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("unknown Excel column");
  }

  private WorkRecordField field(String code, FieldType type) {
    OffsetDateTime now = OffsetDateTime.now();
    return new WorkRecordField(
        "f-" + code,
        "t1",
        "tpl1",
        "v1",
        code,
        code,
        type,
        false,
        null,
        OptionSource.STATIC,
        null,
        "[]",
        ".properties." + code,
        true,
        true,
        true,
        true,
        0,
        true,
        now,
        now);
  }
}
```

### 9.17 WorkRecordAsyncExportJobTest.java

```java
package io.aegisops.worker.job;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.aegisops.persistence.jooq.public_.tables.records.AutomationOutboxRecord;
import io.aegisops.security.UserPrincipalFactory;
import io.aegisops.user.UserAccount;
import io.aegisops.user.UserService;
import io.aegisops.workrecord.extension.application.service.WorkRecordAsyncExportProcessor;
import org.jooq.JSONB;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class WorkRecordAsyncExportJobTest {

  @Test
  void reloadsAuthorizationBeforeExport() {
    var processor = Mockito.mock(WorkRecordAsyncExportProcessor.class);
    var users = Mockito.mock(UserService.class);
    var principals = Mockito.mock(UserPrincipalFactory.class);
    var principal = TestPrincipals.exporter();
    UserAccount account = TestUsers.account("u1", "t1");
    when(users.getById("u1")).thenReturn(account);
    when(principals.create(account)).thenReturn(principal);

    var job =
        new WorkRecordAsyncExportJob(
            processor,
            users,
            principals,
            new ObjectMapper());
    AutomationOutboxRecord row = new AutomationOutboxRecord();
    row.setPayload(
        JSONB.jsonb("{\"tenantId\":\"t1\",\"jobId\":\"j1\",\"requestedBy\":\"u1\"}"));

    assertThat(job.handle(row).isSuccess()).isTrue();
    verify(processor).process("t1", "j1", principal);
  }
}
```

---

## 10. Phase 20.3：评论时间线、附件与关联对象

### 10.1 RecordAccessPort.java

扩展模块的所有记录子资源都必须先复用核心记录权限，不得只校验 `recordId` 是否存在。

```java
package io.aegisops.workrecord.extension.application.port;

import io.aegisops.security.UserPrincipal;
import io.aegisops.workrecord.domain.model.WorkRecord;

public interface RecordAccessPort {

  WorkRecord requireReadable(
      String tenantId,
      String recordId,
      UserPrincipal principal);

  WorkRecord requireWritable(
      String tenantId,
      String recordId,
      UserPrincipal principal);
}
```

### 10.2 CoreRecordAccessAdapter.java

```java
package io.aegisops.workrecord.extension.infrastructure.adapter;

import io.aegisops.security.UserPrincipal;
import io.aegisops.workrecord.application.service.WorkRecordPermissionService;
import io.aegisops.workrecord.application.service.WorkRecordQueryService;
import io.aegisops.workrecord.domain.model.WorkRecord;
import io.aegisops.workrecord.extension.application.port.RecordAccessPort;
import org.springframework.stereotype.Component;

@Component
public class CoreRecordAccessAdapter implements RecordAccessPort {

  private final WorkRecordQueryService queries;
  private final WorkRecordPermissionService permissions;

  public CoreRecordAccessAdapter(
      WorkRecordQueryService queries,
      WorkRecordPermissionService permissions) {
    this.queries = queries;
    this.permissions = permissions;
  }

  @Override
  public WorkRecord requireReadable(
      String tenantId,
      String recordId,
      UserPrincipal principal) {
    return queries.get(tenantId, recordId, principal);
  }

  @Override
  public WorkRecord requireWritable(
      String tenantId,
      String recordId,
      UserPrincipal principal) {
    WorkRecord record = queries.get(tenantId, recordId, principal);
    permissions.requireEdit(principal, record);
    return record;
  }
}
```

### 10.3 WorkRecordComment.java

```java
package io.aegisops.workrecord.extension.domain;

import java.time.OffsetDateTime;
import java.util.List;

public record WorkRecordComment(
    String id,
    String tenantId,
    String recordId,
    String content,
    List<String> mentionUserIds,
    String createdBy,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt,
    int rowVersion) {

  public WorkRecordComment {
    mentionUserIds = mentionUserIds == null ? List.of() : List.copyOf(mentionUserIds);
  }
}
```

### 10.4 CommentRepository.java

```java
package io.aegisops.workrecord.extension.application.port;

import io.aegisops.workrecord.extension.domain.WorkRecordComment;
import java.util.List;
import java.util.Optional;

public interface CommentRepository {

  WorkRecordComment create(
      String tenantId,
      String recordId,
      String content,
      List<String> mentions,
      String actorId);

  List<WorkRecordComment> list(
      String tenantId,
      String recordId,
      int limit,
      String afterId);

  Optional<WorkRecordComment> find(
      String tenantId,
      String commentId);

  boolean update(
      String tenantId,
      String commentId,
      String content,
      List<String> mentions,
      int expectedVersion);

  boolean softDelete(
      String tenantId,
      String commentId,
      int expectedVersion);
}
```

### 10.5 JdbcCommentRepository.java

```java
package io.aegisops.workrecord.extension.infrastructure.jdbc;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.aegisops.common.id.Ids;
import io.aegisops.workrecord.extension.application.port.CommentRepository;
import io.aegisops.workrecord.extension.domain.WorkRecordComment;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcCommentRepository implements CommentRepository {

  private static final String COLUMNS =
      "id, tenant_id, record_id, content, mentions_json::text, created_by,"
          + " created_at, updated_at, row_version";

  private final NamedParameterJdbcTemplate jdbc;
  private final ObjectMapper objectMapper;

  public JdbcCommentRepository(
      NamedParameterJdbcTemplate jdbc,
      ObjectMapper objectMapper) {
    this.jdbc = jdbc;
    this.objectMapper = objectMapper;
  }

  @Override
  public WorkRecordComment create(
      String tenantId,
      String recordId,
      String content,
      List<String> mentions,
      String actorId) {
    String id = Ids.newId();
    jdbc.update(
        """
        insert into work_record.wr_comment(
          id, tenant_id, record_id, content, mentions_json, created_by)
        values (
          :id, :tenantId, :recordId, :content,
          cast(:mentions as jsonb), :actorId)
        """,
        Map.of(
            "id", id,
            "tenantId", tenantId,
            "recordId", recordId,
            "content", content,
            "mentions", write(mentions),
            "actorId", actorId));
    return find(tenantId, id).orElseThrow();
  }

  @Override
  public List<WorkRecordComment> list(
      String tenantId,
      String recordId,
      int limit,
      String afterId) {
    Map<String, Object> params = new HashMap<>();
    params.put("tenantId", tenantId);
    params.put("recordId", recordId);
    params.put("afterId", blankToNull(afterId));
    params.put("limit", Math.min(Math.max(limit, 1), 200));

    return jdbc.query(
        "select " + COLUMNS
            + " from work_record.wr_comment"
            + " where tenant_id=:tenantId and record_id=:recordId"
            + " and deleted_at is null"
            + " and (:afterId is null or id > :afterId)"
            + " order by created_at asc, id asc limit :limit",
        params,
        (rs, rowNum) -> map(rs));
  }

  @Override
  public Optional<WorkRecordComment> find(
      String tenantId,
      String commentId) {
    List<WorkRecordComment> rows =
        jdbc.query(
            "select " + COLUMNS
                + " from work_record.wr_comment"
                + " where tenant_id=:tenantId and id=:id and deleted_at is null",
            Map.of("tenantId", tenantId, "id", commentId),
            (rs, rowNum) -> map(rs));
    return rows.stream().findFirst();
  }

  @Override
  public boolean update(
      String tenantId,
      String commentId,
      String content,
      List<String> mentions,
      int expectedVersion) {
    return jdbc.update(
            """
            update work_record.wr_comment
            set content=:content,
                mentions_json=cast(:mentions as jsonb),
                updated_at=now(),
                row_version=row_version+1
            where tenant_id=:tenantId and id=:id
              and row_version=:expectedVersion
              and deleted_at is null
            """,
            Map.of(
                "tenantId", tenantId,
                "id", commentId,
                "content", content,
                "mentions", write(mentions),
                "expectedVersion", expectedVersion))
        == 1;
  }

  @Override
  public boolean softDelete(
      String tenantId,
      String commentId,
      int expectedVersion) {
    return jdbc.update(
            """
            update work_record.wr_comment
            set deleted_at=now(), updated_at=now(), row_version=row_version+1
            where tenant_id=:tenantId and id=:id
              and row_version=:expectedVersion
              and deleted_at is null
            """,
            Map.of(
                "tenantId", tenantId,
                "id", commentId,
                "expectedVersion", expectedVersion))
        == 1;
  }

  private WorkRecordComment map(ResultSet rs) throws SQLException {
    return new WorkRecordComment(
        rs.getString("id"),
        rs.getString("tenant_id"),
        rs.getString("record_id"),
        rs.getString("content"),
        readMentions(rs.getString("mentions_json")),
        rs.getString("created_by"),
        rs.getObject("created_at", java.time.OffsetDateTime.class),
        rs.getObject("updated_at", java.time.OffsetDateTime.class),
        rs.getInt("row_version"));
  }

  private List<String> readMentions(String json) {
    try {
      return objectMapper.readValue(json, new TypeReference<List<String>>() {});
    } catch (Exception ex) {
      throw new IllegalStateException("invalid comment mentions", ex);
    }
  }

  private String write(List<String> mentions) {
    try {
      return objectMapper.writeValueAsString(mentions == null ? List.of() : mentions);
    } catch (Exception ex) {
      throw new IllegalArgumentException("invalid comment mentions", ex);
    }
  }

  private String blankToNull(String value) {
    return value == null || value.isBlank() ? null : value;
  }
}
```

### 10.6 CommentService.java

```java
package io.aegisops.workrecord.extension.application.service;

import io.aegisops.audit.AuditRecordCommand;
import io.aegisops.audit.AuditService;
import io.aegisops.security.UserPrincipal;
import io.aegisops.user.UserService;
import io.aegisops.workrecord.extension.application.port.CommentRepository;
import io.aegisops.workrecord.extension.application.port.RecordAccessPort;
import io.aegisops.workrecord.extension.domain.WorkRecordComment;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CommentService {

  private final CommentRepository repository;
  private final RecordAccessPort recordAccess;
  private final UserService users;
  private final NotificationService notifications;
  private final AuditService auditService;

  public CommentService(
      CommentRepository repository,
      RecordAccessPort recordAccess,
      UserService users,
      NotificationService notifications,
      AuditService auditService) {
    this.repository = repository;
    this.recordAccess = recordAccess;
    this.users = users;
    this.notifications = notifications;
    this.auditService = auditService;
  }

  public List<WorkRecordComment> list(
      String tenantId,
      String recordId,
      UserPrincipal user) {
    recordAccess.requireReadable(tenantId, recordId, user);
    return repository.list(tenantId, recordId, 200, null);
  }

  @Transactional
  public WorkRecordComment create(
      String tenantId,
      String recordId,
      CommentCommand command,
      UserPrincipal user) {
    recordAccess.requireReadable(tenantId, recordId, user);
    requireCommentPermission(user);
    String content = normalizeContent(command.content());
    List<String> mentions = normalizeMentions(tenantId, command.mentionUserIds());
    WorkRecordComment comment =
        repository.create(tenantId, recordId, content, mentions, user.id());

    for (String mentioned : mentions) {
      if (!mentioned.equals(user.id())) {
        notifications.createMention(
            tenantId,
            mentioned,
            recordId,
            user.displayName(),
            comment.id());
      }
    }

    auditService.record(
        new AuditRecordCommand(
            tenantId,
            user.id(),
            "work_record.comment.create",
            "work_record_comment",
            comment.id(),
            "{}",
            "{}",
            "{\"recordId\":\"" + recordId + "\"}"));
    return comment;
  }

  @Transactional
  public WorkRecordComment update(
      String tenantId,
      String commentId,
      CommentCommand command,
      int expectedVersion,
      UserPrincipal user) {
    WorkRecordComment existing = requireOwned(tenantId, commentId, user);
    recordAccess.requireReadable(tenantId, existing.recordId(), user);
    List<String> mentions = normalizeMentions(tenantId, command.mentionUserIds());
    boolean updated =
        repository.update(
            tenantId,
            commentId,
            normalizeContent(command.content()),
            mentions,
            expectedVersion);
    if (!updated) {
      throw new IllegalStateException("comment was modified by another request");
    }
    return repository.find(tenantId, commentId).orElseThrow();
  }

  @Transactional
  public void delete(
      String tenantId,
      String commentId,
      int expectedVersion,
      UserPrincipal user) {
    WorkRecordComment existing = requireOwned(tenantId, commentId, user);
    recordAccess.requireReadable(tenantId, existing.recordId(), user);
    if (!repository.softDelete(tenantId, commentId, expectedVersion)) {
      throw new IllegalStateException("comment was modified by another request");
    }
  }

  private WorkRecordComment requireOwned(
      String tenantId,
      String commentId,
      UserPrincipal user) {
    WorkRecordComment comment =
        repository.find(tenantId, commentId)
            .orElseThrow(() -> new IllegalArgumentException("comment not found"));
    boolean administrator = user.hasPermission("work-record:comment:moderate");
    if (!administrator && !user.id().equals(comment.createdBy())) {
      throw new AccessDeniedException("not allowed to modify this comment");
    }
    return comment;
  }

  private void requireCommentPermission(UserPrincipal user) {
    if (user == null || !user.hasPermission("work-record:comment")) {
      throw new AccessDeniedException("not allowed to comment");
    }
  }

  private String normalizeContent(String value) {
    String normalized = value == null ? "" : value.trim();
    if (normalized.isEmpty() || normalized.length() > 4000) {
      throw new IllegalArgumentException("comment length must be between 1 and 4000");
    }
    return normalized;
  }

  private List<String> normalizeMentions(
      String tenantId,
      List<String> values) {
    LinkedHashSet<String> result = new LinkedHashSet<>();
    if (values == null) {
      return List.of();
    }
    if (values.size() > 20) {
      throw new IllegalArgumentException("at most 20 mentions are allowed");
    }
    for (String userId : values) {
      if (userId == null || userId.isBlank()) {
        continue;
      }
      var account = users.getById(userId);
      if (!tenantId.equals(account.tenantId())) {
        throw new IllegalArgumentException("mentioned user is outside tenant");
      }
      result.add(userId);
    }
    return List.copyOf(result);
  }

  public record CommentCommand(
      String content,
      List<String> mentionUserIds) {}
}
```

### 10.7 CommentController.java

```java
package io.aegisops.workrecord.extension.api;

import io.aegisops.common.api.ApiResponse;
import io.aegisops.common.tenant.TenantContext;
import io.aegisops.security.UserPrincipal;
import io.aegisops.workrecord.extension.application.service.CommentService;
import java.util.List;
import java.util.Map;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/work-record/records/{recordId}/comments")
@ConditionalOnProperty(
    prefix = "aiops.runtime",
    name = "app",
    havingValue = "server",
    matchIfMissing = true)
public class CommentController {

  private final CommentService service;

  public CommentController(CommentService service) {
    this.service = service;
  }

  @GetMapping
  @PreAuthorize("hasAuthority('work-record:read:self') or hasAuthority('work-record:read:all')")
  public ApiResponse<?> list(
      @PathVariable String recordId,
      @AuthenticationPrincipal UserPrincipal user) {
    return ApiResponse.ok(
        service.list(TenantContext.requireTenantId(), recordId, user));
  }

  @PostMapping
  @PreAuthorize("hasAuthority('work-record:comment')")
  public ApiResponse<?> create(
      @PathVariable String recordId,
      @RequestBody CommentRequest request,
      @AuthenticationPrincipal UserPrincipal user) {
    return ApiResponse.ok(
        service.create(
            TenantContext.requireTenantId(),
            recordId,
            new CommentService.CommentCommand(request.content(), request.mentionUserIds()),
            user));
  }

  @PutMapping("/{commentId}")
  @PreAuthorize("hasAuthority('work-record:comment')")
  public ApiResponse<?> update(
      @PathVariable String recordId,
      @PathVariable String commentId,
      @RequestBody CommentRequest request,
      @RequestParam int rowVersion,
      @AuthenticationPrincipal UserPrincipal user) {
    return ApiResponse.ok(
        service.update(
            TenantContext.requireTenantId(),
            commentId,
            new CommentService.CommentCommand(request.content(), request.mentionUserIds()),
            rowVersion,
            user));
  }

  @DeleteMapping("/{commentId}")
  @PreAuthorize("hasAuthority('work-record:comment')")
  public ApiResponse<Map<String, Boolean>> delete(
      @PathVariable String recordId,
      @PathVariable String commentId,
      @RequestParam int rowVersion,
      @AuthenticationPrincipal UserPrincipal user) {
    service.delete(
        TenantContext.requireTenantId(),
        commentId,
        rowVersion,
        user);
    return ApiResponse.ok(Map.of("deleted", true));
  }

  public record CommentRequest(
      String content,
      List<String> mentionUserIds) {}
}
```

### 10.8 WorkRecordAttachment.java

```java
package io.aegisops.workrecord.extension.domain;

import java.time.OffsetDateTime;

public record WorkRecordAttachment(
    String id,
    String tenantId,
    String recordId,
    String objectKey,
    String fileName,
    String contentType,
    long sizeBytes,
    String sha256,
    String status,
    String uploadedBy,
    OffsetDateTime createdAt) {}
```

### 10.9 AttachmentRepository.java

```java
package io.aegisops.workrecord.extension.application.port;

import io.aegisops.workrecord.extension.domain.WorkRecordAttachment;
import java.util.List;
import java.util.Optional;

public interface AttachmentRepository {

  WorkRecordAttachment create(CreateAttachment command);

  List<WorkRecordAttachment> list(String tenantId, String recordId);

  Optional<WorkRecordAttachment> find(String tenantId, String attachmentId);

  boolean markDeleted(String tenantId, String attachmentId);

  record CreateAttachment(
      String id,
      String tenantId,
      String recordId,
      String objectKey,
      String fileName,
      String contentType,
      long sizeBytes,
      String sha256,
      String uploadedBy) {}
}
```

### 10.10 JdbcAttachmentRepository.java

```java
package io.aegisops.workrecord.extension.infrastructure.jdbc;

import io.aegisops.workrecord.extension.application.port.AttachmentRepository;
import io.aegisops.workrecord.extension.domain.WorkRecordAttachment;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcAttachmentRepository implements AttachmentRepository {

  private static final String COLUMNS =
      "id, tenant_id, record_id, object_key, file_name, content_type, size_bytes,"
          + " sha256, status, uploaded_by, created_at";

  private final NamedParameterJdbcTemplate jdbc;

  public JdbcAttachmentRepository(NamedParameterJdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  public WorkRecordAttachment create(CreateAttachment command) {
    Map<String, Object> params = new HashMap<>();
    params.put("id", command.id());
    params.put("tenantId", command.tenantId());
    params.put("recordId", command.recordId());
    params.put("objectKey", command.objectKey());
    params.put("fileName", command.fileName());
    params.put("contentType", command.contentType());
    params.put("size", command.sizeBytes());
    params.put("sha256", command.sha256());
    params.put("uploadedBy", command.uploadedBy());
    jdbc.update(
        """
        insert into work_record.wr_attachment(
          id, tenant_id, record_id, object_key, file_name, content_type,
          size_bytes, sha256, uploaded_by)
        values (
          :id, :tenantId, :recordId, :objectKey, :fileName, :contentType,
          :size, :sha256, :uploadedBy)
        """,
        params);
    return find(command.tenantId(), command.id()).orElseThrow();
  }

  @Override
  public List<WorkRecordAttachment> list(String tenantId, String recordId) {
    return jdbc.query(
        "select " + COLUMNS
            + " from work_record.wr_attachment"
            + " where tenant_id=:tenantId and record_id=:recordId"
            + " and deleted_at is null and status='ready'"
            + " order by created_at desc, id desc",
        Map.of("tenantId", tenantId, "recordId", recordId),
        (rs, rowNum) -> map(rs));
  }

  @Override
  public Optional<WorkRecordAttachment> find(String tenantId, String attachmentId) {
    List<WorkRecordAttachment> rows =
        jdbc.query(
            "select " + COLUMNS
                + " from work_record.wr_attachment"
                + " where tenant_id=:tenantId and id=:id and deleted_at is null",
            Map.of("tenantId", tenantId, "id", attachmentId),
            (rs, rowNum) -> map(rs));
    return rows.stream().findFirst();
  }

  @Override
  public boolean markDeleted(String tenantId, String attachmentId) {
    return jdbc.update(
            """
            update work_record.wr_attachment
            set status='deleted', deleted_at=now()
            where tenant_id=:tenantId and id=:id and deleted_at is null
            """,
            Map.of("tenantId", tenantId, "id", attachmentId))
        == 1;
  }

  private WorkRecordAttachment map(ResultSet rs) throws SQLException {
    return new WorkRecordAttachment(
        rs.getString("id"),
        rs.getString("tenant_id"),
        rs.getString("record_id"),
        rs.getString("object_key"),
        rs.getString("file_name"),
        rs.getString("content_type"),
        rs.getLong("size_bytes"),
        rs.getString("sha256"),
        rs.getString("status"),
        rs.getString("uploaded_by"),
        rs.getObject("created_at", java.time.OffsetDateTime.class));
  }
}
```

### 10.11 AttachmentService.java

```java
package io.aegisops.workrecord.extension.application.service;

import io.aegisops.common.id.Ids;
import io.aegisops.security.UserPrincipal;
import io.aegisops.workrecord.extension.application.port.AttachmentRepository;
import io.aegisops.workrecord.extension.application.port.ObjectStoragePort;
import io.aegisops.workrecord.extension.application.port.RecordAccessPort;
import io.aegisops.workrecord.extension.domain.WorkRecordAttachment;
import java.io.InputStream;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AttachmentService {

  private static final Set<String> BLOCKED_TYPES =
      Set.of(
          "application/x-msdownload",
          "application/x-sh",
          "application/x-bat",
          "application/java-archive");

  private final RecordAccessPort recordAccess;
  private final AttachmentRepository repository;
  private final ObjectStoragePort storage;
  private final ObjectStorageProperties properties;

  public AttachmentService(
      RecordAccessPort recordAccess,
      AttachmentRepository repository,
      ObjectStoragePort storage,
      ObjectStorageProperties properties) {
    this.recordAccess = recordAccess;
    this.repository = repository;
    this.storage = storage;
    this.properties = properties;
  }

  public List<WorkRecordAttachment> list(
      String tenantId,
      String recordId,
      UserPrincipal user) {
    recordAccess.requireReadable(tenantId, recordId, user);
    return repository.list(tenantId, recordId);
  }

  @Transactional
  public WorkRecordAttachment upload(
      String tenantId,
      String recordId,
      UploadCommand command,
      UserPrincipal user) {
    recordAccess.requireWritable(tenantId, recordId, user);
    requirePermission(user, "work-record:attachment");
    validate(command);

    String id = Ids.newId();
    String safeName = safeFileName(command.fileName());
    String objectKey =
        tenantId + "/records/" + recordId + "/attachments/" + id + "/" + safeName;
    ObjectStoragePort.StoredObject stored =
        storage.put(
            new ObjectStoragePort.PutObjectCommand(
                objectKey,
                command.contentType(),
                command.sizeBytes()),
            command.input());

    try {
      return repository.create(
          new AttachmentRepository.CreateAttachment(
              id,
              tenantId,
              recordId,
              objectKey,
              command.fileName(),
              command.contentType(),
              stored.sizeBytes(),
              stored.sha256(),
              user.id()));
    } catch (RuntimeException ex) {
      storage.delete(objectKey);
      throw ex;
    }
  }

  public String downloadUrl(
      String tenantId,
      String attachmentId,
      UserPrincipal user) {
    WorkRecordAttachment attachment =
        repository.find(tenantId, attachmentId)
            .orElseThrow(() -> new IllegalArgumentException("attachment not found"));
    recordAccess.requireReadable(tenantId, attachment.recordId(), user);
    return storage.presignedGet(
        attachment.objectKey(),
        Duration.ofSeconds(properties.downloadUrlExpirySeconds()));
  }

  @Transactional
  public void delete(
      String tenantId,
      String attachmentId,
      UserPrincipal user) {
    WorkRecordAttachment attachment =
        repository.find(tenantId, attachmentId)
            .orElseThrow(() -> new IllegalArgumentException("attachment not found"));
    recordAccess.requireWritable(tenantId, attachment.recordId(), user);
    if (!attachment.uploadedBy().equals(user.id())
        && !user.hasPermission("work-record:attachment:moderate")) {
      throw new AccessDeniedException("not allowed to delete attachment");
    }
    if (repository.markDeleted(tenantId, attachmentId)) {
      storage.delete(attachment.objectKey());
    }
  }

  private void requirePermission(UserPrincipal user, String permission) {
    if (user == null || !user.hasPermission(permission)) {
      throw new AccessDeniedException("not allowed to upload attachment");
    }
  }

  private void validate(UploadCommand command) {
    if (command.sizeBytes() < 1
        || command.sizeBytes() > properties.attachmentMaxBytes()) {
      throw new IllegalArgumentException("attachment size exceeds limit");
    }
    if (command.contentType() == null || BLOCKED_TYPES.contains(command.contentType())) {
      throw new IllegalArgumentException("attachment content type is not allowed");
    }
  }

  private String safeFileName(String fileName) {
    String value = fileName == null ? "attachment" : fileName;
    value = value.replace('\\', '_').replace('/', '_').replace("..", "_");
    return value.length() <= 120 ? value : value.substring(value.length() - 120);
  }

  public record UploadCommand(
      String fileName,
      String contentType,
      long sizeBytes,
      InputStream input) {}
}
```

生产环境还应在 `stored` 和 `ready` 之间增加杀毒/内容扫描状态；第一版至少拒绝明显可执行类型并记录 SHA-256。

### 10.12 RecordRelation.java

```java
package io.aegisops.workrecord.extension.domain;

import java.time.OffsetDateTime;

public record RecordRelation(
    String id,
    String tenantId,
    String recordId,
    RelationType relationType,
    String targetId,
    String targetTitle,
    String targetStatus,
    String snapshotJson,
    String createdBy,
    OffsetDateTime createdAt) {}
```

### 10.13 RelationType.java

```java
package io.aegisops.workrecord.extension.domain;

public enum RelationType {
  ALERT,
  INSPECTION,
  INCIDENT;

  public String value() {
    return name().toLowerCase();
  }

  public static RelationType from(String value) {
    return RelationType.valueOf(value.trim().toUpperCase());
  }
}
```

### 10.14 RelationTargetPort.java

```java
package io.aegisops.workrecord.extension.application.port;

import io.aegisops.security.UserPrincipal;
import io.aegisops.workrecord.extension.domain.RelationType;

public interface RelationTargetPort {

  ResolvedTarget resolve(
      String tenantId,
      RelationType type,
      String targetId,
      UserPrincipal principal);

  record ResolvedTarget(
      String id,
      String title,
      String status,
      String snapshotJson) {}
}
```

### 10.15 JdbcRelationTargetAdapter.java

该适配器只读取三个现有领域表，并同时校验调用人的领域权限。当前 alert/incident 表均包含 `tenant_id/title/status`，巡检运行表通过任务表获得名称。

```java
package io.aegisops.workrecord.extension.infrastructure.adapter;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.aegisops.security.UserPrincipal;
import io.aegisops.workrecord.extension.application.port.RelationTargetPort;
import io.aegisops.workrecord.extension.domain.RelationType;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;

@Component
public class JdbcRelationTargetAdapter implements RelationTargetPort {

  private final NamedParameterJdbcTemplate jdbc;
  private final ObjectMapper objectMapper;

  public JdbcRelationTargetAdapter(
      NamedParameterJdbcTemplate jdbc,
      ObjectMapper objectMapper) {
    this.jdbc = jdbc;
    this.objectMapper = objectMapper;
  }

  @Override
  public ResolvedTarget resolve(
      String tenantId,
      RelationType type,
      String targetId,
      UserPrincipal principal) {
    requirePermission(type, principal);
    return switch (type) {
      case ALERT -> one(
          """
          select id, title, status, severity, starts_at
          from alert_event
          where tenant_id=:tenantId and id=:id
          """,
          tenantId,
          targetId);
      case INCIDENT -> one(
          """
          select id, title, status, severity, started_at
          from incident
          where tenant_id=:tenantId and id=:id
          """,
          tenantId,
          targetId);
      case INSPECTION -> inspection(tenantId, targetId);
    };
  }

  private ResolvedTarget inspection(String tenantId, String targetId) {
    List<ResolvedTarget> rows =
        jdbc.query(
            """
            select r.id,
                   t.name as title,
                   r.status,
                   null as severity,
                   r.started_at
            from inspection_run r
            join inspection_task t on t.id=r.task_id
            where r.tenant_id=:tenantId and r.id=:id
            """,
            Map.of("tenantId", tenantId, "id", targetId),
            (rs, rowNum) -> target(rs));
    return rows.stream()
        .findFirst()
        .orElseThrow(() -> new IllegalArgumentException("inspection run not found"));
  }

  private ResolvedTarget one(
      String sql,
      String tenantId,
      String targetId) {
    List<ResolvedTarget> rows =
        jdbc.query(
            sql,
            Map.of("tenantId", tenantId, "id", targetId),
            (rs, rowNum) -> target(rs));
    return rows.stream()
        .findFirst()
        .orElseThrow(() -> new IllegalArgumentException("relation target not found"));
  }

  private ResolvedTarget target(java.sql.ResultSet rs) throws java.sql.SQLException {
    Map<String, Object> snapshot = new LinkedHashMap<>();
    snapshot.put("severity", rs.getString("severity"));
    snapshot.put("startedAt", rs.getObject("started_at"));
    try {
      return new ResolvedTarget(
          rs.getString("id"),
          rs.getString("title"),
          rs.getString("status"),
          objectMapper.writeValueAsString(snapshot));
    } catch (Exception ex) {
      throw new IllegalStateException("failed to serialize relation snapshot", ex);
    }
  }

  private void requirePermission(RelationType type, UserPrincipal principal) {
    String permission =
        switch (type) {
          case ALERT -> "alert:read";
          case INCIDENT -> "incident:read";
          case INSPECTION -> "incident:read";
        };
    if (principal == null || !principal.hasPermission(permission)) {
      throw new AccessDeniedException("not allowed to read relation target");
    }
  }
}
```

### 10.16 RecordRelationService.java

```java
package io.aegisops.workrecord.extension.application.service;

import io.aegisops.common.id.Ids;
import io.aegisops.security.UserPrincipal;
import io.aegisops.workrecord.extension.application.port.RecordAccessPort;
import io.aegisops.workrecord.extension.application.port.RecordRelationRepository;
import io.aegisops.workrecord.extension.application.port.RelationTargetPort;
import io.aegisops.workrecord.extension.domain.RecordRelation;
import io.aegisops.workrecord.extension.domain.RelationType;
import java.util.List;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RecordRelationService {

  private final RecordAccessPort records;
  private final RelationTargetPort targets;
  private final RecordRelationRepository relations;

  public RecordRelationService(
      RecordAccessPort records,
      RelationTargetPort targets,
      RecordRelationRepository relations) {
    this.records = records;
    this.targets = targets;
    this.relations = relations;
  }

  public List<RecordRelation> list(
      String tenantId,
      String recordId,
      UserPrincipal user) {
    records.requireReadable(tenantId, recordId, user);
    return relations.list(tenantId, recordId);
  }

  @Transactional
  public RecordRelation create(
      String tenantId,
      String recordId,
      RelationType type,
      String targetId,
      UserPrincipal user) {
    records.requireWritable(tenantId, recordId, user);
    if (!user.hasPermission("work-record:relation")) {
      throw new AccessDeniedException("not allowed to create relation");
    }
    RelationTargetPort.ResolvedTarget target =
        targets.resolve(tenantId, type, targetId, user);
    return relations.create(
        new RecordRelationRepository.CreateRelation(
            Ids.newId(),
            tenantId,
            recordId,
            type,
            target.id(),
            target.title(),
            target.status(),
            target.snapshotJson(),
            user.id()));
  }

  @Transactional
  public void delete(
      String tenantId,
      String recordId,
      String relationId,
      UserPrincipal user) {
    records.requireWritable(tenantId, recordId, user);
    if (!relations.delete(tenantId, recordId, relationId)) {
      throw new IllegalArgumentException("relation not found");
    }
  }
}
```

`RecordRelationRepository` 的 JDBC 实现按 `V0029` 字段直接插入、查询、删除，所有 SQL 必须同时包含 `tenant_id` 和 `record_id`。

### 10.17 CommentServiceTest.java

```java
package io.aegisops.workrecord.extension.application.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.aegisops.audit.AuditService;
import io.aegisops.user.UserService;
import io.aegisops.workrecord.extension.application.port.CommentRepository;
import io.aegisops.workrecord.extension.application.port.RecordAccessPort;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class CommentServiceTest {

  @Test
  void createRequiresRecordVisibilityBeforeWriting() {
    CommentRepository repository = Mockito.mock(CommentRepository.class);
    RecordAccessPort access = Mockito.mock(RecordAccessPort.class);
    UserService users = Mockito.mock(UserService.class);
    NotificationService notifications = Mockito.mock(NotificationService.class);
    AuditService audit = Mockito.mock(AuditService.class);
    var principal = TestPrincipals.commenter();
    when(access.requireReadable("t1", "r1", principal))
        .thenThrow(new org.springframework.security.access.AccessDeniedException("denied"));

    CommentService service =
        new CommentService(repository, access, users, notifications, audit);

    assertThatThrownBy(
            () ->
                service.create(
                    "t1",
                    "r1",
                    new CommentService.CommentCommand("hello", List.of()),
                    principal))
        .isInstanceOf(org.springframework.security.access.AccessDeniedException.class);

    verify(access).requireReadable("t1", "r1", principal);
    Mockito.verifyNoInteractions(repository);
  }
}
```

### 10.18 AttachmentServiceTest.java

```java
package io.aegisops.workrecord.extension.application.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.aegisops.workrecord.extension.application.port.AttachmentRepository;
import io.aegisops.workrecord.extension.application.port.ObjectStoragePort;
import io.aegisops.workrecord.extension.application.port.RecordAccessPort;
import java.io.ByteArrayInputStream;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class AttachmentServiceTest {

  @Test
  void deletesStoredObjectWhenDatabaseInsertFails() {
    RecordAccessPort access = Mockito.mock(RecordAccessPort.class);
    AttachmentRepository repository = Mockito.mock(AttachmentRepository.class);
    ObjectStoragePort storage = Mockito.mock(ObjectStoragePort.class);
    ObjectStorageProperties properties = ObjectStorageProperties.defaults();
    var user = TestPrincipals.attachmentWriter();
    when(storage.put(Mockito.any(), Mockito.any()))
        .thenReturn(new ObjectStoragePort.StoredObject("key", 3, "a".repeat(64)));
    when(repository.create(Mockito.any()))
        .thenThrow(new IllegalStateException("db failed"));

    AttachmentService service =
        new AttachmentService(access, repository, storage, properties);

    assertThatThrownBy(
            () ->
                service.upload(
                    "t1",
                    "r1",
                    new AttachmentService.UploadCommand(
                        "a.txt",
                        "text/plain",
                        3,
                        new ByteArrayInputStream(new byte[] {1, 2, 3})),
                    user))
        .isInstanceOf(IllegalStateException.class);

    verify(storage).delete(Mockito.contains("/records/r1/attachments/"));
  }
}
```

---

## 11. Phase 20.4：统计报表、工作量分析、缺失提醒与值班交接

### 11.1 统计原则

第一版统计只查询 PostgreSQL，不立即接 ClickHouse：

```text
记录量 < 100 万：PostgreSQL 聚合 + 合理索引
记录量 >= 100 万或跨年高频报表：异步同步 ClickHouse
```

动态字段只允许统计模板版本中 `statistical=true` 且类型为 `number/select/multi_select/boolean` 的字段。任何字段编码都必须由字段索引表解析，禁止把请求中的字段名直接拼入 SQL。

### 11.2 StatisticsQuery.java

```java
package io.aegisops.workrecord.extension.application.command;

import java.time.OffsetDateTime;

public record StatisticsQuery(
    String templateId,
    String templateVersionId,
    OffsetDateTime from,
    OffsetDateTime to,
    String groupBy,
    String statisticalFieldCode) {}
```

### 11.3 StatisticsResult.java

```java
package io.aegisops.workrecord.extension.application.model;

import java.math.BigDecimal;
import java.util.List;

public record StatisticsResult(
    long totalRecords,
    long completedRecords,
    long distinctOwners,
    List<SeriesPoint> series,
    FieldAggregate fieldAggregate) {

  public StatisticsResult {
    series = series == null ? List.of() : List.copyOf(series);
  }

  public record SeriesPoint(
      String key,
      String label,
      long count,
      BigDecimal value) {}

  public record FieldAggregate(
      String fieldCode,
      BigDecimal sum,
      BigDecimal average,
      BigDecimal minimum,
      BigDecimal maximum,
      long valueCount) {}
}
```

### 11.4 WorkloadSummary.java

```java
package io.aegisops.workrecord.extension.application.model;

import java.math.BigDecimal;
import java.util.List;

public record WorkloadSummary(
    int workdayCount,
    List<UserWorkload> users) {

  public WorkloadSummary {
    users = users == null ? List.of() : List.copyOf(users);
  }

  public record UserWorkload(
      String userId,
      String displayName,
      long recordCount,
      long completedCount,
      BigDecimal numericWorkload,
      BigDecimal recordsPerWorkday) {}
}
```

### 11.5 StatisticsRepository.java

```java
package io.aegisops.workrecord.extension.application.port;

import io.aegisops.workrecord.extension.application.command.StatisticsQuery;
import io.aegisops.workrecord.extension.application.model.StatisticsResult;
import io.aegisops.workrecord.extension.application.model.WorkloadSummary;
import java.time.OffsetDateTime;

public interface StatisticsRepository {

  StatisticsResult aggregate(
      String tenantId,
      StatisticsQuery query,
      StatisticalField field);

  WorkloadSummary workload(
      String tenantId,
      String templateId,
      OffsetDateTime from,
      OffsetDateTime to,
      int workdayCount,
      StatisticalField field);

  record StatisticalField(
      String fieldCode,
      String fieldType) {

    public static StatisticalField none() {
      return new StatisticalField(null, null);
    }

    public boolean present() {
      return fieldCode != null;
    }
  }
}
```

### 11.6 JdbcStatisticsRepository.java

```java
package io.aegisops.workrecord.extension.infrastructure.jdbc;

import io.aegisops.workrecord.extension.application.command.StatisticsQuery;
import io.aegisops.workrecord.extension.application.model.StatisticsResult;
import io.aegisops.workrecord.extension.application.model.WorkloadSummary;
import io.aegisops.workrecord.extension.application.port.StatisticsRepository;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcStatisticsRepository implements StatisticsRepository {

  private final NamedParameterJdbcTemplate jdbc;

  public JdbcStatisticsRepository(NamedParameterJdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  public StatisticsResult aggregate(
      String tenantId,
      StatisticsQuery query,
      StatisticalField field) {
    Map<String, Object> params = params(tenantId, query);
    Map<String, Object> totals =
        jdbc.queryForMap(
            """
            select count(*) as total_count,
                   count(*) filter (where status='done') as completed_count,
                   count(distinct owner_id) filter (where owner_id is not null) as owner_count
            from work_record.wr_record
            where tenant_id=:tenantId and deleted_at is null
              and (:templateId is null or template_id=:templateId)
              and (:versionId is null or template_version_id=:versionId)
              and (:fromTime is null or record_time>=:fromTime)
              and (:toTime is null or record_time<:toTime)
            """,
            params);

    List<StatisticsResult.SeriesPoint> series =
        groupedSeries(query.groupBy(), params);
    StatisticsResult.FieldAggregate aggregate =
        field.present() ? numericAggregate(field, params) : null;

    return new StatisticsResult(
        number(totals.get("total_count")),
        number(totals.get("completed_count")),
        number(totals.get("owner_count")),
        series,
        aggregate);
  }

  @Override
  public WorkloadSummary workload(
      String tenantId,
      String templateId,
      java.time.OffsetDateTime from,
      java.time.OffsetDateTime to,
      int workdayCount,
      StatisticalField field) {
    Map<String, Object> params = new HashMap<>();
    params.put("tenantId", tenantId);
    params.put("templateId", blankToNull(templateId));
    params.put("fromTime", from);
    params.put("toTime", to);
    params.put("workdays", Math.max(workdayCount, 1));

    String valueExpression =
        field.present() && "number".equals(field.fieldType())
            ? "coalesce(sum(case when jsonb_typeof(custom_data_json -> '"
                + sqlLiteral(field.fieldCode())
                + "')='number' then (custom_data_json ->> '"
                + sqlLiteral(field.fieldCode())
                + "')::numeric else 0 end),0)"
            : "0::numeric";

    List<WorkloadSummary.UserWorkload> rows =
        jdbc.query(
            """
            select coalesce(owner_id, creator_id) as user_id,
                   count(*) as record_count,
                   count(*) filter (where status='done') as completed_count,
                   """
                + valueExpression
                + """ as workload,
                   count(*)::numeric / :workdays as records_per_workday
            from work_record.wr_record
            where tenant_id=:tenantId and deleted_at is null
              and (:templateId is null or template_id=:templateId)
              and record_time>=:fromTime and record_time<:toTime
            group by coalesce(owner_id, creator_id)
            order by workload desc, record_count desc, user_id
            """,
            params,
            (rs, rowNum) ->
                new WorkloadSummary.UserWorkload(
                    rs.getString("user_id"),
                    rs.getString("user_id"),
                    rs.getLong("record_count"),
                    rs.getLong("completed_count"),
                    rs.getBigDecimal("workload"),
                    rs.getBigDecimal("records_per_workday")));
    return new WorkloadSummary(workdayCount, rows);
  }

  private List<StatisticsResult.SeriesPoint> groupedSeries(
      String groupBy,
      Map<String, Object> params) {
    Grouping grouping = Grouping.from(groupBy);
    String sql =
        "select " + grouping.expression() + " as group_key, count(*) as item_count"
            + " from work_record.wr_record"
            + " where tenant_id=:tenantId and deleted_at is null"
            + " and (:templateId is null or template_id=:templateId)"
            + " and (:versionId is null or template_version_id=:versionId)"
            + " and (:fromTime is null or record_time>=:fromTime)"
            + " and (:toTime is null or record_time<:toTime)"
            + " group by " + grouping.expression()
            + " order by group_key";
    return jdbc.query(
        sql,
        params,
        (rs, rowNum) ->
            new StatisticsResult.SeriesPoint(
                rs.getString("group_key"),
                rs.getString("group_key"),
                rs.getLong("item_count"),
                null));
  }

  private StatisticsResult.FieldAggregate numericAggregate(
      StatisticalField field,
      Map<String, Object> params) {
    if (!"number".equals(field.fieldType())) {
      return null;
    }
    String code = sqlLiteral(field.fieldCode());
    String numeric =
        "case when jsonb_typeof(custom_data_json -> '" + code + "')='number'"
            + " then (custom_data_json ->> '" + code + "')::numeric end";
    Map<String, Object> row =
        jdbc.queryForMap(
            "select coalesce(sum(" + numeric + "),0) as value_sum,"
                + " avg(" + numeric + ") as value_avg,"
                + " min(" + numeric + ") as value_min,"
                + " max(" + numeric + ") as value_max,"
                + " count(" + numeric + ") as value_count"
                + " from work_record.wr_record"
                + " where tenant_id=:tenantId and deleted_at is null"
                + " and (:templateId is null or template_id=:templateId)"
                + " and (:versionId is null or template_version_id=:versionId)"
                + " and (:fromTime is null or record_time>=:fromTime)"
                + " and (:toTime is null or record_time<:toTime)",
            params);
    return new StatisticsResult.FieldAggregate(
        field.fieldCode(),
        decimal(row.get("value_sum")),
        decimal(row.get("value_avg")),
        decimal(row.get("value_min")),
        decimal(row.get("value_max")),
        number(row.get("value_count")));
  }

  private Map<String, Object> params(String tenantId, StatisticsQuery query) {
    Map<String, Object> params = new HashMap<>();
    params.put("tenantId", tenantId);
    params.put("templateId", blankToNull(query.templateId()));
    params.put("versionId", blankToNull(query.templateVersionId()));
    params.put("fromTime", query.from());
    params.put("toTime", query.to());
    return params;
  }

  private String sqlLiteral(String value) {
    if (value == null || !value.matches("^[a-zA-Z][a-zA-Z0-9_]{0,63}$")) {
      throw new IllegalArgumentException("invalid statistical field code");
    }
    return value;
  }

  private String blankToNull(String value) {
    return value == null || value.isBlank() ? null : value;
  }

  private long number(Object value) {
    return value instanceof Number number ? number.longValue() : 0L;
  }

  private BigDecimal decimal(Object value) {
    return value instanceof BigDecimal decimal ? decimal : null;
  }

  private enum Grouping {
    DAY("to_char(record_time at time zone 'UTC', 'YYYY-MM-DD')"),
    MONTH("to_char(record_time at time zone 'UTC', 'YYYY-MM')"),
    STATUS("status"),
    OWNER("coalesce(owner_id, creator_id)"),
    TEMPLATE("template_id");

    private final String expression;

    Grouping(String expression) {
      this.expression = expression;
    }

    String expression() {
      return expression;
    }

    static Grouping from(String value) {
      if (value == null || value.isBlank()) {
        return DAY;
      }
      return Grouping.valueOf(value.trim().toUpperCase());
    }
  }
}
```

### 11.7 StatisticsService.java

```java
package io.aegisops.workrecord.extension.application.service;

import io.aegisops.security.UserPrincipal;
import io.aegisops.workrecord.application.port.WorkRecordCalendarPort;
import io.aegisops.workrecord.application.port.WorkRecordFieldIndexRepository;
import io.aegisops.workrecord.domain.model.FieldType;
import io.aegisops.workrecord.domain.model.WorkRecordField;
import io.aegisops.workrecord.extension.application.command.StatisticsQuery;
import io.aegisops.workrecord.extension.application.model.StatisticsResult;
import io.aegisops.workrecord.extension.application.model.WorkloadSummary;
import io.aegisops.workrecord.extension.application.port.StatisticsRepository;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

@Service
public class StatisticsService {

  private final StatisticsRepository repository;
  private final WorkRecordFieldIndexRepository fields;
  private final WorkRecordCalendarPort calendar;
  private final Clock clock;

  public StatisticsService(
      StatisticsRepository repository,
      WorkRecordFieldIndexRepository fields,
      WorkRecordCalendarPort calendar,
      @org.springframework.beans.factory.annotation.Qualifier("workRecordClock") Clock clock) {
    this.repository = repository;
    this.fields = fields;
    this.calendar = calendar;
    this.clock = clock;
  }

  public StatisticsResult statistics(
      String tenantId,
      StatisticsQuery query,
      UserPrincipal user) {
    requireAnalytics(user);
    validateRange(query.from(), query.to());
    return repository.aggregate(tenantId, query, resolveField(tenantId, query));
  }

  public WorkloadSummary workload(
      String tenantId,
      StatisticsQuery query,
      UserPrincipal user) {
    requireAnalytics(user);
    validateRange(query.from(), query.to());
    int workdays =
        calendar.countWorkdays(
            tenantId,
            query.from().toInstant(),
            query.to().toInstant());
    return repository.workload(
        tenantId,
        query.templateId(),
        query.from(),
        query.to(),
        workdays,
        resolveField(tenantId, query));
  }

  private StatisticsRepository.StatisticalField resolveField(
      String tenantId,
      StatisticsQuery query) {
    if (query.statisticalFieldCode() == null || query.statisticalFieldCode().isBlank()) {
      return StatisticsRepository.StatisticalField.none();
    }
    if (query.templateVersionId() == null || query.templateVersionId().isBlank()) {
      throw new IllegalArgumentException("templateVersionId is required for dynamic statistics");
    }
    List<WorkRecordField> versionFields =
        fields.listByVersion(tenantId, query.templateVersionId());
    WorkRecordField field =
        versionFields.stream()
            .filter(item -> item.fieldCode().equals(query.statisticalFieldCode()))
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException("statistical field not found"));
    if (!field.statistical() || field.fieldType() != FieldType.NUMBER) {
      throw new IllegalArgumentException("field is not a statistical number field");
    }
    return new StatisticsRepository.StatisticalField(
        field.fieldCode(), field.fieldType().value());
  }

  private void requireAnalytics(UserPrincipal user) {
    if (user == null || !user.hasPermission("work-record:analytics")) {
      throw new AccessDeniedException("not allowed to read work-record analytics");
    }
  }

  private void validateRange(OffsetDateTime from, OffsetDateTime to) {
    if (from == null || to == null || !to.isAfter(from)) {
      throw new IllegalArgumentException("valid statistics time range is required");
    }
    if (java.time.Duration.between(from, to).toDays() > 730) {
      throw new IllegalArgumentException("statistics range cannot exceed 730 days");
    }
  }
}
```

`WorkRecordCalendarPort` 当前没有 `countWorkdays` 时，增加该方法并由 Platform Calendar Adapter 使用 `platform_calendar_day.is_workday=true` 聚合实现。

### 11.8 StatisticsController.java

```java
package io.aegisops.workrecord.extension.api;

import io.aegisops.common.api.ApiResponse;
import io.aegisops.common.tenant.TenantContext;
import io.aegisops.security.UserPrincipal;
import io.aegisops.workrecord.extension.application.command.StatisticsQuery;
import io.aegisops.workrecord.extension.application.service.StatisticsService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/work-record/analytics")
@ConditionalOnProperty(
    prefix = "aiops.runtime",
    name = "app",
    havingValue = "server",
    matchIfMissing = true)
public class StatisticsController {

  private final StatisticsService service;

  public StatisticsController(StatisticsService service) {
    this.service = service;
  }

  @GetMapping("/statistics")
  @PreAuthorize("hasAuthority('work-record:analytics')")
  public ApiResponse<?> statistics(
      @ModelAttribute StatisticsQuery query,
      @AuthenticationPrincipal UserPrincipal user) {
    return ApiResponse.ok(
        service.statistics(TenantContext.requireTenantId(), query, user));
  }

  @GetMapping("/workload")
  @PreAuthorize("hasAuthority('work-record:analytics')")
  public ApiResponse<?> workload(
      @ModelAttribute StatisticsQuery query,
      @AuthenticationPrincipal UserPrincipal user) {
    return ApiResponse.ok(
        service.workload(TenantContext.requireTenantId(), query, user));
  }
}
```

### 11.9 NotificationService.java

```java
package io.aegisops.workrecord.extension.application.service;

import io.aegisops.common.id.Ids;
import io.aegisops.workrecord.extension.application.port.NotificationRepository;
import io.aegisops.workrecord.extension.domain.WorkRecordNotification;
import java.time.LocalDate;
import org.springframework.stereotype.Service;

@Service
public class NotificationService {

  private final NotificationRepository repository;

  public NotificationService(NotificationRepository repository) {
    this.repository = repository;
  }

  public void createMention(
      String tenantId,
      String userId,
      String recordId,
      String actorName,
      String commentId) {
    repository.insertIfAbsent(
        new WorkRecordNotification(
            Ids.newId(),
            tenantId,
            userId,
            "comment_mention",
            "工作记录评论提到了你",
            actorName + " 在评论中提到了你",
            "work_record",
            recordId,
            "comment-mention:" + commentId + ":" + userId,
            null,
            null));
  }

  public void createMissingDaily(
      String tenantId,
      String userId,
      String templateId,
      LocalDate date) {
    repository.insertIfAbsent(
        new WorkRecordNotification(
            Ids.newId(),
            tenantId,
            userId,
            "daily_record_missing",
            "日报尚未填写",
            date + " 为工作日，请及时填写日报",
            "work_record_template",
            templateId,
            "daily-missing:" + templateId + ":" + date + ":" + userId,
            null,
            null));
  }
}
```

`NotificationRepository.insertIfAbsent` 使用 `on conflict (tenant_id,user_id,dedupe_key) do nothing`，从数据库层保证多 Worker 实例不重复提醒。

### 11.10 MissingDailyReminderScheduler.java

```java
package io.aegisops.worker.job;

import io.aegisops.workrecord.extension.application.service.MissingDailyReminderService;
import java.time.Clock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class MissingDailyReminderScheduler {

  private final MissingDailyReminderService service;
  private final Clock clock;

  public MissingDailyReminderScheduler(
      MissingDailyReminderService service,
      Clock clock) {
    this.service = service;
    this.clock = clock;
  }

  @Scheduled(fixedDelayString = "${aiops.work-record.reminder-scan-ms:300000}")
  public void scan() {
    service.scanDueRules(clock.instant());
  }
}
```

### 11.11 MissingDailyReminderService.java

```java
package io.aegisops.workrecord.extension.application.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.aegisops.workrecord.application.port.WorkRecordCalendarPort;
import io.aegisops.workrecord.extension.application.port.ReminderRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;

@Service
public class MissingDailyReminderService {

  private final ReminderRepository reminders;
  private final WorkRecordCalendarPort calendar;
  private final NotificationService notifications;
  private final ObjectMapper objectMapper;

  public MissingDailyReminderService(
      ReminderRepository reminders,
      WorkRecordCalendarPort calendar,
      NotificationService notifications,
      ObjectMapper objectMapper) {
    this.reminders = reminders;
    this.calendar = calendar;
    this.notifications = notifications;
    this.objectMapper = objectMapper;
  }

  public void scanDueRules(Instant now) {
    for (var rule : reminders.findDueRules(now, 200)) {
      ZoneId zone = ZoneId.of(rule.timeZone());
      LocalDate date = now.atZone(zone).toLocalDate();
      if (!calendar.isWorkday(rule.tenantId(), date)) {
        continue;
      }
      for (String userId : targetUsers(rule)) {
        if (!reminders.hasRecord(
            rule.tenantId(), rule.templateId(), userId, date, zone)) {
          notifications.createMissingDaily(
              rule.tenantId(), userId, rule.templateId(), date);
        }
      }
    }
  }

  private Set<String> targetUsers(ReminderRepository.ReminderRule rule) {
    try {
      var node = objectMapper.readTree(rule.targetJson());
      if ("users".equals(rule.targetType())) {
        List<String> users =
            objectMapper.convertValue(node.path("userIds"), new TypeReference<List<String>>() {});
        return new LinkedHashSet<>(users);
      }
      return new LinkedHashSet<>(reminders.usersByRole(rule.tenantId(), node.path("roleCode").asText()));
    } catch (Exception ex) {
      throw new IllegalStateException("invalid reminder target", ex);
    }
  }
}
```

调休工作日由 `calendar.isWorkday()` 决定；周末但被标记为调休上班时必须提醒，节假日必须跳过。

### 11.12 HandoverStatus.java

```java
package io.aegisops.workrecord.extension.domain;

public enum HandoverStatus {
  DRAFT,
  SUBMITTED,
  ACCEPTED,
  COMPLETED,
  CANCELLED;

  public String value() {
    return name().toLowerCase();
  }
}
```

### 11.13 HandoverService.java

```java
package io.aegisops.workrecord.extension.application.service;

import io.aegisops.security.UserPrincipal;
import io.aegisops.workrecord.extension.application.port.HandoverRepository;
import io.aegisops.workrecord.extension.application.port.RecordAccessPort;
import io.aegisops.workrecord.extension.domain.HandoverStatus;
import io.aegisops.workrecord.extension.domain.WorkRecordHandover;
import java.util.List;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class HandoverService {

  private final HandoverRepository repository;
  private final RecordAccessPort records;

  public HandoverService(
      HandoverRepository repository,
      RecordAccessPort records) {
    this.repository = repository;
    this.records = records;
  }

  @Transactional
  public WorkRecordHandover create(
      String tenantId,
      CreateHandover command,
      UserPrincipal user) {
    requirePermission(user);
    if (!command.shiftEnd().isAfter(command.shiftStart())) {
      throw new IllegalArgumentException("shiftEnd must be after shiftStart");
    }
    List<String> recordIds = command.recordIds() == null ? List.of() : command.recordIds();
    if (recordIds.size() > 200) {
      throw new IllegalArgumentException("handover supports at most 200 records");
    }
    for (String recordId : recordIds) {
      records.requireReadable(tenantId, recordId, user);
    }
    return repository.create(tenantId, command, user.id());
  }

  @Transactional
  public WorkRecordHandover submit(
      String tenantId,
      String handoverId,
      int rowVersion,
      UserPrincipal user) {
    WorkRecordHandover current = require(tenantId, handoverId);
    if (!current.createdBy().equals(user.id()) || current.status() != HandoverStatus.DRAFT) {
      throw new AccessDeniedException("handover cannot be submitted");
    }
    return transition(current, HandoverStatus.SUBMITTED, rowVersion);
  }

  @Transactional
  public WorkRecordHandover accept(
      String tenantId,
      String handoverId,
      int rowVersion,
      UserPrincipal user) {
    WorkRecordHandover current = require(tenantId, handoverId);
    if (!current.toUserId().equals(user.id()) || current.status() != HandoverStatus.SUBMITTED) {
      throw new AccessDeniedException("handover cannot be accepted");
    }
    return transition(current, HandoverStatus.ACCEPTED, rowVersion);
  }

  @Transactional
  public WorkRecordHandover complete(
      String tenantId,
      String handoverId,
      int rowVersion,
      UserPrincipal user) {
    WorkRecordHandover current = require(tenantId, handoverId);
    if (!current.toUserId().equals(user.id()) || current.status() != HandoverStatus.ACCEPTED) {
      throw new AccessDeniedException("handover cannot be completed");
    }
    return transition(current, HandoverStatus.COMPLETED, rowVersion);
  }

  private WorkRecordHandover transition(
      WorkRecordHandover current,
      HandoverStatus target,
      int expectedVersion) {
    if (!repository.transition(
        current.tenantId(),
        current.id(),
        current.status(),
        target,
        expectedVersion)) {
      throw new IllegalStateException("handover was modified by another request");
    }
    return require(current.tenantId(), current.id());
  }

  private WorkRecordHandover require(String tenantId, String id) {
    return repository.find(tenantId, id)
        .orElseThrow(() -> new IllegalArgumentException("handover not found"));
  }

  private void requirePermission(UserPrincipal user) {
    if (user == null || !user.hasPermission("work-record:handover")) {
      throw new AccessDeniedException("not allowed to manage handover");
    }
  }

  public record CreateHandover(
      String fromUserId,
      String toUserId,
      java.time.OffsetDateTime shiftStart,
      java.time.OffsetDateTime shiftEnd,
      String summary,
      List<String> recordIds,
      List<String> relationIds) {}
}
```

### 11.14 StatisticsServiceTest.java

```java
package io.aegisops.workrecord.extension.application.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;

import io.aegisops.workrecord.application.port.WorkRecordCalendarPort;
import io.aegisops.workrecord.application.port.WorkRecordFieldIndexRepository;
import io.aegisops.workrecord.extension.application.command.StatisticsQuery;
import io.aegisops.workrecord.extension.application.port.StatisticsRepository;
import java.time.Clock;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class StatisticsServiceTest {

  @Test
  void rejectsNonStatisticalUserBeforeQuery() {
    StatisticsRepository repository = Mockito.mock(StatisticsRepository.class);
    WorkRecordFieldIndexRepository fields = Mockito.mock(WorkRecordFieldIndexRepository.class);
    WorkRecordCalendarPort calendar = Mockito.mock(WorkRecordCalendarPort.class);
    StatisticsService service =
        new StatisticsService(repository, fields, calendar, Clock.systemUTC());

    assertThatThrownBy(
            () ->
                service.statistics(
                    "t1",
                    new StatisticsQuery(
                        null,
                        null,
                        OffsetDateTime.parse("2026-07-01T00:00:00Z"),
                        OffsetDateTime.parse("2026-08-01T00:00:00Z"),
                        "day",
                        null),
                    TestPrincipals.normalUser()))
        .isInstanceOf(org.springframework.security.access.AccessDeniedException.class);

    verifyNoInteractions(repository);
  }
}
```

### 11.15 MissingDailyReminderServiceTest.java

```java
package io.aegisops.workrecord.extension.application.service;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.aegisops.workrecord.application.port.WorkRecordCalendarPort;
import io.aegisops.workrecord.extension.application.port.ReminderRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class MissingDailyReminderServiceTest {

  @Test
  void holidayDoesNotCreateMissingNotification() {
    ReminderRepository repository = Mockito.mock(ReminderRepository.class);
    WorkRecordCalendarPort calendar = Mockito.mock(WorkRecordCalendarPort.class);
    NotificationService notifications = Mockito.mock(NotificationService.class);
    var rule = TestReminderRules.users("rule1", "t1", "tpl1", "Asia/Tokyo", "u1");
    when(repository.findDueRules(Mockito.any(), Mockito.anyInt()))
        .thenReturn(List.of(rule));
    when(calendar.isWorkday("t1", LocalDate.of(2026, 7, 20))).thenReturn(false);

    var service =
        new MissingDailyReminderService(
            repository,
            calendar,
            notifications,
            new ObjectMapper());
    service.scanDueRules(Instant.parse("2026-07-20T10:00:00Z"));

    verify(notifications, never())
        .createMissingDaily(Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any());
  }

  @Test
  void adjustedWeekendWorkdayCreatesReminder() {
    ReminderRepository repository = Mockito.mock(ReminderRepository.class);
    WorkRecordCalendarPort calendar = Mockito.mock(WorkRecordCalendarPort.class);
    NotificationService notifications = Mockito.mock(NotificationService.class);
    var rule = TestReminderRules.users("rule1", "t1", "tpl1", "Asia/Tokyo", "u1");
    when(repository.findDueRules(Mockito.any(), Mockito.anyInt()))
        .thenReturn(List.of(rule));
    when(calendar.isWorkday("t1", LocalDate.of(2026, 7, 19))).thenReturn(true);
    when(repository.hasRecord(Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any()))
        .thenReturn(false);

    var service =
        new MissingDailyReminderService(
            repository,
            calendar,
            notifications,
            new ObjectMapper());
    service.scanDueRules(Instant.parse("2026-07-19T10:00:00Z"));

    verify(notifications).createMissingDaily("t1", "u1", "tpl1", LocalDate.of(2026, 7, 19));
  }
}
```

---

## 12. Phase 20.5：AI 自动总结与 AI 月报

### 12.1 安全边界

AI 生成遵守以下规则：

```text
1. 只把调用人有权读取的字段送给 Agent。
2. mask_mode=full 的字段永不进入 Prompt。
3. 附件正文默认不进入 Prompt，只传文件名和类型。
4. 输出永远是草稿，不自动覆盖工作记录或自动发布月报。
5. 输入快照、promptVersion、provider、model、reviewer 全部留痕。
6. 相同 inputHash 的成功结果直接复用，防止重复计费。
7. Agent 只返回 Markdown 和结构化 warnings，不执行外部操作。
```

### 12.2 Python Agent Schema

在 `apps/aiops-agent/src/aiops_agent/schemas.py` 增加：

```python
from datetime import date
from typing import Literal


class WorkRecordItem(BaseModel):
    id: str
    title: str
    status: str
    recordTime: datetime
    ownerName: str | None = None
    fields: dict[str, Any] = Field(default_factory=dict)
    relations: list[dict[str, Any]] = Field(default_factory=list)


class WorkRecordGenerateRequest(BaseModel):
    contractVersion: str = "work-record-generation.v1"
    generationType: Literal["record_summary", "monthly_report"]
    tenantId: str
    resourceId: str
    periodStart: date | None = None
    periodEnd: date | None = None
    locale: str = "zh-CN"
    promptVersion: str = "work-record-summary-v1"
    records: list[WorkRecordItem] = Field(default_factory=list, max_length=5000)
    statistics: dict[str, Any] = Field(default_factory=dict)
    traceId: str


class WorkRecordGenerateResponse(BaseModel):
    contractVersion: str = "work-record-generation.v1"
    provider: str
    model: str
    promptVersion: str
    markdown: str = Field(min_length=1, max_length=100000)
    warnings: list[str] = Field(default_factory=list)
    raw: dict[str, Any] = Field(default_factory=dict)
```

### 12.3 Python 生成服务

新增 `apps/aiops-agent/src/aiops_agent/work_record_generation.py`：

````python
from __future__ import annotations

import json
from collections import Counter

from aiops_agent.schemas import (
    WorkRecordGenerateRequest,
    WorkRecordGenerateResponse,
)
from aiops_agent.settings import Settings


class WorkRecordGenerationService:
    def __init__(self, settings: Settings) -> None:
        self._settings = settings

    async def generate(
        self,
        request: WorkRecordGenerateRequest,
    ) -> WorkRecordGenerateResponse:
        if request.generationType == "record_summary":
            markdown = self._record_summary(request)
        else:
            markdown = self._monthly_report(request)

        return WorkRecordGenerateResponse(
            provider=self._settings.provider,
            model=self._settings.model,
            promptVersion=request.promptVersion,
            markdown=markdown,
            warnings=[],
            raw={
                "recordCount": len(request.records),
                "generationType": request.generationType,
            },
        )

    def _record_summary(self, request: WorkRecordGenerateRequest) -> str:
        if len(request.records) != 1:
            raise ValueError("record_summary requires exactly one record")

        record = request.records[0]
        field_lines = [
            f"- **{key}**：{self._render(value)}"
            for key, value in record.fields.items()
        ]
        relation_lines = [
            f"- {item.get('type', 'relation')}："
            f"{item.get('title') or item.get('id', '-') }"
            for item in record.relations
        ]

        return "\n".join(
            [
                f"# {record.title}",
                "",
                "## 工作概述",
                f"记录状态：{record.status}；记录时间：{record.recordTime.isoformat()}。",
                "",
                "## 关键内容",
                *(field_lines or ["- 暂无可总结字段。"]),
                "",
                "## 关联对象",
                *(relation_lines or ["- 暂无关联告警、巡检或事件。"]),
                "",
                "## 后续建议",
                "- 请由记录负责人复核总结内容后再发布。",
            ]
        )

    def _monthly_report(self, request: WorkRecordGenerateRequest) -> str:
        status_counter = Counter(record.status for record in request.records)
        owner_counter = Counter(
            record.ownerName or "未分配" for record in request.records
        )
        top_owners = owner_counter.most_common(10)

        return "\n".join(
            [
                "# 工作月报",
                "",
                f"统计周期：{request.periodStart} 至 {request.periodEnd}",
                f"记录总数：{len(request.records)}",
                "",
                "## 状态分布",
                *[
                    f"- {status}：{count}"
                    for status, count in sorted(status_counter.items())
                ],
                "",
                "## 工作量分布",
                *[f"- {owner}：{count}" for owner, count in top_owners],
                "",
                "## 系统统计",
                "```json",
                json.dumps(request.statistics, ensure_ascii=False, indent=2),
                "```",
                "",
                "## 风险与改进",
                "- 请结合未完成记录、关联事件与 SLA 超时情况人工复核。",
            ]
        )

    def _render(self, value: object) -> str:
        if isinstance(value, (dict, list)):
            return json.dumps(value, ensure_ascii=False)
        return str(value)
````

生成模式接入真实 LLM 时，`_record_summary/_monthly_report` 替换为 OpenAI-compatible structured output 调用，但 deterministic fallback 必须保留，Agent 不可用时仍能生成基础报告。

### 12.4 Python API

在 `main.py` 增加：

```python
from aiops_agent.schemas import (
    WorkRecordGenerateRequest,
    WorkRecordGenerateResponse,
)
from aiops_agent.work_record_generation import WorkRecordGenerationService


def work_record_generation_service() -> WorkRecordGenerationService:
    return WorkRecordGenerationService(settings)


@app.post(
    "/v1/work-record/generate",
    response_model=WorkRecordGenerateResponse,
    dependencies=[Depends(verify_internal_token)],
)
async def generate_work_record(
    request: WorkRecordGenerateRequest,
    service: WorkRecordGenerationService = Depends(
        work_record_generation_service
    ),
) -> WorkRecordGenerateResponse:
    if request.contractVersion != "work-record-generation.v1":
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail="unsupported work-record generation contract",
        )
    return await service.generate(request)
```

### 12.5 Python 测试

新增 `apps/aiops-agent/tests/test_work_record_generation.py`：

```python
from datetime import UTC, date, datetime

import pytest

from aiops_agent.schemas import (
    WorkRecordGenerateRequest,
    WorkRecordItem,
)
from aiops_agent.settings import settings
from aiops_agent.work_record_generation import WorkRecordGenerationService


@pytest.mark.asyncio
async def test_record_summary_contains_visible_fields() -> None:
    service = WorkRecordGenerationService(settings)
    response = await service.generate(
        WorkRecordGenerateRequest(
            generationType="record_summary",
            tenantId="t1",
            resourceId="r1",
            records=[
                WorkRecordItem(
                    id="r1",
                    title="日报",
                    status="done",
                    recordTime=datetime(2026, 7, 11, tzinfo=UTC),
                    fields={"result": "发布完成"},
                )
            ],
            traceId="trace-1",
        )
    )

    assert "发布完成" in response.markdown
    assert response.promptVersion == "work-record-summary-v1"


@pytest.mark.asyncio
async def test_monthly_report_counts_records() -> None:
    service = WorkRecordGenerationService(settings)
    response = await service.generate(
        WorkRecordGenerateRequest(
            generationType="monthly_report",
            tenantId="t1",
            resourceId="2026-07",
            periodStart=date(2026, 7, 1),
            periodEnd=date(2026, 7, 31),
            records=[
                WorkRecordItem(
                    id="r1",
                    title="a",
                    status="done",
                    recordTime=datetime(2026, 7, 1, tzinfo=UTC),
                ),
                WorkRecordItem(
                    id="r2",
                    title="b",
                    status="draft",
                    recordTime=datetime(2026, 7, 2, tzinfo=UTC),
                ),
            ],
            traceId="trace-2",
        )
    )

    assert "记录总数：2" in response.markdown
    assert "done：1" in response.markdown
```

### 12.6 Java Client 契约

新增 `modules/aiops-ai-client/src/main/java/io/aegisops/ai/client/workrecord/WorkRecordAiClient.java`：

```java
package io.aegisops.ai.client.workrecord;

public interface WorkRecordAiClient {

  WorkRecordGenerationResponse generate(
      WorkRecordGenerationRequest request);
}
```

```java
package io.aegisops.ai.client.workrecord;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

public record WorkRecordGenerationRequest(
    String contractVersion,
    String generationType,
    String tenantId,
    String resourceId,
    LocalDate periodStart,
    LocalDate periodEnd,
    String locale,
    String promptVersion,
    List<RecordItem> records,
    Map<String, Object> statistics,
    String traceId) {

  public WorkRecordGenerationRequest {
    records = records == null ? List.of() : List.copyOf(records);
    statistics = statistics == null ? Map.of() : Map.copyOf(statistics);
  }

  public record RecordItem(
      String id,
      String title,
      String status,
      OffsetDateTime recordTime,
      String ownerName,
      Map<String, Object> fields,
      List<Map<String, Object>> relations) {

    public RecordItem {
      fields = fields == null ? Map.of() : Map.copyOf(fields);
      relations = relations == null ? List.of() : List.copyOf(relations);
    }
  }
}
```

```java
package io.aegisops.ai.client.workrecord;

import java.util.List;
import java.util.Map;

public record WorkRecordGenerationResponse(
    String contractVersion,
    String provider,
    String model,
    String promptVersion,
    String markdown,
    List<String> warnings,
    Map<String, Object> raw) {

  public WorkRecordGenerationResponse {
    warnings = warnings == null ? List.of() : List.copyOf(warnings);
    raw = raw == null ? Map.of() : Map.copyOf(raw);
  }
}
```

### 12.7 HttpWorkRecordAiClient.java

```java
package io.aegisops.ai.client.workrecord;

import io.aegisops.ai.client.AgentClientProperties;
import io.aegisops.ai.client.AgentContract;
import io.aegisops.common.exception.AppException;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class HttpWorkRecordAiClient implements WorkRecordAiClient {

  private final AgentClientProperties properties;
  private final RestClient restClient;

  public HttpWorkRecordAiClient(
      AgentClientProperties properties,
      RestClient.Builder builder) {
    this.properties = properties;
    this.restClient =
        builder
            .baseUrl(properties.normalizedBaseUrl())
            .defaultHeader(
                HttpHeaders.CONTENT_TYPE,
                MediaType.APPLICATION_JSON_VALUE)
            .build();
  }

  @Override
  public WorkRecordGenerationResponse generate(
      WorkRecordGenerationRequest request) {
    try {
      WorkRecordGenerationResponse response =
          restClient
              .post()
              .uri("/v1/work-record/generate")
              .header(
                  AgentContract.INTERNAL_TOKEN_HEADER,
                  properties.normalizedInternalToken())
              .header(AgentContract.TRACE_ID_HEADER, request.traceId())
              .body(request)
              .retrieve()
              .body(WorkRecordGenerationResponse.class);
      if (response == null || response.markdown() == null || response.markdown().isBlank()) {
        throw new AppException("AI_WORK_RECORD_EMPTY", "AI returned an empty work-record result");
      }
      return response;
    } catch (AppException ex) {
      throw ex;
    } catch (RuntimeException ex) {
      throw new AppException("AI_WORK_RECORD_CALL_FAILED", "Failed to call work-record AI agent");
    }
  }
}
```

### 12.8 AiGenerationRepository.java

```java
package io.aegisops.workrecord.extension.application.port;

import io.aegisops.workrecord.extension.domain.AiGeneration;
import java.util.List;
import java.util.Optional;

public interface AiGenerationRepository {

  AiGeneration create(CreateGeneration command);

  Optional<AiGeneration> find(String tenantId, String generationId);

  Optional<AiGeneration> findReusable(
      String tenantId,
      String generationType,
      String resourceType,
      String resourceId,
      String inputHash);

  List<AiGeneration> listByResource(
      String tenantId,
      String resourceType,
      String resourceId);

  boolean markRunning(String tenantId, String generationId);

  boolean complete(
      String tenantId,
      String generationId,
      String markdown,
      String provider,
      String model);

  boolean fail(String tenantId, String generationId);

  boolean review(
      String tenantId,
      String generationId,
      String targetStatus,
      String reviewerId);

  record CreateGeneration(
      String id,
      String tenantId,
      String generationType,
      String resourceType,
      String resourceId,
      java.time.LocalDate periodStart,
      java.time.LocalDate periodEnd,
      String promptVersion,
      String inputHash,
      String inputJson,
      String requestedBy) {}
}
```

### 12.9 AiGenerationService.java

```java
package io.aegisops.workrecord.extension.application.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.aegisops.common.id.Ids;
import io.aegisops.common.outbox.OutboxMessage;
import io.aegisops.common.outbox.OutboxWriter;
import io.aegisops.security.UserPrincipal;
import io.aegisops.workrecord.extension.application.port.AiGenerationRepository;
import io.aegisops.workrecord.extension.domain.AiGeneration;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AiGenerationService {

  private final AiGenerationRepository repository;
  private final AiInputBuilder inputBuilder;
  private final OutboxWriter outbox;
  private final ObjectMapper objectMapper;

  public AiGenerationService(
      AiGenerationRepository repository,
      AiInputBuilder inputBuilder,
      OutboxWriter outbox,
      ObjectMapper objectMapper) {
    this.repository = repository;
    this.inputBuilder = inputBuilder;
    this.outbox = outbox;
    this.objectMapper = objectMapper;
  }

  @Transactional
  public AiGeneration requestRecordSummary(
      String tenantId,
      String recordId,
      UserPrincipal user) {
    requireGenerate(user);
    AiInputBuilder.AiInput input =
        inputBuilder.recordSummary(tenantId, recordId, user);
    return createOrReuse(
        tenantId,
        "record_summary",
        "record",
        recordId,
        null,
        null,
        "work-record-summary-v1",
        input,
        user);
  }

  @Transactional
  public AiGeneration requestMonthlyReport(
      String tenantId,
      LocalDate month,
      UserPrincipal user) {
    requireGenerate(user);
    if (month == null) {
      throw new IllegalArgumentException("month is required");
    }
    LocalDate start = month.withDayOfMonth(1);
    LocalDate end = start.plusMonths(1).minusDays(1);
    AiInputBuilder.AiInput input =
        inputBuilder.monthlyReport(tenantId, start, end, user);
    return createOrReuse(
        tenantId,
        "monthly_report",
        "tenant_month",
        start.toString().substring(0, 7),
        start,
        end,
        "work-record-monthly-v1",
        input,
        user);
  }

  @Transactional
  public AiGeneration review(
      String tenantId,
      String generationId,
      boolean accepted,
      UserPrincipal user) {
    if (!user.hasPermission("work-record:ai:review")) {
      throw new AccessDeniedException("not allowed to review AI result");
    }
    String status = accepted ? "accepted" : "rejected";
    if (!repository.review(tenantId, generationId, status, user.id())) {
      throw new IllegalStateException("AI result cannot be reviewed");
    }
    return repository.find(tenantId, generationId).orElseThrow();
  }

  private AiGeneration createOrReuse(
      String tenantId,
      String generationType,
      String resourceType,
      String resourceId,
      LocalDate periodStart,
      LocalDate periodEnd,
      String promptVersion,
      AiInputBuilder.AiInput input,
      UserPrincipal user) {
    String inputJson = write(input.payload());
    String hash = sha256(inputJson);
    var reusable =
        repository.findReusable(
            tenantId,
            generationType,
            resourceType,
            resourceId,
            hash);
    if (reusable.isPresent()) {
      return reusable.get();
    }

    String id = Ids.newId();
    AiGeneration created =
        repository.create(
            new AiGenerationRepository.CreateGeneration(
                id,
                tenantId,
                generationType,
                resourceType,
                resourceId,
                periodStart,
                periodEnd,
                promptVersion,
                hash,
                inputJson,
                user.id()));

    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("tenantId", tenantId);
    payload.put("generationId", id);
    payload.put("requestedBy", user.id());
    outbox.enqueue(
        new OutboxMessage(
            "worker",
            "work-record-ai-generate",
            tenantId,
            "ai-generation:" + id,
            OffsetDateTime.now(),
            5,
            payload));
    return created;
  }

  private void requireGenerate(UserPrincipal user) {
    if (user == null || !user.hasPermission("work-record:ai:generate")) {
      throw new AccessDeniedException("not allowed to generate AI work-record content");
    }
  }

  private String write(Object value) {
    try {
      return objectMapper.writeValueAsString(value);
    } catch (Exception ex) {
      throw new IllegalStateException("failed to serialize AI input", ex);
    }
  }

  private String sha256(String value) {
    try {
      return HexFormat.of().formatHex(
          MessageDigest.getInstance("SHA-256")
              .digest(value.getBytes(StandardCharsets.UTF_8)));
    } catch (Exception ex) {
      throw new IllegalStateException("SHA-256 unavailable", ex);
    }
  }
}
```

### 12.10 AiGenerationProcessor.java

```java
package io.aegisops.workrecord.extension.application.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.aegisops.ai.client.workrecord.WorkRecordAiClient;
import io.aegisops.ai.client.workrecord.WorkRecordGenerationRequest;
import io.aegisops.workrecord.extension.application.port.AiGenerationRepository;
import org.springframework.stereotype.Service;

@Service
public class AiGenerationProcessor {

  private final AiGenerationRepository repository;
  private final WorkRecordAiClient client;
  private final ObjectMapper objectMapper;

  public AiGenerationProcessor(
      AiGenerationRepository repository,
      WorkRecordAiClient client,
      ObjectMapper objectMapper) {
    this.repository = repository;
    this.client = client;
    this.objectMapper = objectMapper;
  }

  public void process(String tenantId, String generationId) {
    var generation =
        repository.find(tenantId, generationId)
            .orElseThrow(() -> new IllegalArgumentException("AI generation not found"));
    if (!repository.markRunning(tenantId, generationId)) {
      return;
    }
    try {
      WorkRecordGenerationRequest request =
          objectMapper.readValue(
              generation.inputJson(),
              WorkRecordGenerationRequest.class);
      var response = client.generate(request);
      if (!repository.complete(
          tenantId,
          generationId,
          response.markdown(),
          response.provider(),
          response.model())) {
        throw new IllegalStateException("AI generation state changed");
      }
    } catch (RuntimeException ex) {
      repository.fail(tenantId, generationId);
      throw ex;
    } catch (Exception ex) {
      repository.fail(tenantId, generationId);
      throw new IllegalStateException("invalid AI input", ex);
    }
  }
}
```

### 12.11 AI 关键测试

```java
@Test
void recordSummaryInputMustExcludeFullyMaskedField() {
  FieldPolicyService policies = mock(FieldPolicyService.class);
  when(policies.readDecision(any(), eq("secret"), any()))
      .thenReturn(FieldReadDecision.hidden());

  AiInputBuilder builder = testBuilder(policies);
  AiInputBuilder.AiInput input =
      builder.recordSummary("t1", "r1", TestPrincipals.aiGenerator());

  assertThat(input.payload().toString()).doesNotContain("secret-value");
}

@Test
void sameInputHashReusesSuccessfulGeneration() {
  when(repository.findReusable("t1", "record_summary", "record", "r1", HASH))
      .thenReturn(Optional.of(existing));

  AiGeneration result =
      service.requestRecordSummary("t1", "r1", TestPrincipals.aiGenerator());

  assertThat(result.id()).isEqualTo(existing.id());
  verifyNoInteractions(outbox);
}
```

---

## 13. Phase 20.6：模板市场与字段级权限

### 13.1 Schema 协议升级

历史版本必须继续按原版本展示，所以 Phase 20 不能简单把 `CURRENT_SCHEMA_VERSION` 改为 2 后拒绝 v1。正确规则：

```text
历史读取：支持 v1、v2
新草稿发布：统一规范化为 v2
v1 缺少字段权限：等价于 readRoles=[]、writeRoles=[]、maskMode=none
```

完整替换版本常量：

```java
package io.aegisops.workrecord.application.schema;

public final class WorkRecordSchemaContract {

  public static final int MIN_SUPPORTED_SCHEMA_VERSION = 1;
  public static final int CURRENT_SCHEMA_VERSION = 2;

  public static final String ROOT_SCHEMA_VERSION_KEY =
      "x-work-record-schema-version";
  public static final String FIELD_EXTENSION_KEY = "x-work-record";

  public static boolean supportedVersion(int version) {
    return version >= MIN_SUPPORTED_SCHEMA_VERSION
        && version <= CURRENT_SCHEMA_VERSION;
  }

  private WorkRecordSchemaContract() {}
}
```

`WorkRecordSchemaValidator.validateRoot()` 的版本判断替换为：

```java
int version =
    root.has(ROOT_SCHEMA_VERSION_KEY)
        ? root.path(ROOT_SCHEMA_VERSION_KEY).asInt()
        : WorkRecordSchemaContract.MIN_SUPPORTED_SCHEMA_VERSION;

if (!WorkRecordSchemaContract.supportedVersion(version)) {
  throw new IllegalArgumentException("unsupported schema version: " + version);
}
```

### 13.2 字段协议

v2 字段扩展：

```json
{
  "x-work-record": {
    "fieldCode": "salary",
    "fieldType": "number",
    "readRoles": ["system_admin", "record_admin"],
    "writeRoles": ["system_admin"],
    "maskMode": "partial",
    "listVisible": false,
    "filterable": false,
    "exportable": false,
    "statistical": true
  }
}
```

字段级权限不是单独的 UI 配置，而是模板版本的一部分。发布时将权限同步到 `wr_field_policy`，历史模板版本的权限不可原地修改。

### 13.3 FieldPolicyDescriptor.java

```java
package io.aegisops.workrecord.domain.model;

import java.util.List;

public record FieldPolicyDescriptor(
    List<String> readRoles,
    List<String> writeRoles,
    String maskMode) {

  public FieldPolicyDescriptor {
    readRoles = readRoles == null ? List.of() : List.copyOf(readRoles);
    writeRoles = writeRoles == null ? List.of() : List.copyOf(writeRoles);
    maskMode = maskMode == null || maskMode.isBlank() ? "none" : maskMode;
  }

  public static FieldPolicyDescriptor unrestricted() {
    return new FieldPolicyDescriptor(List.of(), List.of(), "none");
  }
}
```

`FormFieldDescriptor` 增加最后一个属性：

```java
FieldPolicyDescriptor policy
```

Parser 增加：

```java
private FieldPolicyDescriptor policy(JsonNode ext) {
  return new FieldPolicyDescriptor(
      stringArray(ext.path("readRoles")),
      stringArray(ext.path("writeRoles")),
      textOrDefault(ext, "maskMode", "none"));
}

private List<String> stringArray(JsonNode node) {
  if (!node.isArray()) {
    return List.of();
  }
  List<String> result = new ArrayList<>();
  for (JsonNode item : node) {
    if (item.isTextual() && !item.asText().isBlank()) {
      result.add(item.asText());
    }
  }
  return List.copyOf(result);
}
```

Validator 增加：

```java
private static final Set<String> MASK_MODES =
    Set.of("none", "full", "partial");

public void validateFieldPolicy(JsonNode ext, String schemaPath) {
  validateRoleArray(ext.path("readRoles"), "readRoles", schemaPath);
  validateRoleArray(ext.path("writeRoles"), "writeRoles", schemaPath);

  String maskMode = optionalText(ext, "maskMode", "none");
  if (!MASK_MODES.contains(maskMode)) {
    throw new IllegalArgumentException(
        "unsupported maskMode at " + schemaPath + ": " + maskMode);
  }
}

private void validateRoleArray(JsonNode node, String name, String path) {
  if (node.isMissingNode()) {
    return;
  }
  if (!node.isArray() || node.size() > 20) {
    throw new IllegalArgumentException(name + " must be array with at most 20 roles at " + path);
  }
  for (JsonNode item : node) {
    if (!item.isTextual() || !item.asText().matches("^[a-z][a-z0-9_-]{1,63}$")) {
      throw new IllegalArgumentException("invalid role code in " + name + " at " + path);
    }
  }
}
```

### 13.4 FieldPolicy.java

```java
package io.aegisops.workrecord.extension.domain;

import java.util.List;

public record FieldPolicy(
    String templateVersionId,
    String fieldCode,
    List<String> readRoles,
    List<String> writeRoles,
    MaskMode maskMode) {

  public FieldPolicy {
    readRoles = readRoles == null ? List.of() : List.copyOf(readRoles);
    writeRoles = writeRoles == null ? List.of() : List.copyOf(writeRoles);
  }

  public enum MaskMode {
    NONE,
    FULL,
    PARTIAL;

    public static MaskMode from(String value) {
      return value == null ? NONE : valueOf(value.toUpperCase());
    }
  }
}
```

### 13.5 FieldPolicyRepository.java

```java
package io.aegisops.workrecord.extension.application.port;

import io.aegisops.workrecord.extension.domain.FieldPolicy;
import java.util.List;

public interface FieldPolicyRepository {

  void replaceForVersion(
      String tenantId,
      String templateVersionId,
      List<FieldPolicy> policies);

  List<FieldPolicy> listByVersion(
      String tenantId,
      String templateVersionId);
}
```

### 13.6 FieldPolicyService.java

```java
package io.aegisops.workrecord.extension.application.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.aegisops.security.UserPrincipal;
import io.aegisops.workrecord.extension.application.port.FieldPolicyRepository;
import io.aegisops.workrecord.extension.domain.FieldPolicy;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

@Service
public class FieldPolicyService {

  private final FieldPolicyRepository repository;
  private final ObjectMapper objectMapper;

  public FieldPolicyService(
      FieldPolicyRepository repository,
      ObjectMapper objectMapper) {
    this.repository = repository;
    this.objectMapper = objectMapper;
  }

  public String filterReadableJson(
      String tenantId,
      String templateVersionId,
      String customDataJson,
      UserPrincipal principal) {
    ObjectNode source = object(customDataJson);
    ObjectNode result = objectMapper.createObjectNode();
    Map<String, FieldPolicy> policies = policies(tenantId, templateVersionId);

    source.fields().forEachRemaining(entry -> {
      FieldReadDecision decision =
          readDecision(policies.get(entry.getKey()), principal);
      if (decision.visible()) {
        result.set(entry.getKey(), mask(entry.getValue(), decision.maskMode()));
      }
    });
    return write(result);
  }

  public void requireWritablePatch(
      String tenantId,
      String templateVersionId,
      String beforeJson,
      String afterJson,
      UserPrincipal principal) {
    ObjectNode before = object(beforeJson);
    ObjectNode after = object(afterJson);
    Map<String, FieldPolicy> policies = policies(tenantId, templateVersionId);

    Set<String> keys = new java.util.HashSet<>();
    before.fieldNames().forEachRemaining(keys::add);
    after.fieldNames().forEachRemaining(keys::add);

    for (String key : keys) {
      JsonNode oldValue = before.get(key);
      JsonNode newValue = after.get(key);
      if (java.util.Objects.equals(oldValue, newValue)) {
        continue;
      }
      if (!canWrite(policies.get(key), principal)) {
        throw new AccessDeniedException("not allowed to write field: " + key);
      }
    }
  }

  public boolean canReadField(
      String tenantId,
      String templateVersionId,
      String fieldCode,
      UserPrincipal principal) {
    return readDecision(
            policies(tenantId, templateVersionId).get(fieldCode),
            principal)
        .visible();
  }

  public boolean canWriteField(
      String tenantId,
      String templateVersionId,
      String fieldCode,
      UserPrincipal principal) {
    return canWrite(
        policies(tenantId, templateVersionId).get(fieldCode),
        principal);
  }

  private Map<String, FieldPolicy> policies(
      String tenantId,
      String versionId) {
    Map<String, FieldPolicy> result = new HashMap<>();
    for (FieldPolicy policy : repository.listByVersion(tenantId, versionId)) {
      result.put(policy.fieldCode(), policy);
    }
    return Map.copyOf(result);
  }

  private FieldReadDecision readDecision(
      FieldPolicy policy,
      UserPrincipal principal) {
    if (policy == null || policy.readRoles().isEmpty()) {
      return new FieldReadDecision(true, FieldPolicy.MaskMode.NONE);
    }
    boolean matched =
        principal != null
            && principal.roles().stream().anyMatch(policy.readRoles()::contains);
    return matched
        ? new FieldReadDecision(true, policy.maskMode())
        : new FieldReadDecision(false, FieldPolicy.MaskMode.FULL);
  }

  private boolean canWrite(FieldPolicy policy, UserPrincipal principal) {
    if (policy == null || policy.writeRoles().isEmpty()) {
      return true;
    }
    return principal != null
        && principal.roles().stream().anyMatch(policy.writeRoles()::contains);
  }

  private JsonNode mask(JsonNode value, FieldPolicy.MaskMode mode) {
    if (mode == FieldPolicy.MaskMode.NONE) {
      return value;
    }
    if (mode == FieldPolicy.MaskMode.FULL) {
      return objectMapper.getNodeFactory().textNode("******");
    }
    if (!value.isTextual()) {
      return objectMapper.getNodeFactory().textNode("***");
    }
    String text = value.asText();
    if (text.length() <= 4) {
      return objectMapper.getNodeFactory().textNode("****");
    }
    return objectMapper.getNodeFactory().textNode(
        text.substring(0, 2) + "****" + text.substring(text.length() - 2));
  }

  private ObjectNode object(String json) {
    try {
      JsonNode node = objectMapper.readTree(json == null ? "{}" : json);
      if (!node.isObject()) {
        throw new IllegalArgumentException("custom data must be object");
      }
      return (ObjectNode) node;
    } catch (IllegalArgumentException ex) {
      throw ex;
    } catch (Exception ex) {
      throw new IllegalArgumentException("invalid custom data", ex);
    }
  }

  private String write(JsonNode node) {
    try {
      return objectMapper.writeValueAsString(node);
    } catch (Exception ex) {
      throw new IllegalStateException("failed to serialize filtered data", ex);
    }
  }

  public record FieldReadDecision(
      boolean visible,
      FieldPolicy.MaskMode maskMode) {}
}
```

### 13.7 字段权限必须接入的所有路径

```text
GET record detail
record list dynamic columns
record update
record create
filter metadata
filter validator
CSV/XLSX export
Excel import
statistics/workload
AI input builder
record audit detail UI
```

核心改造示例：

```java
public WorkRecord getVisible(
    String tenantId,
    String recordId,
    UserPrincipal user) {
  WorkRecord record = queryService.get(tenantId, recordId, user);
  return record.withCustomDataJson(
      fieldPolicyService.filterReadableJson(
          tenantId,
          record.templateVersionId(),
          record.customDataJson(),
          user));
}
```

当前 `WorkRecord` 是 record，没有 `withCustomDataJson`，应新增静态 mapper：

```java
public final class WorkRecordViews {

  public static WorkRecord customData(
      WorkRecord source,
      String customDataJson) {
    return new WorkRecord(
        source.id(),
        source.tenantId(),
        source.templateId(),
        source.templateVersionId(),
        source.title(),
        source.status(),
        source.ownerId(),
        source.creatorId(),
        source.recordTime(),
        source.builtinDataJson(),
        customDataJson,
        source.rowVersion(),
        source.createdAt(),
        source.updatedAt(),
        source.deletedAt());
  }

  private WorkRecordViews() {}
}
```

更新记录时，在领域校验之前增加：

```java
fieldPolicyService.requireWritablePatch(
    tenantId,
    existing.templateVersionId(),
    existing.customDataJson(),
    effectiveCustomJson,
    user);
```

动态筛选时：

```java
if (!fieldPolicyService.canReadField(
    tenantId,
    templateVersionId,
    filter.fieldCode(),
    user)) {
  throw new AccessDeniedException(
      "not allowed to filter field: " + filter.fieldCode());
}
```

### 13.8 MarketPackageDocument.java

模板市场发布的是不可变包，不是对源模板的实时引用。

```java
package io.aegisops.workrecord.extension.application.model;

import java.util.List;
import java.util.Map;

public record MarketPackageDocument(
    int contractVersion,
    String packageCode,
    String name,
    String description,
    String templateCode,
    String templateName,
    String schemaJson,
    String designerJson,
    List<DictionaryDependency> dictionaries,
    Map<String, Object> metadata) {

  public MarketPackageDocument {
    dictionaries = dictionaries == null ? List.of() : List.copyOf(dictionaries);
    metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
  }

  public record DictionaryDependency(
      String dictCode,
      String dictName,
      List<DictionaryItem> items) {

    public DictionaryDependency {
      items = items == null ? List.of() : List.copyOf(items);
    }
  }

  public record DictionaryItem(
      String label,
      String value,
      String color,
      int sortOrder) {}
}
```

### 13.9 TemplatePackagePort.java

该 Port 放入 `aiops-work-record`，避免市场模块直接操作模板仓库。

```java
package io.aegisops.workrecord.application.port;

public interface TemplatePackagePort {

  TemplateSnapshot exportPublished(
      String tenantId,
      String templateId);

  InstalledTemplate install(
      String tenantId,
      InstallTemplateCommand command,
      String actorId);

  record TemplateSnapshot(
      String templateCode,
      String templateName,
      String description,
      String schemaJson,
      String designerJson) {}

  record InstallTemplateCommand(
      String templateCode,
      String templateName,
      String description,
      String schemaJson,
      String designerJson) {}

  record InstalledTemplate(
      String templateId,
      String templateVersionId) {}
}
```

### 13.10 TemplateMarketService.java

```java
package io.aegisops.workrecord.extension.application.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.aegisops.common.id.Ids;
import io.aegisops.security.UserPrincipal;
import io.aegisops.workrecord.application.port.TemplatePackagePort;
import io.aegisops.workrecord.extension.application.model.MarketPackageDocument;
import io.aegisops.workrecord.extension.application.port.TemplateMarketRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TemplateMarketService {

  private final TemplateMarketRepository repository;
  private final TemplatePackagePort templates;
  private final MarketDictionaryService dictionaries;
  private final ObjectMapper objectMapper;

  public TemplateMarketService(
      TemplateMarketRepository repository,
      TemplatePackagePort templates,
      MarketDictionaryService dictionaries,
      ObjectMapper objectMapper) {
    this.repository = repository;
    this.templates = templates;
    this.dictionaries = dictionaries;
    this.objectMapper = objectMapper;
  }

  @Transactional
  public String publish(
      String tenantId,
      PublishPackage command,
      UserPrincipal user) {
    require(user, "work-record:market:publish");
    TemplatePackagePort.TemplateSnapshot snapshot =
        templates.exportPublished(tenantId, command.templateId());
    MarketPackageDocument document =
        new MarketPackageDocument(
            1,
            command.packageCode(),
            command.name(),
            snapshot.description(),
            snapshot.templateCode(),
            snapshot.templateName(),
            snapshot.schemaJson(),
            snapshot.designerJson(),
            dictionaries.dependencies(tenantId, snapshot.schemaJson()),
            java.util.Map.of("sourceTemplateId", command.templateId()));
    String json = write(document);
    String checksum = sha256(json);
    return repository.publish(
        tenantId,
        command.packageCode(),
        command.name(),
        command.category(),
        command.visibility(),
        json,
        checksum,
        user.id());
  }

  @Transactional
  public TemplatePackagePort.InstalledTemplate install(
      String tenantId,
      String packageVersionId,
      String targetTemplateCode,
      UserPrincipal user) {
    require(user, "work-record:market:install");
    var version = repository.requireVisibleVersion(tenantId, packageVersionId);
    verifyChecksum(version.packageJson(), version.checksum());
    MarketPackageDocument document = read(version.packageJson());
    dictionaries.installDependencies(tenantId, document.dictionaries(), user.id());
    TemplatePackagePort.InstalledTemplate installed =
        templates.install(
            tenantId,
            new TemplatePackagePort.InstallTemplateCommand(
                targetTemplateCode,
                document.templateName(),
                document.description(),
                document.schemaJson(),
                document.designerJson()),
            user.id());
    repository.recordInstall(
        Ids.newId(),
        tenantId,
        version.packageId(),
        version.id(),
        installed.templateId(),
        user.id());
    return installed;
  }

  private void require(UserPrincipal user, String permission) {
    if (user == null || !user.hasPermission(permission)) {
      throw new AccessDeniedException("template market permission denied");
    }
  }

  private String write(Object value) {
    try {
      return objectMapper.writeValueAsString(value);
    } catch (Exception ex) {
      throw new IllegalStateException("failed to serialize market package", ex);
    }
  }

  private MarketPackageDocument read(String value) {
    try {
      return objectMapper.readValue(value, MarketPackageDocument.class);
    } catch (Exception ex) {
      throw new IllegalStateException("invalid market package", ex);
    }
  }

  private void verifyChecksum(String json, String checksum) {
    if (!sha256(json).equals(checksum)) {
      throw new IllegalStateException("market package checksum mismatch");
    }
  }

  private String sha256(String value) {
    try {
      return HexFormat.of().formatHex(
          MessageDigest.getInstance("SHA-256")
              .digest(value.getBytes(StandardCharsets.UTF_8)));
    } catch (Exception ex) {
      throw new IllegalStateException("SHA-256 unavailable", ex);
    }
  }

  public record PublishPackage(
      String templateId,
      String packageCode,
      String name,
      String category,
      String visibility) {}
}
```

### 13.11 FieldPolicyServiceTest.java

```java
package io.aegisops.workrecord.extension.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.aegisops.workrecord.extension.application.port.FieldPolicyRepository;
import io.aegisops.workrecord.extension.domain.FieldPolicy;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class FieldPolicyServiceTest {

  @Test
  void hiddenFieldIsRemovedFromReadableJson() {
    FieldPolicyRepository repository = Mockito.mock(FieldPolicyRepository.class);
    when(repository.listByVersion("t1", "v1"))
        .thenReturn(
            List.of(
                new FieldPolicy(
                    "v1",
                    "salary",
                    List.of("record_admin"),
                    List.of("record_admin"),
                    FieldPolicy.MaskMode.FULL)));
    FieldPolicyService service =
        new FieldPolicyService(repository, new ObjectMapper());

    String visible =
        service.filterReadableJson(
            "t1",
            "v1",
            "{\"salary\":10000,\"summary\":\"done\"}",
            TestPrincipals.normalUser());

    assertThat(visible).doesNotContain("salary").contains("summary");
  }

  @Test
  void unauthorizedRoleCannotModifyField() {
    FieldPolicyRepository repository = Mockito.mock(FieldPolicyRepository.class);
    when(repository.listByVersion("t1", "v1"))
        .thenReturn(
            List.of(
                new FieldPolicy(
                    "v1",
                    "salary",
                    List.of(),
                    List.of("system_admin"),
                    FieldPolicy.MaskMode.NONE)));
    FieldPolicyService service =
        new FieldPolicyService(repository, new ObjectMapper());

    assertThatThrownBy(
            () ->
                service.requireWritablePatch(
                    "t1",
                    "v1",
                    "{\"salary\":10000}",
                    "{\"salary\":20000}",
                    TestPrincipals.normalUser()))
        .isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
  }
}
```

### 13.12 TemplateMarketServiceTest.java

```java
@Test
void installRejectsTamperedPackageBeforeCreatingTemplate() {
  when(repository.requireVisibleVersion("t1", "pv1"))
      .thenReturn(TestMarketPackages.version("{\"name\":\"tampered\"}", "0".repeat(64)));

  assertThatThrownBy(
          () ->
              service.install(
                  "t1",
                  "pv1",
                  "installed_template",
                  TestPrincipals.marketInstaller()))
      .isInstanceOf(IllegalStateException.class)
      .hasMessageContaining("checksum");

  verifyNoInteractions(templates);
}
```

---

## 14. Phase 20.7：审批流与 SLA

### 14.1 记录状态扩展

审批引入后，记录状态扩展为：

```java
package io.aegisops.workrecord.domain.model;

public enum RecordStatus {
  DRAFT,
  PROCESSING,
  PENDING_APPROVAL,
  REJECTED,
  DONE,
  ARCHIVED;

  public String value() {
    return name().toLowerCase();
  }

  public static RecordStatus from(String value) {
    if (value == null || value.isBlank()) {
      return DRAFT;
    }
    return valueOf(value.trim().toUpperCase());
  }
}
```

`V0033` 需要同时修正记录状态约束；若当前表没有状态 CHECK，则只更新前后端枚举：

```sql
alter table work_record.wr_record
    drop constraint if exists ck_wr_record_status;

alter table work_record.wr_record
    add constraint ck_wr_record_status
    check (
        status in (
            'draft',
            'processing',
            'pending_approval',
            'rejected',
            'done',
            'archived'
        )
    ) not valid;
```

### 14.2 WorkRecordLifecycleExtension.java

放入 `aiops-work-record`：

```java
package io.aegisops.workrecord.application.port;

import io.aegisops.workrecord.domain.model.RecordStatus;
import io.aegisops.workrecord.domain.model.WorkRecord;

public interface WorkRecordLifecycleExtension {

  default TransitionDecision beforeTransition(TransitionContext context) {
    return TransitionDecision.allow(context.targetStatus());
  }

  default void afterMutation(MutationEvent event) {}

  record TransitionContext(
      String tenantId,
      WorkRecord existing,
      RecordStatus targetStatus,
      String actorId,
      boolean trustedWorkflow) {}

  record TransitionDecision(
      RecordStatus effectiveStatus,
      String workflowReference) {

    public static TransitionDecision allow(RecordStatus target) {
      return new TransitionDecision(target, null);
    }

    public static TransitionDecision redirect(
        RecordStatus target,
        String workflowReference) {
      return new TransitionDecision(target, workflowReference);
    }
  }

  record MutationEvent(
      String tenantId,
      WorkRecord before,
      WorkRecord after,
      String actorId,
      String workflowReference) {}
}
```

### 14.3 WorkRecordWorkflowPort.java

```java
package io.aegisops.workrecord.application.port;

import io.aegisops.workrecord.domain.model.RecordStatus;
import io.aegisops.workrecord.domain.model.WorkRecord;

public interface WorkRecordWorkflowPort {

  WorkRecord transitionStatus(
      String tenantId,
      String recordId,
      RecordStatus expectedStatus,
      RecordStatus targetStatus,
      String actorId,
      String reason,
      String workflowReference);
}
```

核心实现必须：

```text
校验 expectedStatus
更新 row_version
写 before/after 审计
调用 afterMutation，但 trustedWorkflow=true，避免再次创建审批
```

### 14.4 WorkRecordService 接入 Hook

构造器新增：

```java
private final List<WorkRecordLifecycleExtension> lifecycleExtensions;
```

更新时状态决策：

```java
RecordStatus effectiveStatus = targetStatus;
String workflowReference = null;

if (targetStatus != existing.status()) {
  for (WorkRecordLifecycleExtension extension : lifecycleExtensions) {
    WorkRecordLifecycleExtension.TransitionDecision decision =
        extension.beforeTransition(
            new WorkRecordLifecycleExtension.TransitionContext(
                tenantId,
                existing,
                effectiveStatus,
                actorId(user),
                false));
    effectiveStatus = decision.effectiveStatus();
    if (decision.workflowReference() != null) {
      workflowReference = decision.workflowReference();
    }
  }
}
```

写入成功后：

```java
for (WorkRecordLifecycleExtension extension : lifecycleExtensions) {
  extension.afterMutation(
      new WorkRecordLifecycleExtension.MutationEvent(
          tenantId,
          existing,
          updated,
          actorId(user),
          workflowReference));
}
```

### 14.5 ApprovalRepository.java

```java
package io.aegisops.workrecord.extension.application.port;

import io.aegisops.workrecord.extension.domain.ApprovalDefinition;
import io.aegisops.workrecord.extension.domain.ApprovalInstance;
import io.aegisops.workrecord.extension.domain.ApprovalTask;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

public interface ApprovalRepository {

  Optional<ApprovalDefinition> enabledDefinition(
      String tenantId,
      String templateId,
      String triggerStatus);

  ApprovalInstance createInstance(
      String tenantId,
      String recordId,
      ApprovalDefinition definition,
      String actorId);

  List<ApprovalTask> createTasks(
      String tenantId,
      String instanceId,
      int stepNo,
      List<TaskAssignee> assignees,
      OffsetDateTime dueAt);

  Optional<ApprovalInstance> findInstance(
      String tenantId,
      String instanceId,
      boolean forUpdate);

  Optional<ApprovalTask> findTask(
      String tenantId,
      String taskId,
      boolean forUpdate);

  List<ApprovalTask> tasksForStep(
      String tenantId,
      String instanceId,
      int stepNo);

  boolean actTask(
      String tenantId,
      String taskId,
      String targetStatus,
      String actedBy,
      String comment);

  boolean advanceInstance(
      String tenantId,
      String instanceId,
      int expectedStep,
      int nextStep);

  boolean finishInstance(
      String tenantId,
      String instanceId,
      String status);

  record TaskAssignee(
      String assigneeType,
      String assigneeValue) {}
}
```

所有 `findInstance/findTask(forUpdate=true)` 的 JDBC 实现必须在事务内使用 `FOR UPDATE`，防止两名审批人同时推进实例。

### 14.6 ApprovalLifecycleExtension.java

```java
package io.aegisops.workrecord.extension.application.service;

import io.aegisops.workrecord.application.port.WorkRecordLifecycleExtension;
import io.aegisops.workrecord.domain.model.RecordStatus;
import io.aegisops.workrecord.extension.application.port.ApprovalRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class ApprovalLifecycleExtension implements WorkRecordLifecycleExtension {

  private final ApprovalRepository approvals;
  private final ApprovalAssigneeResolver assignees;

  public ApprovalLifecycleExtension(
      ApprovalRepository approvals,
      ApprovalAssigneeResolver assignees) {
    this.approvals = approvals;
    this.assignees = assignees;
  }

  @Override
  @Transactional
  public TransitionDecision beforeTransition(TransitionContext context) {
    if (context.trustedWorkflow() || context.targetStatus() != RecordStatus.DONE) {
      return TransitionDecision.allow(context.targetStatus());
    }

    var definition =
        approvals.enabledDefinition(
            context.tenantId(),
            context.existing().templateId(),
            RecordStatus.DONE.value());
    if (definition.isEmpty()) {
      return TransitionDecision.allow(RecordStatus.DONE);
    }

    var instance =
        approvals.createInstance(
            context.tenantId(),
            context.existing().id(),
            definition.get(),
            context.actorId());
    var step = definition.get().steps().stream()
        .filter(item -> item.stepNo() == 1)
        .findFirst()
        .orElseThrow(() -> new IllegalStateException("approval step 1 is missing"));
    approvals.createTasks(
        context.tenantId(),
        instance.id(),
        1,
        assignees.resolve(context.tenantId(), context.existing(), step),
        step.timeoutMinutes() == null
            ? null
            : java.time.OffsetDateTime.now().plusMinutes(step.timeoutMinutes()));
    return TransitionDecision.redirect(
        RecordStatus.PENDING_APPROVAL,
        instance.id());
  }
}
```

注意：实例创建发生在记录更新事务内。若记录更新失败，审批实例和任务必须一并回滚。

### 14.7 ApprovalService.java

```java
package io.aegisops.workrecord.extension.application.service;

import io.aegisops.security.UserPrincipal;
import io.aegisops.workrecord.application.port.WorkRecordWorkflowPort;
import io.aegisops.workrecord.domain.model.RecordStatus;
import io.aegisops.workrecord.extension.application.port.ApprovalRepository;
import io.aegisops.workrecord.extension.domain.ApprovalInstance;
import io.aegisops.workrecord.extension.domain.ApprovalTask;
import java.util.List;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ApprovalService {

  private final ApprovalRepository repository;
  private final ApprovalAssigneeResolver assignees;
  private final WorkRecordWorkflowPort records;

  public ApprovalService(
      ApprovalRepository repository,
      ApprovalAssigneeResolver assignees,
      WorkRecordWorkflowPort records) {
    this.repository = repository;
    this.assignees = assignees;
    this.records = records;
  }

  @Transactional
  public ApprovalInstance act(
      String tenantId,
      String taskId,
      ApprovalAction action,
      UserPrincipal user) {
    ApprovalTask task =
        repository.findTask(tenantId, taskId, true)
            .orElseThrow(() -> new IllegalArgumentException("approval task not found"));
    ApprovalInstance instance =
        repository.findInstance(tenantId, task.instanceId(), true)
            .orElseThrow(() -> new IllegalArgumentException("approval instance not found"));
    if (!"pending".equals(task.status()) || !"pending".equals(instance.status())) {
      throw new IllegalStateException("approval task is already finished");
    }
    if (!assignees.canAct(tenantId, task, user)) {
      throw new AccessDeniedException("not assigned to this approval task");
    }

    String target = action.approved() ? "approved" : "rejected";
    if (!repository.actTask(
        tenantId,
        taskId,
        target,
        user.id(),
        normalizeComment(action.comment()))) {
      throw new IllegalStateException("approval task state changed");
    }

    if (!action.approved()) {
      repository.finishInstance(tenantId, instance.id(), "rejected");
      records.transitionStatus(
          tenantId,
          instance.recordId(),
          RecordStatus.PENDING_APPROVAL,
          RecordStatus.REJECTED,
          user.id(),
          "approval rejected",
          instance.id());
      return repository.findInstance(tenantId, instance.id(), false).orElseThrow();
    }

    List<ApprovalTask> currentTasks =
        repository.tasksForStep(tenantId, instance.id(), instance.currentStepNo());
    if (!stepApproved(currentTasks, instance.definition().step(instance.currentStepNo()).approvalMode())) {
      return instance;
    }

    var nextStep = instance.definition().nextStep(instance.currentStepNo());
    if (nextStep.isEmpty()) {
      repository.finishInstance(tenantId, instance.id(), "approved");
      records.transitionStatus(
          tenantId,
          instance.recordId(),
          RecordStatus.PENDING_APPROVAL,
          RecordStatus.DONE,
          user.id(),
          "approval completed",
          instance.id());
    } else {
      repository.advanceInstance(
          tenantId,
          instance.id(),
          instance.currentStepNo(),
          nextStep.get().stepNo());
      repository.createTasks(
          tenantId,
          instance.id(),
          nextStep.get().stepNo(),
          assignees.resolve(tenantId, instance.recordSnapshot(), nextStep.get()),
          nextStep.get().timeoutMinutes() == null
              ? null
              : java.time.OffsetDateTime.now().plusMinutes(nextStep.get().timeoutMinutes()));
    }
    return repository.findInstance(tenantId, instance.id(), false).orElseThrow();
  }

  private boolean stepApproved(List<ApprovalTask> tasks, String mode) {
    if ("all".equals(mode)) {
      return !tasks.isEmpty() && tasks.stream().allMatch(task -> "approved".equals(task.status()));
    }
    return tasks.stream().anyMatch(task -> "approved".equals(task.status()));
  }

  private String normalizeComment(String value) {
    if (value == null) {
      return null;
    }
    String result = value.trim();
    if (result.length() > 1000) {
      throw new IllegalArgumentException("approval comment is too long");
    }
    return result.isEmpty() ? null : result;
  }

  public record ApprovalAction(
      boolean approved,
      String comment) {}
}
```

### 14.8 SLA Port 与时间计算

`WorkRecordCalendarPort` 增加：

```java
OffsetDateTime addWorkingMinutes(
    String tenantId,
    OffsetDateTime start,
    int minutes);
```

非 calendar-aware SLA 直接 `start.plusMinutes(targetMinutes)`；calendar-aware SLA 必须按企业日历、工作时间窗口计算，节假日和非工作时间不累计。

### 14.9 SlaLifecycleExtension.java

```java
package io.aegisops.workrecord.extension.application.service;

import io.aegisops.workrecord.application.port.WorkRecordCalendarPort;
import io.aegisops.workrecord.application.port.WorkRecordLifecycleExtension;
import io.aegisops.workrecord.domain.model.RecordStatus;
import io.aegisops.workrecord.extension.application.port.SlaRepository;
import java.time.OffsetDateTime;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class SlaLifecycleExtension implements WorkRecordLifecycleExtension {

  private final SlaRepository repository;
  private final WorkRecordCalendarPort calendar;

  public SlaLifecycleExtension(
      SlaRepository repository,
      WorkRecordCalendarPort calendar) {
    this.repository = repository;
    this.calendar = calendar;
  }

  @Override
  @Transactional
  public void afterMutation(MutationEvent event) {
    if (event.after() == null) {
      return;
    }
    if (event.before() == null) {
      startPolicies(event);
      return;
    }
    if (event.before().status() != event.after().status()) {
      startPolicies(event);
      stopPolicies(event);
    }
  }

  private void startPolicies(MutationEvent event) {
    for (var policy : repository.enabledPolicies(
        event.tenantId(), event.after().templateId())) {
      if (!matches(policy.startEvent(), event.after().status())) {
        continue;
      }
      OffsetDateTime startedAt = OffsetDateTime.now();
      OffsetDateTime dueAt =
          policy.calendarAware()
              ? calendar.addWorkingMinutes(
                  event.tenantId(), startedAt, policy.targetMinutes())
              : startedAt.plusMinutes(policy.targetMinutes());
      repository.startIfAbsent(
          event.tenantId(),
          event.after().id(),
          policy,
          startedAt,
          dueAt);
    }
  }

  private void stopPolicies(MutationEvent event) {
    for (var instance : repository.runningByRecord(
        event.tenantId(), event.after().id())) {
      if (matches(instance.policy().stopEvent(), event.after().status())) {
        repository.stop(
            event.tenantId(),
            instance.id(),
            OffsetDateTime.now());
      }
    }
  }

  private boolean matches(String event, RecordStatus status) {
    return event != null && event.equals(status.value());
  }
}
```

### 14.10 SlaBreachScheduler.java

```java
package io.aegisops.worker.job;

import io.aegisops.workrecord.extension.application.service.SlaBreachService;
import java.time.Clock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class SlaBreachScheduler {

  private final SlaBreachService service;
  private final Clock clock;

  public SlaBreachScheduler(
      SlaBreachService service,
      Clock clock) {
    this.service = service;
    this.clock = clock;
  }

  @Scheduled(fixedDelayString = "${aiops.work-record.sla-scan-ms:60000}")
  public void scan() {
    service.markBreached(clock.instant(), 500);
  }
}
```

### 14.11 SlaBreachService.java

```java
package io.aegisops.workrecord.extension.application.service;

import io.aegisops.workrecord.extension.application.port.SlaRepository;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SlaBreachService {

  private final SlaRepository repository;
  private final NotificationService notifications;

  public SlaBreachService(
      SlaRepository repository,
      NotificationService notifications) {
    this.repository = repository;
    this.notifications = notifications;
  }

  @Transactional
  public int markBreached(Instant now, int limit) {
    OffsetDateTime current = OffsetDateTime.ofInstant(now, ZoneOffset.UTC);
    int count = 0;
    for (var instance : repository.claimDue(current, limit)) {
      if (repository.markBreached(
          instance.tenantId(), instance.id(), current)) {
        notifications.createSlaBreach(instance);
        count++;
      }
    }
    return count;
  }
}
```

`claimDue` 使用：

```sql
select ...
from work_record.wr_sla_instance
where status='running' and due_at<=:now
order by due_at, id
for update skip locked
limit :limit
```

### 14.12 ApprovalServiceTest.java

```java
@Test
void twoApproversCannotFinishSameTaskTwice() {
  when(repository.findTask("t1", "task1", true)).thenReturn(Optional.of(pendingTask));
  when(repository.findInstance("t1", "i1", true)).thenReturn(Optional.of(instance));
  when(assignees.canAct("t1", pendingTask, approver)).thenReturn(true);
  when(repository.actTask("t1", "task1", "approved", "u1", null))
      .thenReturn(false);

  assertThatThrownBy(
          () ->
              service.act(
                  "t1",
                  "task1",
                  new ApprovalService.ApprovalAction(true, null),
                  approver))
      .isInstanceOf(IllegalStateException.class)
      .hasMessageContaining("state changed");

  verifyNoInteractions(records);
}
```

### 14.13 ApprovalLifecycleExtensionTest.java

```java
@Test
void transitionToDoneIsRedirectedToPendingApproval() {
  when(repository.enabledDefinition("t1", "tpl1", "done"))
      .thenReturn(Optional.of(definition));
  when(repository.createInstance("t1", "r1", definition, "u1"))
      .thenReturn(instance);

  var decision =
      extension.beforeTransition(
          new WorkRecordLifecycleExtension.TransitionContext(
              "t1",
              record,
              RecordStatus.DONE,
              "u1",
              false));

  assertThat(decision.effectiveStatus()).isEqualTo(RecordStatus.PENDING_APPROVAL);
  assertThat(decision.workflowReference()).isEqualTo(instance.id());
}
```

### 14.14 SlaBreachServiceTest.java

```java
@Test
void sameSlaInstanceOnlyCreatesOneBreachNotification() {
  when(repository.claimDue(any(), eq(100))).thenReturn(List.of(instance));
  when(repository.markBreached(eq("t1"), eq("sla1"), any())).thenReturn(true, false);

  service.markBreached(Instant.parse("2026-07-11T10:00:00Z"), 100);
  service.markBreached(Instant.parse("2026-07-11T10:01:00Z"), 100);

  verify(notifications, times(1)).createSlaBreach(instance);
}
```

---

## 15. Phase 20.8：Portal 页面与交互

### 15.1 页面规划

```text
/work-records/:recordId
  基本信息
  动态字段
  评论
  附件
  关联对象
  变更历史
  AI 总结
  审批状态
  SLA 状态

/work-records/jobs
  我的导入/导出/AI 异步任务

/work-records/analytics
  统计报表
  工作量分析
  AI 月报

/work-records/handovers
  我的交接
  待接收
  已完成

/work-records/market
  模板市场
  已安装模板

/work-records/approvals
  待我审批
  我发起的审批
```

详情页保留现有 `DetailPageLayout`，新增内容放到 `RecordExtensionPanel`；不修改动态字段只读渲染器。

### 15.2 extension-types.ts

```ts
import { z } from 'zod'

export const commentSchema = z.object({
  id: z.string(),
  tenantId: z.string(),
  recordId: z.string(),
  content: z.string(),
  mentionUserIds: z.array(z.string()).default([]),
  createdBy: z.string(),
  createdAt: z.string(),
  updatedAt: z.string(),
  rowVersion: z.number().int(),
})

export const attachmentSchema = z.object({
  id: z.string(),
  recordId: z.string(),
  fileName: z.string(),
  contentType: z.string(),
  sizeBytes: z.number(),
  sha256: z.string(),
  uploadedBy: z.string(),
  createdAt: z.string(),
})

export const relationSchema = z.object({
  id: z.string(),
  recordId: z.string(),
  relationType: z.enum(['ALERT', 'INSPECTION', 'INCIDENT']),
  targetId: z.string(),
  targetTitle: z.string().nullable(),
  targetStatus: z.string().nullable(),
  snapshotJson: z.string(),
  createdAt: z.string(),
})

export const asyncJobSchema = z.object({
  id: z.string(),
  jobType: z.enum(['RECORD_IMPORT', 'RECORD_EXPORT', 'AI_RECORD_SUMMARY', 'AI_MONTHLY_REPORT']),
  status: z.enum(['QUEUED', 'RUNNING', 'SUCCEEDED', 'PARTIAL_SUCCESS', 'FAILED', 'CANCELLED']),
  requestedBy: z.string(),
  totalCount: z.number().int(),
  processedCount: z.number().int(),
  successCount: z.number().int(),
  failureCount: z.number().int(),
  resultFileName: z.string().nullable(),
  errorMessage: z.string().nullable(),
  createdAt: z.string(),
  startedAt: z.string().nullable(),
  finishedAt: z.string().nullable(),
})

export const aiGenerationSchema = z.object({
  id: z.string(),
  generationType: z.enum(['record_summary', 'monthly_report']),
  resourceType: z.enum(['record', 'tenant_month']),
  resourceId: z.string(),
  status: z.enum(['queued', 'running', 'success', 'failed', 'accepted', 'rejected']),
  outputMarkdown: z.string().nullable(),
  provider: z.string().nullable(),
  model: z.string().nullable(),
  requestedBy: z.string(),
  reviewedBy: z.string().nullable(),
  createdAt: z.string(),
})

export const approvalTaskSchema = z.object({
  id: z.string(),
  instanceId: z.string(),
  stepNo: z.number().int(),
  status: z.string(),
  assigneeType: z.string(),
  assigneeValue: z.string(),
  dueAt: z.string().nullable(),
  createdAt: z.string(),
})

export const slaInstanceSchema = z.object({
  id: z.string(),
  recordId: z.string(),
  status: z.enum(['running', 'met', 'breached', 'cancelled']),
  startedAt: z.string(),
  dueAt: z.string(),
  stoppedAt: z.string().nullable(),
  breachedAt: z.string().nullable(),
  severity: z.enum(['info', 'warning', 'critical']),
})

export const statisticsSchema = z.object({
  totalRecords: z.number(),
  completedRecords: z.number(),
  distinctOwners: z.number(),
  series: z.array(
    z.object({
      key: z.string(),
      label: z.string(),
      count: z.number(),
      value: z.number().nullable(),
    })
  ),
  fieldAggregate: z
    .object({
      fieldCode: z.string(),
      sum: z.number().nullable(),
      average: z.number().nullable(),
      minimum: z.number().nullable(),
      maximum: z.number().nullable(),
      valueCount: z.number(),
    })
    .nullable(),
})

export type WorkRecordComment = z.infer<typeof commentSchema>
export type WorkRecordAttachment = z.infer<typeof attachmentSchema>
export type RecordRelation = z.infer<typeof relationSchema>
export type AsyncJob = z.infer<typeof asyncJobSchema>
export type AiGeneration = z.infer<typeof aiGenerationSchema>
export type ApprovalTask = z.infer<typeof approvalTaskSchema>
export type SlaInstance = z.infer<typeof slaInstanceSchema>
export type StatisticsResult = z.infer<typeof statisticsSchema>
```

### 15.3 extension-api.ts

```ts
import { z } from 'zod'
import { request } from '@/lib/request'
import {
  aiGenerationSchema,
  asyncJobSchema,
  attachmentSchema,
  commentSchema,
  relationSchema,
  slaInstanceSchema,
  statisticsSchema,
} from './extension-types'

const unwrap = <T>(schema: z.ZodType<T>, value: unknown): T => {
  const envelope = z.object({ success: z.boolean(), data: schema }).parse(value)
  return envelope.data
}

export async function listComments(recordId: string) {
  const value = await request(`/api/work-record/records/${recordId}/comments`)
  return unwrap(z.array(commentSchema), value)
}

export async function createComment(
  recordId: string,
  input: { content: string; mentionUserIds: string[] }
) {
  const value = await request(`/api/work-record/records/${recordId}/comments`, {
    method: 'POST',
    body: JSON.stringify(input),
  })
  return unwrap(commentSchema, value)
}

export async function deleteComment(recordId: string, commentId: string, rowVersion: number) {
  await request(
    `/api/work-record/records/${recordId}/comments/${commentId}?rowVersion=${rowVersion}`,
    { method: 'DELETE' }
  )
}

export async function listAttachments(recordId: string) {
  const value = await request(`/api/work-record/records/${recordId}/attachments`)
  return unwrap(z.array(attachmentSchema), value)
}

export async function uploadAttachment(recordId: string, file: File) {
  const body = new FormData()
  body.append('file', file)
  const value = await request(`/api/work-record/records/${recordId}/attachments`, {
    method: 'POST',
    body,
    omitContentType: true,
  })
  return unwrap(attachmentSchema, value)
}

export async function attachmentDownloadUrl(attachmentId: string) {
  const value = await request(`/api/work-record/attachments/${attachmentId}/download-url`)
  return unwrap(z.object({ url: z.string().url(), fileName: z.string() }), value)
}

export async function listRelations(recordId: string) {
  const value = await request(`/api/work-record/records/${recordId}/relations`)
  return unwrap(z.array(relationSchema), value)
}

export async function createRelation(
  recordId: string,
  input: { relationType: string; targetId: string }
) {
  const value = await request(`/api/work-record/records/${recordId}/relations`, {
    method: 'POST',
    body: JSON.stringify(input),
  })
  return unwrap(relationSchema, value)
}

export async function submitImport(input: {
  file: File
  templateId: string
  templateVersionId: string
  defaultStatus: string
  stopOnError: boolean
}) {
  const body = new FormData()
  body.append('file', input.file)
  body.append('templateId', input.templateId)
  body.append('templateVersionId', input.templateVersionId)
  body.append('defaultStatus', input.defaultStatus)
  body.append('stopOnError', String(input.stopOnError))
  const value = await request('/api/work-record/imports', {
    method: 'POST',
    body,
    omitContentType: true,
  })
  return unwrap(z.object({ jobId: z.string() }), value)
}

export async function submitAsyncExport(input: unknown) {
  const value = await request('/api/work-record/jobs/exports', {
    method: 'POST',
    body: JSON.stringify(input),
  })
  return unwrap(z.object({ jobId: z.string() }), value)
}

export async function listAsyncJobs() {
  const value = await request('/api/work-record/jobs')
  return unwrap(z.array(asyncJobSchema), value)
}

export async function asyncJobDownloadUrl(jobId: string) {
  const value = await request(`/api/work-record/jobs/${jobId}/download-url`)
  return unwrap(z.object({ url: z.string().url(), fileName: z.string() }), value)
}

export async function requestRecordAiSummary(recordId: string) {
  const value = await request(`/api/work-record/ai/records/${recordId}/summary`, {
    method: 'POST',
  })
  return unwrap(aiGenerationSchema, value)
}

export async function listRecordAiGenerations(recordId: string) {
  const value = await request(`/api/work-record/ai/records/${recordId}`)
  return unwrap(z.array(aiGenerationSchema), value)
}

export async function reviewAiGeneration(id: string, accepted: boolean) {
  const value = await request(`/api/work-record/ai/${id}/review`, {
    method: 'POST',
    body: JSON.stringify({ accepted }),
  })
  return unwrap(aiGenerationSchema, value)
}

export async function recordSla(recordId: string) {
  const value = await request(`/api/work-record/records/${recordId}/sla`)
  return unwrap(z.array(slaInstanceSchema), value)
}

export async function statistics(search: URLSearchParams) {
  const value = await request(`/api/work-record/analytics/statistics?${search}`)
  return unwrap(statisticsSchema, value)
}
```

`request()` 需要支持 `FormData`：当 `omitContentType=true` 时，不手工设置 `Content-Type`，由浏览器写入 multipart boundary。

### 15.4 RecordExtensionPanel.tsx

```tsx
import { useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/components/ui/tabs'
import { ErrorState, TableLoadingState } from '@/components/feedback/async-state'
import {
  listAttachments,
  listComments,
  listRecordAiGenerations,
  listRelations,
  recordSla,
} from './extension-api'
import { AttachmentPanel } from './record-attachment-panel'
import { CommentTimeline } from './record-comment-timeline'
import { RecordAiPanel } from './record-ai-panel'
import { RecordRelationPanel } from './record-relation-panel'
import { RecordSlaPanel } from './record-sla-panel'

export function RecordExtensionPanel({ recordId }: { recordId: string }) {
  const [tab, setTab] = useState('comments')
  const comments = useQuery({
    queryKey: ['work-record-comments', recordId],
    queryFn: () => listComments(recordId),
  })
  const attachments = useQuery({
    queryKey: ['work-record-attachments', recordId],
    queryFn: () => listAttachments(recordId),
    enabled: tab === 'attachments',
  })
  const relations = useQuery({
    queryKey: ['work-record-relations', recordId],
    queryFn: () => listRelations(recordId),
    enabled: tab === 'relations',
  })
  const ai = useQuery({
    queryKey: ['work-record-ai', recordId],
    queryFn: () => listRecordAiGenerations(recordId),
    enabled: tab === 'ai',
  })
  const sla = useQuery({
    queryKey: ['work-record-sla', recordId],
    queryFn: () => recordSla(recordId),
    enabled: tab === 'sla',
  })

  return (
    <Card>
      <CardHeader>
        <CardTitle>协作与流程</CardTitle>
      </CardHeader>
      <CardContent>
        <Tabs value={tab} onValueChange={setTab}>
          <TabsList className="flex h-auto flex-wrap justify-start">
            <TabsTrigger value="comments">评论</TabsTrigger>
            <TabsTrigger value="attachments">附件</TabsTrigger>
            <TabsTrigger value="relations">关联对象</TabsTrigger>
            <TabsTrigger value="ai">AI 总结</TabsTrigger>
            <TabsTrigger value="sla">SLA</TabsTrigger>
          </TabsList>

          <TabsContent value="comments">
            {comments.isLoading ? (
              <TableLoadingState columns={1} />
            ) : comments.error ? (
              <ErrorState error={comments.error} onRetry={() => comments.refetch()} />
            ) : (
              <CommentTimeline recordId={recordId} comments={comments.data ?? []} />
            )}
          </TabsContent>

          <TabsContent value="attachments">
            <AttachmentPanel
              recordId={recordId}
              attachments={attachments.data ?? []}
              loading={attachments.isLoading}
            />
          </TabsContent>

          <TabsContent value="relations">
            <RecordRelationPanel
              recordId={recordId}
              relations={relations.data ?? []}
              loading={relations.isLoading}
            />
          </TabsContent>

          <TabsContent value="ai">
            <RecordAiPanel recordId={recordId} generations={ai.data ?? []} loading={ai.isLoading} />
          </TabsContent>

          <TabsContent value="sla">
            <RecordSlaPanel items={sla.data ?? []} loading={sla.isLoading} />
          </TabsContent>
        </Tabs>
      </CardContent>
    </Card>
  )
}
```

在现有 `RecordReadonlyView` 的 `RecordHistoryCard` 前插入：

```tsx
<RecordExtensionPanel recordId={record.id} />
```

### 15.5 record-comment-timeline.tsx

```tsx
import { useState } from 'react'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { Button } from '@/components/ui/button'
import { Textarea } from '@/components/ui/textarea'
import { EmptyState } from '@/components/feedback/async-state'
import { toast } from '@/components/ui/use-toast'
import { createComment, deleteComment } from './extension-api'
import type { WorkRecordComment } from './extension-types'

export function CommentTimeline({
  recordId,
  comments,
}: {
  recordId: string
  comments: WorkRecordComment[]
}) {
  const [content, setContent] = useState('')
  const queryClient = useQueryClient()
  const refresh = () =>
    queryClient.invalidateQueries({ queryKey: ['work-record-comments', recordId] })

  const create = useMutation({
    mutationFn: () => createComment(recordId, { content: content.trim(), mentionUserIds: [] }),
    onSuccess: async () => {
      setContent('')
      await refresh()
      toast({ title: '评论已发布' })
    },
  })

  const remove = useMutation({
    mutationFn: (comment: WorkRecordComment) =>
      deleteComment(recordId, comment.id, comment.rowVersion),
    onSuccess: refresh,
  })

  return (
    <div className="grid gap-4">
      <div className="grid gap-2">
        <Textarea
          value={content}
          maxLength={4000}
          placeholder="输入评论，可在后续版本接入 @用户选择器"
          onChange={(event) => setContent(event.target.value)}
        />
        <div className="flex items-center justify-between text-xs text-muted-foreground">
          <span>{content.length}/4000</span>
          <Button disabled={!content.trim() || create.isPending} onClick={() => create.mutate()}>
            发布评论
          </Button>
        </div>
      </div>

      {comments.length === 0 ? (
        <EmptyState title="暂无评论" description="发布第一条协作评论。" />
      ) : (
        <ol className="grid gap-3">
          {comments.map((comment) => (
            <li key={comment.id} className="rounded-md border p-3">
              <div className="flex items-center justify-between gap-3">
                <span className="text-sm font-medium">{comment.createdBy}</span>
                <time className="text-xs text-muted-foreground">
                  {new Date(comment.createdAt).toLocaleString()}
                </time>
              </div>
              <p className="mt-2 whitespace-pre-wrap break-words text-sm">{comment.content}</p>
              <Button
                variant="ghost"
                size="sm"
                className="mt-2"
                onClick={() => remove.mutate(comment)}
              >
                删除
              </Button>
            </li>
          ))}
        </ol>
      )}
    </div>
  )
}
```

删除按钮最终必须套 `PermissionGate` 并结合当前用户是否是评论作者；后端仍是最终授权边界。

### 15.6 record-attachment-panel.tsx

```tsx
import { useRef } from 'react'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { Button } from '@/components/ui/button'
import { EmptyState, TableLoadingState } from '@/components/feedback/async-state'
import { attachmentDownloadUrl, uploadAttachment } from './extension-api'
import type { WorkRecordAttachment } from './extension-types'

export function AttachmentPanel({
  recordId,
  attachments,
  loading,
}: {
  recordId: string
  attachments: WorkRecordAttachment[]
  loading: boolean
}) {
  const inputRef = useRef<HTMLInputElement>(null)
  const queryClient = useQueryClient()
  const upload = useMutation({
    mutationFn: (file: File) => uploadAttachment(recordId, file),
    onSuccess: () =>
      queryClient.invalidateQueries({
        queryKey: ['work-record-attachments', recordId],
      }),
  })

  if (loading) return <TableLoadingState columns={3} />

  return (
    <div className="grid gap-3">
      <div>
        <input
          ref={inputRef}
          type="file"
          className="hidden"
          onChange={(event) => {
            const file = event.target.files?.[0]
            if (file) upload.mutate(file)
            event.target.value = ''
          }}
        />
        <Button onClick={() => inputRef.current?.click()} disabled={upload.isPending}>
          上传附件
        </Button>
        <span className="ml-3 text-xs text-muted-foreground">单文件最大 20 MiB</span>
      </div>

      {attachments.length === 0 ? (
        <EmptyState title="暂无附件" description="上传记录相关文档或截图。" />
      ) : (
        <ul className="divide-y rounded-md border">
          {attachments.map((item) => (
            <li key={item.id} className="flex items-center justify-between gap-4 p-3">
              <div className="min-w-0">
                <p className="truncate text-sm font-medium">{item.fileName}</p>
                <p className="text-xs text-muted-foreground">
                  {(item.sizeBytes / 1024).toFixed(1)} KiB · {item.contentType}
                </p>
              </div>
              <Button
                variant="outline"
                size="sm"
                onClick={async () => {
                  const download = await attachmentDownloadUrl(item.id)
                  window.location.assign(download.url)
                }}
              >
                下载
              </Button>
            </li>
          ))}
        </ul>
      )}
    </div>
  )
}
```

### 15.7 ImportDialog.tsx

```tsx
import { useState } from 'react'
import { useMutation } from '@tanstack/react-query'
import { Button } from '@/components/ui/button'
import {
  Dialog,
  DialogContent,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { submitImport } from './extension-api'

export function ImportDialog({
  open,
  templateId,
  templateVersionId,
  onOpenChange,
  onCreated,
}: {
  open: boolean
  templateId: string
  templateVersionId: string
  onOpenChange: (open: boolean) => void
  onCreated: (jobId: string) => void
}) {
  const [file, setFile] = useState<File | null>(null)
  const mutation = useMutation({
    mutationFn: () => {
      if (!file) throw new Error('请选择 Excel 文件')
      return submitImport({
        file,
        templateId,
        templateVersionId,
        defaultStatus: 'draft',
        stopOnError: false,
      })
    },
    onSuccess: ({ jobId }) => {
      setFile(null)
      onOpenChange(false)
      onCreated(jobId)
    },
  })

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>Excel 导入工作记录</DialogTitle>
        </DialogHeader>
        <div className="grid gap-2">
          <Label htmlFor="work-record-import-file">Excel 文件</Label>
          <Input
            id="work-record-import-file"
            type="file"
            accept=".xlsx,application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
            onChange={(event) => setFile(event.target.files?.[0] ?? null)}
          />
          <p className="text-xs text-muted-foreground">
            第一行为字段编码；必须包含 title 和 recordTime。
          </p>
        </div>
        <DialogFooter>
          <Button variant="outline" onClick={() => onOpenChange(false)}>
            取消
          </Button>
          <Button disabled={!file || mutation.isPending} onClick={() => mutation.mutate()}>
            创建导入任务
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}
```

### 15.8 JobCenterPage.tsx

```tsx
import { useQuery } from '@tanstack/react-query'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { EmptyState, ErrorState, TableLoadingState } from '@/components/feedback/async-state'
import { ResponsiveTable } from '@/components/layout/responsive-table'
import { asyncJobDownloadUrl, listAsyncJobs } from './extension-api'

export function JobCenterPage() {
  const jobs = useQuery({
    queryKey: ['work-record-jobs'],
    queryFn: listAsyncJobs,
    refetchInterval: (query) =>
      query.state.data?.some((item) => ['QUEUED', 'RUNNING'].includes(item.status)) ? 3000 : false,
  })

  return (
    <main className="grid gap-4 p-4 md:p-6">
      <header>
        <h1 className="text-2xl font-semibold">任务中心</h1>
        <p className="text-sm text-muted-foreground">查看导入、导出和 AI 生成进度。</p>
      </header>
      <Card>
        <CardHeader>
          <CardTitle>异步任务</CardTitle>
        </CardHeader>
        <CardContent>
          {jobs.isLoading ? (
            <TableLoadingState columns={6} />
          ) : jobs.error ? (
            <ErrorState error={jobs.error} onRetry={() => jobs.refetch()} />
          ) : (jobs.data?.length ?? 0) === 0 ? (
            <EmptyState title="暂无任务" description="导入或异步导出后会显示在这里。" />
          ) : (
            <ResponsiveTable>
              <table className="w-full text-sm">
                <thead>
                  <tr className="border-b text-left">
                    <th className="p-2">类型</th>
                    <th className="p-2">状态</th>
                    <th className="p-2">进度</th>
                    <th className="p-2">成功</th>
                    <th className="p-2">失败</th>
                    <th className="p-2">操作</th>
                  </tr>
                </thead>
                <tbody>
                  {jobs.data?.map((job) => (
                    <tr key={job.id} className="border-b">
                      <td className="p-2">{job.jobType}</td>
                      <td className="p-2">{job.status}</td>
                      <td className="p-2">
                        {job.processedCount}/{job.totalCount || '-'}
                      </td>
                      <td className="p-2">{job.successCount}</td>
                      <td className="p-2">{job.failureCount}</td>
                      <td className="p-2">
                        {['SUCCEEDED', 'PARTIAL_SUCCESS'].includes(job.status) &&
                        job.resultFileName ? (
                          <Button
                            size="sm"
                            variant="outline"
                            onClick={async () => {
                              const result = await asyncJobDownloadUrl(job.id)
                              window.location.assign(result.url)
                            }}
                          >
                            下载
                          </Button>
                        ) : null}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </ResponsiveTable>
          )}
        </CardContent>
      </Card>
    </main>
  )
}
```

### 15.9 AnalyticsPage.tsx

```tsx
import { useMemo, useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { Bar, BarChart, CartesianGrid, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Input } from '@/components/ui/input'
import { ErrorState, PageLoadingState } from '@/components/feedback/async-state'
import { statistics } from './extension-api'

export function AnalyticsPage() {
  const [from, setFrom] = useState(() => new Date().toISOString().slice(0, 7) + '-01')
  const [to, setTo] = useState(() => new Date().toISOString().slice(0, 10))
  const search = useMemo(() => {
    const value = new URLSearchParams()
    value.set('from', `${from}T00:00:00Z`)
    value.set('to', `${to}T23:59:59Z`)
    value.set('groupBy', 'day')
    return value
  }, [from, to])
  const query = useQuery({
    queryKey: ['work-record-statistics', search.toString()],
    queryFn: () => statistics(search),
  })

  if (query.isLoading) return <PageLoadingState />
  if (query.error) {
    return <ErrorState error={query.error} onRetry={() => query.refetch()} />
  }

  return (
    <main className="grid gap-4 p-4 md:p-6">
      <header>
        <h1 className="text-2xl font-semibold">工作记录统计</h1>
      </header>
      <div className="grid gap-3 sm:grid-cols-2">
        <Input type="date" value={from} onChange={(event) => setFrom(event.target.value)} />
        <Input type="date" value={to} onChange={(event) => setTo(event.target.value)} />
      </div>
      <div className="grid gap-4 md:grid-cols-3">
        <Metric title="记录数" value={query.data?.totalRecords ?? 0} />
        <Metric title="已完成" value={query.data?.completedRecords ?? 0} />
        <Metric title="负责人数量" value={query.data?.distinctOwners ?? 0} />
      </div>
      <Card>
        <CardHeader>
          <CardTitle>每日记录数量</CardTitle>
        </CardHeader>
        <CardContent className="h-80">
          <ResponsiveContainer width="100%" height="100%">
            <BarChart data={query.data?.series ?? []}>
              <CartesianGrid strokeDasharray="3 3" />
              <XAxis dataKey="label" />
              <YAxis allowDecimals={false} />
              <Tooltip />
              <Bar dataKey="count" />
            </BarChart>
          </ResponsiveContainer>
        </CardContent>
      </Card>
    </main>
  )
}

function Metric({ title, value }: { title: string; value: number }) {
  return (
    <Card>
      <CardHeader>
        <CardTitle className="text-sm">{title}</CardTitle>
      </CardHeader>
      <CardContent className="text-2xl font-semibold">{value}</CardContent>
    </Card>
  )
}
```

遵守当前前端测试/图表约束时，不在组件中写死颜色；由现有 CSS theme 控制。

### 15.10 路由

```tsx
// src/routes/_authenticated/work-records/jobs.tsx
import { createFileRoute } from '@tanstack/react-router'
import { requireAnyPermission } from '@/features/auth/permission'
import { JobCenterPage } from '@/features/work-records/extension/job-center-page'

export const Route = createFileRoute('/_authenticated/work-records/jobs')({
  beforeLoad: () => requireAnyPermission(['work-record:import', 'work-record:export:async']),
  component: JobCenterPage,
})
```

```tsx
// src/routes/_authenticated/work-records/analytics.tsx
import { createFileRoute } from '@tanstack/react-router'
import { requirePermission } from '@/features/auth/permission'
import { AnalyticsPage } from '@/features/work-records/extension/analytics-page'

export const Route = createFileRoute('/_authenticated/work-records/analytics')({
  beforeLoad: () => requirePermission('work-record:analytics'),
  component: AnalyticsPage,
})
```

其他页面同样以权限守卫注册：

```text
/work-records/handovers  -> work-record:handover
/work-records/market     -> work-record:market:install 或 publish
/work-records/approvals  -> work-record:approval:act
```

### 15.11 前端测试

#### record-comment-timeline.test.tsx

```tsx
import { describe, expect, it, vi } from 'vitest'
import { render } from 'vitest-browser-react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { CommentTimeline } from './record-comment-timeline'

vi.mock('./extension-api', () => ({
  createComment: vi.fn().mockResolvedValue({}),
  deleteComment: vi.fn().mockResolvedValue(undefined),
}))

describe('CommentTimeline', () => {
  it('renders empty state and disables blank submit', async () => {
    const client = new QueryClient()
    const screen = await render(
      <QueryClientProvider client={client}>
        <CommentTimeline recordId="r1" comments={[]} />
      </QueryClientProvider>
    )

    await expect.element(screen.getByText('暂无评论')).toBeVisible()
    await expect.element(screen.getByRole('button', { name: '发布评论' })).toBeDisabled()
  })

  it('renders existing comments', async () => {
    const client = new QueryClient()
    const screen = await render(
      <QueryClientProvider client={client}>
        <CommentTimeline
          recordId="r1"
          comments={[
            {
              id: 'c1',
              tenantId: 't1',
              recordId: 'r1',
              content: '已完成数据库升级',
              mentionUserIds: [],
              createdBy: 'u1',
              createdAt: '2026-07-11T10:00:00Z',
              updatedAt: '2026-07-11T10:00:00Z',
              rowVersion: 1,
            },
          ]}
        />
      </QueryClientProvider>
    )

    await expect.element(screen.getByText('已完成数据库升级')).toBeVisible()
  })
})
```

#### job-center-page.test.tsx

```tsx
import { describe, expect, it, vi } from 'vitest'
import { render } from 'vitest-browser-react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { JobCenterPage } from './job-center-page'

vi.mock('./extension-api', () => ({
  listAsyncJobs: vi.fn().mockResolvedValue([
    {
      id: 'j1',
      jobType: 'RECORD_EXPORT',
      status: 'RUNNING',
      requestedBy: 'u1',
      totalCount: 100,
      processedCount: 40,
      successCount: 40,
      failureCount: 0,
      resultFileName: null,
      errorMessage: null,
      createdAt: '2026-07-11T10:00:00Z',
      startedAt: '2026-07-11T10:00:01Z',
      finishedAt: null,
    },
  ]),
}))

describe('JobCenterPage', () => {
  it('shows async job progress', async () => {
    const client = new QueryClient({
      defaultOptions: { queries: { retry: false } },
    })
    const screen = await render(
      <QueryClientProvider client={client}>
        <JobCenterPage />
      </QueryClientProvider>
    )

    await expect.element(screen.getByText('RECORD_EXPORT')).toBeVisible()
    await expect.element(screen.getByText('40/100')).toBeVisible()
  })
})
```

#### import-dialog.test.tsx

```tsx
it('only accepts xlsx and creates import job', async () => {
  const screen = await render(
    <ImportDialog
      open
      templateId="tpl1"
      templateVersionId="v1"
      onOpenChange={() => undefined}
      onCreated={() => undefined}
    />
  )

  const input = screen.getByLabelText('Excel 文件')
  await input.upload(
    new File(['xlsx'], 'records.xlsx', {
      type: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',
    })
  )
  await expect.element(screen.getByRole('button', { name: '创建导入任务' })).toBeEnabled()
})
```

---

## 16. 补充领域模型与 Repository 契约

### 16.1 WorkRecordNotification.java

```java
package io.aegisops.workrecord.extension.domain;

import java.time.OffsetDateTime;

public record WorkRecordNotification(
    String id,
    String tenantId,
    String userId,
    String notificationType,
    String title,
    String content,
    String resourceType,
    String resourceId,
    String dedupeKey,
    OffsetDateTime createdAt,
    OffsetDateTime readAt) {}
```

### 16.2 WorkRecordHandover.java

```java
package io.aegisops.workrecord.extension.domain;

import java.time.OffsetDateTime;
import java.util.List;

public record WorkRecordHandover(
    String id,
    String tenantId,
    String fromUserId,
    String toUserId,
    OffsetDateTime shiftStart,
    OffsetDateTime shiftEnd,
    HandoverStatus status,
    String summary,
    List<String> recordIds,
    List<String> relationIds,
    String createdBy,
    OffsetDateTime createdAt,
    OffsetDateTime acceptedAt,
    OffsetDateTime completedAt,
    int rowVersion) {

  public WorkRecordHandover {
    recordIds = recordIds == null ? List.of() : List.copyOf(recordIds);
    relationIds = relationIds == null ? List.of() : List.copyOf(relationIds);
  }
}
```

### 16.3 AiGeneration.java

```java
package io.aegisops.workrecord.extension.domain;

import java.time.LocalDate;
import java.time.OffsetDateTime;

public record AiGeneration(
    String id,
    String tenantId,
    String generationType,
    String resourceType,
    String resourceId,
    LocalDate periodStart,
    LocalDate periodEnd,
    String status,
    String promptVersion,
    String inputHash,
    String inputJson,
    String outputMarkdown,
    String provider,
    String model,
    String requestedBy,
    String reviewedBy,
    OffsetDateTime reviewedAt,
    OffsetDateTime createdAt,
    OffsetDateTime finishedAt) {}
```

### 16.4 Approval 模型

```java
package io.aegisops.workrecord.extension.domain;

import io.aegisops.workrecord.domain.model.WorkRecord;
import java.util.List;
import java.util.Optional;

public record ApprovalDefinition(
    String id,
    String tenantId,
    String templateId,
    String name,
    int versionNo,
    String triggerStatus,
    boolean enabled,
    List<ApprovalStep> steps) {

  public ApprovalDefinition {
    steps = steps == null ? List.of() : List.copyOf(steps);
  }

  public ApprovalStep step(int stepNo) {
    return steps.stream()
        .filter(value -> value.stepNo() == stepNo)
        .findFirst()
        .orElseThrow(() -> new IllegalArgumentException("approval step not found"));
  }

  public Optional<ApprovalStep> nextStep(int current) {
    return steps.stream()
        .filter(value -> value.stepNo() > current)
        .min(java.util.Comparator.comparingInt(ApprovalStep::stepNo));
  }
}
```

```java
package io.aegisops.workrecord.extension.domain;

public record ApprovalStep(
    String id,
    int stepNo,
    String name,
    String approverType,
    String approverValue,
    String approvalMode,
    Integer timeoutMinutes) {}
```

```java
package io.aegisops.workrecord.extension.domain;

import io.aegisops.workrecord.domain.model.WorkRecord;
import java.time.OffsetDateTime;

public record ApprovalInstance(
    String id,
    String tenantId,
    String recordId,
    ApprovalDefinition definition,
    String status,
    int currentStepNo,
    String startedBy,
    OffsetDateTime startedAt,
    OffsetDateTime finishedAt,
    int rowVersion,
    WorkRecord recordSnapshot) {}
```

```java
package io.aegisops.workrecord.extension.domain;

import java.time.OffsetDateTime;

public record ApprovalTask(
    String id,
    String tenantId,
    String instanceId,
    int stepNo,
    String assigneeType,
    String assigneeValue,
    String status,
    String actedBy,
    String actionComment,
    OffsetDateTime dueAt,
    OffsetDateTime actedAt,
    OffsetDateTime createdAt) {}
```

### 16.5 SLA 模型

```java
package io.aegisops.workrecord.extension.domain;

public record SlaPolicy(
    String id,
    String tenantId,
    String templateId,
    String name,
    String startEvent,
    String stopEvent,
    int targetMinutes,
    boolean calendarAware,
    String severity,
    boolean enabled) {}
```

```java
package io.aegisops.workrecord.extension.domain;

import java.time.OffsetDateTime;

public record SlaInstance(
    String id,
    String tenantId,
    String recordId,
    SlaPolicy policy,
    String status,
    OffsetDateTime startedAt,
    OffsetDateTime dueAt,
    OffsetDateTime stoppedAt,
    OffsetDateTime breachedAt,
    Integer elapsedMinutes,
    int rowVersion) {}
```

### 16.6 RecordRelationRepository.java

```java
package io.aegisops.workrecord.extension.application.port;

import io.aegisops.workrecord.extension.domain.RecordRelation;
import io.aegisops.workrecord.extension.domain.RelationType;
import java.util.List;

public interface RecordRelationRepository {

  RecordRelation create(CreateRelation command);

  List<RecordRelation> list(String tenantId, String recordId);

  boolean delete(String tenantId, String recordId, String relationId);

  record CreateRelation(
      String id,
      String tenantId,
      String recordId,
      RelationType relationType,
      String targetId,
      String targetTitle,
      String targetStatus,
      String snapshotJson,
      String createdBy) {}
}
```

### 16.7 NotificationRepository.java

```java
package io.aegisops.workrecord.extension.application.port;

import io.aegisops.workrecord.extension.domain.WorkRecordNotification;
import java.util.List;

public interface NotificationRepository {

  boolean insertIfAbsent(WorkRecordNotification notification);

  List<WorkRecordNotification> listUnread(
      String tenantId,
      String userId,
      int limit);

  boolean markRead(
      String tenantId,
      String userId,
      String notificationId);
}
```

### 16.8 ReminderRepository.java

```java
package io.aegisops.workrecord.extension.application.port;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;

public interface ReminderRepository {

  List<ReminderRule> findDueRules(Instant now, int limit);

  boolean hasRecord(
      String tenantId,
      String templateId,
      String userId,
      LocalDate date,
      ZoneId zoneId);

  List<String> usersByRole(String tenantId, String roleCode);

  record ReminderRule(
      String id,
      String tenantId,
      String templateId,
      String targetType,
      String targetJson,
      LocalTime cutoffTime,
      String timeZone) {}
}
```

### 16.9 HandoverRepository.java

```java
package io.aegisops.workrecord.extension.application.port;

import io.aegisops.workrecord.extension.application.service.HandoverService.CreateHandover;
import io.aegisops.workrecord.extension.domain.HandoverStatus;
import io.aegisops.workrecord.extension.domain.WorkRecordHandover;
import java.util.List;
import java.util.Optional;

public interface HandoverRepository {

  WorkRecordHandover create(
      String tenantId,
      CreateHandover command,
      String actorId);

  Optional<WorkRecordHandover> find(String tenantId, String handoverId);

  List<WorkRecordHandover> listForUser(
      String tenantId,
      String userId,
      int limit);

  boolean transition(
      String tenantId,
      String handoverId,
      HandoverStatus expected,
      HandoverStatus target,
      int expectedVersion);
}
```

### 16.10 SlaRepository.java

```java
package io.aegisops.workrecord.extension.application.port;

import io.aegisops.workrecord.extension.domain.SlaInstance;
import io.aegisops.workrecord.extension.domain.SlaPolicy;
import java.time.OffsetDateTime;
import java.util.List;

public interface SlaRepository {

  List<SlaPolicy> enabledPolicies(
      String tenantId,
      String templateId);

  boolean startIfAbsent(
      String tenantId,
      String recordId,
      SlaPolicy policy,
      OffsetDateTime startedAt,
      OffsetDateTime dueAt);

  List<SlaInstance> runningByRecord(
      String tenantId,
      String recordId);

  boolean stop(
      String tenantId,
      String instanceId,
      OffsetDateTime stoppedAt);

  List<SlaInstance> claimDue(
      OffsetDateTime now,
      int limit);

  boolean markBreached(
      String tenantId,
      String instanceId,
      OffsetDateTime breachedAt);
}
```

### 16.11 JDBC 实现统一要求

上述 Repository 的 JDBC 实现遵守同一规则：

```text
1. 每个查询都有 tenant_id 条件。
2. 修改状态时带 expected status/row_version。
3. 多 Worker 抢占使用 FOR UPDATE SKIP LOCKED。
4. JSON 通过 ObjectMapper 序列化，不手拼用户内容。
5. 所有列表有固定上限和稳定 id 次级排序。
6. 删除采用软删除或明确状态迁移。
7. Repository 不做权限判断，权限在 Application Service 完成。
```

---

## 17. 测试体系

### 17.1 后端单元测试清单

```text
OutboxWriterTest
  延迟时间写入
  idempotencyKey 冲突复用
  payload 序列化失败回滚

JooqOutboxRepositoryTest
  claim 只选择 available_at<=now
  processing 租约超时可回收
  多线程 claim 不重复
  failure 指数退避

ExcelImportParserTest
  动态字段类型
  必填 title/recordTime
  未知列
  最大行数/列数
  boolean/date/datetime/multi_select

WorkRecordImportProcessorTest
  逐行领域校验
  stopOnError
  partial_success
  字典非法值
  历史模板版本

WorkRecordAsyncExportProcessorTest
  权限重新加载
  字段级权限
  大结果流式写 MinIO
  文件超限清理

CommentServiceTest
  记录不可见时拒绝
  仅作者或管理员删除
  mention 不能跨租户
  乐观锁

AttachmentServiceTest
  类型/大小限制
  数据库失败清理对象
  下载权限
  删除权限

RecordRelationServiceTest
  target 租户隔离
  alert/inspection/incident 权限
  重复关系冲突

StatisticsServiceTest
  statistical 白名单
  时间范围上限
  字段权限
  工作日数量

MissingDailyReminderServiceTest
  节假日跳过
  调休工作日提醒
  已填写不提醒
  dedupe

HandoverServiceTest
  状态机
  接收人权限
  记录可见性
  乐观锁

AiGenerationServiceTest
  inputHash 复用
  敏感字段裁剪
  人工接受/拒绝
  Agent 失败

TemplateMarketServiceTest
  checksum
  可见性
  字典依赖安装
  安装后模板独立

FieldPolicyServiceTest
  readRoles/writeRoles
  full/partial mask
  filter/export/AI 接入

ApprovalServiceTest
  any/all
  user/role/owner 审批人
  双击并发
  拒绝回写记录

SlaLifecycleExtensionTest
  start/stop event
  工作日历截止时间
  breach 幂等
```

### 17.2 PostgreSQL Testcontainers 集成测试

新增：

```text
Phase20MigrationPostgresIT
Phase20OutboxConcurrencyPostgresIT
WorkRecordImportPostgresIT
AsyncExportPostgresIT
CommentAttachmentRelationPostgresIT
StatisticsPostgresIT
ReminderDedupePostgresIT
ApprovalConcurrencyPostgresIT
SlaClaimPostgresIT
TemplateMarketInstallPostgresIT
FieldPolicyQueryPostgresIT
```

#### Phase20OutboxConcurrencyPostgresIT 核心测试

```java
@Test
void concurrentWorkersCannotClaimSameRows() throws Exception {
  insertPendingRows(100);
  ExecutorService executor = Executors.newFixedThreadPool(4);
  try {
    List<Future<List<String>>> futures =
        java.util.stream.IntStream.range(0, 4)
            .mapToObj(index ->
                executor.submit(() ->
                    repository.claimNextPending("worker", 25, Duration.ofMinutes(2))
                        .stream()
                        .map(AutomationOutboxRecord::getId)
                        .toList()))
            .toList();

    List<String> all = new ArrayList<>();
    for (Future<List<String>> future : futures) {
      all.addAll(future.get());
    }

    assertThat(all).hasSize(100);
    assertThat(new HashSet<>(all)).hasSize(100);
  } finally {
    executor.shutdownNow();
  }
}
```

#### FieldPolicyQueryPostgresIT 核心测试

```java
@Test
void forbiddenDynamicFieldCannotBeUsedAsSideChannelFilter() {
  var normalUser = TestPrincipals.normalUser();

  assertThatThrownBy(() ->
      queryService.page(
          "t1",
          TestQueries.filter("salary", "gte", 10000),
          normalUser))
      .isInstanceOf(AccessDeniedException.class);

  verifyNoInteractions(recordRepository);
}
```

### 17.3 ArchUnit

```java
package io.aegisops.workrecord.extension.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

@AnalyzeClasses(
    packages = "io.aegisops.workrecord.extension",
    importOptions = ImportOption.DoNotIncludeTests.class)
class WorkRecordExtensionArchitectureTest {

  @ArchTest
  static final ArchRule domainIsIndependent =
      noClasses()
          .that()
          .resideInAPackage("..domain..")
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage(
              "org.springframework..",
              "..application..",
              "..api..",
              "..infrastructure..");

  @ArchTest
  static final ArchRule applicationDoesNotDependOnAdapters =
      noClasses()
          .that()
          .resideInAPackage("..application..")
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage("..api..", "..infrastructure..");

  @ArchTest
  static final ArchRule apiDoesNotUseJdbc =
      noClasses()
          .that()
          .resideInAPackage("..api..")
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage("org.springframework.jdbc..", "java.sql..");
}
```

### 17.4 前端测试清单

```text
record-comment-timeline.test.tsx
record-attachment-panel.test.tsx
record-relation-panel.test.tsx
record-ai-panel.test.tsx
record-approval-panel.test.tsx
record-sla-panel.test.tsx
import-dialog.test.tsx
job-center-page.test.tsx
analytics-page.test.tsx
handover-page.test.tsx
template-market-page.test.tsx
field-policy-editor.test.tsx
```

必须验证：

```text
loading/empty/error
移动端卡片模式
权限按钮隐藏
导入文件限制
异步任务轮询停止
AI 结果必须审核
字段隐藏后 DOM 中不存在原值
审批并发错误提示
SLA breached 状态
```

### 17.5 E2E 企业场景

```text
1. 管理员发布带字段权限、审批和 SLA 的模板。
2. 普通用户通过 Excel 导入 3 条记录。
3. 1 条成功、1 条字典非法、1 条缺失必填，任务 partial_success。
4. 普通用户只能看自己的任务。
5. 记录添加评论、附件、告警、巡检、事件关联。
6. 受限字段普通用户看不到，管理员可见脱敏值。
7. 普通用户提交完成，记录进入 pending_approval。
8. 审批人通过后记录变为 done。
9. SLA 在截止前完成显示 met。
10. 另一条记录超时后显示 breached，并产生唯一通知。
11. 管理员查看统计与工作量。
12. 生成 AI 月报草稿，管理员接受。
13. 模板发布到市场，另一租户安装后得到独立模板。
14. 异步导出完成，通过 5 分钟预签名 URL 下载。
15. 节假日不发缺失提醒，调休工作日正常提醒。
16. 值班人员创建并完成交接。
17. 审计日志覆盖导入、评论、附件、关系、AI、审批、SLA、市场安装。
```

---

## 18. 实施顺序与提交边界

Phase 20 不应一次提交 17 项能力。推荐：

```text
20.0 phase20/outbox-foundation
  V0028
  Outbox availableAt/lease/idempotency
  AsyncJob/MinIO

20.1 phase20/import-export
  Excel 导入
  异步导出
  任务中心

20.2 phase20/collaboration
  评论
  附件
  关联告警/巡检/事件

20.4 phase20/analytics-reminder-handover
  统计报表
  工作量分析
  日报缺失提醒
  值班交接

20.5 phase20/ai-generation
  AI 自动总结
  AI 月报
  人工审核

20.6 phase20/market-field-policy
  Schema v2
  模板市场
  字段级权限

20.7 phase20/approval-sla
  审批流
  SLA

20.8 phase20-enterprise-e2e
  Portal 收口
  权限回归
  全链路 E2E
  性能和生产配置
```

每个子阶段都必须独立通过 Maven、Portal 和数据库迁移验证，禁止多个迁移阶段共享一个不可回滚的大提交。

---

## 19. 验收命令

```bash
mvn -B -ntp \
  -pl modules/aiops-common,modules/aiops-work-record,modules/aiops-work-record-extension,modules/aiops-security,modules/aiops-ai-client,apps/aiops-worker,apps/aiops-server \
  -am \
  verify
```

```bash
pnpm -C web/portal run typecheck
pnpm -C web/portal run lint
pnpm -C web/portal run test
pnpm -C web/portal run build
```

```bash
cd apps/aiops-agent
pytest
```

```bash
mvn -B -ntp \
  -pl modules/aiops-work-record-extension,apps/aiops-server \
  -am \
  -DskipITs=false \
  verify
```

生产前还必须执行：

```text
Phase 19 修复版通过
V0028~V0035 在空库和历史库均迁移成功
MinIO bucket 策略与生命周期配置完成
Worker 多实例 outbox 并发测试通过
字段权限无法通过筛选/导出/AI 侧信道绕过
AI 输出审核流程通过
附件恶意类型和超限测试通过
审批和 SLA 并发测试通过
```

---

## 20. 最终结论

Phase 20 最适合继续保持当前模块化单体：

```text
Server：HTTP、权限、领域事务、任务派发
Worker：异步导入/导出、提醒、AI、SLA 扫描
Agent：纯生成能力，不直接访问业务数据库
PostgreSQL：业务事实、任务状态、审批、SLA
MinIO：附件、导入源文件、导出结果
Portal：详情扩展、任务中心、报表、市场和审批页面
```

不需要因为 Phase 20 引入 Kafka、独立审批微服务、独立报表微服务或微前端。当前 `automation_outbox + worker + MinIO + 模块化单体` 足以支撑第一版企业增强能力；当异步任务达到高吞吐、多团队独立发布或跨区域部署时，再评估消息队列和服务拆分。
