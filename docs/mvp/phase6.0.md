# Phase6.0：Postmortem Report

> Phase6.0 目标：把前面阶段沉淀的 **Incident / Alert / RCA / Evidence / AI Diagnosis / Execution / Rollback / Execution Report / Timeline** 汇总为一份结构化事故复盘报告。
> 注意：Phase6.0 **不新增执行能力**，不做自动修复，不做自动回滚，只做复盘报告生成、结构化沉淀、Markdown 导出和待办项管理。

---

# 1. Phase6.0 定位

前置能力：

```txt id="t3we5l"
Phase3    RCA / Evidence Chain
Phase4    AI Diagnosis / Agent Trace / Eval
Phase5.0  Runbook Recommendation
Phase5.1  Approval
Phase5.2  Runner Execution
Phase5.3  Runner Reliability / Artifact
Phase5.4  Webhook Adapter
Phase5.5  Ansible Adapter
Phase5.7  Rollback Plan
Phase5.8  Execution Report & Audit
```

Phase6.0 聚合这些数据，生成：

```txt id="ngqbfm"
Postmortem Report
  -> Summary
  -> Impact
  -> Timeline
  -> Root Cause
  -> Detection
  -> Resolution
  -> Execution / Rollback Summary
  -> Lessons Learned
  -> Follow-up Action Items
  -> Markdown Export
```

---

# 2. Phase6.0 不做什么

```txt id="21jy5f"
1. 不让 AI 自动关闭事故
2. 不让 AI 自动执行修复
3. 不让 AI 自动生成可执行回滚
4. 不引入新的 runner adapter
5. 不做向量检索
6. 不做 Case Library
7. 不做 Prompt Regression
```

这些留给后续：

```txt id="wepkpf"
Phase6.1  Incident Case Library
Phase6.2  Knowledge Base & Vector Retrieval
Phase6.3  Agent Eval & Prompt Regression
```

---

# 3. 数据流

```txt id="ip4lzf"
Incident
  -> Alert Events
  -> RCA Analysis
  -> AI Diagnosis
  -> Agent Run Trace
  -> Automation Plan
  -> Execution Run
  -> Execution Report
  -> Rollback Plan
  -> Incident Timeline
  -> Postmortem Report
```

---

# 4. API 设计

```txt id="slaklo"
POST /api/incidents/{incidentId}/postmortems/generate
GET  /api/incidents/{incidentId}/postmortems/latest
GET  /api/postmortems/{postmortemId}
GET  /api/postmortems/{postmortemId}/markdown

POST /api/postmortems/{postmortemId}/action-items
POST /api/postmortem-action-items/{actionItemId}/status
GET  /api/postmortems/{postmortemId}/action-items
```

---

# 5. 状态机

## 5.1 postmortem_report.status

```txt id="1agkv5"
draft
generated
reviewed
archived
```

## 5.2 postmortem_action_item.status

```txt id="iqy4hl"
open
in_progress
done
cancelled
```

## 5.3 postmortem_action_item.priority

```txt id="y4lqr2"
low
medium
high
critical
```

---

# 6. Migration

路径：

```txt id="tuz88c"
apps/aiops-server/src/main/resources/db/migration/V20__phase6_0_postmortem_report.sql
```

```sql id="kj76pl"
-- Phase 6.0: Postmortem Report.
-- This phase does not add execution capability.
-- It generates structured incident postmortem reports from existing incident,
-- RCA, AI diagnosis, execution, rollback, and timeline data.

create table if not exists postmortem_report (
  id varchar(64) primary key,
  tenant_id varchar(64) not null references tenant(id) on delete cascade,
  incident_id varchar(64) not null,
  status varchar(32) not null default 'generated',
  severity varchar(32),
  title varchar(240) not null,
  summary text not null,
  impact text,
  root_cause text,
  detection text,
  resolution text,
  prevention text,
  markdown text not null,
  source_snapshot jsonb not null default '{}'::jsonb,
  generated_by varchar(64) not null,
  generated_at timestamptz not null default now(),
  reviewed_by varchar(64),
  reviewed_at timestamptz,
  archived_at timestamptz,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  constraint ck_postmortem_report_status
    check (status in ('draft', 'generated', 'reviewed', 'archived')),
  constraint ck_postmortem_report_severity
    check (severity is null or severity in ('info', 'low', 'medium', 'high', 'critical'))
);

create index if not exists idx_postmortem_report_incident
  on postmortem_report(tenant_id, incident_id, generated_at desc);

create index if not exists idx_postmortem_report_status
  on postmortem_report(tenant_id, status, generated_at desc);

create table if not exists postmortem_section (
  id varchar(64) primary key,
  tenant_id varchar(64) not null references tenant(id) on delete cascade,
  postmortem_id varchar(64) not null references postmortem_report(id) on delete cascade,
  section_order int not null,
  section_type varchar(64) not null,
  title varchar(240) not null,
  content text not null,
  metadata jsonb not null default '{}'::jsonb,
  created_at timestamptz not null default now(),
  constraint uq_postmortem_section_order
    unique (tenant_id, postmortem_id, section_order)
);

create index if not exists idx_postmortem_section_postmortem
  on postmortem_section(tenant_id, postmortem_id, section_order);

create table if not exists postmortem_action_item (
  id varchar(64) primary key,
  tenant_id varchar(64) not null references tenant(id) on delete cascade,
  postmortem_id varchar(64) not null references postmortem_report(id) on delete cascade,
  title varchar(240) not null,
  description text,
  owner varchar(64),
  priority varchar(32) not null default 'medium',
  status varchar(32) not null default 'open',
  due_date date,
  source_type varchar(64) not null default 'manual',
  source_ref_id varchar(64),
  created_by varchar(64) not null,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  constraint ck_postmortem_action_item_priority
    check (priority in ('low', 'medium', 'high', 'critical')),
  constraint ck_postmortem_action_item_status
    check (status in ('open', 'in_progress', 'done', 'cancelled')),
  constraint ck_postmortem_action_item_source_type
    check (source_type in ('manual', 'rca', 'ai_diagnosis', 'execution', 'rollback'))
);

create index if not exists idx_postmortem_action_item_postmortem
  on postmortem_action_item(tenant_id, postmortem_id, created_at);

create index if not exists idx_postmortem_action_item_owner_status
  on postmortem_action_item(tenant_id, owner, status);
```

---

# 7. jOOQ Codegen

路径：

```txt id="vynnp8"
modules/aiops-persistence/src/main/resources/jooq-codegen.xml
```

`includes` 追加：

```txt id="gd1eqi"
postmortem_report | postmortem_section | postmortem_action_item
```

完整 includes 建议：

```xml id="1ib0kc"
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
  execution_report | execution_report_section | execution_verification | execution_audit_event |
  postmortem_report | postmortem_section | postmortem_action_item
</includes>
```

---

# 8. DTO 完整代码

## 8.1 `PostmortemGenerateRequest.java`

路径：

```txt id="up2m8f"
modules/aiops-execution/src/main/java/io/aegisops/execution/dto/PostmortemGenerateRequest.java
```

```java id="k4ax2r"
package io.aegisops.execution.dto;

public record PostmortemGenerateRequest(
    String generatedBy,
    Boolean includeAiDiagnosis,
    Boolean includeRca,
    Boolean includeExecutions,
    Boolean includeRollback,
    Boolean includeTimeline,
    Boolean generateActionItems) {}
```

---

## 8.2 `PostmortemReportCreateCommand.java`

```java id="17fug8"
package io.aegisops.execution.dto;

public record PostmortemReportCreateCommand(
    String id,
    String tenantId,
    String incidentId,
    String status,
    String severity,
    String title,
    String summary,
    String impact,
    String rootCause,
    String detection,
    String resolution,
    String prevention,
    String markdown,
    String sourceSnapshotJson,
    String generatedBy) {}
```

---

## 8.3 `PostmortemSectionCreateCommand.java`

```java id="ldmdw3"
package io.aegisops.execution.dto;

public record PostmortemSectionCreateCommand(
    String id,
    String tenantId,
    String postmortemId,
    int sectionOrder,
    String sectionType,
    String title,
    String content,
    String metadataJson) {}
```

---

## 8.4 `PostmortemActionItemCreateRequest.java`

```java id="tf13bc"
package io.aegisops.execution.dto;

import java.time.LocalDate;

public record PostmortemActionItemCreateRequest(
    String title,
    String description,
    String owner,
    String priority,
    LocalDate dueDate,
    String sourceType,
    String sourceRefId,
    String createdBy) {}
```

---

## 8.5 `PostmortemActionItemCreateCommand.java`

```java id="6upijm"
package io.aegisops.execution.dto;

import java.time.LocalDate;

public record PostmortemActionItemCreateCommand(
    String id,
    String tenantId,
    String postmortemId,
    String title,
    String description,
    String owner,
    String priority,
    String status,
    LocalDate dueDate,
    String sourceType,
    String sourceRefId,
    String createdBy) {}
```

---

## 8.6 `PostmortemActionItemStatusRequest.java`

```java id="278935"
package io.aegisops.execution.dto;

public record PostmortemActionItemStatusRequest(
    String status,
    String operator) {}
```

---

## 8.7 `PostmortemReportRecord.java`

```java id="x4tyv1"
package io.aegisops.execution.dto;

import java.time.OffsetDateTime;

public record PostmortemReportRecord(
    String id,
    String tenantId,
    String incidentId,
    String status,
    String severity,
    String title,
    String summary,
    String impact,
    String rootCause,
    String detection,
    String resolution,
    String prevention,
    String markdown,
    String sourceSnapshotJson,
    String generatedBy,
    OffsetDateTime generatedAt,
    String reviewedBy,
    OffsetDateTime reviewedAt,
    OffsetDateTime archivedAt,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt) {}
```

---

## 8.8 `PostmortemSectionRecord.java`

```java id="ih86lm"
package io.aegisops.execution.dto;

import java.time.OffsetDateTime;

public record PostmortemSectionRecord(
    String id,
    String tenantId,
    String postmortemId,
    int sectionOrder,
    String sectionType,
    String title,
    String content,
    String metadataJson,
    OffsetDateTime createdAt) {}
```

---

## 8.9 `PostmortemActionItemRecord.java`

```java id="44q9l8"
package io.aegisops.execution.dto;

import java.time.LocalDate;
import java.time.OffsetDateTime;

public record PostmortemActionItemRecord(
    String id,
    String tenantId,
    String postmortemId,
    String title,
    String description,
    String owner,
    String priority,
    String status,
    LocalDate dueDate,
    String sourceType,
    String sourceRefId,
    String createdBy,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt) {}
```

---

## 8.10 `PostmortemReportResponse.java`

