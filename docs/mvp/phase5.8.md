---
title: Phase5.8：Execution Report & Audit
type: design
status: accepted
phase: global
owner: ai
created: 2026-06-30
updated: 2026-06-30
related: []
---
# Phase5.8：Execution Report & Audit

> 这次严格按修正后的主路线来：
> **Phase5.8 = 执行审计增强与执行报告**。
> 不再继续做 Rollback，也不做 SSH，不新增任何真实执行能力。

---

# 0. 前置状态

按修正后的路线，当前应是：

```txt id="ppl9v3"
Phase5.0  Runbook Recommendation & AutomationPlan Draft      ✅
Phase5.1  Approval Workflow & Risk Policy                    ✅
Phase5.2  Runner Dry-run Execution Loop                      ✅
Phase5.3  Runner Reliability：lease / heartbeat / retry      ✅
Phase5.4  Webhook Adapter                                    ✅
Phase5.5  Ansible Adapter                                    ✅
Phase5.6  SSH Adapter                                        ⏳ 如果还没做，可以后置
Phase5.7  Rollback Plan                                      ✅ / 待修 d053230 P1
Phase5.8  Execution Report & Audit                           当前设计
```

注意：如果 `d053230` 的 3 个 P1 还没修，建议先修 Phase5.7 再落 Phase5.8。

---

# 1. Phase5.8 目标

Phase5.8 解决的是：

```txt id="qnrk6k"
执行完了之后，如何给用户、审计人员、后续复盘系统一个可信报告。
```

核心能力：

```txt id="m063kb"
1. execution_report：执行报告主表
2. execution_report_section：报告分段
3. execution_verification：执行前后验证记录
4. execution_audit_event：执行审计事件
5. artifact summary：执行产物摘要
6. Markdown 导出
7. rollback / normal execution 都能生成报告
```

---

# 2. 不做什么

Phase5.8 明确不做：

```txt id="32k9nb"
1. 不新增执行适配器
2. 不新增 SSH 能力
3. 不做自动回滚
4. 不让 AI 执行任何动作
5. 不修改 runner 执行模型
6. 不绕过 execution_run / execution_step / execution_artifact
```

---

# 3. 产品效果

Phase5.8 完成后，一个执行任务可以生成这样的报告：

```md id="dcsinq"
# Execution Report

## Summary

- Execution: exec_xxx
- Mode: live
- Kind: normal
- Status: succeeded
- Requested By: alice
- Started At: 2026-06-19T10:00:00+09:00
- Finished At: 2026-06-19T10:03:21+09:00

## Step Summary

| Order | Step            | Action  | Status    |
| ----- | --------------- | ------- | --------- |
| 1     | Restart service | webhook | succeeded |
| 2     | Verify service  | webhook | succeeded |

## Artifact Summary

| Step   | Artifact              | Type |
| ------ | --------------------- | ---- |
| step_1 | webhook-response.json | json |

## Verification

| Type   | Target        | Status | Summary               |
| ------ | ------------- | ------ | --------------------- |
| before | order-service | passed | service was unhealthy |
| after  | order-service | passed | service recovered     |

## Audit Timeline

- execution queued
- runner claimed
- step 1 succeeded
- report generated
```

---

# 4. API 设计

## 4.1 Report API

```txt id="eohct8"
POST /api/executions/{executionId}/reports/generate
GET  /api/executions/{executionId}/reports/latest
GET  /api/execution-reports/{reportId}
GET  /api/execution-reports/{reportId}/markdown
```

---

## 4.2 Verification API

```txt id="au46mi"
POST /api/executions/{executionId}/verifications
GET  /api/executions/{executionId}/verifications
```

---

## 4.3 Audit Event API

```txt id="w9oszy"
GET /api/executions/{executionId}/audit-events
```

审计事件由系统写入，不开放任意创建接口。

---

# 5. 数据库设计

## 5.1 Migration

路径：

```txt id="vw9ux0"
apps/aiops-server/src/main/resources/db/migration/V19__phase5_8_execution_report_audit.sql
```

```sql id="p76t9t"
-- Phase 5.8: Execution Report & Audit.
-- This phase does not add new execution capability.
-- It stores execution reports, verification records, and execution audit events.

create table if not exists execution_report (
  id varchar(64) primary key,
  tenant_id varchar(64) not null references tenant(id) on delete cascade,
  execution_id varchar(64) not null references execution_run(id) on delete cascade,
  report_type varchar(32) not null default 'standard',
  status varchar(32) not null default 'generated',
  title varchar(240) not null,
  summary text,
  markdown text not null,
  generated_by varchar(64) not null,
  generated_at timestamptz not null default now(),
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  constraint ck_execution_report_type
    check (report_type in ('standard', 'rollback', 'audit')),
  constraint ck_execution_report_status
    check (status in ('generated', 'superseded'))
);

create index if not exists idx_execution_report_execution
  on execution_report(tenant_id, execution_id, generated_at desc);

create table if not exists execution_report_section (
  id varchar(64) primary key,
  tenant_id varchar(64) not null references tenant(id) on delete cascade,
  report_id varchar(64) not null references execution_report(id) on delete cascade,
  section_order int not null,
  section_type varchar(64) not null,
  title varchar(240) not null,
  content text not null,
  metadata jsonb not null default '{}'::jsonb,
  created_at timestamptz not null default now(),
  constraint uq_execution_report_section_order
    unique (tenant_id, report_id, section_order)
);

create index if not exists idx_execution_report_section_report
  on execution_report_section(tenant_id, report_id, section_order);

create table if not exists execution_verification (
  id varchar(64) primary key,
  tenant_id varchar(64) not null references tenant(id) on delete cascade,
  execution_id varchar(64) not null references execution_run(id) on delete cascade,
  step_id varchar(64),
  verification_type varchar(32) not null,
  target_type varchar(64) not null,
  target_id varchar(128),
  status varchar(32) not null,
  summary text not null,
  details jsonb not null default '{}'::jsonb,
  created_by varchar(64) not null,
  created_at timestamptz not null default now(),
  constraint ck_execution_verification_type
    check (verification_type in ('before', 'after', 'manual', 'post')),
  constraint ck_execution_verification_status
    check (status in ('passed', 'failed', 'warn', 'skipped'))
);

create index if not exists idx_execution_verification_execution
  on execution_verification(tenant_id, execution_id, created_at);

create table if not exists execution_audit_event (
  id varchar(64) primary key,
  tenant_id varchar(64) not null references tenant(id) on delete cascade,
  execution_id varchar(64) not null references execution_run(id) on delete cascade,
  step_id varchar(64),
  event_type varchar(64) not null,
  actor varchar(64) not null,
  summary text not null,
  payload jsonb not null default '{}'::jsonb,
  created_at timestamptz not null default now()
);

create index if not exists idx_execution_audit_event_execution
  on execution_audit_event(tenant_id, execution_id, created_at);
```

---

# 6. jOOQ Codegen

路径：

```txt id="gpul78"
modules/aiops-persistence/src/main/resources/jooq-codegen.xml
```

`includes` 追加：

```txt id="shf7al"
execution_report | execution_report_section | execution_verification | execution_audit_event
```

完整 includes 建议：

```xml id="g3prms"
<includes>
  tenant | sys_user | sys_role | sys_permission | sys_user_role | sys_role_permission |
  datasource | datasource_sync_run | asset | asset_relation | alert_event | incident |
  incident_event | incident_timeline | audit_log | rca_analysis | ai_diagnosis |
  agent_run | agent_run_step | agent_eval_result | log_event | change_event |
  runbook | runbook_step_template | automation_plan | automation_plan_step |
  approval_policy | automation_approval | approval_decision |
  execution_run | execution_step | execution_artifact |
  webhook_connector | webhook_execution_policy |
  ansible_inventory | ansible_playbook | ansible_execution_policy | ansible_credential_ref |
  rollback_plan | rollback_plan_step | rollback_decision |
  execution_report | execution_report_section | execution_verification | execution_audit_event
</includes>
```

---

# 7. DTO 完整代码

## 7.1 `ExecutionReportGenerateRequest.java`

路径：

```txt id="bzaavv"
modules/aiops-execution/src/main/java/io/aegisops/execution/dto/ExecutionReportGenerateRequest.java
```

```java id="9c8mfk"
package io.aegisops.execution.dto;

public record ExecutionReportGenerateRequest(
    String reportType,
    String generatedBy,
    Boolean includeArtifacts,
    Boolean includeVerifications,
    Boolean includeAuditEvents) {}
```

---

## 7.2 `ExecutionReportCreateCommand.java`

```java id="hjr9t9"
package io.aegisops.execution.dto;

public record ExecutionReportCreateCommand(
    String id,
    String tenantId,
    String executionId,
    String reportType,
    String status,
    String title,
    String summary,
    String markdown,
    String generatedBy) {}
```

---

## 7.3 `ExecutionReportSectionCreateCommand.java`

