---
title: Phase 20.0–20.1 共享底座、Outbox、异步任务与 MinIO
type: phase
status: draft
phase: work-record-20
owner: ai
created: 2026-07-14
updated: 2026-07-14
related: []
---

# Phase 20.0–20.1：共享底座、Outbox、异步任务与 MinIO

> 基线：`baicie/ai-ops`，分支 `feat/record-doc-portal`，提交 `43d16315cc4b68cc705177d2c8faba1d8e42644e`。
>
> 本文件由 Phase 20 总方案按主题拆分。Phase 20 开发前必须先完成 Phase 19 P0 修复。

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

web/portal/src/features/work-records/extensions/
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

## 数据库迁移

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
    return new ObjectStorageProperties(null, null, null, null, 0, 0);
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