```java id="g9ivp9"
package io.aegisops.execution.dto;

import java.time.OffsetDateTime;
import java.util.List;

public record PostmortemReportResponse(
    String id,
    String tenantId,
    String incidentId,
    String status,
    String severity,
    String title,
    String summary,
    String impact,
    String rootCause,
    String detection,
    String resolution,
    String prevention,
    String markdown,
    String sourceSnapshotJson,
    String generatedBy,
    OffsetDateTime generatedAt,
    List<PostmortemSectionResponse> sections,
    List<PostmortemActionItemResponse> actionItems,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt) {}
```

---

## 8.11 `PostmortemSectionResponse.java`

```java id="dcar3n"
package io.aegisops.execution.dto;

import java.time.OffsetDateTime;

public record PostmortemSectionResponse(
    String id,
    int sectionOrder,
    String sectionType,
    String title,
    String content,
    String metadataJson,
    OffsetDateTime createdAt) {}
```

---

## 8.12 `PostmortemActionItemResponse.java`

```java id="kwk79f"
package io.aegisops.execution.dto;

import java.time.LocalDate;
import java.time.OffsetDateTime;

public record PostmortemActionItemResponse(
    String id,
    String title,
    String description,
    String owner,
    String priority,
    String status,
    LocalDate dueDate,
    String sourceType,
    String sourceRefId,
    String createdBy,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt) {}
```

---

# 9. Source Snapshot DTO

这些 DTO 用于从现有数据源聚合复盘信息。

路径：

```txt id="d5clkw"
modules/aiops-execution/src/main/java/io/aegisops/execution/dto/PostmortemSourceBundle.java
```

```java id="17l9oy"
package io.aegisops.execution.dto;

import java.time.OffsetDateTime;
import java.util.List;

public record PostmortemSourceBundle(
    IncidentSnapshot incident,
    List<RcaSnapshot> rcaAnalyses,
    List<AiDiagnosisSnapshot> aiDiagnoses,
    List<ExecutionSnapshot> executions,
    List<RollbackSnapshot> rollbackPlans,
    List<TimelineSnapshot> timeline) {
  public record IncidentSnapshot(
      String id,
      String title,
      String status,
      String severity,
      OffsetDateTime createdAt,
      OffsetDateTime updatedAt) {}

  public record RcaSnapshot(
      String id,
      String summary,
      String rootCause,
      String confidence,
      OffsetDateTime createdAt) {}

  public record AiDiagnosisSnapshot(
      String id,
      String summary,
      String rootCause,
      String recommendation,
      String confidence,
      OffsetDateTime createdAt) {}

  public record ExecutionSnapshot(
      String id,
      String mode,
      String executionKind,
      String status,
      String summary,
      OffsetDateTime startedAt,
      OffsetDateTime finishedAt) {}

  public record RollbackSnapshot(
      String id,
      String status,
      String reason,
      OffsetDateTime createdAt,
      OffsetDateTime updatedAt) {}

  public record TimelineSnapshot(
      String id,
      String eventType,
      String title,
      String content,
      OffsetDateTime createdAt) {}
}
```

---

# 10. Json 工具

路径：

```txt id="ddh5fl"
modules/aiops-execution/src/main/java/io/aegisops/execution/PostmortemJson.java
```

```java id="bu1oac"
package io.aegisops.execution;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.aegisops.common.exception.AppException;

public class PostmortemJson {
  private final ObjectMapper objectMapper;

  public PostmortemJson(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  public String write(Object value) {
    try {
      if (value == null) {
        return "{}";
      }
      return objectMapper.writeValueAsString(value);
    } catch (Exception ex) {
      throw new AppException("POSTMORTEM_JSON_WRITE_FAILED", "Failed to serialize postmortem json");
    }
  }
}
```

---

# 11. Repository 接口

## 11.1 `PostmortemRepository.java`

路径：

```txt id="rg7b4m"
modules/aiops-execution/src/main/java/io/aegisops/execution/PostmortemRepository.java
```

```java id="44gdfj"
package io.aegisops.execution;

import io.aegisops.execution.dto.PostmortemActionItemCreateCommand;
import io.aegisops.execution.dto.PostmortemActionItemRecord;
import io.aegisops.execution.dto.PostmortemReportCreateCommand;
import io.aegisops.execution.dto.PostmortemReportRecord;
import io.aegisops.execution.dto.PostmortemSectionCreateCommand;
import io.aegisops.execution.dto.PostmortemSectionRecord;
import java.util.List;
import java.util.Optional;

public interface PostmortemRepository {
  void createReport(PostmortemReportCreateCommand command);

  void createSection(PostmortemSectionCreateCommand command);

  Optional<PostmortemReportRecord> findReport(String tenantId, String postmortemId);

  Optional<PostmortemReportRecord> findLatestByIncident(String tenantId, String incidentId);

  List<PostmortemSectionRecord> listSections(String tenantId, String postmortemId);

  void createActionItem(PostmortemActionItemCreateCommand command);

  List<PostmortemActionItemRecord> listActionItems(String tenantId, String postmortemId);

  Optional<PostmortemActionItemRecord> findActionItem(String tenantId, String actionItemId);

  boolean updateActionItemStatus(String tenantId, String actionItemId, String status);
}
```

---

## 11.2 `PostmortemSourceRepository.java`

```java id="o56sep"
package io.aegisops.execution;

import io.aegisops.execution.dto.PostmortemSourceBundle;
import java.util.Optional;

public interface PostmortemSourceRepository {
  Optional<PostmortemSourceBundle> load(String tenantId, String incidentId);
}
```

---

# 12. jOOQ Repository 实现

## 12.1 `JooqPostmortemRepository.java`

路径：

```txt id="3kck5s"
modules/aiops-execution/src/main/java/io/aegisops/execution/JooqPostmortemRepository.java
```