```java id="5uauz5"
package io.aegisops.execution.dto;

public record ExecutionReportSectionCreateCommand(
    String id,
    String tenantId,
    String reportId,
    int sectionOrder,
    String sectionType,
    String title,
    String content,
    String metadataJson) {}
```

---

## 7.4 `ExecutionReportRecord.java`

```java id="wpu2w3"
package io.aegisops.execution.dto;

import java.time.OffsetDateTime;

public record ExecutionReportRecord(
    String id,
    String tenantId,
    String executionId,
    String reportType,
    String status,
    String title,
    String summary,
    String markdown,
    String generatedBy,
    OffsetDateTime generatedAt,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt) {}
```

---

## 7.5 `ExecutionReportSectionRecord.java`

```java id="yqaskc"
package io.aegisops.execution.dto;

import java.time.OffsetDateTime;

public record ExecutionReportSectionRecord(
    String id,
    String tenantId,
    String reportId,
    int sectionOrder,
    String sectionType,
    String title,
    String content,
    String metadataJson,
    OffsetDateTime createdAt) {}
```

---

## 7.6 `ExecutionReportResponse.java`

```java id="7z2y7k"
package io.aegisops.execution.dto;

import java.time.OffsetDateTime;
import java.util.List;

public record ExecutionReportResponse(
    String id,
    String tenantId,
    String executionId,
    String reportType,
    String status,
    String title,
    String summary,
    String markdown,
    String generatedBy,
    OffsetDateTime generatedAt,
    List<ExecutionReportSectionResponse> sections,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt) {}
```

---

## 7.7 `ExecutionReportSectionResponse.java`

```java id="74qko4"
package io.aegisops.execution.dto;

import java.time.OffsetDateTime;

public record ExecutionReportSectionResponse(
    String id,
    int sectionOrder,
    String sectionType,
    String title,
    String content,
    String metadataJson,
    OffsetDateTime createdAt) {}
```

---

## 7.8 `ExecutionVerificationCreateRequest.java`

```java id="usjf9v"
package io.aegisops.execution.dto;

import java.util.Map;

public record ExecutionVerificationCreateRequest(
    String stepId,
    String verificationType,
    String targetType,
    String targetId,
    String status,
    String summary,
    Map<String, Object> details,
    String createdBy) {}
```

---

## 7.9 `ExecutionVerificationCreateCommand.java`

```java id="9urdvc"
package io.aegisops.execution.dto;

public record ExecutionVerificationCreateCommand(
    String id,
    String tenantId,
    String executionId,
    String stepId,
    String verificationType,
    String targetType,
    String targetId,
    String status,
    String summary,
    String detailsJson,
    String createdBy) {}
```

---

## 7.10 `ExecutionVerificationRecord.java`

```java id="qmy3e7"
package io.aegisops.execution.dto;

import java.time.OffsetDateTime;

public record ExecutionVerificationRecord(
    String id,
    String tenantId,
    String executionId,
    String stepId,
    String verificationType,
    String targetType,
    String targetId,
    String status,
    String summary,
    String detailsJson,
    String createdBy,
    OffsetDateTime createdAt) {}
```

---

## 7.11 `ExecutionVerificationResponse.java`

```java id="rxj3sj"
package io.aegisops.execution.dto;

import java.time.OffsetDateTime;

public record ExecutionVerificationResponse(
    String id,
    String executionId,
    String stepId,
    String verificationType,
    String targetType,
    String targetId,
    String status,
    String summary,
    String detailsJson,
    String createdBy,
    OffsetDateTime createdAt) {}
```

---

## 7.12 `ExecutionAuditEventCreateCommand.java`

```java id="wubvq3"
package io.aegisops.execution.dto;

public record ExecutionAuditEventCreateCommand(
    String id,
    String tenantId,
    String executionId,
    String stepId,
    String eventType,
    String actor,
    String summary,
    String payloadJson) {}
```

---

## 7.13 `ExecutionAuditEventRecord.java`

```java id="tlgrj8"
package io.aegisops.execution.dto;

import java.time.OffsetDateTime;

public record ExecutionAuditEventRecord(
    String id,
    String tenantId,
    String executionId,
    String stepId,
    String eventType,
    String actor,
    String summary,
    String payloadJson,
    OffsetDateTime createdAt) {}
```

---

## 7.14 `ExecutionAuditEventResponse.java`

```java id="8bs3vh"
package io.aegisops.execution.dto;

import java.time.OffsetDateTime;

public record ExecutionAuditEventResponse(
    String id,
    String executionId,
    String stepId,
    String eventType,
    String actor,
    String summary,
    String payloadJson,
    OffsetDateTime createdAt) {}
```

---

# 8. Json 工具

## 8.1 `ExecutionReportJson.java`

路径：

```txt id="2k7bxi"
modules/aiops-execution/src/main/java/io/aegisops/execution/ExecutionReportJson.java
```

```java id="bbt9c5"
package io.aegisops.execution;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.aegisops.common.exception.AppException;

public class ExecutionReportJson {
  private final ObjectMapper objectMapper;

  public ExecutionReportJson(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  public String write(Object value) {
    try {
      if (value == null) {
        return "{}";
      }
      return objectMapper.writeValueAsString(value);
    } catch (Exception ex) {
      throw new AppException("EXECUTION_REPORT_JSON_WRITE_FAILED", "Failed to serialize execution report json");
    }
  }
}
```

---

# 9. Repository 接口

## 9.1 `ExecutionReportRepository.java`

路径：

```txt id="x4w5mc"
modules/aiops-execution/src/main/java/io/aegisops/execution/ExecutionReportRepository.java
```

```java id="ya4cx3"
package io.aegisops.execution;

import io.aegisops.execution.dto.ExecutionAuditEventCreateCommand;
import io.aegisops.execution.dto.ExecutionAuditEventRecord;
import io.aegisops.execution.dto.ExecutionReportCreateCommand;
import io.aegisops.execution.dto.ExecutionReportRecord;
import io.aegisops.execution.dto.ExecutionReportSectionCreateCommand;
import io.aegisops.execution.dto.ExecutionReportSectionRecord;
import io.aegisops.execution.dto.ExecutionVerificationCreateCommand;
import io.aegisops.execution.dto.ExecutionVerificationRecord;
import java.util.List;
import java.util.Optional;

public interface ExecutionReportRepository {
  void createReport(ExecutionReportCreateCommand command);

  void createSection(ExecutionReportSectionCreateCommand command);

  Optional<ExecutionReportRecord> findReport(String tenantId, String reportId);

  Optional<ExecutionReportRecord> findLatestReportByExecution(String tenantId, String executionId);

  List<ExecutionReportSectionRecord> listSections(String tenantId, String reportId);

  void createVerification(ExecutionVerificationCreateCommand command);

  List<ExecutionVerificationRecord> listVerifications(String tenantId, String executionId);

  void createAuditEvent(ExecutionAuditEventCreateCommand command);

  List<ExecutionAuditEventRecord> listAuditEvents(String tenantId, String executionId);
}
```

---

# 10. jOOQ Repository 实现

## 10.1 `JooqExecutionReportRepository.java`

路径：

```txt id="qu93ji"
modules/aiops-execution/src/main/java/io/aegisops/execution/JooqExecutionReportRepository.java
```

```java id="5bn3lz"
package io.aegisops.execution;

import static io.aegisops.persistence.AegisJooq.jsonbValue;
import static io.aegisops.persistence.jooq.Tables.EXECUTION_AUDIT_EVENT;
import static io.aegisops.persistence.jooq.Tables.EXECUTION_REPORT;
import static io.aegisops.persistence.jooq.Tables.EXECUTION_REPORT_SECTION;
import static io.aegisops.persistence.jooq.Tables.EXECUTION_VERIFICATION;

import io.aegisops.execution.dto.ExecutionAuditEventCreateCommand;
import io.aegisops.execution.dto.ExecutionAuditEventRecord;
import io.aegisops.execution.dto.ExecutionReportCreateCommand;
import io.aegisops.execution.dto.ExecutionReportRecord;
import io.aegisops.execution.dto.ExecutionReportSectionCreateCommand;
import io.aegisops.execution.dto.ExecutionReportSectionRecord;
import io.aegisops.execution.dto.ExecutionVerificationCreateCommand;
import io.aegisops.execution.dto.ExecutionVerificationRecord;
import java.util.List;
import java.util.Optional;
import org.jooq.DSLContext;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Repository;

@Repository
public class JooqExecutionReportRepository implements ExecutionReportRepository {
  private final DSLContext dsl;

  public JooqExecutionReportRepository(DSLContext dsl) {
    this.dsl = dsl;
  }

  @Override
  public void createReport(ExecutionReportCreateCommand command) {
    dsl.insertInto(EXECUTION_REPORT)
        .set(EXECUTION_REPORT.ID, command.id())
        .set(EXECUTION_REPORT.TENANT_ID, command.tenantId())
        .set(EXECUTION_REPORT.EXECUTION_ID, command.executionId())
        .set(EXECUTION_REPORT.REPORT_TYPE, command.reportType())
        .set(EXECUTION_REPORT.STATUS, command.status())
        .set(EXECUTION_REPORT.TITLE, command.title())
        .set(EXECUTION_REPORT.SUMMARY, command.summary())
        .set(EXECUTION_REPORT.MARKDOWN, command.markdown())
        .set(EXECUTION_REPORT.GENERATED_BY, command.generatedBy())
        .set(EXECUTION_REPORT.GENERATED_AT, DSL.currentOffsetDateTime())
        .set(EXECUTION_REPORT.CREATED_AT, DSL.currentOffsetDateTime())
        .set(EXECUTION_REPORT.UPDATED_AT, DSL.currentOffsetDateTime())
        .execute();
  }

  @Override
  public void createSection(ExecutionReportSectionCreateCommand command) {
    dsl.insertInto(EXECUTION_REPORT_SECTION)
        .set(EXECUTION_REPORT_SECTION.ID, command.id())
        .set(EXECUTION_REPORT_SECTION.TENANT_ID, command.tenantId())
        .set(EXECUTION_REPORT_SECTION.REPORT_ID, command.reportId())
        .set(EXECUTION_REPORT_SECTION.SECTION_ORDER, command.sectionOrder())
        .set(EXECUTION_REPORT_SECTION.SECTION_TYPE, command.sectionType())
        .set(EXECUTION_REPORT_SECTION.TITLE, command.title())
        .set(EXECUTION_REPORT_SECTION.CONTENT, command.content())
        .set(EXECUTION_REPORT_SECTION.METADATA, jsonbValue(command.metadataJson()))
        .set(EXECUTION_REPORT_SECTION.CREATED_AT, DSL.currentOffsetDateTime())
        .execute();
  }

  @Override
  public Optional<ExecutionReportRecord> findReport(String tenantId, String reportId) {
    return selectReport()
        .where(EXECUTION_REPORT.TENANT_ID.eq(tenantId))
        .and(EXECUTION_REPORT.ID.eq(reportId))
        .fetchOptional(this::toReportRecord);
  }

  @Override
  public Optional<ExecutionReportRecord> findLatestReportByExecution(String tenantId, String executionId) {
    return selectReport()
        .where(EXECUTION_REPORT.TENANT_ID.eq(tenantId))
        .and(EXECUTION_REPORT.EXECUTION_ID.eq(executionId))
        .orderBy(EXECUTION_REPORT.GENERATED_AT.desc())
        .limit(1)
        .fetchOptional(this::toReportRecord);
  }

  @Override
  public List<ExecutionReportSectionRecord> listSections(String tenantId, String reportId) {
    return dsl.select(
            EXECUTION_REPORT_SECTION.ID,
            EXECUTION_REPORT_SECTION.TENANT_ID,
            EXECUTION_REPORT_SECTION.REPORT_ID,
            EXECUTION_REPORT_SECTION.SECTION_ORDER,
            EXECUTION_REPORT_SECTION.SECTION_TYPE,
            EXECUTION_REPORT_SECTION.TITLE,
            EXECUTION_REPORT_SECTION.CONTENT,
            EXECUTION_REPORT_SECTION.METADATA.cast(String.class).as("metadata_json"),
            EXECUTION_REPORT_SECTION.CREATED_AT)
        .from(EXECUTION_REPORT_SECTION)
        .where(EXECUTION_REPORT_SECTION.TENANT_ID.eq(tenantId))
        .and(EXECUTION_REPORT_SECTION.REPORT_ID.eq(reportId))
        .orderBy(EXECUTION_REPORT_SECTION.SECTION_ORDER.asc())
        .fetch(this::toSectionRecord);
  }

  @Override
  public void createVerification(ExecutionVerificationCreateCommand command) {
    dsl.insertInto(EXECUTION_VERIFICATION)
        .set(EXECUTION_VERIFICATION.ID, command.id())
        .set(EXECUTION_VERIFICATION.TENANT_ID, command.tenantId())
        .set(EXECUTION_VERIFICATION.EXECUTION_ID, command.executionId())
        .set(EXECUTION_VERIFICATION.STEP_ID, command.stepId())
        .set(EXECUTION_VERIFICATION.VERIFICATION_TYPE, command.verificationType())
        .set(EXECUTION_VERIFICATION.TARGET_TYPE, command.targetType())
        .set(EXECUTION_VERIFICATION.TARGET_ID, command.targetId())
        .set(EXECUTION_VERIFICATION.STATUS, command.status())
        .set(EXECUTION_VERIFICATION.SUMMARY, command.summary())
        .set(EXECUTION_VERIFICATION.DETAILS, jsonbValue(command.detailsJson()))
        .set(EXECUTION_VERIFICATION.CREATED_BY, command.createdBy())
        .set(EXECUTION_VERIFICATION.CREATED_AT, DSL.currentOffsetDateTime())
        .execute();
  }

  @Override
  public List<ExecutionVerificationRecord> listVerifications(String tenantId, String executionId) {
    return dsl.select(
            EXECUTION_VERIFICATION.ID,
            EXECUTION_VERIFICATION.TENANT_ID,
            EXECUTION_VERIFICATION.EXECUTION_ID,
            EXECUTION_VERIFICATION.STEP_ID,
            EXECUTION_VERIFICATION.VERIFICATION_TYPE,
            EXECUTION_VERIFICATION.TARGET_TYPE,
            EXECUTION_VERIFICATION.TARGET_ID,
            EXECUTION_VERIFICATION.STATUS,
            EXECUTION_VERIFICATION.SUMMARY,
            EXECUTION_VERIFICATION.DETAILS.cast(String.class).as("details_json"),
            EXECUTION_VERIFICATION.CREATED_BY,
            EXECUTION_VERIFICATION.CREATED_AT)
        .from(EXECUTION_VERIFICATION)
        .where(EXECUTION_VERIFICATION.TENANT_ID.eq(tenantId))
        .and(EXECUTION_VERIFICATION.EXECUTION_ID.eq(executionId))
        .orderBy(EXECUTION_VERIFICATION.CREATED_AT.asc())
        .fetch(this::toVerificationRecord);
  }

  @Override
  public void createAuditEvent(ExecutionAuditEventCreateCommand command) {
    dsl.insertInto(EXECUTION_AUDIT_EVENT)
        .set(EXECUTION_AUDIT_EVENT.ID, command.id())
        .set(EXECUTION_AUDIT_EVENT.TENANT_ID, command.tenantId())
        .set(EXECUTION_AUDIT_EVENT.EXECUTION_ID, command.executionId())
        .set(EXECUTION_AUDIT_EVENT.STEP_ID, command.stepId())
        .set(EXECUTION_AUDIT_EVENT.EVENT_TYPE, command.eventType())
        .set(EXECUTION_AUDIT_EVENT.ACTOR, command.actor())
        .set(EXECUTION_AUDIT_EVENT.SUMMARY, command.summary())
        .set(EXECUTION_AUDIT_EVENT.PAYLOAD, jsonbValue(command.payloadJson()))
        .set(EXECUTION_AUDIT_EVENT.CREATED_AT, DSL.currentOffsetDateTime())
        .execute();
  }

  @Override
  public List<ExecutionAuditEventRecord> listAuditEvents(String tenantId, String executionId) {
    return dsl.select(
            EXECUTION_AUDIT_EVENT.ID,
            EXECUTION_AUDIT_EVENT.TENANT_ID,
            EXECUTION_AUDIT_EVENT.EXECUTION_ID,
            EXECUTION_AUDIT_EVENT.STEP_ID,
            EXECUTION_AUDIT_EVENT.EVENT_TYPE,
            EXECUTION_AUDIT_EVENT.ACTOR,
            EXECUTION_AUDIT_EVENT.SUMMARY,
            EXECUTION_AUDIT_EVENT.PAYLOAD.cast(String.class).as("payload_json"),
            EXECUTION_AUDIT_EVENT.CREATED_AT)
        .from(EXECUTION_AUDIT_EVENT)
        .where(EXECUTION_AUDIT_EVENT.TENANT_ID.eq(tenantId))
        .and(EXECUTION_AUDIT_EVENT.EXECUTION_ID.eq(executionId))
        .orderBy(EXECUTION_AUDIT_EVENT.CREATED_AT.asc())
        .fetch(this::toAuditEventRecord);
  }

  private org.jooq.SelectJoinStep<org.jooq.Record> selectReport() {
    return dsl.select(
            EXECUTION_REPORT.ID,
            EXECUTION_REPORT.TENANT_ID,
            EXECUTION_REPORT.EXECUTION_ID,
            EXECUTION_REPORT.REPORT_TYPE,
            EXECUTION_REPORT.STATUS,
            EXECUTION_REPORT.TITLE,
            EXECUTION_REPORT.SUMMARY,
            EXECUTION_REPORT.MARKDOWN,
            EXECUTION_REPORT.GENERATED_BY,
            EXECUTION_REPORT.GENERATED_AT,
            EXECUTION_REPORT.CREATED_AT,
            EXECUTION_REPORT.UPDATED_AT)
        .from(EXECUTION_REPORT);
  }

  private ExecutionReportRecord toReportRecord(org.jooq.Record record) {
    return new ExecutionReportRecord(
        record.get(EXECUTION_REPORT.ID),
        record.get(EXECUTION_REPORT.TENANT_ID),
        record.get(EXECUTION_REPORT.EXECUTION_ID),
        record.get(EXECUTION_REPORT.REPORT_TYPE),
        record.get(EXECUTION_REPORT.STATUS),
        record.get(EXECUTION_REPORT.TITLE),
        record.get(EXECUTION_REPORT.SUMMARY),
        record.get(EXECUTION_REPORT.MARKDOWN),
        record.get(EXECUTION_REPORT.GENERATED_BY),
        record.get(EXECUTION_REPORT.GENERATED_AT),
        record.get(EXECUTION_REPORT.CREATED_AT),
        record.get(EXECUTION_REPORT.UPDATED_AT));
  }

  private ExecutionReportSectionRecord toSectionRecord(org.jooq.Record record) {
    return new ExecutionReportSectionRecord(
        record.get(EXECUTION_REPORT_SECTION.ID),
        record.get(EXECUTION_REPORT_SECTION.TENANT_ID),
        record.get(EXECUTION_REPORT_SECTION.REPORT_ID),
        value(record.get(EXECUTION_REPORT_SECTION.SECTION_ORDER)),
        record.get(EXECUTION_REPORT_SECTION.SECTION_TYPE),
        record.get(EXECUTION_REPORT_SECTION.TITLE),
        record.get(EXECUTION_REPORT_SECTION.CONTENT),
        record.get("metadata_json", String.class),
        record.get(EXECUTION_REPORT_SECTION.CREATED_AT));
  }

  private ExecutionVerificationRecord toVerificationRecord(org.jooq.Record record) {
    return new ExecutionVerificationRecord(
        record.get(EXECUTION_VERIFICATION.ID),
        record.get(EXECUTION_VERIFICATION.TENANT_ID),
        record.get(EXECUTION_VERIFICATION.EXECUTION_ID),
        record.get(EXECUTION_VERIFICATION.STEP_ID),
        record.get(EXECUTION_VERIFICATION.VERIFICATION_TYPE),
        record.get(EXECUTION_VERIFICATION.TARGET_TYPE),
        record.get(EXECUTION_VERIFICATION.TARGET_ID),
        record.get(EXECUTION_VERIFICATION.STATUS),
        record.get(EXECUTION_VERIFICATION.SUMMARY),
        record.get("details_json", String.class),
        record.get(EXECUTION_VERIFICATION.CREATED_BY),
        record.get(EXECUTION_VERIFICATION.CREATED_AT));
  }

  private ExecutionAuditEventRecord toAuditEventRecord(org.jooq.Record record) {
    return new ExecutionAuditEventRecord(
        record.get(EXECUTION_AUDIT_EVENT.ID),
        record.get(EXECUTION_AUDIT_EVENT.TENANT_ID),
        record.get(EXECUTION_AUDIT_EVENT.EXECUTION_ID),
        record.get(EXECUTION_AUDIT_EVENT.STEP_ID),
        record.get(EXECUTION_AUDIT_EVENT.EVENT_TYPE),
        record.get(EXECUTION_AUDIT_EVENT.ACTOR),
        record.get(EXECUTION_AUDIT_EVENT.SUMMARY),
        record.get("payload_json", String.class),
        record.get(EXECUTION_AUDIT_EVENT.CREATED_AT));
  }

  private int value(Integer value) {
    return value == null ? 0 : value;
  }
}
```