```java id="4c64by"
package io.aegisops.execution;

import static io.aegisops.persistence.AegisJooq.jsonbValue;
import static io.aegisops.persistence.jooq.Tables.POSTMORTEM_ACTION_ITEM;
import static io.aegisops.persistence.jooq.Tables.POSTMORTEM_REPORT;
import static io.aegisops.persistence.jooq.Tables.POSTMORTEM_SECTION;

import io.aegisops.execution.dto.PostmortemActionItemCreateCommand;
import io.aegisops.execution.dto.PostmortemActionItemRecord;
import io.aegisops.execution.dto.PostmortemReportCreateCommand;
import io.aegisops.execution.dto.PostmortemReportRecord;
import io.aegisops.execution.dto.PostmortemSectionCreateCommand;
import io.aegisops.execution.dto.PostmortemSectionRecord;
import java.util.List;
import java.util.Optional;
import org.jooq.DSLContext;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Repository;

@Repository
public class JooqPostmortemRepository implements PostmortemRepository {
  private final DSLContext dsl;

  public JooqPostmortemRepository(DSLContext dsl) {
    this.dsl = dsl;
  }

  @Override
  public void createReport(PostmortemReportCreateCommand command) {
    dsl.insertInto(POSTMORTEM_REPORT)
        .set(POSTMORTEM_REPORT.ID, command.id())
        .set(POSTMORTEM_REPORT.TENANT_ID, command.tenantId())
        .set(POSTMORTEM_REPORT.INCIDENT_ID, command.incidentId())
        .set(POSTMORTEM_REPORT.STATUS, command.status())
        .set(POSTMORTEM_REPORT.SEVERITY, command.severity())
        .set(POSTMORTEM_REPORT.TITLE, command.title())
        .set(POSTMORTEM_REPORT.SUMMARY, command.summary())
        .set(POSTMORTEM_REPORT.IMPACT, command.impact())
        .set(POSTMORTEM_REPORT.ROOT_CAUSE, command.rootCause())
        .set(POSTMORTEM_REPORT.DETECTION, command.detection())
        .set(POSTMORTEM_REPORT.RESOLUTION, command.resolution())
        .set(POSTMORTEM_REPORT.PREVENTION, command.prevention())
        .set(POSTMORTEM_REPORT.MARKDOWN, command.markdown())
        .set(POSTMORTEM_REPORT.SOURCE_SNAPSHOT, jsonbValue(command.sourceSnapshotJson()))
        .set(POSTMORTEM_REPORT.GENERATED_BY, command.generatedBy())
        .set(POSTMORTEM_REPORT.GENERATED_AT, DSL.currentOffsetDateTime())
        .set(POSTMORTEM_REPORT.CREATED_AT, DSL.currentOffsetDateTime())
        .set(POSTMORTEM_REPORT.UPDATED_AT, DSL.currentOffsetDateTime())
        .execute();
  }

  @Override
  public void createSection(PostmortemSectionCreateCommand command) {
    dsl.insertInto(POSTMORTEM_SECTION)
        .set(POSTMORTEM_SECTION.ID, command.id())
        .set(POSTMORTEM_SECTION.TENANT_ID, command.tenantId())
        .set(POSTMORTEM_SECTION.POSTMORTEM_ID, command.postmortemId())
        .set(POSTMORTEM_SECTION.SECTION_ORDER, command.sectionOrder())
        .set(POSTMORTEM_SECTION.SECTION_TYPE, command.sectionType())
        .set(POSTMORTEM_SECTION.TITLE, command.title())
        .set(POSTMORTEM_SECTION.CONTENT, command.content())
        .set(POSTMORTEM_SECTION.METADATA, jsonbValue(command.metadataJson()))
        .set(POSTMORTEM_SECTION.CREATED_AT, DSL.currentOffsetDateTime())
        .execute();
  }

  @Override
  public Optional<PostmortemReportRecord> findReport(String tenantId, String postmortemId) {
    return selectReport()
        .where(POSTMORTEM_REPORT.TENANT_ID.eq(tenantId))
        .and(POSTMORTEM_REPORT.ID.eq(postmortemId))
        .fetchOptional(this::toReportRecord);
  }

  @Override
  public Optional<PostmortemReportRecord> findLatestByIncident(String tenantId, String incidentId) {
    return selectReport()
        .where(POSTMORTEM_REPORT.TENANT_ID.eq(tenantId))
        .and(POSTMORTEM_REPORT.INCIDENT_ID.eq(incidentId))
        .orderBy(POSTMORTEM_REPORT.GENERATED_AT.desc())
        .limit(1)
        .fetchOptional(this::toReportRecord);
  }

  @Override
  public List<PostmortemSectionRecord> listSections(String tenantId, String postmortemId) {
    return dsl.select(
            POSTMORTEM_SECTION.ID,
            POSTMORTEM_SECTION.TENANT_ID,
            POSTMORTEM_SECTION.POSTMORTEM_ID,
            POSTMORTEM_SECTION.SECTION_ORDER,
            POSTMORTEM_SECTION.SECTION_TYPE,
            POSTMORTEM_SECTION.TITLE,
            POSTMORTEM_SECTION.CONTENT,
            POSTMORTEM_SECTION.METADATA.cast(String.class).as("metadata_json"),
            POSTMORTEM_SECTION.CREATED_AT)
        .from(POSTMORTEM_SECTION)
        .where(POSTMORTEM_SECTION.TENANT_ID.eq(tenantId))
        .and(POSTMORTEM_SECTION.POSTMORTEM_ID.eq(postmortemId))
        .orderBy(POSTMORTEM_SECTION.SECTION_ORDER.asc())
        .fetch(this::toSectionRecord);
  }

  @Override
  public void createActionItem(PostmortemActionItemCreateCommand command) {
    dsl.insertInto(POSTMORTEM_ACTION_ITEM)
        .set(POSTMORTEM_ACTION_ITEM.ID, command.id())
        .set(POSTMORTEM_ACTION_ITEM.TENANT_ID, command.tenantId())
        .set(POSTMORTEM_ACTION_ITEM.POSTMORTEM_ID, command.postmortemId())
        .set(POSTMORTEM_ACTION_ITEM.TITLE, command.title())
        .set(POSTMORTEM_ACTION_ITEM.DESCRIPTION, command.description())
        .set(POSTMORTEM_ACTION_ITEM.OWNER, command.owner())
        .set(POSTMORTEM_ACTION_ITEM.PRIORITY, command.priority())
        .set(POSTMORTEM_ACTION_ITEM.STATUS, command.status())
        .set(POSTMORTEM_ACTION_ITEM.DUE_DATE, command.dueDate())
        .set(POSTMORTEM_ACTION_ITEM.SOURCE_TYPE, command.sourceType())
        .set(POSTMORTEM_ACTION_ITEM.SOURCE_REF_ID, command.sourceRefId())
        .set(POSTMORTEM_ACTION_ITEM.CREATED_BY, command.createdBy())
        .set(POSTMORTEM_ACTION_ITEM.CREATED_AT, DSL.currentOffsetDateTime())
        .set(POSTMORTEM_ACTION_ITEM.UPDATED_AT, DSL.currentOffsetDateTime())
        .execute();
  }

  @Override
  public List<PostmortemActionItemRecord> listActionItems(String tenantId, String postmortemId) {
    return dsl.select(
            POSTMORTEM_ACTION_ITEM.ID,
            POSTMORTEM_ACTION_ITEM.TENANT_ID,
            POSTMORTEM_ACTION_ITEM.POSTMORTEM_ID,
            POSTMORTEM_ACTION_ITEM.TITLE,
            POSTMORTEM_ACTION_ITEM.DESCRIPTION,
            POSTMORTEM_ACTION_ITEM.OWNER,
            POSTMORTEM_ACTION_ITEM.PRIORITY,
            POSTMORTEM_ACTION_ITEM.STATUS,
            POSTMORTEM_ACTION_ITEM.DUE_DATE,
            POSTMORTEM_ACTION_ITEM.SOURCE_TYPE,
            POSTMORTEM_ACTION_ITEM.SOURCE_REF_ID,
            POSTMORTEM_ACTION_ITEM.CREATED_BY,
            POSTMORTEM_ACTION_ITEM.CREATED_AT,
            POSTMORTEM_ACTION_ITEM.UPDATED_AT)
        .from(POSTMORTEM_ACTION_ITEM)
        .where(POSTMORTEM_ACTION_ITEM.TENANT_ID.eq(tenantId))
        .and(POSTMORTEM_ACTION_ITEM.POSTMORTEM_ID.eq(postmortemId))
        .orderBy(POSTMORTEM_ACTION_ITEM.CREATED_AT.asc())
        .fetch(this::toActionItemRecord);
  }

  @Override
  public Optional<PostmortemActionItemRecord> findActionItem(String tenantId, String actionItemId) {
    return dsl.select(
            POSTMORTEM_ACTION_ITEM.ID,
            POSTMORTEM_ACTION_ITEM.TENANT_ID,
            POSTMORTEM_ACTION_ITEM.POSTMORTEM_ID,
            POSTMORTEM_ACTION_ITEM.TITLE,
            POSTMORTEM_ACTION_ITEM.DESCRIPTION,
            POSTMORTEM_ACTION_ITEM.OWNER,
            POSTMORTEM_ACTION_ITEM.PRIORITY,
            POSTMORTEM_ACTION_ITEM.STATUS,
            POSTMORTEM_ACTION_ITEM.DUE_DATE,
            POSTMORTEM_ACTION_ITEM.SOURCE_TYPE,
            POSTMORTEM_ACTION_ITEM.SOURCE_REF_ID,
            POSTMORTEM_ACTION_ITEM.CREATED_BY,
            POSTMORTEM_ACTION_ITEM.CREATED_AT,
            POSTMORTEM_ACTION_ITEM.UPDATED_AT)
        .from(POSTMORTEM_ACTION_ITEM)
        .where(POSTMORTEM_ACTION_ITEM.TENANT_ID.eq(tenantId))
        .and(POSTMORTEM_ACTION_ITEM.ID.eq(actionItemId))
        .fetchOptional(this::toActionItemRecord);
  }

  @Override
  public boolean updateActionItemStatus(String tenantId, String actionItemId, String status) {
    return dsl.update(POSTMORTEM_ACTION_ITEM)
            .set(POSTMORTEM_ACTION_ITEM.STATUS, status)
            .set(POSTMORTEM_ACTION_ITEM.UPDATED_AT, DSL.currentOffsetDateTime())
            .where(POSTMORTEM_ACTION_ITEM.TENANT_ID.eq(tenantId))
            .and(POSTMORTEM_ACTION_ITEM.ID.eq(actionItemId))
            .execute()
        > 0;
  }

  private org.jooq.SelectJoinStep<org.jooq.Record> selectReport() {
    return dsl.select(
            POSTMORTEM_REPORT.ID,
            POSTMORTEM_REPORT.TENANT_ID,
            POSTMORTEM_REPORT.INCIDENT_ID,
            POSTMORTEM_REPORT.STATUS,
            POSTMORTEM_REPORT.SEVERITY,
            POSTMORTEM_REPORT.TITLE,
            POSTMORTEM_REPORT.SUMMARY,
            POSTMORTEM_REPORT.IMPACT,
            POSTMORTEM_REPORT.ROOT_CAUSE,
            POSTMORTEM_REPORT.DETECTION,
            POSTMORTEM_REPORT.RESOLUTION,
            POSTMORTEM_REPORT.PREVENTION,
            POSTMORTEM_REPORT.MARKDOWN,
            POSTMORTEM_REPORT.SOURCE_SNAPSHOT.cast(String.class).as("source_snapshot_json"),
            POSTMORTEM_REPORT.GENERATED_BY,
            POSTMORTEM_REPORT.GENERATED_AT,
            POSTMORTEM_REPORT.REVIEWED_BY,
            POSTMORTEM_REPORT.REVIEWED_AT,
            POSTMORTEM_REPORT.ARCHIVED_AT,
            POSTMORTEM_REPORT.CREATED_AT,
            POSTMORTEM_REPORT.UPDATED_AT)
        .from(POSTMORTEM_REPORT);
  }

  private PostmortemReportRecord toReportRecord(org.jooq.Record record) {
    return new PostmortemReportRecord(
        record.get(POSTMORTEM_REPORT.ID),
        record.get(POSTMORTEM_REPORT.TENANT_ID),
        record.get(POSTMORTEM_REPORT.INCIDENT_ID),
        record.get(POSTMORTEM_REPORT.STATUS),
        record.get(POSTMORTEM_REPORT.SEVERITY),
        record.get(POSTMORTEM_REPORT.TITLE),
        record.get(POSTMORTEM_REPORT.SUMMARY),
        record.get(POSTMORTEM_REPORT.IMPACT),
        record.get(POSTMORTEM_REPORT.ROOT_CAUSE),
        record.get(POSTMORTEM_REPORT.DETECTION),
        record.get(POSTMORTEM_REPORT.RESOLUTION),
        record.get(POSTMORTEM_REPORT.PREVENTION),
        record.get(POSTMORTEM_REPORT.MARKDOWN),
        record.get("source_snapshot_json", String.class),
        record.get(POSTMORTEM_REPORT.GENERATED_BY),
        record.get(POSTMORTEM_REPORT.GENERATED_AT),
        record.get(POSTMORTEM_REPORT.REVIEWED_BY),
        record.get(POSTMORTEM_REPORT.REVIEWED_AT),
        record.get(POSTMORTEM_REPORT.ARCHIVED_AT),
        record.get(POSTMORTEM_REPORT.CREATED_AT),
        record.get(POSTMORTEM_REPORT.UPDATED_AT));
  }

  private PostmortemSectionRecord toSectionRecord(org.jooq.Record record) {
    return new PostmortemSectionRecord(
        record.get(POSTMORTEM_SECTION.ID),
        record.get(POSTMORTEM_SECTION.TENANT_ID),
        record.get(POSTMORTEM_SECTION.POSTMORTEM_ID),
        value(record.get(POSTMORTEM_SECTION.SECTION_ORDER)),
        record.get(POSTMORTEM_SECTION.SECTION_TYPE),
        record.get(POSTMORTEM_SECTION.TITLE),
        record.get(POSTMORTEM_SECTION.CONTENT),
        record.get("metadata_json", String.class),
        record.get(POSTMORTEM_SECTION.CREATED_AT));
  }

  private PostmortemActionItemRecord toActionItemRecord(org.jooq.Record record) {
    return new PostmortemActionItemRecord(
        record.get(POSTMORTEM_ACTION_ITEM.ID),
        record.get(POSTMORTEM_ACTION_ITEM.TENANT_ID),
        record.get(POSTMORTEM_ACTION_ITEM.POSTMORTEM_ID),
        record.get(POSTMORTEM_ACTION_ITEM.TITLE),
        record.get(POSTMORTEM_ACTION_ITEM.DESCRIPTION),
        record.get(POSTMORTEM_ACTION_ITEM.OWNER),
        record.get(POSTMORTEM_ACTION_ITEM.PRIORITY),
        record.get(POSTMORTEM_ACTION_ITEM.STATUS),
        record.get(POSTMORTEM_ACTION_ITEM.DUE_DATE),
        record.get(POSTMORTEM_ACTION_ITEM.SOURCE_TYPE),
        record.get(POSTMORTEM_ACTION_ITEM.SOURCE_REF_ID),
        record.get(POSTMORTEM_ACTION_ITEM.CREATED_BY),
        record.get(POSTMORTEM_ACTION_ITEM.CREATED_AT),
        record.get(POSTMORTEM_ACTION_ITEM.UPDATED_AT));
  }

  private int value(Integer value) {
    return value == null ? 0 : value;
  }
}
```

---

# 13. Source Repository

## 13.1 `JooqPostmortemSourceRepository.java`

路径：