---

# 11. Markdown Builder

## 11.1 `ExecutionReportMarkdownBuilder.java`

路径：

```txt id="a1gpjm"
modules/aiops-execution/src/main/java/io/aegisops/execution/ExecutionReportMarkdownBuilder.java
```

```java id="p8f6oq"
package io.aegisops.execution;

import io.aegisops.execution.dto.ExecutionArtifactResponse;
import io.aegisops.execution.dto.ExecutionAuditEventRecord;
import io.aegisops.execution.dto.ExecutionRunResponse;
import io.aegisops.execution.dto.ExecutionStepResponse;
import io.aegisops.execution.dto.ExecutionVerificationRecord;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class ExecutionReportMarkdownBuilder {
  public String build(
      ExecutionRunResponse execution,
      List<ExecutionVerificationRecord> verifications,
      List<ExecutionAuditEventRecord> auditEvents) {
    StringBuilder md = new StringBuilder();

    md.append("# Execution Report\n\n");
    md.append("## Summary\n\n");
    md.append("- Execution ID: ").append(value(execution.id())).append("\n");
    md.append("- Tenant ID: ").append(value(execution.tenantId())).append("\n");
    md.append("- Incident ID: ").append(value(execution.incidentId())).append("\n");
    md.append("- Plan ID: ").append(value(execution.planId())).append("\n");
    md.append("- Mode: ").append(value(execution.mode())).append("\n");
    md.append("- Kind: ").append(value(execution.executionKind())).append("\n");
    md.append("- Status: ").append(value(execution.status())).append("\n");
    md.append("- Requested By: ").append(value(execution.requestedBy())).append("\n");
    md.append("- Runner ID: ").append(value(execution.runnerId())).append("\n");
    md.append("- Started At: ").append(value(execution.startedAt())).append("\n");
    md.append("- Finished At: ").append(value(execution.finishedAt())).append("\n");

    if (execution.rollbackPlanId() != null && !execution.rollbackPlanId().isBlank()) {
      md.append("- Rollback Plan ID: ").append(execution.rollbackPlanId()).append("\n");
    }

    if (execution.errorMessage() != null && !execution.errorMessage().isBlank()) {
      md.append("- Error: ").append(escape(execution.errorMessage())).append("\n");
    }

    md.append("\n## Step Summary\n\n");
    md.append("| Order | Step | Action | Target | Status |\n");
    md.append("|---:|---|---|---|---|\n");

    for (ExecutionStepResponse step : execution.steps()) {
      md.append("| ")
          .append(step.stepOrder())
          .append(" | ")
          .append(escape(step.title()))
          .append(" | ")
          .append(escape(step.actionType()))
          .append(" | ")
          .append(escape(step.targetType()))
          .append(" | ")
          .append(escape(step.status()))
          .append(" |\n");
    }

    md.append("\n## Artifact Summary\n\n");
    md.append("| Step ID | Artifact | Type |\n");
    md.append("|---|---|---|\n");

    for (ExecutionArtifactResponse artifact : execution.artifacts()) {
      md.append("| ")
          .append(escape(artifact.stepId()))
          .append(" | ")
          .append(escape(artifact.name()))
          .append(" | ")
          .append(escape(artifact.artifactType()))
          .append(" |\n");
    }

    md.append("\n## Verification\n\n");
    if (verifications.isEmpty()) {
      md.append("No verification records.\n");
    } else {
      md.append("| Type | Target | Status | Summary |\n");
      md.append("|---|---|---|---|\n");
      for (ExecutionVerificationRecord verification : verifications) {
        md.append("| ")
            .append(escape(verification.verificationType()))
            .append(" | ")
            .append(escape(verification.targetType()))
            .append(":")
            .append(escape(verification.targetId()))
            .append(" | ")
            .append(escape(verification.status()))
            .append(" | ")
            .append(escape(verification.summary()))
            .append(" |\n");
      }
    }

    md.append("\n## Audit Events\n\n");
    if (auditEvents.isEmpty()) {
      md.append("No audit events.\n");
    } else {
      for (ExecutionAuditEventRecord event : auditEvents) {
        md.append("- ")
            .append(value(event.createdAt()))
            .append(" [")
            .append(escape(event.eventType()))
            .append("] ")
            .append(escape(event.summary()))
            .append(" by ")
            .append(escape(event.actor()))
            .append("\n");
      }
    }

    return md.toString();
  }

  private String value(Object value) {
    return value == null ? "" : String.valueOf(value);
  }

  private String escape(String value) {
    if (value == null) {
      return "";
    }

    return value.replace("|", "\\|").replace("\n", " ").replace("\r", " ");
  }
}
```

---

# 12. Report Service

## 12.1 `ExecutionReportService.java`

路径：

```txt id="smg56l"
modules/aiops-execution/src/main/java/io/aegisops/execution/ExecutionReportService.java
```

```java id="ej5lyv"
package io.aegisops.execution;

import io.aegisops.common.exception.AppException;
import io.aegisops.execution.dto.ExecutionAuditEventCreateCommand;
import io.aegisops.execution.dto.ExecutionAuditEventRecord;
import io.aegisops.execution.dto.ExecutionAuditEventResponse;
import io.aegisops.execution.dto.ExecutionReportCreateCommand;
import io.aegisops.execution.dto.ExecutionReportGenerateRequest;
import io.aegisops.execution.dto.ExecutionReportRecord;
import io.aegisops.execution.dto.ExecutionReportResponse;
import io.aegisops.execution.dto.ExecutionReportSectionCreateCommand;
import io.aegisops.execution.dto.ExecutionReportSectionRecord;
import io.aegisops.execution.dto.ExecutionReportSectionResponse;
import io.aegisops.execution.dto.ExecutionRunResponse;
import io.aegisops.execution.dto.ExecutionVerificationCreateCommand;
import io.aegisops.execution.dto.ExecutionVerificationCreateRequest;
import io.aegisops.execution.dto.ExecutionVerificationRecord;
import io.aegisops.execution.dto.ExecutionVerificationResponse;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ExecutionReportService {
  private final ExecutionRequestService executionRequestService;
  private final ExecutionReportRepository repository;
  private final ExecutionReportMarkdownBuilder markdownBuilder;
  private final ExecutionReportJson json;

  public ExecutionReportService(
      ExecutionRequestService executionRequestService,
      ExecutionReportRepository repository,
      ExecutionReportMarkdownBuilder markdownBuilder,
      com.fasterxml.jackson.databind.ObjectMapper objectMapper) {
    this.executionRequestService = executionRequestService;
    this.repository = repository;
    this.markdownBuilder = markdownBuilder;
    this.json = new ExecutionReportJson(objectMapper);
  }

  @Transactional
  public ExecutionReportResponse generate(
      String tenantId, String executionId, ExecutionReportGenerateRequest request) {
    ExecutionRunResponse execution = executionRequestService.get(tenantId, executionId);

    validateTerminal(execution);

    List<ExecutionVerificationRecord> verifications =
        include(request == null ? null : request.includeVerifications())
            ? repository.listVerifications(tenantId, executionId)
            : List.of();

    List<ExecutionAuditEventRecord> auditEvents =
        include(request == null ? null : request.includeAuditEvents())
            ? repository.listAuditEvents(tenantId, executionId)
            : List.of();

    String markdown = markdownBuilder.build(execution, verifications, auditEvents);

    String reportId = newId("exr");
    String reportType = normalizeReportType(request == null ? null : request.reportType(), execution);
    String title = "Execution Report - " + execution.id();
    String summary = buildSummary(execution);

    repository.createReport(
        new ExecutionReportCreateCommand(
            reportId,
            tenantId,
            executionId,
            reportType,
            "generated",
            title,
            summary,
            markdown,
            blankToDefault(request == null ? null : request.generatedBy(), "system")));

    int order = 1;
    createSection(tenantId, reportId, order++, "summary", "Summary", summary, Map.of());
    createSection(tenantId, reportId, order++, "steps", "Step Summary", buildStepSummary(execution), Map.of());
    createSection(
        tenantId,
        reportId,
        order++,
        "artifacts",
        "Artifact Summary",
        buildArtifactSummary(execution),
        Map.of("artifactCount", execution.artifacts().size()));
    createSection(
        tenantId,
        reportId,
        order++,
        "verification",
        "Verification",
        buildVerificationSummary(verifications),
        Map.of("verificationCount", verifications.size()));
    createSection(
        tenantId,
        reportId,
        order++,
        "audit",
        "Audit Events",
        buildAuditSummary(auditEvents),
        Map.of("auditEventCount", auditEvents.size()));

    appendAuditEvent(
        tenantId,
        executionId,
        null,
        "report_generated",
        blankToDefault(request == null ? null : request.generatedBy(), "system"),
        "Execution report generated",
        Map.of("reportId", reportId));

    return get(tenantId, reportId);
  }

  public ExecutionReportResponse get(String tenantId, String reportId) {
    ExecutionReportRecord report =
        repository
            .findReport(tenantId, reportId)
            .orElseThrow(() -> new AppException("EXECUTION_REPORT_NOT_FOUND", "Execution report not found"));

    return toResponse(report, repository.listSections(tenantId, reportId));
  }

  public ExecutionReportResponse latestByExecution(String tenantId, String executionId) {
    ExecutionReportRecord report =
        repository
            .findLatestReportByExecution(tenantId, executionId)
            .orElseThrow(() -> new AppException("EXECUTION_REPORT_NOT_FOUND", "Execution report not found"));

    return get(tenantId, report.id());
  }

  public String markdown(String tenantId, String reportId) {
    return get(tenantId, reportId).markdown();
  }

  @Transactional
  public ExecutionVerificationResponse createVerification(
      String tenantId, String executionId, ExecutionVerificationCreateRequest request) {
    executionRequestService.get(tenantId, executionId);
    validateVerificationRequest(request);

    String id = newId("exv");

    repository.createVerification(
        new ExecutionVerificationCreateCommand(
            id,
            tenantId,
            executionId,
            request.stepId(),
            normalizeVerificationType(request.verificationType()),
            request.targetType().trim(),
            request.targetId(),
            normalizeVerificationStatus(request.status()),
            request.summary().trim(),
            json.write(request.details()),
            blankToDefault(request.createdBy(), "system")));

    appendAuditEvent(
        tenantId,
        executionId,
        request.stepId(),
        "verification_created",
        blankToDefault(request.createdBy(), "system"),
        "Execution verification created",
        Map.of("verificationId", id, "status", normalizeVerificationStatus(request.status())));

    return listVerifications(tenantId, executionId).stream()
        .filter(item -> item.id().equals(id))
        .findFirst()
        .orElseThrow(() -> new AppException("EXECUTION_VERIFICATION_NOT_FOUND", "Execution verification not found"));
  }

  public List<ExecutionVerificationResponse> listVerifications(String tenantId, String executionId) {
    executionRequestService.get(tenantId, executionId);
    return repository.listVerifications(tenantId, executionId).stream()
        .map(this::toVerificationResponse)
        .toList();
  }

  public List<ExecutionAuditEventResponse> listAuditEvents(String tenantId, String executionId) {
    executionRequestService.get(tenantId, executionId);
    return repository.listAuditEvents(tenantId, executionId).stream()
        .map(this::toAuditEventResponse)
        .toList();
  }

  public void appendAuditEvent(
      String tenantId,
      String executionId,
      String stepId,
      String eventType,
      String actor,
      String summary,
      Object payload) {
    repository.createAuditEvent(
        new ExecutionAuditEventCreateCommand(
            newId("xae"),
            tenantId,
            executionId,
            stepId,
            eventType,
            blankToDefault(actor, "system"),
            summary,
            json.write(payload)));
  }

  private void validateTerminal(ExecutionRunResponse execution) {
    if (!List.of("succeeded", "failed", "cancelled", "timeout").contains(execution.status())) {
      throw new AppException(
          "EXECUTION_REPORT_STATUS_INVALID",
          "Only terminal execution can generate report");
    }
  }

  private void validateVerificationRequest(ExecutionVerificationCreateRequest request) {
    if (request == null) {
      throw new AppException("EXECUTION_VERIFICATION_REQUEST_REQUIRED", "Verification request is required");
    }

    if (request.targetType() == null || request.targetType().isBlank()) {
      throw new AppException("EXECUTION_VERIFICATION_TARGET_REQUIRED", "Verification targetType is required");
    }

    if (request.summary() == null || request.summary().isBlank()) {
      throw new AppException("EXECUTION_VERIFICATION_SUMMARY_REQUIRED", "Verification summary is required");
    }

    normalizeVerificationType(request.verificationType());
    normalizeVerificationStatus(request.status());
  }

  private String normalizeReportType(String value, ExecutionRunResponse execution) {
    if (value != null && !value.isBlank()) {
      String reportType = value.trim().toLowerCase();
      if (!List.of("standard", "rollback", "audit").contains(reportType)) {
        throw new AppException("EXECUTION_REPORT_TYPE_INVALID", "Invalid execution report type");
      }
      return reportType;
    }

    return "rollback".equals(execution.executionKind()) ? "rollback" : "standard";
  }

  private String normalizeVerificationType(String value) {
    String type = value == null || value.isBlank() ? "manual" : value.trim().toLowerCase();
    if (!List.of("before", "after", "manual", "post").contains(type)) {
      throw new AppException("EXECUTION_VERIFICATION_TYPE_INVALID", "Invalid verification type");
    }
    return type;
  }

  private String normalizeVerificationStatus(String value) {
    String status = value == null || value.isBlank() ? "skipped" : value.trim().toLowerCase();
    if (!List.of("passed", "failed", "warn", "skipped").contains(status)) {
      throw new AppException("EXECUTION_VERIFICATION_STATUS_INVALID", "Invalid verification status");
    }
    return status;
  }

  private boolean include(Boolean value) {
    return value == null || value;
  }

  private void createSection(
      String tenantId,
      String reportId,
      int order,
      String sectionType,
      String title,
      String content,
      Object metadata) {
    repository.createSection(
        new ExecutionReportSectionCreateCommand(
            newId("exrs"),
            tenantId,
            reportId,
            order,
            sectionType,
            title,
            content,
            json.write(metadata)));
  }

  private String buildSummary(ExecutionRunResponse execution) {
    return "Execution "
        + execution.id()
        + " finished with status "
        + execution.status()
        + ". Mode="
        + execution.mode()
        + ", Kind="
        + execution.executionKind()
        + ".";
  }

  private String buildStepSummary(ExecutionRunResponse execution) {
    StringBuilder builder = new StringBuilder();
    for (var step : execution.steps()) {
      builder
          .append(step.stepOrder())
          .append(". ")
          .append(step.title())
          .append(" [")
          .append(step.actionType())
          .append("] -> ")
          .append(step.status())
          .append("\n");
    }
    return builder.toString();
  }

  private String buildArtifactSummary(ExecutionRunResponse execution) {
    StringBuilder builder = new StringBuilder();
    for (var artifact : execution.artifacts()) {
      builder
          .append("- ")
          .append(artifact.name())
          .append(" (")
          .append(artifact.artifactType())
          .append(")")
          .append(" step=")
          .append(artifact.stepId())
          .append("\n");
    }
    return builder.isEmpty() ? "No artifacts." : builder.toString();
  }

  private String buildVerificationSummary(List<ExecutionVerificationRecord> verifications) {
    if (verifications.isEmpty()) {
      return "No verification records.";
    }

    StringBuilder builder = new StringBuilder();
    for (ExecutionVerificationRecord verification : verifications) {
      builder
          .append("- ")
          .append(verification.verificationType())
          .append(" ")
          .append(verification.targetType())
          .append(":")
          .append(verification.targetId())
          .append(" -> ")
          .append(verification.status())
          .append(" - ")
          .append(verification.summary())
          .append("\n");
    }
    return builder.toString();
  }

  private String buildAuditSummary(List<ExecutionAuditEventRecord> auditEvents) {
    if (auditEvents.isEmpty()) {
      return "No audit events.";
    }

    StringBuilder builder = new StringBuilder();
    for (ExecutionAuditEventRecord event : auditEvents) {
      builder
          .append("- ")
          .append(event.createdAt())
          .append(" [")
          .append(event.eventType())
          .append("] ")
          .append(event.summary())
          .append(" by ")
          .append(event.actor())
          .append("\n");
    }
    return builder.toString();
  }

  private ExecutionReportResponse toResponse(
      ExecutionReportRecord report, List<ExecutionReportSectionRecord> sections) {
    return new ExecutionReportResponse(
        report.id(),
        report.tenantId(),
        report.executionId(),
        report.reportType(),
        report.status(),
        report.title(),
        report.summary(),
        report.markdown(),
        report.generatedBy(),
        report.generatedAt(),
        sections.stream().map(this::toSectionResponse).toList(),
        report.createdAt(),
        report.updatedAt());
  }

  private ExecutionReportSectionResponse toSectionResponse(ExecutionReportSectionRecord section) {
    return new ExecutionReportSectionResponse(
        section.id(),
        section.sectionOrder(),
        section.sectionType(),
        section.title(),
        section.content(),
        section.metadataJson(),
        section.createdAt());
  }

  private ExecutionVerificationResponse toVerificationResponse(ExecutionVerificationRecord record) {
    return new ExecutionVerificationResponse(
        record.id(),
        record.executionId(),
        record.stepId(),
        record.verificationType(),
        record.targetType(),
        record.targetId(),
        record.status(),
        record.summary(),
        record.detailsJson(),
        record.createdBy(),
        record.createdAt());
  }

  private ExecutionAuditEventResponse toAuditEventResponse(ExecutionAuditEventRecord record) {
    return new ExecutionAuditEventResponse(
        record.id(),
        record.executionId(),
        record.stepId(),
        record.eventType(),
        record.actor(),
        record.summary(),
        record.payloadJson(),
        record.createdAt());
  }

  private String blankToDefault(String value, String fallback) {
    return value == null || value.isBlank() ? fallback : value.trim();
  }

  private String newId(String prefix) {
    return prefix + "_" + UUID.randomUUID().toString().replace("-", "");
  }
}
```