```txt id="uyd6x5"
modules/aiops-execution/src/main/java/io/aegisops/execution/JooqPostmortemSourceRepository.java
```

```java id="z5ihjx"
package io.aegisops.execution;

import static io.aegisops.persistence.jooq.Tables.AI_DIAGNOSIS;
import static io.aegisops.persistence.jooq.Tables.EXECUTION_RUN;
import static io.aegisops.persistence.jooq.Tables.INCIDENT;
import static io.aegisops.persistence.jooq.Tables.INCIDENT_TIMELINE;
import static io.aegisops.persistence.jooq.Tables.RCA_ANALYSIS;
import static io.aegisops.persistence.jooq.Tables.ROLLBACK_PLAN;

import io.aegisops.execution.dto.PostmortemSourceBundle;
import io.aegisops.execution.dto.PostmortemSourceBundle.AiDiagnosisSnapshot;
import io.aegisops.execution.dto.PostmortemSourceBundle.ExecutionSnapshot;
import io.aegisops.execution.dto.PostmortemSourceBundle.IncidentSnapshot;
import io.aegisops.execution.dto.PostmortemSourceBundle.RcaSnapshot;
import io.aegisops.execution.dto.PostmortemSourceBundle.RollbackSnapshot;
import io.aegisops.execution.dto.PostmortemSourceBundle.TimelineSnapshot;
import java.util.List;
import java.util.Optional;
import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;

@Repository
public class JooqPostmortemSourceRepository implements PostmortemSourceRepository {
  private final DSLContext dsl;

  public JooqPostmortemSourceRepository(DSLContext dsl) {
    this.dsl = dsl;
  }

  @Override
  public Optional<PostmortemSourceBundle> load(String tenantId, String incidentId) {
    Optional<IncidentSnapshot> incident = loadIncident(tenantId, incidentId);
    if (incident.isEmpty()) {
      return Optional.empty();
    }

    return Optional.of(
        new PostmortemSourceBundle(
            incident.get(),
            loadRca(tenantId, incidentId),
            loadAiDiagnosis(tenantId, incidentId),
            loadExecutions(tenantId, incidentId),
            loadRollbackPlans(tenantId, incidentId),
            loadTimeline(tenantId, incidentId)));
  }

  private Optional<IncidentSnapshot> loadIncident(String tenantId, String incidentId) {
    return dsl.select(
            INCIDENT.ID,
            INCIDENT.TITLE,
            INCIDENT.STATUS,
            INCIDENT.SEVERITY,
            INCIDENT.CREATED_AT,
            INCIDENT.UPDATED_AT)
        .from(INCIDENT)
        .where(INCIDENT.TENANT_ID.eq(tenantId))
        .and(INCIDENT.ID.eq(incidentId))
        .fetchOptional(
            record ->
                new IncidentSnapshot(
                    record.get(INCIDENT.ID),
                    record.get(INCIDENT.TITLE),
                    record.get(INCIDENT.STATUS),
                    record.get(INCIDENT.SEVERITY),
                    record.get(INCIDENT.CREATED_AT),
                    record.get(INCIDENT.UPDATED_AT)));
  }

  private List<RcaSnapshot> loadRca(String tenantId, String incidentId) {
    return dsl.select(
            RCA_ANALYSIS.ID,
            RCA_ANALYSIS.SUMMARY,
            RCA_ANALYSIS.ROOT_CAUSE,
            RCA_ANALYSIS.CONFIDENCE,
            RCA_ANALYSIS.CREATED_AT)
        .from(RCA_ANALYSIS)
        .where(RCA_ANALYSIS.TENANT_ID.eq(tenantId))
        .and(RCA_ANALYSIS.INCIDENT_ID.eq(incidentId))
        .orderBy(RCA_ANALYSIS.CREATED_AT.desc())
        .fetch(
            record ->
                new RcaSnapshot(
                    record.get(RCA_ANALYSIS.ID),
                    record.get(RCA_ANALYSIS.SUMMARY),
                    record.get(RCA_ANALYSIS.ROOT_CAUSE),
                    String.valueOf(record.get(RCA_ANALYSIS.CONFIDENCE)),
                    record.get(RCA_ANALYSIS.CREATED_AT)));
  }

  private List<AiDiagnosisSnapshot> loadAiDiagnosis(String tenantId, String incidentId) {
    return dsl.select(
            AI_DIAGNOSIS.ID,
            AI_DIAGNOSIS.SUMMARY,
            AI_DIAGNOSIS.ROOT_CAUSE,
            AI_DIAGNOSIS.RECOMMENDATION,
            AI_DIAGNOSIS.CONFIDENCE,
            AI_DIAGNOSIS.CREATED_AT)
        .from(AI_DIAGNOSIS)
        .where(AI_DIAGNOSIS.TENANT_ID.eq(tenantId))
        .and(AI_DIAGNOSIS.INCIDENT_ID.eq(incidentId))
        .orderBy(AI_DIAGNOSIS.CREATED_AT.desc())
        .fetch(
            record ->
                new AiDiagnosisSnapshot(
                    record.get(AI_DIAGNOSIS.ID),
                    record.get(AI_DIAGNOSIS.SUMMARY),
                    record.get(AI_DIAGNOSIS.ROOT_CAUSE),
                    record.get(AI_DIAGNOSIS.RECOMMENDATION),
                    String.valueOf(record.get(AI_DIAGNOSIS.CONFIDENCE)),
                    record.get(AI_DIAGNOSIS.CREATED_AT)));
  }

  private List<ExecutionSnapshot> loadExecutions(String tenantId, String incidentId) {
    return dsl.select(
            EXECUTION_RUN.ID,
            EXECUTION_RUN.MODE,
            EXECUTION_RUN.EXECUTION_KIND,
            EXECUTION_RUN.STATUS,
            EXECUTION_RUN.SUMMARY,
            EXECUTION_RUN.STARTED_AT,
            EXECUTION_RUN.FINISHED_AT)
        .from(EXECUTION_RUN)
        .where(EXECUTION_RUN.TENANT_ID.eq(tenantId))
        .and(EXECUTION_RUN.INCIDENT_ID.eq(incidentId))
        .orderBy(EXECUTION_RUN.CREATED_AT.asc())
        .fetch(
            record ->
                new ExecutionSnapshot(
                    record.get(EXECUTION_RUN.ID),
                    record.get(EXECUTION_RUN.MODE),
                    record.get(EXECUTION_RUN.EXECUTION_KIND),
                    record.get(EXECUTION_RUN.STATUS),
                    record.get(EXECUTION_RUN.SUMMARY),
                    record.get(EXECUTION_RUN.STARTED_AT),
                    record.get(EXECUTION_RUN.FINISHED_AT)));
  }

  private List<RollbackSnapshot> loadRollbackPlans(String tenantId, String incidentId) {
    return dsl.select(
            ROLLBACK_PLAN.ID,
            ROLLBACK_PLAN.STATUS,
            ROLLBACK_PLAN.REASON,
            ROLLBACK_PLAN.CREATED_AT,
            ROLLBACK_PLAN.UPDATED_AT)
        .from(ROLLBACK_PLAN)
        .where(ROLLBACK_PLAN.TENANT_ID.eq(tenantId))
        .and(ROLLBACK_PLAN.INCIDENT_ID.eq(incidentId))
        .orderBy(ROLLBACK_PLAN.CREATED_AT.asc())
        .fetch(
            record ->
                new RollbackSnapshot(
                    record.get(ROLLBACK_PLAN.ID),
                    record.get(ROLLBACK_PLAN.STATUS),
                    record.get(ROLLBACK_PLAN.REASON),
                    record.get(ROLLBACK_PLAN.CREATED_AT),
                    record.get(ROLLBACK_PLAN.UPDATED_AT)));
  }

  private List<TimelineSnapshot> loadTimeline(String tenantId, String incidentId) {
    return dsl.select(
            INCIDENT_TIMELINE.ID,
            INCIDENT_TIMELINE.EVENT_TYPE,
            INCIDENT_TIMELINE.TITLE,
            INCIDENT_TIMELINE.CONTENT,
            INCIDENT_TIMELINE.CREATED_AT)
        .from(INCIDENT_TIMELINE)
        .where(INCIDENT_TIMELINE.TENANT_ID.eq(tenantId))
        .and(INCIDENT_TIMELINE.INCIDENT_ID.eq(incidentId))
        .orderBy(INCIDENT_TIMELINE.CREATED_AT.asc())
        .fetch(
            record ->
                new TimelineSnapshot(
                    record.get(INCIDENT_TIMELINE.ID),
                    record.get(INCIDENT_TIMELINE.EVENT_TYPE),
                    record.get(INCIDENT_TIMELINE.TITLE),
                    record.get(INCIDENT_TIMELINE.CONTENT),
                    record.get(INCIDENT_TIMELINE.CREATED_AT)));
  }
}
```

> 如果你现有 generated table 字段名和这里略有差异，比如 `AI_DIAGNOSIS.RECOMMENDATION` 实际叫 `NEXT_STEPS`，按生成类调整字段即可。Phase6.0 的核心结构不变。

---

# 14. Markdown Builder

## 14.1 `PostmortemMarkdownBuilder.java`

路径：

```txt id="8zayvv"
modules/aiops-execution/src/main/java/io/aegisops/execution/PostmortemMarkdownBuilder.java
```