---

# 13. Controller

## 13.1 `ExecutionReportController.java`

路径：

```txt id="hq4c11"
modules/aiops-execution/src/main/java/io/aegisops/execution/ExecutionReportController.java
```

```java id="7ssn12"
package io.aegisops.execution;

import io.aegisops.common.api.ApiResponse;
import io.aegisops.common.tenant.TenantContext;
import io.aegisops.execution.dto.ExecutionAuditEventResponse;
import io.aegisops.execution.dto.ExecutionReportGenerateRequest;
import io.aegisops.execution.dto.ExecutionReportResponse;
import io.aegisops.execution.dto.ExecutionVerificationCreateRequest;
import io.aegisops.execution.dto.ExecutionVerificationResponse;
import java.util.List;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ExecutionReportController {
  private final ExecutionReportService service;

  public ExecutionReportController(ExecutionReportService service) {
    this.service = service;
  }

  @PostMapping("/api/executions/{executionId}/reports/generate")
  public ApiResponse<ExecutionReportResponse> generate(
      @PathVariable String executionId,
      @RequestBody(required = false) ExecutionReportGenerateRequest request) {
    return ApiResponse.ok(service.generate(TenantContext.requireTenantId(), executionId, request));
  }

  @GetMapping("/api/executions/{executionId}/reports/latest")
  public ApiResponse<ExecutionReportResponse> latest(@PathVariable String executionId) {
    return ApiResponse.ok(service.latestByExecution(TenantContext.requireTenantId(), executionId));
  }

  @GetMapping("/api/execution-reports/{reportId}")
  public ApiResponse<ExecutionReportResponse> get(@PathVariable String reportId) {
    return ApiResponse.ok(service.get(TenantContext.requireTenantId(), reportId));
  }

  @GetMapping(
      value = "/api/execution-reports/{reportId}/markdown",
      produces = MediaType.TEXT_MARKDOWN_VALUE)
  public String markdown(@PathVariable String reportId) {
    return service.markdown(TenantContext.requireTenantId(), reportId);
  }

  @PostMapping("/api/executions/{executionId}/verifications")
  public ApiResponse<ExecutionVerificationResponse> createVerification(
      @PathVariable String executionId,
      @RequestBody ExecutionVerificationCreateRequest request) {
    return ApiResponse.ok(service.createVerification(TenantContext.requireTenantId(), executionId, request));
  }

  @GetMapping("/api/executions/{executionId}/verifications")
  public ApiResponse<List<ExecutionVerificationResponse>> listVerifications(
      @PathVariable String executionId) {
    return ApiResponse.ok(service.listVerifications(TenantContext.requireTenantId(), executionId));
  }

  @GetMapping("/api/executions/{executionId}/audit-events")
  public ApiResponse<List<ExecutionAuditEventResponse>> auditEvents(@PathVariable String executionId) {
    return ApiResponse.ok(service.listAuditEvents(TenantContext.requireTenantId(), executionId));
  }
}
```

> 如果你的 Spring 版本没有 `MediaType.TEXT_MARKDOWN_VALUE`，改成：
>
> ```java id="yu98fn"
> produces = "text/markdown"
> ```

---

# 14. Runner 审计事件增强

Phase5.8 不要求强绑定 runner，但建议在 runner 关键位置调用 `ExecutionReportService.appendAuditEvent(...)`。

## 14.1 修改 `RunnerExecutionService`

路径：

```txt id="ut8wqu"
apps/aiops-runner/src/main/java/io/aegisops/runner/RunnerExecutionService.java
```

新增字段：

```java id="j7gi63"
private final ExecutionReportService executionReportService;
```

构造函数增加参数。

在 runner claim 成功后追加：

```java id="r1hyo5"
executionReportService.appendAuditEvent(
    run.tenantId(),
    run.id(),
    null,
    "runner_claimed",
    properties.getRunnerId(),
    "Runner claimed execution",
    java.util.Map.of("runnerId", properties.getRunnerId()));
```

在 step 成功后追加：

```java id="eef9mr"
executionReportService.appendAuditEvent(
    run.tenantId(),
    run.id(),
    step.id(),
    "step_succeeded",
    properties.getRunnerId(),
    "Execution step succeeded: " + step.title(),
    java.util.Map.of("stepId", step.id(), "actionType", step.actionType()));
```

在 step 失败后追加：

```java id="tnqwf2"
executionReportService.appendAuditEvent(
    run.tenantId(),
    run.id(),
    step.id(),
    "step_failed",
    properties.getRunnerId(),
    "Execution step failed: " + step.title(),
    java.util.Map.of("stepId", step.id(), "actionType", step.actionType()));
```

在 run succeeded 后追加：

```java id="heuyxy"
executionReportService.appendAuditEvent(
    run.tenantId(),
    run.id(),
    null,
    "execution_succeeded",
    properties.getRunnerId(),
    "Execution succeeded",
    java.util.Map.of("executionId", run.id()));
```

在 run failed 后追加：

```java id="xvfe1d"
executionReportService.appendAuditEvent(
    run.tenantId(),
    run.id(),
    null,
    "execution_failed",
    properties.getRunnerId(),
    "Execution failed",
    java.util.Map.of("executionId", run.id()));
```