```java id="65c1jw"
package io.aegisops.execution;

import io.aegisops.execution.dto.PostmortemSourceBundle;
import io.aegisops.execution.dto.PostmortemSourceBundle.AiDiagnosisSnapshot;
import io.aegisops.execution.dto.PostmortemSourceBundle.ExecutionSnapshot;
import io.aegisops.execution.dto.PostmortemSourceBundle.RcaSnapshot;
import io.aegisops.execution.dto.PostmortemSourceBundle.RollbackSnapshot;
import io.aegisops.execution.dto.PostmortemSourceBundle.TimelineSnapshot;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class PostmortemMarkdownBuilder {
  public String build(
      PostmortemSourceBundle source,
      String summary,
      String impact,
      String rootCause,
      String detection,
      String resolution,
      String prevention,
      List<String> actionItems) {
    StringBuilder md = new StringBuilder();

    md.append("# Postmortem Report\n\n");

    md.append("## Incident Summary\n\n");
    md.append("- Incident ID: ").append(value(source.incident().id())).append("\n");
    md.append("- Title: ").append(escape(source.incident().title())).append("\n");
    md.append("- Severity: ").append(value(source.incident().severity())).append("\n");
    md.append("- Status: ").append(value(source.incident().status())).append("\n");
    md.append("- Created At: ").append(value(source.incident().createdAt())).append("\n");
    md.append("- Updated At: ").append(value(source.incident().updatedAt())).append("\n\n");
    md.append(summary).append("\n\n");

    md.append("## Impact\n\n");
    md.append(blankToFallback(impact, "Impact was not explicitly recorded.")).append("\n\n");

    md.append("## Timeline\n\n");
    appendTimeline(md, source.timeline());

    md.append("\n## Root Cause\n\n");
    md.append(blankToFallback(rootCause, "Root cause is unknown or not confirmed.")).append("\n\n");

    md.append("## Detection\n\n");
    md.append(blankToFallback(detection, "Detection details are not available.")).append("\n\n");

    md.append("## Resolution\n\n");
    md.append(blankToFallback(resolution, "Resolution details are not available.")).append("\n\n");

    md.append("## Execution Summary\n\n");
    appendExecutions(md, source.executions());

    md.append("\n## Rollback Summary\n\n");
    appendRollbacks(md, source.rollbackPlans());

    md.append("\n## RCA Evidence\n\n");
    appendRca(md, source.rcaAnalyses());

    md.append("\n## AI Diagnosis\n\n");
    appendAi(md, source.aiDiagnoses());

    md.append("\n## Prevention\n\n");
    md.append(blankToFallback(prevention, "No prevention actions recorded.")).append("\n\n");

    md.append("## Follow-up Action Items\n\n");
    if (actionItems.isEmpty()) {
      md.append("No follow-up action items generated.\n");
    } else {
      for (String item : actionItems) {
        md.append("- [ ] ").append(escape(item)).append("\n");
      }
    }

    return md.toString();
  }

  private void appendTimeline(StringBuilder md, List<TimelineSnapshot> timeline) {
    if (timeline.isEmpty()) {
      md.append("No timeline events.\n");
      return;
    }

    for (TimelineSnapshot event : timeline) {
      md.append("- ")
          .append(value(event.createdAt()))
          .append(" [")
          .append(escape(event.eventType()))
          .append("] ")
          .append(escape(event.title()))
          .append(": ")
          .append(escape(event.content()))
          .append("\n");
    }
  }

  private void appendExecutions(StringBuilder md, List<ExecutionSnapshot> executions) {
    if (executions.isEmpty()) {
      md.append("No executions were recorded.\n");
      return;
    }

    md.append("| Execution | Mode | Kind | Status | Summary |\n");
    md.append("|---|---|---|---|---|\n");

    for (ExecutionSnapshot execution : executions) {
      md.append("| ")
          .append(escape(execution.id()))
          .append(" | ")
          .append(escape(execution.mode()))
          .append(" | ")
          .append(escape(execution.executionKind()))
          .append(" | ")
          .append(escape(execution.status()))
          .append(" | ")
          .append(escape(execution.summary()))
          .append(" |\n");
    }
  }

  private void appendRollbacks(StringBuilder md, List<RollbackSnapshot> rollbacks) {
    if (rollbacks.isEmpty()) {
      md.append("No rollback plans were recorded.\n");
      return;
    }

    md.append("| Rollback Plan | Status | Reason |\n");
    md.append("|---|---|---|\n");

    for (RollbackSnapshot rollback : rollbacks) {
      md.append("| ")
          .append(escape(rollback.id()))
          .append(" | ")
          .append(escape(rollback.status()))
          .append(" | ")
          .append(escape(rollback.reason()))
          .append(" |\n");
    }
  }

  private void appendRca(StringBuilder md, List<RcaSnapshot> rcaAnalyses) {
    if (rcaAnalyses.isEmpty()) {
      md.append("No RCA analysis was recorded.\n");
      return;
    }

    for (RcaSnapshot rca : rcaAnalyses) {
      md.append("- RCA ")
          .append(escape(rca.id()))
          .append(": ")
          .append(escape(rca.summary()))
          .append(" / Root Cause: ")
          .append(escape(rca.rootCause()))
          .append(" / Confidence: ")
          .append(escape(rca.confidence()))
          .append("\n");
    }
  }

  private void appendAi(StringBuilder md, List<AiDiagnosisSnapshot> aiDiagnoses) {
    if (aiDiagnoses.isEmpty()) {
      md.append("No AI diagnosis was recorded.\n");
      return;
    }

    for (AiDiagnosisSnapshot diagnosis : aiDiagnoses) {
      md.append("- AI Diagnosis ")
          .append(escape(diagnosis.id()))
          .append(": ")
          .append(escape(diagnosis.summary()))
          .append(" / Root Cause: ")
          .append(escape(diagnosis.rootCause()))
          .append(" / Recommendation: ")
          .append(escape(diagnosis.recommendation()))
          .append(" / Confidence: ")
          .append(escape(diagnosis.confidence()))
          .append("\n");
    }
  }

  private String blankToFallback(String value, String fallback) {
    return value == null || value.isBlank() ? fallback : value;
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

# 15. Postmortem Draft Builder

## 15.1 `PostmortemDraft.java`

路径：

```txt id="khc0nt"
modules/aiops-execution/src/main/java/io/aegisops/execution/PostmortemDraft.java
```

```java id="m8uz9m"
package io.aegisops.execution;

import java.util.List;

public record PostmortemDraft(
    String title,
    String summary,
    String impact,
    String rootCause,
    String detection,
    String resolution,
    String prevention,
    List<String> actionItems) {}
```

---

## 15.2 `PostmortemDraftBuilder.java`

路径：

```txt id="qtrj89"
modules/aiops-execution/src/main/java/io/aegisops/execution/PostmortemDraftBuilder.java
```

```java id="k1mey1"
package io.aegisops.execution;

import io.aegisops.execution.dto.PostmortemSourceBundle;
import io.aegisops.execution.dto.PostmortemSourceBundle.AiDiagnosisSnapshot;
import io.aegisops.execution.dto.PostmortemSourceBundle.ExecutionSnapshot;
import io.aegisops.execution.dto.PostmortemSourceBundle.RcaSnapshot;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class PostmortemDraftBuilder {
  public PostmortemDraft build(PostmortemSourceBundle source, boolean generateActionItems) {
    String title = "Postmortem - " + source.incident().title();

    String rootCause = chooseRootCause(source);
    String summary =
        "Incident "
            + source.incident().id()
            + " was handled with status "
            + source.incident().status()
            + ". Severity="
            + source.incident().severity()
            + ".";

    String impact =
        switch (safe(source.incident().severity())) {
          case "critical" -> "Critical impact. Service availability or core business flow may have been affected.";
          case "high" -> "High impact. Users or important business functions may have been affected.";
          case "medium" -> "Medium impact. Partial degradation or limited scope impact was observed.";
          case "low" -> "Low impact. The incident appears to have limited customer-facing impact.";
          default -> "Impact requires manual confirmation.";
        };

    String detection =
        source.rcaAnalyses().isEmpty() && source.aiDiagnoses().isEmpty()
            ? "Detected by alert aggregation and incident creation."
            : "Detected by alert aggregation, followed by RCA and AI diagnosis.";

    String resolution = chooseResolution(source);
    String prevention =
        "Review monitoring coverage, runbook accuracy, rollback readiness, and ownership of follow-up action items.";

    List<String> actionItems = generateActionItems ? buildActionItems(source, rootCause) : List.of();

    return new PostmortemDraft(
        title, summary, impact, rootCause, detection, resolution, prevention, actionItems);
  }

  private String chooseRootCause(PostmortemSourceBundle source) {
    return source.aiDiagnoses().stream()
        .map(AiDiagnosisSnapshot::rootCause)
        .filter(value -> value != null && !value.isBlank())
        .findFirst()
        .orElseGet(
            () ->
                source.rcaAnalyses().stream()
                    .map(RcaSnapshot::rootCause)
                    .filter(value -> value != null && !value.isBlank())
                    .findFirst()
                    .orElse("Root cause is not confirmed."));
  }

  private String chooseResolution(PostmortemSourceBundle source) {
    boolean hasSucceededExecution =
        source.executions().stream().anyMatch(item -> "succeeded".equals(item.status()));

    boolean hasRollback =
        source.rollbackPlans().stream()
            .anyMatch(item -> List.of("succeeded", "approved", "executing").contains(item.status()));

    if (hasRollback) {
      return "Rollback plan was created or executed as part of mitigation.";
    }

    if (hasSucceededExecution) {
      return "Automation execution succeeded as part of incident mitigation.";
    }

    return "Resolution requires manual confirmation.";
  }

  private List<String> buildActionItems(PostmortemSourceBundle source, String rootCause) {
    List<String> items = new ArrayList<>();

    items.add("Confirm the final root cause and attach supporting evidence.");
    items.add("Review and update the related runbook based on this incident.");
    items.add("Add or improve alerting rules to reduce detection time.");

    if (rootCause.toLowerCase().contains("unknown")) {
      items.add("Improve evidence collection for similar incidents.");
    }

    if (source.rollbackPlans().isEmpty()) {
      items.add("Prepare rollback plan for this failure mode.");
    }

    if (source.aiDiagnoses().isEmpty()) {
      items.add("Add this incident to AI diagnosis evaluation cases after review.");
    }

    return items;
  }

  private String safe(String value) {
    return value == null ? "" : value.toLowerCase();
  }
}
```

---

# 16. Service

## 16.1 `PostmortemService.java`

路径：

```txt id="4k3dg2"
modules/aiops-execution/src/main/java/io/aegisops/execution/PostmortemService.java
```

```java id="c29z1s"
package io.aegisops.execution;