如果你觉得改 runner 影响测试太大，可以先不接 runner，只保留 report generate 时写 `report_generated` 审计事件。Phase5.8 最小可验收不依赖 runner 改造。

---

# 15. 单元测试

## 15.1 `ExecutionReportMarkdownBuilderTest.java`

路径：

```txt id="59cfl6"
modules/aiops-execution/src/test/java/io/aegisops/execution/ExecutionReportMarkdownBuilderTest.java
```

```java id="0e9671"
package io.aegisops.execution;

import static org.junit.jupiter.api.Assertions.assertTrue;

import io.aegisops.execution.dto.ExecutionArtifactResponse;
import io.aegisops.execution.dto.ExecutionAuditEventRecord;
import io.aegisops.execution.dto.ExecutionRunResponse;
import io.aegisops.execution.dto.ExecutionStepResponse;
import io.aegisops.execution.dto.ExecutionVerificationRecord;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

class ExecutionReportMarkdownBuilderTest {
  @Test
  void buildMarkdownReport() {
    ExecutionReportMarkdownBuilder builder = new ExecutionReportMarkdownBuilder();

    String markdown =
        builder.build(
            execution(),
            List.of(
                new ExecutionVerificationRecord(
                    "exv_1",
                    "tenant_1",
                    "exec_1",
                    null,
                    "after",
                    "service",
                    "order-service",
                    "passed",
                    "service recovered",
                    "{}",
                    "alice",
                    OffsetDateTime.now())),
            List.of(
                new ExecutionAuditEventRecord(
                    "xae_1",
                    "tenant_1",
                    "exec_1",
                    null,
                    "execution_succeeded",
                    "runner_1",
                    "Execution succeeded",
                    "{}",
                    OffsetDateTime.now())));

    assertTrue(markdown.contains("# Execution Report"));
    assertTrue(markdown.contains("order-service"));
    assertTrue(markdown.contains("execution_succeeded"));
    assertTrue(markdown.contains("webhook-response.json"));
  }

  private ExecutionRunResponse execution() {
    return new ExecutionRunResponse(
        "exec_1",
        "tenant_1",
        "inc_1",
        "plan_1",
        "succeeded",
        "live",
        "alice",
        "runner_1",
        null,
        "ok",
        1,
        1,
        null,
        null,
        null,
        1800,
        null,
        "{}",
        "medium",
        null,
        "normal",
        null,
        null,
        List.of(
            new ExecutionStepResponse(
                "step_1",
                "planstep_1",
                1,
                "Call webhook",
                "webhook",
                "service",
                "succeeded",
                "{}",
                "ok",
                null,
                null,
                null,
                1,
                300,
                1,
                OffsetDateTime.now(),
                OffsetDateTime.now())),
        List.of(
            new ExecutionArtifactResponse(
                "artifact_1",
                "exec_1",
                "step_1",
                "json",
                "webhook-response.json",
                "{}",
                "{}",
                OffsetDateTime.now())),
        OffsetDateTime.now(),
        OffsetDateTime.now(),
        OffsetDateTime.now(),
        OffsetDateTime.now());
  }
}
```

> 如果你当前 `ExecutionStepResponse / ExecutionArtifactResponse` 字段和上面略有差异，按现有 DTO 字段顺序调整构造参数。核心测试目标是 Markdown 内容。

---

## 15.2 `ExecutionReportServiceTest.java`

路径：

```txt id="sxixkv"
modules/aiops-execution/src/test/java/io/aegisops/execution/ExecutionReportServiceTest.java
```

```java id="mw4akx"
package io.aegisops.execution;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.aegisops.common.exception.AppException;
import io.aegisops.execution.dto.ExecutionAuditEventCreateCommand;
import io.aegisops.execution.dto.ExecutionAuditEventRecord;
import io.aegisops.execution.dto.ExecutionReportCreateCommand;
import io.aegisops.execution.dto.ExecutionReportGenerateRequest;
import io.aegisops.execution.dto.ExecutionReportRecord;
import io.aegisops.execution.dto.ExecutionReportSectionCreateCommand;
import io.aegisops.execution.dto.ExecutionReportSectionRecord;
import io.aegisops.execution.dto.ExecutionRunResponse;
import io.aegisops.execution.dto.ExecutionVerificationCreateCommand;
import io.aegisops.execution.dto.ExecutionVerificationCreateRequest;
import io.aegisops.execution.dto.ExecutionVerificationRecord;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class ExecutionReportServiceTest {
  @Test
  void generateReportForTerminalExecution() {
    FakeExecutionReportRepository repository = new FakeExecutionReportRepository();

    ExecutionReportService service =
        new ExecutionReportService(
            new FakeExecutionRequestService("succeeded"),
            repository,
            new ExecutionReportMarkdownBuilder(),
            new ObjectMapper());

    var response =
        service.generate(
            "tenant_1",
            "exec_1",
            new ExecutionReportGenerateRequest("standard", "alice", true, true, true));

    assertEquals("generated", response.status());
    assertTrue(response.markdown().contains("# Execution Report"));
    assertEquals(5, response.sections().size());
    assertTrue(repository.auditEvents.stream().anyMatch(item -> "report_generated".equals(item.eventType())));
  }

  @Test
  void rejectReportForNonTerminalExecution() {
    ExecutionReportService service =
        new ExecutionReportService(
            new FakeExecutionRequestService("running"),
            new FakeExecutionReportRepository(),
            new ExecutionReportMarkdownBuilder(),
            new ObjectMapper());

    assertThrows(
        AppException.class,
        () ->
            service.generate(
                "tenant_1",
                "exec_1",
                new ExecutionReportGenerateRequest("standard", "alice", true, true, true)));
  }

  @Test
  void createVerificationWritesAuditEvent() {
    FakeExecutionReportRepository repository = new FakeExecutionReportRepository();

    ExecutionReportService service =
        new ExecutionReportService(
            new FakeExecutionRequestService("succeeded"),
            repository,
            new ExecutionReportMarkdownBuilder(),
            new ObjectMapper());

    var response =
        service.createVerification(
            "tenant_1",
            "exec_1",
            new ExecutionVerificationCreateRequest(
                null,
                "after",
                "service",
                "order-service",
                "passed",
                "service recovered",
                Map.of("httpStatus", 200),
                "alice"));

    assertEquals("passed", response.status());
    assertEquals(1, repository.verifications.size());
    assertTrue(repository.auditEvents.stream().anyMatch(item -> "verification_created".equals(item.eventType())));
  }

  private static class FakeExecutionRequestService extends ExecutionRequestService {
    private final String status;

    FakeExecutionRequestService(String status) {
      super(null, null, null, null);
      this.status = status;
    }

    @Override
    public ExecutionRunResponse get(String tenantId, String executionId) {
      return ExecutionReportTestFixtures.execution(status);
    }
  }

  private static class FakeExecutionReportRepository implements ExecutionReportRepository {
    ExecutionReportRecord report;
    final List<ExecutionReportSectionRecord> sections = new ArrayList<>();
    final List<ExecutionVerificationRecord> verifications = new ArrayList<>();
    final List<ExecutionAuditEventRecord> auditEvents = new ArrayList<>();

    @Override
    public void createReport(ExecutionReportCreateCommand command) {
      report =
          new ExecutionReportRecord(
              command.id(),
              command.tenantId(),
              command.executionId(),
              command.reportType(),
              command.status(),
              command.title(),
              command.summary(),
              command.markdown(),
              command.generatedBy(),
              OffsetDateTime.now(),
              OffsetDateTime.now(),
              OffsetDateTime.now());
    }

    @Override
    public void createSection(ExecutionReportSectionCreateCommand command) {
      sections.add(
          new ExecutionReportSectionRecord(
              command.id(),
              command.tenantId(),
              command.reportId(),
              command.sectionOrder(),
              command.sectionType(),
              command.title(),
              command.content(),
              command.metadataJson(),
              OffsetDateTime.now()));
    }

    @Override
    public Optional<ExecutionReportRecord> findReport(String tenantId, String reportId) {
      return Optional.ofNullable(report);
    }

    @Override
    public Optional<ExecutionReportRecord> findLatestReportByExecution(String tenantId, String executionId) {
      return Optional.ofNullable(report);
    }

    @Override
    public List<ExecutionReportSectionRecord> listSections(String tenantId, String reportId) {
      return sections;
    }

    @Override
    public void createVerification(ExecutionVerificationCreateCommand command) {
      verifications.add(
          new ExecutionVerificationRecord(
              command.id(),
              command.tenantId(),
              command.executionId(),
              command.stepId(),
              command.verificationType(),
              command.targetType(),
              command.targetId(),
              command.status(),
              command.summary(),
              command.detailsJson(),
              command.createdBy(),
              OffsetDateTime.now()));
    }

    @Override
    public List<ExecutionVerificationRecord> listVerifications(String tenantId, String executionId) {
      return verifications;
    }

    @Override
    public void createAuditEvent(ExecutionAuditEventCreateCommand command) {
      auditEvents.add(
          new ExecutionAuditEventRecord(
              command.id(),
              command.tenantId(),
              command.executionId(),
              command.stepId(),
              command.eventType(),
              command.actor(),
              command.summary(),
              command.payloadJson(),
              OffsetDateTime.now()));
    }

    @Override
    public List<ExecutionAuditEventRecord> listAuditEvents(String tenantId, String executionId) {
      return auditEvents;
    }
  }
}
```