import io.aegisops.common.exception.AppException;
import io.aegisops.execution.dto.PostmortemActionItemCreateCommand;
import io.aegisops.execution.dto.PostmortemActionItemCreateRequest;
import io.aegisops.execution.dto.PostmortemActionItemRecord;
import io.aegisops.execution.dto.PostmortemActionItemResponse;
import io.aegisops.execution.dto.PostmortemActionItemStatusRequest;
import io.aegisops.execution.dto.PostmortemGenerateRequest;
import io.aegisops.execution.dto.PostmortemReportCreateCommand;
import io.aegisops.execution.dto.PostmortemReportRecord;
import io.aegisops.execution.dto.PostmortemReportResponse;
import io.aegisops.execution.dto.PostmortemSectionCreateCommand;
import io.aegisops.execution.dto.PostmortemSectionRecord;
import io.aegisops.execution.dto.PostmortemSectionResponse;
import io.aegisops.execution.dto.PostmortemSourceBundle;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PostmortemService {
  private final PostmortemRepository repository;
  private final PostmortemSourceRepository sourceRepository;
  private final PostmortemDraftBuilder draftBuilder;
  private final PostmortemMarkdownBuilder markdownBuilder;
  private final PostmortemJson json;

  public PostmortemService(
      PostmortemRepository repository,
      PostmortemSourceRepository sourceRepository,
      PostmortemDraftBuilder draftBuilder,
      PostmortemMarkdownBuilder markdownBuilder,
      com.fasterxml.jackson.databind.ObjectMapper objectMapper) {
    this.repository = repository;
    this.sourceRepository = sourceRepository;
    this.draftBuilder = draftBuilder;
    this.markdownBuilder = markdownBuilder;
    this.json = new PostmortemJson(objectMapper);
  }

  @Transactional
  public PostmortemReportResponse generate(
      String tenantId, String incidentId, PostmortemGenerateRequest request) {
    PostmortemSourceBundle source =
        sourceRepository
            .load(tenantId, incidentId)
            .orElseThrow(() -> new AppException("INCIDENT_NOT_FOUND", "Incident not found"));

    PostmortemSourceBundle filtered = filterSource(source, request);
    PostmortemDraft draft =
        draftBuilder.build(filtered, include(request == null ? null : request.generateActionItems()));

    String markdown =
        markdownBuilder.build(
            filtered,
            draft.summary(),
            draft.impact(),
            draft.rootCause(),
            draft.detection(),
            draft.resolution(),
            draft.prevention(),
            draft.actionItems());

    String postmortemId = newId("pmr");
    String actor = blankToDefault(request == null ? null : request.generatedBy(), "system");

    repository.createReport(
        new PostmortemReportCreateCommand(
            postmortemId,
            tenantId,
            incidentId,
            "generated",
            normalizeSeverity(filtered.incident().severity()),
            draft.title(),
            draft.summary(),
            draft.impact(),
            draft.rootCause(),
            draft.detection(),
            draft.resolution(),
            draft.prevention(),
            markdown,
            json.write(filtered),
            actor));

    int order = 1;
    createSection(tenantId, postmortemId, order++, "summary", "Incident Summary", draft.summary(), Map.of());
    createSection(tenantId, postmortemId, order++, "impact", "Impact", draft.impact(), Map.of());
    createSection(tenantId, postmortemId, order++, "timeline", "Timeline", buildTimelineSection(filtered), Map.of("count", filtered.timeline().size()));
    createSection(tenantId, postmortemId, order++, "root_cause", "Root Cause", draft.rootCause(), Map.of());
    createSection(tenantId, postmortemId, order++, "detection", "Detection", draft.detection(), Map.of());
    createSection(tenantId, postmortemId, order++, "resolution", "Resolution", draft.resolution(), Map.of());
    createSection(tenantId, postmortemId, order++, "prevention", "Prevention", draft.prevention(), Map.of());

    if (include(request == null ? null : request.generateActionItems())) {
      for (String item : draft.actionItems()) {
        repository.createActionItem(
            new PostmortemActionItemCreateCommand(
                newId("pmai"),
                tenantId,
                postmortemId,
                item,
                null,
                null,
                "medium",
                "open",
                null,
                "ai_diagnosis",
                null,
                actor));
      }
    }

    return get(tenantId, postmortemId);
  }

  public PostmortemReportResponse get(String tenantId, String postmortemId) {
    PostmortemReportRecord report =
        repository
            .findReport(tenantId, postmortemId)
            .orElseThrow(() -> new AppException("POSTMORTEM_NOT_FOUND", "Postmortem report not found"));

    return toResponse(
        report,
        repository.listSections(tenantId, postmortemId),
        repository.listActionItems(tenantId, postmortemId));
  }

  public PostmortemReportResponse latestByIncident(String tenantId, String incidentId) {
    PostmortemReportRecord report =
        repository
            .findLatestByIncident(tenantId, incidentId)
            .orElseThrow(() -> new AppException("POSTMORTEM_NOT_FOUND", "Postmortem report not found"));

    return get(tenantId, report.id());
  }

  public String markdown(String tenantId, String postmortemId) {
    return get(tenantId, postmortemId).markdown();
  }

  @Transactional
  public PostmortemActionItemResponse createActionItem(
      String tenantId, String postmortemId, PostmortemActionItemCreateRequest request) {
    repository
        .findReport(tenantId, postmortemId)
        .orElseThrow(() -> new AppException("POSTMORTEM_NOT_FOUND", "Postmortem report not found"));

    validateActionItemRequest(request);

    String id = newId("pmai");

    repository.createActionItem(
        new PostmortemActionItemCreateCommand(
            id,
            tenantId,
            postmortemId,
            request.title().trim(),
            request.description(),
            request.owner(),
            normalizePriority(request.priority()),
            "open",
            request.dueDate(),
            normalizeSourceType(request.sourceType()),
            request.sourceRefId(),
            blankToDefault(request.createdBy(), "system")));

    return repository
        .findActionItem(tenantId, id)
        .map(this::toActionItemResponse)
        .orElseThrow(() -> new AppException("POSTMORTEM_ACTION_ITEM_NOT_FOUND", "Action item not found"));
  }

  public List<PostmortemActionItemResponse> listActionItems(String tenantId, String postmortemId) {
    repository
        .findReport(tenantId, postmortemId)
        .orElseThrow(() -> new AppException("POSTMORTEM_NOT_FOUND", "Postmortem report not found"));

    return repository.listActionItems(tenantId, postmortemId).stream()
        .map(this::toActionItemResponse)
        .toList();
  }

  @Transactional
  public PostmortemActionItemResponse updateActionItemStatus(
      String tenantId, String actionItemId, PostmortemActionItemStatusRequest request) {
    String status = normalizeActionStatus(request == null ? null : request.status());

    boolean updated = repository.updateActionItemStatus(tenantId, actionItemId, status);
    if (!updated) {
      throw new AppException("POSTMORTEM_ACTION_ITEM_UPDATE_FAILED", "Action item was not updated");
    }

    return repository
        .findActionItem(tenantId, actionItemId)
        .map(this::toActionItemResponse)
        .orElseThrow(() -> new AppException("POSTMORTEM_ACTION_ITEM_NOT_FOUND", "Action item not found"));
  }

  private PostmortemSourceBundle filterSource(
      PostmortemSourceBundle source, PostmortemGenerateRequest request) {
    if (request == null) {
      return source;
    }

    return new PostmortemSourceBundle(
        source.incident(),
        include(request.includeRca()) ? source.rcaAnalyses() : List.of(),
        include(request.includeAiDiagnosis()) ? source.aiDiagnoses() : List.of(),
        include(request.includeExecutions()) ? source.executions() : List.of(),
        include(request.includeRollback()) ? source.rollbackPlans() : List.of(),
        include(request.includeTimeline()) ? source.timeline() : List.of());
  }

  private String buildTimelineSection(PostmortemSourceBundle source) {
    if (source.timeline().isEmpty()) {
      return "No timeline events.";
    }

    StringBuilder builder = new StringBuilder();
    for (var event : source.timeline()) {
      builder
          .append("- ")
          .append(event.createdAt())
          .append(" [")
          .append(event.eventType())
          .append("] ")
          .append(event.title())
          .append(": ")
          .append(event.content())
          .append("\n");
    }
    return builder.toString();
  }

  private void createSection(
      String tenantId,
      String postmortemId,
      int order,
      String sectionType,
      String title,
      String content,
      Object metadata) {
    repository.createSection(
        new PostmortemSectionCreateCommand(
            newId("pms"),
            tenantId,
            postmortemId,
            order,
            sectionType,
            title,
            content,
            json.write(metadata)));
  }

  private void validateActionItemRequest(PostmortemActionItemCreateRequest request) {
    if (request == null) {
      throw new AppException("POSTMORTEM_ACTION_ITEM_REQUEST_REQUIRED", "Action item request is required");
    }

    if (request.title() == null || request.title().isBlank()) {
      throw new AppException("POSTMORTEM_ACTION_ITEM_TITLE_REQUIRED", "Action item title is required");
    }

    normalizePriority(request.priority());
    normalizeSourceType(request.sourceType());
  }

  private String normalizeSeverity(String severity) {
    if (severity == null || severity.isBlank()) {
      return "medium";
    }

    String value = severity.trim().toLowerCase();
    if (!List.of("info", "low", "medium", "high", "critical").contains(value)) {
      return "medium";
    }
    return value;
  }

  private String normalizePriority(String priority) {
    String value = priority == null || priority.isBlank() ? "medium" : priority.trim().toLowerCase();
    if (!List.of("low", "medium", "high", "critical").contains(value)) {
      throw new AppException("POSTMORTEM_ACTION_ITEM_PRIORITY_INVALID", "Invalid action item priority");
    }
    return value;
  }

  private String normalizeSourceType(String sourceType) {
    String value = sourceType == null || sourceType.isBlank() ? "manual" : sourceType.trim().toLowerCase();
    if (!List.of("manual", "rca", "ai_diagnosis", "execution", "rollback").contains(value)) {
      throw new AppException("POSTMORTEM_ACTION_ITEM_SOURCE_TYPE_INVALID", "Invalid action item source type");
    }
    return value;
  }

  private String normalizeActionStatus(String status) {
    String value = status == null || status.isBlank() ? "" : status.trim().toLowerCase();
    if (!List.of("open", "in_progress", "done", "cancelled").contains(value)) {
      throw new AppException("POSTMORTEM_ACTION_ITEM_STATUS_INVALID", "Invalid action item status");
    }
    return value;
  }

  private boolean include(Boolean value) {
    return value == null || value;
  }

  private PostmortemReportResponse toResponse(
      PostmortemReportRecord report,
      List<PostmortemSectionRecord> sections,
      List<PostmortemActionItemRecord> actionItems) {
    return new PostmortemReportResponse(
        report.id(),
        report.tenantId(),
        report.incidentId(),
        report.status(),
        report.severity(),
        report.title(),
        report.summary(),
        report.impact(),
        report.rootCause(),
        report.detection(),
        report.resolution(),
        report.prevention(),
        report.markdown(),
        report.sourceSnapshotJson(),
        report.generatedBy(),
        report.generatedAt(),
        sections.stream().map(this::toSectionResponse).toList(),
        actionItems.stream().map(this::toActionItemResponse).toList(),
        report.createdAt(),
        report.updatedAt());
  }

  private PostmortemSectionResponse toSectionResponse(PostmortemSectionRecord section) {
    return new PostmortemSectionResponse(
        section.id(),
        section.sectionOrder(),
        section.sectionType(),
        section.title(),
        section.content(),
        section.metadataJson(),
        section.createdAt());
  }

  private PostmortemActionItemResponse toActionItemResponse(PostmortemActionItemRecord item) {
    return new PostmortemActionItemResponse(
        item.id(),
        item.title(),
        item.description(),
        item.owner(),
        item.priority(),
        item.status(),
        item.dueDate(),
        item.sourceType(),
        item.sourceRefId(),
        item.createdBy(),
        item.createdAt(),
        item.updatedAt());
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

# 17. Controller

## 17.1 `PostmortemController.java`

路径：

```txt id="lnh4w3"
modules/aiops-execution/src/main/java/io/aegisops/execution/PostmortemController.java
```

```java id="x7589s"
package io.aegisops.execution;

import io.aegisops.common.api.ApiResponse;
import io.aegisops.common.tenant.TenantContext;
import io.aegisops.execution.dto.PostmortemActionItemCreateRequest;
import io.aegisops.execution.dto.PostmortemActionItemResponse;
import io.aegisops.execution.dto.PostmortemActionItemStatusRequest;
import io.aegisops.execution.dto.PostmortemGenerateRequest;
import io.aegisops.execution.dto.PostmortemReportResponse;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class PostmortemController {
  private final PostmortemService service;

  public PostmortemController(PostmortemService service) {
    this.service = service;
  }

  @PostMapping("/api/incidents/{incidentId}/postmortems/generate")
  public ApiResponse<PostmortemReportResponse> generate(
      @PathVariable String incidentId,
      @RequestBody(required = false) PostmortemGenerateRequest request) {
    return ApiResponse.ok(service.generate(TenantContext.requireTenantId(), incidentId, request));
  }

  @GetMapping("/api/incidents/{incidentId}/postmortems/latest")
  public ApiResponse<PostmortemReportResponse> latest(@PathVariable String incidentId) {
    return ApiResponse.ok(service.latestByIncident(TenantContext.requireTenantId(), incidentId));
  }

  @GetMapping("/api/postmortems/{postmortemId}")
  public ApiResponse<PostmortemReportResponse> get(@PathVariable String postmortemId) {
    return ApiResponse.ok(service.get(TenantContext.requireTenantId(), postmortemId));
  }

  @GetMapping(value = "/api/postmortems/{postmortemId}/markdown", produces = "text/markdown")
  public String markdown(@PathVariable String postmortemId) {
    return service.markdown(TenantContext.requireTenantId(), postmortemId);
  }

  @PostMapping("/api/postmortems/{postmortemId}/action-items")
  public ApiResponse<PostmortemActionItemResponse> createActionItem(
      @PathVariable String postmortemId,
      @RequestBody PostmortemActionItemCreateRequest request) {
    return ApiResponse.ok(service.createActionItem(TenantContext.requireTenantId(), postmortemId, request));
  }

  @GetMapping("/api/postmortems/{postmortemId}/action-items")
  public ApiResponse<List<PostmortemActionItemResponse>> listActionItems(
      @PathVariable String postmortemId) {
    return ApiResponse.ok(service.listActionItems(TenantContext.requireTenantId(), postmortemId));
  }

  @PostMapping("/api/postmortem-action-items/{actionItemId}/status")
  public ApiResponse<PostmortemActionItemResponse> updateActionItemStatus(
      @PathVariable String actionItemId,
      @RequestBody PostmortemActionItemStatusRequest request) {
    return ApiResponse.ok(service.updateActionItemStatus(TenantContext.requireTenantId(), actionItemId, request));
  }
}
```

---

# 18. 单元测试

## 18.1 `PostmortemDraftBuilderTest.java`

路径：

```txt id="akg0di"
modules/aiops-execution/src/test/java/io/aegisops/execution/PostmortemDraftBuilderTest.java
```

```java id="0gvizj"
package io.aegisops.execution;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.aegisops.execution.dto.PostmortemSourceBundle;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

class PostmortemDraftBuilderTest {
  private final PostmortemDraftBuilder builder = new PostmortemDraftBuilder();

  @Test
  void chooseAiRootCauseFirst() {
    PostmortemDraft draft =
        builder.build(
            new PostmortemSourceBundle(
                incident("high"),
                List.of(
                    new PostmortemSourceBundle.RcaSnapshot(
                        "rca_1", "rca summary", "rca root cause", "0.8", OffsetDateTime.now())),
                List.of(
                    new PostmortemSourceBundle.AiDiagnosisSnapshot(
                        "ai_1",
                        "ai summary",
                        "ai root cause",
                        "restart service",
                        "0.9",
                        OffsetDateTime.now())),
                List.of(),
                List.of(),
                List.of()),
            true);

    assertEquals("ai root cause", draft.rootCause());
    assertTrue(draft.actionItems().contains("Review and update the related runbook based on this incident."));
  }

  @Test
  void generateRollbackActionItemWhenNoRollbackPlan() {
    PostmortemDraft draft =
        builder.build(
            new PostmortemSourceBundle(
                incident("medium"),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of()),
            true);

    assertTrue(draft.actionItems().contains("Prepare rollback plan for this failure mode."));
  }

  private PostmortemSourceBundle.IncidentSnapshot incident(String severity) {
    return new PostmortemSourceBundle.IncidentSnapshot(
        "inc_1",
        "Order service error",
        "resolved",
        severity,
        OffsetDateTime.now(),
        OffsetDateTime.now());
  }
}
```

---

## 18.2 `PostmortemMarkdownBuilderTest.java`

路径：

```txt id="5ccj3h"
modules/aiops-execution/src/test/java/io/aegisops/execution/PostmortemMarkdownBuilderTest.java
```

```java id="9jzjff"
package io.aegisops.execution;

import static org.junit.jupiter.api.Assertions.assertTrue;

import io.aegisops.execution.dto.PostmortemSourceBundle;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

class PostmortemMarkdownBuilderTest {
  @Test
  void buildMarkdown() {
    PostmortemMarkdownBuilder builder = new PostmortemMarkdownBuilder();

    String markdown =
        builder.build(
            source(),
            "summary",
            "impact",
            "root cause",
            "detection",
            "resolution",
            "prevention",
            List.of("fix monitor", "update runbook"));

    assertTrue(markdown.contains("# Postmortem Report"));
    assertTrue(markdown.contains("Order service error"));
    assertTrue(markdown.contains("root cause"));
    assertTrue(markdown.contains("fix monitor"));
  }

  private PostmortemSourceBundle source() {
    return new PostmortemSourceBundle(
        new PostmortemSourceBundle.IncidentSnapshot(
            "inc_1",
            "Order service error",
            "resolved",
            "high",
            OffsetDateTime.now(),
            OffsetDateTime.now()),
        List.of(
            new PostmortemSourceBundle.RcaSnapshot(
                "rca_1", "rca summary", "db timeout", "0.8", OffsetDateTime.now())),
        List.of(),
        List.of(
            new PostmortemSourceBundle.ExecutionSnapshot(
                "exec_1",
                "live",
                "normal",
                "succeeded",
                "restart service",
                OffsetDateTime.now(),
                OffsetDateTime.now())),
        List.of(),
        List.of(
            new PostmortemSourceBundle.TimelineSnapshot(
                "tl_1",
                "incident_created",
                "Incident created",
                "incident was created",
                OffsetDateTime.now())));
  }
}
```

---

## 18.3 `PostmortemServiceTest.java`

路径：

```txt id="tfki4o"
modules/aiops-execution/src/test/java/io/aegisops/execution/PostmortemServiceTest.java
```

```java id="x6bcd6"
package io.aegisops.execution;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.aegisops.common.exception.AppException;
import io.aegisops.execution.dto.PostmortemActionItemCreateCommand;
import io.aegisops.execution.dto.PostmortemActionItemCreateRequest;
import io.aegisops.execution.dto.PostmortemActionItemRecord;
import io.aegisops.execution.dto.PostmortemActionItemStatusRequest;
import io.aegisops.execution.dto.PostmortemGenerateRequest;
import io.aegisops.execution.dto.PostmortemReportCreateCommand;
import io.aegisops.execution.dto.PostmortemReportRecord;
import io.aegisops.execution.dto.PostmortemSectionCreateCommand;
import io.aegisops.execution.dto.PostmortemSectionRecord;
import io.aegisops.execution.dto.PostmortemSourceBundle;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class PostmortemServiceTest {
  @Test
  void generatePostmortemReport() {
    FakePostmortemRepository repository = new FakePostmortemRepository();

    PostmortemService service =
        new PostmortemService(
            repository,
            new FakePostmortemSourceRepository(true),
            new PostmortemDraftBuilder(),
            new PostmortemMarkdownBuilder(),
            new ObjectMapper());

    var response =
        service.generate(
            "tenant_1",
            "inc_1",
            new PostmortemGenerateRequest("alice", true, true, true, true, true, true));

    assertEquals("generated", response.status());
    assertTrue(response.markdown().contains("# Postmortem Report"));
    assertTrue(response.sections().size() >= 7);
    assertTrue(response.actionItems().size() > 0);
  }

  @Test
  void rejectGenerateWhenIncidentNotFound() {
    PostmortemService service =
        new PostmortemService(
            new FakePostmortemRepository(),
            new FakePostmortemSourceRepository(false),
            new PostmortemDraftBuilder(),
            new PostmortemMarkdownBuilder(),
            new ObjectMapper());

    assertThrows(
        AppException.class,
        () ->
            service.generate(
                "tenant_1",
                "inc_missing",
                new PostmortemGenerateRequest("alice", true, true, true, true, true, true)));
  }

  @Test
  void createManualActionItem() {
    FakePostmortemRepository repository = new FakePostmortemRepository();
    repository.report =
        report("pmr_1", "tenant_1", "inc_1", "generated");

    PostmortemService service =
        new PostmortemService(
            repository,
            new FakePostmortemSourceRepository(true),
            new PostmortemDraftBuilder(),
            new PostmortemMarkdownBuilder(),
            new ObjectMapper());

    var response =
        service.createActionItem(
            "tenant_1",
            "pmr_1",
            new PostmortemActionItemCreateRequest(
                "Update runbook",
                "Add rollback step",
                "bob",
                "high",
                LocalDate.now().plusDays(7),
                "manual",
                null,
                "alice"));

    assertEquals("Update runbook", response.title());
    assertEquals("open", response.status());
  }

  @Test
  void updateActionItemStatus() {
    FakePostmortemRepository repository = new FakePostmortemRepository();
    repository.report = report("pmr_1", "tenant_1", "inc_1", "generated");

    PostmortemActionItemRecord item =
        new PostmortemActionItemRecord(
            "pmai_1",
            "tenant_1",
            "pmr_1",
            "Update runbook",
            null,
            "bob",
            "high",
            "open",
            null,
            "manual",
            null,
            "alice",
            OffsetDateTime.now(),
            OffsetDateTime.now());

    repository.actionItems.add(item);

    PostmortemService service =
        new PostmortemService(
            repository,
            new FakePostmortemSourceRepository(true),
            new PostmortemDraftBuilder(),
            new PostmortemMarkdownBuilder(),
            new ObjectMapper());

    var response =
        service.updateActionItemStatus(
            "tenant_1",
            "pmai_1",
            new PostmortemActionItemStatusRequest("done", "bob"));

    assertEquals("done", response.status());
  }

  private static PostmortemReportRecord report(
      String id, String tenantId, String incidentId, String status) {
    return new PostmortemReportRecord(
        id,
        tenantId,
        incidentId,
        status,
        "high",
        "Postmortem",
        "summary",
        "impact",
        "root cause",
        "detection",
        "resolution",
        "prevention",
        "# md",
        "{}",
        "alice",
        OffsetDateTime.now(),
        null,
        null,
        null,
        OffsetDateTime.now(),
        OffsetDateTime.now());
  }

  private static class FakePostmortemSourceRepository implements PostmortemSourceRepository {
    private final boolean found;

    FakePostmortemSourceRepository(boolean found) {
      this.found = found;
    }

    @Override
    public Optional<PostmortemSourceBundle> load(String tenantId, String incidentId) {
      if (!found) {
        return Optional.empty();
      }

      return Optional.of(
          new PostmortemSourceBundle(
              new PostmortemSourceBundle.IncidentSnapshot(
                  incidentId,
                  "Order service error",
                  "resolved",
                  "high",
                  OffsetDateTime.now(),
                  OffsetDateTime.now()),
              List.of(
                  new PostmortemSourceBundle.RcaSnapshot(
                      "rca_1", "rca summary", "db timeout", "0.8", OffsetDateTime.now())),
              List.of(
                  new PostmortemSourceBundle.AiDiagnosisSnapshot(
                      "ai_1",
                      "ai summary",
                      "db timeout",
                      "restart service",
                      "0.9",
                      OffsetDateTime.now())),
              List.of(
                  new PostmortemSourceBundle.ExecutionSnapshot(
                      "exec_1",
                      "live",
                      "normal",
                      "succeeded",
                      "restart service",
                      OffsetDateTime.now(),
                      OffsetDateTime.now())),
              List.of(),
              List.of(
                  new PostmortemSourceBundle.TimelineSnapshot(
                      "tl_1",
                      "incident_created",
                      "Incident created",
                      "created",
                      OffsetDateTime.now()))));
    }
  }

  private static class FakePostmortemRepository implements PostmortemRepository {
    PostmortemReportRecord report;
    final List<PostmortemSectionRecord> sections = new ArrayList<>();
    final List<PostmortemActionItemRecord> actionItems = new ArrayList<>();

    @Override
    public void createReport(PostmortemReportCreateCommand command) {
      report =
          new PostmortemReportRecord(
              command.id(),
              command.tenantId(),
              command.incidentId(),
              command.status(),
              command.severity(),
              command.title(),
              command.summary(),
              command.impact(),
              command.rootCause(),
              command.detection(),
              command.resolution(),
              command.prevention(),
              command.markdown(),
              command.sourceSnapshotJson(),
              command.generatedBy(),
              OffsetDateTime.now(),
              null,
              null,
              null,
              OffsetDateTime.now(),
              OffsetDateTime.now());
    }

    @Override
    public void createSection(PostmortemSectionCreateCommand command) {
      sections.add(
          new PostmortemSectionRecord(
              command.id(),
              command.tenantId(),
              command.postmortemId(),
              command.sectionOrder(),
              command.sectionType(),
              command.title(),
              command.content(),
              command.metadataJson(),
              OffsetDateTime.now()));
    }

    @Override
    public Optional<PostmortemReportRecord> findReport(String tenantId, String postmortemId) {
      return Optional.ofNullable(report);
    }

    @Override
    public Optional<PostmortemReportRecord> findLatestByIncident(String tenantId, String incidentId) {
      return Optional.ofNullable(report);
    }

    @Override
    public List<PostmortemSectionRecord> listSections(String tenantId, String postmortemId) {
      return sections;
    }

    @Override
    public void createActionItem(PostmortemActionItemCreateCommand command) {
      actionItems.add(
          new PostmortemActionItemRecord(
              command.id(),
              command.tenantId(),
              command.postmortemId(),
              command.title(),
              command.description(),
              command.owner(),
              command.priority(),
              command.status(),
              command.dueDate(),
              command.sourceType(),
              command.sourceRefId(),
              command.createdBy(),
              OffsetDateTime.now(),
              OffsetDateTime.now()));
    }

    @Override
    public List<PostmortemActionItemRecord> listActionItems(String tenantId, String postmortemId) {
      return actionItems;
    }

    @Override
    public Optional<PostmortemActionItemRecord> findActionItem(String tenantId, String actionItemId) {
      return actionItems.stream().filter(item -> item.id().equals(actionItemId)).findFirst();
    }

    @Override
    public boolean updateActionItemStatus(String tenantId, String actionItemId, String status) {
      Optional<PostmortemActionItemRecord> found = findActionItem(tenantId, actionItemId);
      if (found.isEmpty()) {
        return false;
      }

      PostmortemActionItemRecord old = found.get();
      actionItems.remove(old);
      actionItems.add(
          new PostmortemActionItemRecord(
              old.id(),
              old.tenantId(),
              old.postmortemId(),
              old.title(),
              old.description(),
              old.owner(),
              old.priority(),
              status,
              old.dueDate(),
              old.sourceType(),
              old.sourceRefId(),
              old.createdBy(),
              old.createdAt(),
              OffsetDateTime.now()));
      return true;
    }
  }
}
```

---

## 18.4 `JooqPostmortemRepositoryGeneratedSqlTest.java`

路径：

```txt id="5trfx4"
modules/aiops-execution/src/test/java/io/aegisops/execution/JooqPostmortemRepositoryGeneratedSqlTest.java
```

```java id="dxyrj4"
package io.aegisops.execution;

import static org.junit.jupiter.api.Assertions.assertTrue;

import io.aegisops.execution.dto.PostmortemActionItemCreateCommand;
import io.aegisops.execution.dto.PostmortemReportCreateCommand;
import io.aegisops.execution.dto.PostmortemSectionCreateCommand;
import io.aegisops.persistence.JooqTestSupport;
import java.time.LocalDate;
import java.util.List;
import org.jooq.Query;
import org.junit.jupiter.api.Test;

class JooqPostmortemRepositoryGeneratedSqlTest {
  @Test
  void createReportUsesPostmortemReportTable() {
    var dsl = JooqTestSupport.dsl();
    var repository = new JooqPostmortemRepository(dsl);

    repository.createReport(
        new PostmortemReportCreateCommand(
            "pmr_1",
            "tenant_1",
            "inc_1",
            "generated",
            "high",
            "title",
            "summary",
            "impact",
            "root cause",
            "detection",
            "resolution",
            "prevention",
            "# md",
            "{}",
            "alice"));

    String sql = renderedSql(dsl.queries());

    assertTrue(sql.contains("postmortem_report"));
    assertTrue(sql.contains("source_snapshot"));
  }

  @Test
  void createSectionUsesPostmortemSectionTable() {
    var dsl = JooqTestSupport.dsl();
    var repository = new JooqPostmortemRepository(dsl);

    repository.createSection(
        new PostmortemSectionCreateCommand(
            "pms_1",
            "tenant_1",
            "pmr_1",
            1,
            "summary",
            "Summary",
            "content",
            "{}"));

    String sql = renderedSql(dsl.queries());

    assertTrue(sql.contains("postmortem_section"));
    assertTrue(sql.contains("section_order"));
  }

  @Test
  void createActionItemUsesPostmortemActionItemTable() {
    var dsl = JooqTestSupport.dsl();
    var repository = new JooqPostmortemRepository(dsl);

    repository.createActionItem(
        new PostmortemActionItemCreateCommand(
            "pmai_1",
            "tenant_1",
            "pmr_1",
            "Update runbook",
            "desc",
            "bob",
            "high",
            "open",
            LocalDate.now().plusDays(7),
            "manual",
            null,
            "alice"));

    String sql = renderedSql(dsl.queries());

    assertTrue(sql.contains("postmortem_action_item"));
    assertTrue(sql.contains("priority"));
  }

  private String renderedSql(List<Query> queries) {
    return queries.stream().map(Query::getSQL).reduce("", (a, b) -> a + "\n" + b).toLowerCase();
  }
}
```

---

# 19. 文档

路径：

```txt id="toz69q"
docs/mvp/design/phase6.0-postmortem-report.md
```

```md id="fxybrd"
# Phase6.0 Postmortem Report

## 目标

Phase6.0 聚合 Incident、RCA、AI Diagnosis、Execution、Rollback、Timeline，生成结构化事故复盘报告。

## 不做

- 不执行修复
- 不自动回滚
- 不新增 Runner Adapter
- 不做向量检索
- 不做 Case Library

## 新增表

- postmortem_report
- postmortem_section
- postmortem_action_item

## API

- POST /api/incidents/{incidentId}/postmortems/generate
- GET /api/incidents/{incidentId}/postmortems/latest
- GET /api/postmortems/{postmortemId}
- GET /api/postmortems/{postmortemId}/markdown
- POST /api/postmortems/{postmortemId}/action-items
- GET /api/postmortems/{postmortemId}/action-items
- POST /api/postmortem-action-items/{actionItemId}/status

## 报告结构

- Incident Summary
- Impact
- Timeline
- Root Cause
- Detection
- Resolution
- Execution Summary
- Rollback Summary
- RCA Evidence
- AI Diagnosis
- Prevention
- Follow-up Action Items

## 验收标准

1. incident 存在时可以生成 postmortem。
2. incident 不存在时拒绝。
3. 生成 markdown。
4. 生成 section。
5. 可以生成 action items。
6. 可以手动新增 action item。
7. 可以更新 action item 状态。
8. 不引入执行能力。
```

---

# 20. 验证命令

```powershell id="74j2rx"
mvn -pl modules/aiops-persistence -am generate-sources
mvn -pl modules/aiops-execution -am test
mvn -pl apps/aiops-server -am test
```

全量：

```powershell id="op4q9x"
mvn test
```

---

# 21. 验收标准

```txt id="qs0ssr"
1. postmortem_report 表存在。
2. postmortem_section 表存在。
3. postmortem_action_item 表存在。
4. incident 存在时可以生成 postmortem。
5. incident 不存在时拒绝生成。
6. postmortem 包含 source_snapshot。
7. postmortem 包含 markdown。
8. postmortem 包含 sections。
9. postmortem 可以生成 action items。
10. 可以手动新增 action item。
11. 可以更新 action item 状态。
12. 可以导出 markdown。
13. 不引入新的执行能力。
14. 不修改 runner。
15. 不自动回滚。
```

---

# 22. 建议提交信息

```txt id="w0xym8"
feat(postmortem): generate incident postmortem reports
```

---

# 23. 下一步 Phase6.1

Phase6.0 完成后，下一步进入：

```txt id="vj4qwu"
Phase6.1 Incident Case Library
```

Phase6.1 要做的是把 reviewed postmortem 变成可复用案例：

```txt id="vsox05"
case_library
case_symptom
case_root_cause
case_resolution
case_tags
case_quality_score
```

然后 Phase6.2 再做向量检索。