---

## 15.3 `ExecutionReportTestFixtures.java`

路径：

```txt id="5nte5p"
modules/aiops-execution/src/test/java/io/aegisops/execution/ExecutionReportTestFixtures.java
```

```java id="tyq5e2"
package io.aegisops.execution;

import io.aegisops.execution.dto.ExecutionArtifactResponse;
import io.aegisops.execution.dto.ExecutionRunResponse;
import io.aegisops.execution.dto.ExecutionStepResponse;
import java.time.OffsetDateTime;
import java.util.List;

final class ExecutionReportTestFixtures {
  private ExecutionReportTestFixtures() {}

  static ExecutionRunResponse execution(String status) {
    return new ExecutionRunResponse(
        "exec_1",
        "tenant_1",
        "inc_1",
        "plan_1",
        status,
        "live",
        "alice",
        "runner_1",
        null,
        "summary",
        1,
        1,
        null,
        null,
        null,
        1800,
        null,
        "{}",
        "medium",
        null,
        "normal",
        null,
        null,
        List.of(
            new ExecutionStepResponse(
                "step_1",
                "planstep_1",
                1,
                "Webhook remediation",
                "webhook",
                "service",
                "succeeded",
                "{}",
                "ok",
                null,
                null,
                null,
                1,
                300,
                1,
                OffsetDateTime.now(),
                OffsetDateTime.now())),
        List.of(
            new ExecutionArtifactResponse(
                "artifact_1",
                "exec_1",
                "step_1",
                "json",
                "webhook-response.json",
                "{\"ok\":true}",
                "{}",
                OffsetDateTime.now())),
        OffsetDateTime.now(),
        OffsetDateTime.now(),
        OffsetDateTime.now(),
        OffsetDateTime.now());
  }
}
```

---

## 15.4 `JooqExecutionReportRepositoryGeneratedSqlTest.java`

路径：

```txt id="f19ar8"
modules/aiops-execution/src/test/java/io/aegisops/execution/JooqExecutionReportRepositoryGeneratedSqlTest.java
```

```java id="xj6ag7"
package io.aegisops.execution;

import static org.junit.jupiter.api.Assertions.assertTrue;

import io.aegisops.execution.dto.ExecutionAuditEventCreateCommand;
import io.aegisops.execution.dto.ExecutionReportCreateCommand;
import io.aegisops.execution.dto.ExecutionReportSectionCreateCommand;
import io.aegisops.execution.dto.ExecutionVerificationCreateCommand;
import io.aegisops.persistence.JooqTestSupport;
import java.util.List;
import org.jooq.Query;
import org.junit.jupiter.api.Test;

class JooqExecutionReportRepositoryGeneratedSqlTest {
  @Test
  void createReportUsesExecutionReportTable() {
    var dsl = JooqTestSupport.dsl();
    var repository = new JooqExecutionReportRepository(dsl);

    repository.createReport(
        new ExecutionReportCreateCommand(
            "exr_1",
            "tenant_1",
            "exec_1",
            "standard",
            "generated",
            "title",
            "summary",
            "# md",
            "alice"));

    String sql = renderedSql(dsl.queries());

    assertTrue(sql.contains("execution_report"));
    assertTrue(sql.contains("markdown"));
  }

  @Test
  void createSectionUsesExecutionReportSectionTable() {
    var dsl = JooqTestSupport.dsl();
    var repository = new JooqExecutionReportRepository(dsl);

    repository.createSection(
        new ExecutionReportSectionCreateCommand(
            "exrs_1",
            "tenant_1",
            "exr_1",
            1,
            "summary",
            "Summary",
            "content",
            "{}"));

    String sql = renderedSql(dsl.queries());

    assertTrue(sql.contains("execution_report_section"));
    assertTrue(sql.contains("section_order"));
  }

  @Test
  void createVerificationUsesExecutionVerificationTable() {
    var dsl = JooqTestSupport.dsl();
    var repository = new JooqExecutionReportRepository(dsl);

    repository.createVerification(
        new ExecutionVerificationCreateCommand(
            "exv_1",
            "tenant_1",
            "exec_1",
            null,
            "after",
            "service",
            "order-service",
            "passed",
            "ok",
            "{}",
            "alice"));

    String sql = renderedSql(dsl.queries());

    assertTrue(sql.contains("execution_verification"));
    assertTrue(sql.contains("verification_type"));
  }

  @Test
  void createAuditEventUsesExecutionAuditEventTable() {
    var dsl = JooqTestSupport.dsl();
    var repository = new JooqExecutionReportRepository(dsl);

    repository.createAuditEvent(
        new ExecutionAuditEventCreateCommand(
            "xae_1",
            "tenant_1",
            "exec_1",
            null,
            "report_generated",
            "alice",
            "report generated",
            "{}"));

    String sql = renderedSql(dsl.queries());

    assertTrue(sql.contains("execution_audit_event"));
    assertTrue(sql.contains("event_type"));
  }

  private String renderedSql(List<Query> queries) {
    return queries.stream().map(Query::getSQL).reduce("", (a, b) -> a + "\n" + b).toLowerCase();
  }
}
```

> 如果你当前项目的 `JooqTestSupport.dsl()` 不是 `MockDataProvider` 形式，按现有 generated sql test 模板改造。核心断言是四张表被引用。

---

# 16. 文档

路径：

```txt id="t94328"
docs/mvp/design/phase5.8-execution-report-audit.md
```

```md id="dkzds1"
# Phase5.8 Execution Report & Audit

## 目标

Phase5.8 为 execution_run 增加执行报告、验证记录、审计事件和 Markdown 导出能力。

## 不做

- 不新增执行适配器
- 不做 SSH
- 不做自动回滚
- 不让 AI 执行动作

## 新增表

- execution_report
- execution_report_section
- execution_verification
- execution_audit_event

## API

- POST /api/executions/{executionId}/reports/generate
- GET /api/executions/{executionId}/reports/latest
- GET /api/execution-reports/{reportId}
- GET /api/execution-reports/{reportId}/markdown
- POST /api/executions/{executionId}/verifications
- GET /api/executions/{executionId}/verifications
- GET /api/executions/{executionId}/audit-events

## 报告内容

- Summary
- Step Summary
- Artifact Summary
- Verification
- Audit Events

## 验收标准

1. terminal execution 可以生成 report。
2. running execution 不能生成 report。
3. report 有 markdown。
4. report 有 section。
5. verification 可以手动添加。
6. 添加 verification 会写 audit event。
7. report generate 会写 audit event。
8. Markdown 可以直接导出。
```

---

# 17. 验证命令

```powershell id="gsgmml"
mvn -pl modules/aiops-persistence -am generate-sources
mvn -pl modules/aiops-execution -am test
mvn -pl apps/aiops-server -am test
```

如果你接了 runner audit event：

```powershell id="lepy9z"
mvn -pl apps/aiops-runner -am test
```

全量：

```powershell id="21a4d8"
mvn test
```

---

# 18. 验收标准

```txt id="b3c6ap"
1. execution_report 表存在。
2. execution_report_section 表存在。
3. execution_verification 表存在。
4. execution_audit_event 表存在。
5. terminal execution 可以生成 report。
6. running execution 不能生成 report。
7. report 自动生成 Markdown。
8. report 自动生成 summary / steps / artifacts / verification / audit sections。
9. 可以查询 execution latest report。
10. 可以按 reportId 查询 report。
11. 可以导出 markdown。
12. 可以创建 before / after / manual / post verification。
13. 创建 verification 会写 execution_audit_event。
14. 生成 report 会写 execution_audit_event。
15. 不引入新的执行能力。
16. 不引入 SSH。
17. 不自动回滚。
```

---

# 19. 建议提交信息

```txt id="x0anmr"
feat(execution): add execution reports verifications and audit events
```

---

# 20. 下一步路线

Phase5.8 完成后，建议进入：

```txt id="bcf0tg"
Phase6.0 Postmortem Report
```

因为现在已经有：

```txt id="k7xfq9"
Incident
RCA
AI Diagnosis
Runbook
Approval
Execution
Rollback
Execution Report
```

Phase6.0 可以顺势把这些内容汇总成事故复盘报告：

```txt id="e3t7e9"
Incident Summary
Timeline
Root Cause
Impact
Actions Taken
Rollback
Lessons Learned
Follow-ups
```

SSH Adapter 如果还没做，建议单独放回：

```txt id="lid3y6"
Phase5.6 SSH Adapter
```

不要混进 Phase5.8。
