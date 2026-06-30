---
title: Phase6.1：Incident Case Library
type: design
status: accepted
phase: global
owner: ai
created: 2026-06-30
updated: 2026-06-30
related: []
---

# Phase6.1：Incident Case Library

> Phase6.1 目标：把 **reviewed / generated Postmortem** 沉淀为可复用的事故案例库。
> 本阶段不做向量检索、不做 RAG、不做 Agent 多轮记忆，只做结构化案例沉淀，为 Phase6.2 `Knowledge Base & Vector Retrieval` 准备高质量数据。

---

# 1. Phase6.1 定位

前置阶段：

```txt id="j4x7w3"
Phase6.0 Postmortem Report
```

Phase6.1 新增：

```txt id="cmsdwd"
incident_case
incident_case_symptom
incident_case_resolution_step
incident_case_tag
```

数据流：

```txt id="tmdx3c"
Postmortem Report
  -> Incident Case draft
  -> symptoms
  -> root cause
  -> resolution steps
  -> tags
  -> quality score
  -> publish
  -> Case Library
```

---

# 2. 不做什么

```txt id="3ibmsp"
1. 不做向量检索
2. 不做 embedding
3. 不接 Milvus / pgvector
4. 不做 Agent Memory
5. 不让 AI 自动发布案例
6. 不影响执行链路
```

这些留给：

```txt id="fxavv0"
Phase6.2 Knowledge Base & Vector Retrieval
Phase6.3 Agent Eval & Prompt Regression
```

---

# 3. API 设计

```txt id="56yscc"
POST /api/postmortems/{postmortemId}/cases
GET  /api/incidents/{incidentId}/cases/latest
GET  /api/incident-cases/{caseId}
GET  /api/incident-cases
POST /api/incident-cases/{caseId}/publish
POST /api/incident-cases/{caseId}/archive
```

---

# 4. 状态机

## 4.1 incident_case.status

```txt id="r28iw4"
draft
published
archived
```

规则：

```txt id="gnt8r6"
draft：从 postmortem 生成，还未进入正式案例库
published：可用于检索、推荐、案例库展示
archived：不再用于推荐和检索
```

---

# 5. Migration

路径：

```txt id="ng3jyw"
apps/aiops-server/src/main/resources/db/migration/V21__phase6_1_incident_case_library.sql
```

```sql id="r8ts4a"
-- Phase 6.1: Incident Case Library.
-- This phase converts postmortem reports into reusable structured incident cases.
-- It does not add vector search, embeddings, or agent memory.

create table if not exists incident_case (
  id varchar(64) primary key,
  tenant_id varchar(64) not null references tenant(id) on delete cascade,
  source_postmortem_id varchar(64) not null references postmortem_report(id) on delete cascade,
  incident_id varchar(64) not null,
  status varchar(32) not null default 'draft',
  severity varchar(32),
  title varchar(240) not null,
  summary text not null,
  root_cause text,
  resolution text,
  prevention text,
  quality_score int not null default 60,
  created_by varchar(64) not null,
  reviewed_by varchar(64),
  published_at timestamptz,
  archived_at timestamptz,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  constraint ck_incident_case_status
    check (status in ('draft', 'published', 'archived')),
  constraint ck_incident_case_severity
    check (severity is null or severity in ('info', 'low', 'medium', 'high', 'critical')),
  constraint ck_incident_case_quality_score
    check (quality_score >= 0 and quality_score <= 100)
);

create unique index if not exists uq_incident_case_source_postmortem
  on incident_case(tenant_id, source_postmortem_id);

create index if not exists idx_incident_case_incident
  on incident_case(tenant_id, incident_id, created_at desc);

create index if not exists idx_incident_case_status_quality
  on incident_case(tenant_id, status, quality_score desc, created_at desc);

create table if not exists incident_case_symptom (
  id varchar(64) primary key,
  tenant_id varchar(64) not null references tenant(id) on delete cascade,
  case_id varchar(64) not null references incident_case(id) on delete cascade,
  symptom_type varchar(64) not null,
  name varchar(160) not null,
  description text,
  created_at timestamptz not null default now()
);

create index if not exists idx_incident_case_symptom_case
  on incident_case_symptom(tenant_id, case_id, created_at);

create table if not exists incident_case_resolution_step (
  id varchar(64) primary key,
  tenant_id varchar(64) not null references tenant(id) on delete cascade,
  case_id varchar(64) not null references incident_case(id) on delete cascade,
  step_order int not null,
  title varchar(240) not null,
  description text,
  action_type varchar(64),
  source_ref_id varchar(64),
  created_at timestamptz not null default now(),
  constraint uq_incident_case_resolution_step_order
    unique (tenant_id, case_id, step_order)
);

create index if not exists idx_incident_case_resolution_step_case
  on incident_case_resolution_step(tenant_id, case_id, step_order);

create table if not exists incident_case_tag (
  id varchar(64) primary key,
  tenant_id varchar(64) not null references tenant(id) on delete cascade,
  case_id varchar(64) not null references incident_case(id) on delete cascade,
  tag varchar(64) not null,
  created_at timestamptz not null default now(),
  constraint uq_incident_case_tag unique (tenant_id, case_id, tag)
);

create index if not exists idx_incident_case_tag_tag
  on incident_case_tag(tenant_id, tag);
```

---

# 6. jOOQ Codegen

路径：

```txt id="kvjdxp"
modules/aiops-persistence/src/main/resources/jooq-codegen.xml
```

`includes` 追加：

```txt id="xolj04"
incident_case | incident_case_symptom | incident_case_resolution_step | incident_case_tag
```

完整 includes 建议：

```xml id="y5yd9v"
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
  postmortem_report | postmortem_section | postmortem_action_item |
  incident_case | incident_case_symptom | incident_case_resolution_step | incident_case_tag
</includes>
```

---

# 7. DTO 完整代码

## 7.1 `IncidentCaseCreateFromPostmortemRequest.java`

路径：

```txt id="g7kh96"
modules/aiops-execution/src/main/java/io/aegisops/execution/dto/IncidentCaseCreateFromPostmortemRequest.java
```

```java id="7fgqbn"
package io.aegisops.execution.dto;

import java.util.List;

public record IncidentCaseCreateFromPostmortemRequest(
    String createdBy,
    Integer qualityScore,
    List<String> tags) {}
```

---

## 7.2 `IncidentCasePublishRequest.java`

```java id="oj8hyy"
package io.aegisops.execution.dto;

public record IncidentCasePublishRequest(String reviewer) {}
```

---

## 7.3 `IncidentCaseCreateCommand.java`

```java id="ctecqt"
package io.aegisops.execution.dto;

public record IncidentCaseCreateCommand(
    String id,
    String tenantId,
    String sourcePostmortemId,
    String incidentId,
    String status,
    String severity,
    String title,
    String summary,
    String rootCause,
    String resolution,
    String prevention,
    int qualityScore,
    String createdBy) {}
```

---

## 7.4 `IncidentCaseSymptomCreateCommand.java`

```java id="wjfkse"
package io.aegisops.execution.dto;

public record IncidentCaseSymptomCreateCommand(
    String id,
    String tenantId,
    String caseId,
    String symptomType,
    String name,
    String description) {}
```

---

## 7.5 `IncidentCaseResolutionStepCreateCommand.java`

```java id="xfzwq5"
package io.aegisops.execution.dto;

public record IncidentCaseResolutionStepCreateCommand(
    String id,
    String tenantId,
    String caseId,
    int stepOrder,
    String title,
    String description,
    String actionType,
    String sourceRefId) {}
```

---

## 7.6 `IncidentCaseTagCreateCommand.java`

```java id="4u8ylj"
package io.aegisops.execution.dto;

public record IncidentCaseTagCreateCommand(
    String id,
    String tenantId,
    String caseId,
    String tag) {}
```

---

## 7.7 `IncidentCaseRecord.java`

```java id="g5h3rp"
package io.aegisops.execution.dto;

import java.time.OffsetDateTime;

public record IncidentCaseRecord(
    String id,
    String tenantId,
    String sourcePostmortemId,
    String incidentId,
    String status,
    String severity,
    String title,
    String summary,
    String rootCause,
    String resolution,
    String prevention,
    int qualityScore,
    String createdBy,
    String reviewedBy,
    OffsetDateTime publishedAt,
    OffsetDateTime archivedAt,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt) {}
```

---

## 7.8 `IncidentCaseSymptomRecord.java`

```java id="k7k1q0"
package io.aegisops.execution.dto;

import java.time.OffsetDateTime;

public record IncidentCaseSymptomRecord(
    String id,
    String tenantId,
    String caseId,
    String symptomType,
    String name,
    String description,
    OffsetDateTime createdAt) {}
```

---

## 7.9 `IncidentCaseResolutionStepRecord.java`

```java id="q3rbkx"
package io.aegisops.execution.dto;

import java.time.OffsetDateTime;

public record IncidentCaseResolutionStepRecord(
    String id,
    String tenantId,
    String caseId,
    int stepOrder,
    String title,
    String description,
    String actionType,
    String sourceRefId,
    OffsetDateTime createdAt) {}
```

---

## 7.10 `IncidentCaseTagRecord.java`

```java id="h62yt2"
package io.aegisops.execution.dto;

import java.time.OffsetDateTime;

public record IncidentCaseTagRecord(
    String id,
    String tenantId,
    String caseId,
    String tag,
    OffsetDateTime createdAt) {}
```

---

## 7.11 `IncidentCaseResponse.java`

```java id="5ny1n2"
package io.aegisops.execution.dto;

import java.time.OffsetDateTime;
import java.util.List;

public record IncidentCaseResponse(
    String id,
    String tenantId,
    String sourcePostmortemId,
    String incidentId,
    String status,
    String severity,
    String title,
    String summary,
    String rootCause,
    String resolution,
    String prevention,
    int qualityScore,
    String createdBy,
    String reviewedBy,
    OffsetDateTime publishedAt,
    OffsetDateTime archivedAt,
    List<IncidentCaseSymptomResponse> symptoms,
    List<IncidentCaseResolutionStepResponse> resolutionSteps,
    List<String> tags,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt) {}
```

---

## 7.12 `IncidentCaseSymptomResponse.java`

```java id="m2h8vl"
package io.aegisops.execution.dto;

import java.time.OffsetDateTime;

public record IncidentCaseSymptomResponse(
    String id,
    String symptomType,
    String name,
    String description,
    OffsetDateTime createdAt) {}
```

---

## 7.13 `IncidentCaseResolutionStepResponse.java`

```java id="tkpv34"
package io.aegisops.execution.dto;

import java.time.OffsetDateTime;

public record IncidentCaseResolutionStepResponse(
    String id,
    int stepOrder,
    String title,
    String description,
    String actionType,
    String sourceRefId,
    OffsetDateTime createdAt) {}
```

---

# 8. Draft Builder

## 8.1 `IncidentCaseDraft.java`

路径：

```txt id="sr6uwe"
modules/aiops-execution/src/main/java/io/aegisops/execution/IncidentCaseDraft.java
```

```java id="c52svc"
package io.aegisops.execution;

import java.util.List;

public record IncidentCaseDraft(
    String title,
    String summary,
    String rootCause,
    String resolution,
    String prevention,
    List<Symptom> symptoms,
    List<ResolutionStep> resolutionSteps,
    List<String> tags) {
  public record Symptom(String symptomType, String name, String description) {}

  public record ResolutionStep(
      String title,
      String description,
      String actionType,
      String sourceRefId) {}
}
```

---

## 8.2 `IncidentCaseDraftBuilder.java`

路径：

```txt id="vjuxo5"
modules/aiops-execution/src/main/java/io/aegisops/execution/IncidentCaseDraftBuilder.java
```

```java id="5hrl68"
package io.aegisops.execution;

import io.aegisops.execution.dto.PostmortemActionItemRecord;
import io.aegisops.execution.dto.PostmortemReportRecord;
import io.aegisops.execution.dto.PostmortemSectionRecord;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class IncidentCaseDraftBuilder {
  public IncidentCaseDraft build(
      PostmortemReportRecord report,
      List<PostmortemSectionRecord> sections,
      List<PostmortemActionItemRecord> actionItems,
      List<String> requestTags) {
    List<IncidentCaseDraft.Symptom> symptoms = new ArrayList<>();
    List<IncidentCaseDraft.ResolutionStep> resolutionSteps = new ArrayList<>();
    LinkedHashSet<String> tags = new LinkedHashSet<>();

    addTag(tags, report.severity());
    addTag(tags, normalizeTag(report.rootCause()));

    for (String tag : requestTags == null ? List.<String>of() : requestTags) {
      addTag(tags, tag);
    }

    for (PostmortemSectionRecord section : sections) {
      switch (section.sectionType()) {
        case "impact", "detection" ->
            symptoms.add(
                new IncidentCaseDraft.Symptom(
                    section.sectionType(),
                    section.title(),
                    section.content()));
        case "resolution" ->
            resolutionSteps.add(
                new IncidentCaseDraft.ResolutionStep(
                    section.title(),
                    section.content(),
                    "manual",
                    section.id()));
        default -> {
          // ignore non-case sections
        }
      }
    }

    int index = 1;
    for (PostmortemActionItemRecord actionItem : actionItems) {
      resolutionSteps.add(
          new IncidentCaseDraft.ResolutionStep(
              "Follow-up " + index++ + ": " + actionItem.title(),
              actionItem.description(),
              "follow_up",
              actionItem.id()));
    }

    if (symptoms.isEmpty()) {
      symptoms.add(
          new IncidentCaseDraft.Symptom(
              "summary",
              "Incident summary",
              report.summary()));
    }

    if (resolutionSteps.isEmpty()) {
      resolutionSteps.add(
          new IncidentCaseDraft.ResolutionStep(
              "Review resolution",
              blankToFallback(report.resolution(), "Resolution requires manual review."),
              "manual",
              report.id()));
    }

    return new IncidentCaseDraft(
        report.title(),
        report.summary(),
        report.rootCause(),
        report.resolution(),
        report.prevention(),
        symptoms,
        resolutionSteps,
        tags.stream().filter(item -> !item.isBlank()).toList());
  }

  private void addTag(LinkedHashSet<String> tags, String value) {
    String tag = normalizeTag(value);
    if (!tag.isBlank()) {
      tags.add(tag);
    }
  }

  private String normalizeTag(String value) {
    if (value == null || value.isBlank()) {
      return "";
    }

    String normalized =
        value.trim()
            .toLowerCase()
            .replaceAll("[^a-z0-9\\u4e00-\\u9fa5]+", "-")
            .replaceAll("^-+", "")
            .replaceAll("-+$", "");

    return normalized.length() > 64 ? normalized.substring(0, 64) : normalized;
  }

  private String blankToFallback(String value, String fallback) {
    return value == null || value.isBlank() ? fallback : value;
  }
}
```

---

# 9. Repository 接口

## 9.1 `IncidentCaseRepository.java`

路径：

```txt id="3jplng"
modules/aiops-execution/src/main/java/io/aegisops/execution/IncidentCaseRepository.java
```

```java id="5rk2au"
package io.aegisops.execution;

import io.aegisops.execution.dto.IncidentCaseCreateCommand;
import io.aegisops.execution.dto.IncidentCaseRecord;
import io.aegisops.execution.dto.IncidentCaseResolutionStepCreateCommand;
import io.aegisops.execution.dto.IncidentCaseResolutionStepRecord;
import io.aegisops.execution.dto.IncidentCaseSymptomCreateCommand;
import io.aegisops.execution.dto.IncidentCaseSymptomRecord;
import io.aegisops.execution.dto.IncidentCaseTagCreateCommand;
import io.aegisops.execution.dto.IncidentCaseTagRecord;
import java.util.List;
import java.util.Optional;

public interface IncidentCaseRepository {
  void createCase(IncidentCaseCreateCommand command);

  void createSymptom(IncidentCaseSymptomCreateCommand command);

  void createResolutionStep(IncidentCaseResolutionStepCreateCommand command);

  void createTag(IncidentCaseTagCreateCommand command);

  Optional<IncidentCaseRecord> findCase(String tenantId, String caseId);

  Optional<IncidentCaseRecord> findByPostmortem(String tenantId, String postmortemId);

  Optional<IncidentCaseRecord> findLatestByIncident(String tenantId, String incidentId);

  List<IncidentCaseRecord> listCases(String tenantId, String status, String tag, int limit);

  List<IncidentCaseSymptomRecord> listSymptoms(String tenantId, String caseId);

  List<IncidentCaseResolutionStepRecord> listResolutionSteps(String tenantId, String caseId);

  List<IncidentCaseTagRecord> listTags(String tenantId, String caseId);

  boolean publish(String tenantId, String caseId, String reviewer);

  boolean archive(String tenantId, String caseId);
}
```

---

# 10. jOOQ Repository 实现

## 10.1 `JooqIncidentCaseRepository.java`

路径：

```txt id="jti9n9"
modules/aiops-execution/src/main/java/io/aegisops/execution/JooqIncidentCaseRepository.java
```

```java id="txqsj5"
package io.aegisops.execution;

import static io.aegisops.persistence.jooq.Tables.INCIDENT_CASE;
import static io.aegisops.persistence.jooq.Tables.INCIDENT_CASE_RESOLUTION_STEP;
import static io.aegisops.persistence.jooq.Tables.INCIDENT_CASE_SYMPTOM;
import static io.aegisops.persistence.jooq.Tables.INCIDENT_CASE_TAG;

import io.aegisops.execution.dto.IncidentCaseCreateCommand;
import io.aegisops.execution.dto.IncidentCaseRecord;
import io.aegisops.execution.dto.IncidentCaseResolutionStepCreateCommand;
import io.aegisops.execution.dto.IncidentCaseResolutionStepRecord;
import io.aegisops.execution.dto.IncidentCaseSymptomCreateCommand;
import io.aegisops.execution.dto.IncidentCaseSymptomRecord;
import io.aegisops.execution.dto.IncidentCaseTagCreateCommand;
import io.aegisops.execution.dto.IncidentCaseTagRecord;
import java.util.List;
import java.util.Optional;
import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Repository;

@Repository
public class JooqIncidentCaseRepository implements IncidentCaseRepository {
  private final DSLContext dsl;

  public JooqIncidentCaseRepository(DSLContext dsl) {
    this.dsl = dsl;
  }

  @Override
  public void createCase(IncidentCaseCreateCommand command) {
    dsl.insertInto(INCIDENT_CASE)
        .set(INCIDENT_CASE.ID, command.id())
        .set(INCIDENT_CASE.TENANT_ID, command.tenantId())
        .set(INCIDENT_CASE.SOURCE_POSTMORTEM_ID, command.sourcePostmortemId())
        .set(INCIDENT_CASE.INCIDENT_ID, command.incidentId())
        .set(INCIDENT_CASE.STATUS, command.status())
        .set(INCIDENT_CASE.SEVERITY, command.severity())
        .set(INCIDENT_CASE.TITLE, command.title())
        .set(INCIDENT_CASE.SUMMARY, command.summary())
        .set(INCIDENT_CASE.ROOT_CAUSE, command.rootCause())
        .set(INCIDENT_CASE.RESOLUTION, command.resolution())
        .set(INCIDENT_CASE.PREVENTION, command.prevention())
        .set(INCIDENT_CASE.QUALITY_SCORE, command.qualityScore())
        .set(INCIDENT_CASE.CREATED_BY, command.createdBy())
        .set(INCIDENT_CASE.CREATED_AT, DSL.currentOffsetDateTime())
        .set(INCIDENT_CASE.UPDATED_AT, DSL.currentOffsetDateTime())
        .execute();
  }

  @Override
  public void createSymptom(IncidentCaseSymptomCreateCommand command) {
    dsl.insertInto(INCIDENT_CASE_SYMPTOM)
        .set(INCIDENT_CASE_SYMPTOM.ID, command.id())
        .set(INCIDENT_CASE_SYMPTOM.TENANT_ID, command.tenantId())
        .set(INCIDENT_CASE_SYMPTOM.CASE_ID, command.caseId())
        .set(INCIDENT_CASE_SYMPTOM.SYMPTOM_TYPE, command.symptomType())
        .set(INCIDENT_CASE_SYMPTOM.NAME, command.name())
        .set(INCIDENT_CASE_SYMPTOM.DESCRIPTION, command.description())
        .set(INCIDENT_CASE_SYMPTOM.CREATED_AT, DSL.currentOffsetDateTime())
        .execute();
  }

  @Override
  public void createResolutionStep(IncidentCaseResolutionStepCreateCommand command) {
    dsl.insertInto(INCIDENT_CASE_RESOLUTION_STEP)
        .set(INCIDENT_CASE_RESOLUTION_STEP.ID, command.id())
        .set(INCIDENT_CASE_RESOLUTION_STEP.TENANT_ID, command.tenantId())
        .set(INCIDENT_CASE_RESOLUTION_STEP.CASE_ID, command.caseId())
        .set(INCIDENT_CASE_RESOLUTION_STEP.STEP_ORDER, command.stepOrder())
        .set(INCIDENT_CASE_RESOLUTION_STEP.TITLE, command.title())
        .set(INCIDENT_CASE_RESOLUTION_STEP.DESCRIPTION, command.description())
        .set(INCIDENT_CASE_RESOLUTION_STEP.ACTION_TYPE, command.actionType())
        .set(INCIDENT_CASE_RESOLUTION_STEP.SOURCE_REF_ID, command.sourceRefId())
        .set(INCIDENT_CASE_RESOLUTION_STEP.CREATED_AT, DSL.currentOffsetDateTime())
        .execute();
  }

  @Override
  public void createTag(IncidentCaseTagCreateCommand command) {
    dsl.insertInto(INCIDENT_CASE_TAG)
        .set(INCIDENT_CASE_TAG.ID, command.id())
        .set(INCIDENT_CASE_TAG.TENANT_ID, command.tenantId())
        .set(INCIDENT_CASE_TAG.CASE_ID, command.caseId())
        .set(INCIDENT_CASE_TAG.TAG, command.tag())
        .set(INCIDENT_CASE_TAG.CREATED_AT, DSL.currentOffsetDateTime())
        .onDuplicateKeyIgnore()
        .execute();
  }

  @Override
  public Optional<IncidentCaseRecord> findCase(String tenantId, String caseId) {
    return selectCase()
        .where(INCIDENT_CASE.TENANT_ID.eq(tenantId))
        .and(INCIDENT_CASE.ID.eq(caseId))
        .fetchOptional(this::toCaseRecord);
  }

  @Override
  public Optional<IncidentCaseRecord> findByPostmortem(String tenantId, String postmortemId) {
    return selectCase()
        .where(INCIDENT_CASE.TENANT_ID.eq(tenantId))
        .and(INCIDENT_CASE.SOURCE_POSTMORTEM_ID.eq(postmortemId))
        .fetchOptional(this::toCaseRecord);
  }

  @Override
  public Optional<IncidentCaseRecord> findLatestByIncident(String tenantId, String incidentId) {
    return selectCase()
        .where(INCIDENT_CASE.TENANT_ID.eq(tenantId))
        .and(INCIDENT_CASE.INCIDENT_ID.eq(incidentId))
        .orderBy(INCIDENT_CASE.CREATED_AT.desc())
        .limit(1)
        .fetchOptional(this::toCaseRecord);
  }

  @Override
  public List<IncidentCaseRecord> listCases(String tenantId, String status, String tag, int limit) {
    Condition condition = INCIDENT_CASE.TENANT_ID.eq(tenantId);

    if (status != null && !status.isBlank()) {
      condition = condition.and(INCIDENT_CASE.STATUS.eq(status));
    }

    var query =
        dsl.selectDistinct(
                INCIDENT_CASE.ID,
                INCIDENT_CASE.TENANT_ID,
                INCIDENT_CASE.SOURCE_POSTMORTEM_ID,
                INCIDENT_CASE.INCIDENT_ID,
                INCIDENT_CASE.STATUS,
                INCIDENT_CASE.SEVERITY,
                INCIDENT_CASE.TITLE,
                INCIDENT_CASE.SUMMARY,
                INCIDENT_CASE.ROOT_CAUSE,
                INCIDENT_CASE.RESOLUTION,
                INCIDENT_CASE.PREVENTION,
                INCIDENT_CASE.QUALITY_SCORE,
                INCIDENT_CASE.CREATED_BY,
                INCIDENT_CASE.REVIEWED_BY,
                INCIDENT_CASE.PUBLISHED_AT,
                INCIDENT_CASE.ARCHIVED_AT,
                INCIDENT_CASE.CREATED_AT,
                INCIDENT_CASE.UPDATED_AT)
            .from(INCIDENT_CASE);

    if (tag != null && !tag.isBlank()) {
      query =
          query.join(INCIDENT_CASE_TAG)
              .on(INCIDENT_CASE_TAG.CASE_ID.eq(INCIDENT_CASE.ID))
              .and(INCIDENT_CASE_TAG.TENANT_ID.eq(INCIDENT_CASE.TENANT_ID));
      condition = condition.and(INCIDENT_CASE_TAG.TAG.eq(tag));
    }

    return query.where(condition)
        .orderBy(INCIDENT_CASE.QUALITY_SCORE.desc(), INCIDENT_CASE.CREATED_AT.desc())
        .limit(Math.max(1, Math.min(limit, 100)))
        .fetch(this::toCaseRecord);
  }

  @Override
  public List<IncidentCaseSymptomRecord> listSymptoms(String tenantId, String caseId) {
    return dsl.select(
            INCIDENT_CASE_SYMPTOM.ID,
            INCIDENT_CASE_SYMPTOM.TENANT_ID,
            INCIDENT_CASE_SYMPTOM.CASE_ID,
            INCIDENT_CASE_SYMPTOM.SYMPTOM_TYPE,
            INCIDENT_CASE_SYMPTOM.NAME,
            INCIDENT_CASE_SYMPTOM.DESCRIPTION,
            INCIDENT_CASE_SYMPTOM.CREATED_AT)
        .from(INCIDENT_CASE_SYMPTOM)
        .where(INCIDENT_CASE_SYMPTOM.TENANT_ID.eq(tenantId))
        .and(INCIDENT_CASE_SYMPTOM.CASE_ID.eq(caseId))
        .orderBy(INCIDENT_CASE_SYMPTOM.CREATED_AT.asc())
        .fetch(this::toSymptomRecord);
  }

  @Override
  public List<IncidentCaseResolutionStepRecord> listResolutionSteps(String tenantId, String caseId) {
    return dsl.select(
            INCIDENT_CASE_RESOLUTION_STEP.ID,
            INCIDENT_CASE_RESOLUTION_STEP.TENANT_ID,
            INCIDENT_CASE_RESOLUTION_STEP.CASE_ID,
            INCIDENT_CASE_RESOLUTION_STEP.STEP_ORDER,
            INCIDENT_CASE_RESOLUTION_STEP.TITLE,
            INCIDENT_CASE_RESOLUTION_STEP.DESCRIPTION,
            INCIDENT_CASE_RESOLUTION_STEP.ACTION_TYPE,
            INCIDENT_CASE_RESOLUTION_STEP.SOURCE_REF_ID,
            INCIDENT_CASE_RESOLUTION_STEP.CREATED_AT)
        .from(INCIDENT_CASE_RESOLUTION_STEP)
        .where(INCIDENT_CASE_RESOLUTION_STEP.TENANT_ID.eq(tenantId))
        .and(INCIDENT_CASE_RESOLUTION_STEP.CASE_ID.eq(caseId))
        .orderBy(INCIDENT_CASE_RESOLUTION_STEP.STEP_ORDER.asc())
        .fetch(this::toResolutionStepRecord);
  }

  @Override
  public List<IncidentCaseTagRecord> listTags(String tenantId, String caseId) {
    return dsl.select(
            INCIDENT_CASE_TAG.ID,
            INCIDENT_CASE_TAG.TENANT_ID,
            INCIDENT_CASE_TAG.CASE_ID,
            INCIDENT_CASE_TAG.TAG,
            INCIDENT_CASE_TAG.CREATED_AT)
        .from(INCIDENT_CASE_TAG)
        .where(INCIDENT_CASE_TAG.TENANT_ID.eq(tenantId))
        .and(INCIDENT_CASE_TAG.CASE_ID.eq(caseId))
        .orderBy(INCIDENT_CASE_TAG.TAG.asc())
        .fetch(this::toTagRecord);
  }

  @Override
  public boolean publish(String tenantId, String caseId, String reviewer) {
    return dsl.update(INCIDENT_CASE)
            .set(INCIDENT_CASE.STATUS, "published")
            .set(INCIDENT_CASE.REVIEWED_BY, reviewer)
            .set(INCIDENT_CASE.PUBLISHED_AT, DSL.currentOffsetDateTime())
            .set(INCIDENT_CASE.UPDATED_AT, DSL.currentOffsetDateTime())
            .where(INCIDENT_CASE.TENANT_ID.eq(tenantId))
            .and(INCIDENT_CASE.ID.eq(caseId))
            .and(INCIDENT_CASE.STATUS.eq("draft"))
            .execute()
        > 0;
  }

  @Override
  public boolean archive(String tenantId, String caseId) {
    return dsl.update(INCIDENT_CASE)
            .set(INCIDENT_CASE.STATUS, "archived")
            .set(INCIDENT_CASE.ARCHIVED_AT, DSL.currentOffsetDateTime())
            .set(INCIDENT_CASE.UPDATED_AT, DSL.currentOffsetDateTime())
            .where(INCIDENT_CASE.TENANT_ID.eq(tenantId))
            .and(INCIDENT_CASE.ID.eq(caseId))
            .and(INCIDENT_CASE.STATUS.ne("archived"))
            .execute()
        > 0;
  }

  private org.jooq.SelectJoinStep<org.jooq.Record> selectCase() {
    return dsl.select(
            INCIDENT_CASE.ID,
            INCIDENT_CASE.TENANT_ID,
            INCIDENT_CASE.SOURCE_POSTMORTEM_ID,
            INCIDENT_CASE.INCIDENT_ID,
            INCIDENT_CASE.STATUS,
            INCIDENT_CASE.SEVERITY,
            INCIDENT_CASE.TITLE,
            INCIDENT_CASE.SUMMARY,
            INCIDENT_CASE.ROOT_CAUSE,
            INCIDENT_CASE.RESOLUTION,
            INCIDENT_CASE.PREVENTION,
            INCIDENT_CASE.QUALITY_SCORE,
            INCIDENT_CASE.CREATED_BY,
            INCIDENT_CASE.REVIEWED_BY,
            INCIDENT_CASE.PUBLISHED_AT,
            INCIDENT_CASE.ARCHIVED_AT,
            INCIDENT_CASE.CREATED_AT,
            INCIDENT_CASE.UPDATED_AT)
        .from(INCIDENT_CASE);
  }

  private IncidentCaseRecord toCaseRecord(org.jooq.Record record) {
    return new IncidentCaseRecord(
        record.get(INCIDENT_CASE.ID),
        record.get(INCIDENT_CASE.TENANT_ID),
        record.get(INCIDENT_CASE.SOURCE_POSTMORTEM_ID),
        record.get(INCIDENT_CASE.INCIDENT_ID),
        record.get(INCIDENT_CASE.STATUS),
        record.get(INCIDENT_CASE.SEVERITY),
        record.get(INCIDENT_CASE.TITLE),
        record.get(INCIDENT_CASE.SUMMARY),
        record.get(INCIDENT_CASE.ROOT_CAUSE),
        record.get(INCIDENT_CASE.RESOLUTION),
        record.get(INCIDENT_CASE.PREVENTION),
        value(record.get(INCIDENT_CASE.QUALITY_SCORE)),
        record.get(INCIDENT_CASE.CREATED_BY),
        record.get(INCIDENT_CASE.REVIEWED_BY),
        record.get(INCIDENT_CASE.PUBLISHED_AT),
        record.get(INCIDENT_CASE.ARCHIVED_AT),
        record.get(INCIDENT_CASE.CREATED_AT),
        record.get(INCIDENT_CASE.UPDATED_AT));
  }

  private IncidentCaseSymptomRecord toSymptomRecord(org.jooq.Record record) {
    return new IncidentCaseSymptomRecord(
        record.get(INCIDENT_CASE_SYMPTOM.ID),
        record.get(INCIDENT_CASE_SYMPTOM.TENANT_ID),
        record.get(INCIDENT_CASE_SYMPTOM.CASE_ID),
        record.get(INCIDENT_CASE_SYMPTOM.SYMPTOM_TYPE),
        record.get(INCIDENT_CASE_SYMPTOM.NAME),
        record.get(INCIDENT_CASE_SYMPTOM.DESCRIPTION),
        record.get(INCIDENT_CASE_SYMPTOM.CREATED_AT));
  }

  private IncidentCaseResolutionStepRecord toResolutionStepRecord(org.jooq.Record record) {
    return new IncidentCaseResolutionStepRecord(
        record.get(INCIDENT_CASE_RESOLUTION_STEP.ID),
        record.get(INCIDENT_CASE_RESOLUTION_STEP.TENANT_ID),
        record.get(INCIDENT_CASE_RESOLUTION_STEP.CASE_ID),
        value(record.get(INCIDENT_CASE_RESOLUTION_STEP.STEP_ORDER)),
        record.get(INCIDENT_CASE_RESOLUTION_STEP.TITLE),
        record.get(INCIDENT_CASE_RESOLUTION_STEP.DESCRIPTION),
        record.get(INCIDENT_CASE_RESOLUTION_STEP.ACTION_TYPE),
        record.get(INCIDENT_CASE_RESOLUTION_STEP.SOURCE_REF_ID),
        record.get(INCIDENT_CASE_RESOLUTION_STEP.CREATED_AT));
  }

  private IncidentCaseTagRecord toTagRecord(org.jooq.Record record) {
    return new IncidentCaseTagRecord(
        record.get(INCIDENT_CASE_TAG.ID),
        record.get(INCIDENT_CASE_TAG.TENANT_ID),
        record.get(INCIDENT_CASE_TAG.CASE_ID),
        record.get(INCIDENT_CASE_TAG.TAG),
        record.get(INCIDENT_CASE_TAG.CREATED_AT));
  }

  private int value(Integer value) {
    return value == null ? 0 : value;
  }
}
```

---

# 11. Service

## 11.1 `IncidentCaseService.java`

路径：

```txt id="hkv8t5"
modules/aiops-execution/src/main/java/io/aegisops/execution/IncidentCaseService.java
```

```java id="6tle7w"
package io.aegisops.execution;

import io.aegisops.common.exception.AppException;
import io.aegisops.execution.dto.IncidentCaseCreateCommand;
import io.aegisops.execution.dto.IncidentCaseCreateFromPostmortemRequest;
import io.aegisops.execution.dto.IncidentCasePublishRequest;
import io.aegisops.execution.dto.IncidentCaseRecord;
import io.aegisops.execution.dto.IncidentCaseResolutionStepCreateCommand;
import io.aegisops.execution.dto.IncidentCaseResolutionStepRecord;
import io.aegisops.execution.dto.IncidentCaseResolutionStepResponse;
import io.aegisops.execution.dto.IncidentCaseResponse;
import io.aegisops.execution.dto.IncidentCaseSymptomCreateCommand;
import io.aegisops.execution.dto.IncidentCaseSymptomRecord;
import io.aegisops.execution.dto.IncidentCaseSymptomResponse;
import io.aegisops.execution.dto.IncidentCaseTagCreateCommand;
import io.aegisops.execution.dto.PostmortemActionItemRecord;
import io.aegisops.execution.dto.PostmortemReportRecord;
import io.aegisops.execution.dto.PostmortemSectionRecord;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class IncidentCaseService {
  private final IncidentCaseRepository caseRepository;
  private final PostmortemRepository postmortemRepository;
  private final IncidentCaseDraftBuilder draftBuilder;

  public IncidentCaseService(
      IncidentCaseRepository caseRepository,
      PostmortemRepository postmortemRepository,
      IncidentCaseDraftBuilder draftBuilder) {
    this.caseRepository = caseRepository;
    this.postmortemRepository = postmortemRepository;
    this.draftBuilder = draftBuilder;
  }

  @Transactional
  public IncidentCaseResponse createFromPostmortem(
      String tenantId,
      String postmortemId,
      IncidentCaseCreateFromPostmortemRequest request) {
    caseRepository
        .findByPostmortem(tenantId, postmortemId)
        .ifPresent(
            existing -> {
              throw new AppException(
                  "INCIDENT_CASE_ALREADY_EXISTS",
                  "Incident case already exists for this postmortem");
            });

    PostmortemReportRecord report =
        postmortemRepository
            .findReport(tenantId, postmortemId)
            .orElseThrow(
                () -> new AppException("POSTMORTEM_NOT_FOUND", "Postmortem report not found"));

    validatePostmortemStatus(report);

    List<PostmortemSectionRecord> sections =
        postmortemRepository.listSections(tenantId, postmortemId);
    List<PostmortemActionItemRecord> actionItems =
        postmortemRepository.listActionItems(tenantId, postmortemId);

    IncidentCaseDraft draft =
        draftBuilder.build(
            report,
            sections,
            actionItems,
            request == null ? List.of() : request.tags());

    String caseId = newId("icase");
    String actor = blankToDefault(request == null ? null : request.createdBy(), "system");

    caseRepository.createCase(
        new IncidentCaseCreateCommand(
            caseId,
            tenantId,
            postmortemId,
            report.incidentId(),
            "draft",
            report.severity(),
            draft.title(),
            draft.summary(),
            draft.rootCause(),
            draft.resolution(),
            draft.prevention(),
            normalizeQualityScore(request == null ? null : request.qualityScore()),
            actor));

    for (IncidentCaseDraft.Symptom symptom : draft.symptoms()) {
      caseRepository.createSymptom(
          new IncidentCaseSymptomCreateCommand(
              newId("icsym"),
              tenantId,
              caseId,
              symptom.symptomType(),
              symptom.name(),
              symptom.description()));
    }

    int order = 1;
    for (IncidentCaseDraft.ResolutionStep step : draft.resolutionSteps()) {
      caseRepository.createResolutionStep(
          new IncidentCaseResolutionStepCreateCommand(
              newId("icstep"),
              tenantId,
              caseId,
              order++,
              step.title(),
              step.description(),
              step.actionType(),
              step.sourceRefId()));
    }

    for (String tag : draft.tags()) {
      caseRepository.createTag(
          new IncidentCaseTagCreateCommand(
              newId("ictag"),
              tenantId,
              caseId,
              tag));
    }

    return get(tenantId, caseId);
  }

  public IncidentCaseResponse get(String tenantId, String caseId) {
    IncidentCaseRecord record =
        caseRepository
            .findCase(tenantId, caseId)
            .orElseThrow(
                () -> new AppException("INCIDENT_CASE_NOT_FOUND", "Incident case not found"));

    return toResponse(record);
  }

  public IncidentCaseResponse latestByIncident(String tenantId, String incidentId) {
    IncidentCaseRecord record =
        caseRepository
            .findLatestByIncident(tenantId, incidentId)
            .orElseThrow(
                () -> new AppException("INCIDENT_CASE_NOT_FOUND", "Incident case not found"));

    return toResponse(record);
  }

  public List<IncidentCaseResponse> list(
      String tenantId, String status, String tag, int limit) {
    String normalizedStatus = normalizeStatusOrNull(status);

    return caseRepository.listCases(tenantId, normalizedStatus, normalizeTagOrNull(tag), limit).stream()
        .map(this::toResponse)
        .toList();
  }

  @Transactional
  public IncidentCaseResponse publish(
      String tenantId, String caseId, IncidentCasePublishRequest request) {
    IncidentCaseRecord record =
        caseRepository
            .findCase(tenantId, caseId)
            .orElseThrow(
                () -> new AppException("INCIDENT_CASE_NOT_FOUND", "Incident case not found"));

    if (!"draft".equals(record.status())) {
      throw new AppException("INCIDENT_CASE_PUBLISH_STATUS_INVALID", "Only draft case can be published");
    }

    if (record.qualityScore() < 60) {
      throw new AppException(
          "INCIDENT_CASE_QUALITY_TOO_LOW",
          "Incident case quality score must be at least 60 to publish");
    }

    String reviewer = request == null ? null : request.reviewer();
    if (reviewer == null || reviewer.isBlank()) {
      throw new AppException("INCIDENT_CASE_REVIEWER_REQUIRED", "Reviewer is required");
    }

    boolean updated = caseRepository.publish(tenantId, caseId, reviewer.trim());
    if (!updated) {
      throw new AppException("INCIDENT_CASE_PUBLISH_FAILED", "Incident case was not published");
    }

    return get(tenantId, caseId);
  }

  @Transactional
  public IncidentCaseResponse archive(String tenantId, String caseId) {
    boolean updated = caseRepository.archive(tenantId, caseId);
    if (!updated) {
      throw new AppException("INCIDENT_CASE_ARCHIVE_FAILED", "Incident case was not archived");
    }
    return get(tenantId, caseId);
  }

  private IncidentCaseResponse toResponse(IncidentCaseRecord record) {
    List<IncidentCaseSymptomResponse> symptoms =
        caseRepository.listSymptoms(record.tenantId(), record.id()).stream()
            .map(this::toSymptomResponse)
            .toList();

    List<IncidentCaseResolutionStepResponse> steps =
        caseRepository.listResolutionSteps(record.tenantId(), record.id()).stream()
            .map(this::toResolutionStepResponse)
            .toList();

    List<String> tags =
        caseRepository.listTags(record.tenantId(), record.id()).stream()
            .map(tag -> tag.tag())
            .toList();

    return new IncidentCaseResponse(
        record.id(),
        record.tenantId(),
        record.sourcePostmortemId(),
        record.incidentId(),
        record.status(),
        record.severity(),
        record.title(),
        record.summary(),
        record.rootCause(),
        record.resolution(),
        record.prevention(),
        record.qualityScore(),
        record.createdBy(),
        record.reviewedBy(),
        record.publishedAt(),
        record.archivedAt(),
        symptoms,
        steps,
        tags,
        record.createdAt(),
        record.updatedAt());
  }

  private IncidentCaseSymptomResponse toSymptomResponse(IncidentCaseSymptomRecord record) {
    return new IncidentCaseSymptomResponse(
        record.id(),
        record.symptomType(),
        record.name(),
        record.description(),
        record.createdAt());
  }

  private IncidentCaseResolutionStepResponse toResolutionStepResponse(
      IncidentCaseResolutionStepRecord record) {
    return new IncidentCaseResolutionStepResponse(
        record.id(),
        record.stepOrder(),
        record.title(),
        record.description(),
        record.actionType(),
        record.sourceRefId(),
        record.createdAt());
  }

  private void validatePostmortemStatus(PostmortemReportRecord report) {
    if (!List.of("generated", "reviewed").contains(report.status())) {
      throw new AppException(
          "POSTMORTEM_STATUS_INVALID",
          "Only generated or reviewed postmortem can be converted to incident case");
    }
  }

  private int normalizeQualityScore(Integer value) {
    if (value == null) {
      return 60;
    }
    return Math.max(0, Math.min(value, 100));
  }

  private String normalizeStatusOrNull(String value) {
    if (value == null || value.isBlank()) {
      return null;
    }

    String status = value.trim().toLowerCase();
    if (!List.of("draft", "published", "archived").contains(status)) {
      throw new AppException("INCIDENT_CASE_STATUS_INVALID", "Invalid incident case status");
    }
    return status;
  }

  private String normalizeTagOrNull(String value) {
    if (value == null || value.isBlank()) {
      return null;
    }

    String tag =
        value.trim()
            .toLowerCase()
            .replaceAll("[^a-z0-9\\u4e00-\\u9fa5]+", "-")
            .replaceAll("^-+", "")
            .replaceAll("-+$", "");

    return tag.isBlank() ? null : tag;
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

# 12. Controller

## 12.1 `IncidentCaseController.java`

路径：

```txt id="sioxt2"
modules/aiops-execution/src/main/java/io/aegisops/execution/IncidentCaseController.java
```

```java id="0y35be"
package io.aegisops.execution;

import io.aegisops.common.api.ApiResponse;
import io.aegisops.common.tenant.TenantContext;
import io.aegisops.execution.dto.IncidentCaseCreateFromPostmortemRequest;
import io.aegisops.execution.dto.IncidentCasePublishRequest;
import io.aegisops.execution.dto.IncidentCaseResponse;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class IncidentCaseController {
  private final IncidentCaseService service;

  public IncidentCaseController(IncidentCaseService service) {
    this.service = service;
  }

  @PostMapping("/api/postmortems/{postmortemId}/cases")
  public ApiResponse<IncidentCaseResponse> createFromPostmortem(
      @PathVariable String postmortemId,
      @RequestBody(required = false) IncidentCaseCreateFromPostmortemRequest request) {
    return ApiResponse.ok(
        service.createFromPostmortem(TenantContext.requireTenantId(), postmortemId, request));
  }

  @GetMapping("/api/incidents/{incidentId}/cases/latest")
  public ApiResponse<IncidentCaseResponse> latestByIncident(@PathVariable String incidentId) {
    return ApiResponse.ok(
        service.latestByIncident(TenantContext.requireTenantId(), incidentId));
  }

  @GetMapping("/api/incident-cases/{caseId}")
  public ApiResponse<IncidentCaseResponse> get(@PathVariable String caseId) {
    return ApiResponse.ok(service.get(TenantContext.requireTenantId(), caseId));
  }

  @GetMapping("/api/incident-cases")
  public ApiResponse<List<IncidentCaseResponse>> list(
      @RequestParam(required = false) String status,
      @RequestParam(required = false) String tag,
      @RequestParam(defaultValue = "20") int limit) {
    return ApiResponse.ok(service.list(TenantContext.requireTenantId(), status, tag, limit));
  }

  @PostMapping("/api/incident-cases/{caseId}/publish")
  public ApiResponse<IncidentCaseResponse> publish(
      @PathVariable String caseId,
      @RequestBody IncidentCasePublishRequest request) {
    return ApiResponse.ok(service.publish(TenantContext.requireTenantId(), caseId, request));
  }

  @PostMapping("/api/incident-cases/{caseId}/archive")
  public ApiResponse<IncidentCaseResponse> archive(@PathVariable String caseId) {
    return ApiResponse.ok(service.archive(TenantContext.requireTenantId(), caseId));
  }
}
```

---

# 13. 单元测试

## 13.1 `IncidentCaseDraftBuilderTest.java`

路径：

```txt id="rbq40v"
modules/aiops-execution/src/test/java/io/aegisops/execution/IncidentCaseDraftBuilderTest.java
```

```java id="u2vssk"
package io.aegisops.execution;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.aegisops.execution.dto.PostmortemActionItemRecord;
import io.aegisops.execution.dto.PostmortemReportRecord;
import io.aegisops.execution.dto.PostmortemSectionRecord;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

class IncidentCaseDraftBuilderTest {
  private final IncidentCaseDraftBuilder builder = new IncidentCaseDraftBuilder();

  @Test
  void buildDraftFromPostmortem() {
    IncidentCaseDraft draft =
        builder.build(
            report(),
            List.of(
                section("pms_1", "impact", "Impact", "High error rate"),
                section("pms_2", "detection", "Detection", "Alert fired"),
                section("pms_3", "resolution", "Resolution", "Restart service")),
            List.of(actionItem()),
            List.of("order-service", "db-timeout"));

    assertEquals("Postmortem - Order service error", draft.title());
    assertTrue(draft.symptoms().size() >= 2);
    assertTrue(draft.resolutionSteps().size() >= 2);
    assertTrue(draft.tags().contains("order-service"));
  }

  @Test
  void fallbackSymptomAndResolutionWhenSectionsEmpty() {
    IncidentCaseDraft draft =
        builder.build(report(), List.of(), List.of(), List.of());

    assertEquals(1, draft.symptoms().size());
    assertEquals(1, draft.resolutionSteps().size());
  }

  private PostmortemReportRecord report() {
    return new PostmortemReportRecord(
        "pmr_1",
        "tenant_1",
        "inc_1",
        "generated",
        "high",
        "Postmortem - Order service error",
        "summary",
        "impact",
        "db timeout",
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

  private PostmortemSectionRecord section(
      String id, String type, String title, String content) {
    return new PostmortemSectionRecord(
        id,
        "tenant_1",
        "pmr_1",
        1,
        type,
        title,
        content,
        "{}",
        OffsetDateTime.now());
  }

  private PostmortemActionItemRecord actionItem() {
    return new PostmortemActionItemRecord(
        "pmai_1",
        "tenant_1",
        "pmr_1",
        "Update runbook",
        "Add rollback step",
        "bob",
        "high",
        "open",
        null,
        "manual",
        null,
        "alice",
        OffsetDateTime.now(),
        OffsetDateTime.now());
  }
}
```

---

## 13.2 `IncidentCaseServiceTest.java`

路径：

```txt id="o2r6s9"
modules/aiops-execution/src/test/java/io/aegisops/execution/IncidentCaseServiceTest.java
```

```java id="jdr80z"
package io.aegisops.execution;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.aegisops.common.exception.AppException;
import io.aegisops.execution.dto.IncidentCaseCreateCommand;
import io.aegisops.execution.dto.IncidentCaseCreateFromPostmortemRequest;
import io.aegisops.execution.dto.IncidentCasePublishRequest;
import io.aegisops.execution.dto.IncidentCaseRecord;
import io.aegisops.execution.dto.IncidentCaseResolutionStepCreateCommand;
import io.aegisops.execution.dto.IncidentCaseResolutionStepRecord;
import io.aegisops.execution.dto.IncidentCaseSymptomCreateCommand;
import io.aegisops.execution.dto.IncidentCaseSymptomRecord;
import io.aegisops.execution.dto.IncidentCaseTagCreateCommand;
import io.aegisops.execution.dto.IncidentCaseTagRecord;
import io.aegisops.execution.dto.PostmortemActionItemRecord;
import io.aegisops.execution.dto.PostmortemReportRecord;
import io.aegisops.execution.dto.PostmortemSectionRecord;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class IncidentCaseServiceTest {
  @Test
  void createCaseFromPostmortem() {
    FakeIncidentCaseRepository caseRepository = new FakeIncidentCaseRepository();
    FakePostmortemRepository postmortemRepository = new FakePostmortemRepository();

    IncidentCaseService service =
        new IncidentCaseService(
            caseRepository,
            postmortemRepository,
            new IncidentCaseDraftBuilder());

    var response =
        service.createFromPostmortem(
            "tenant_1",
            "pmr_1",
            new IncidentCaseCreateFromPostmortemRequest(
                "alice",
                80,
                List.of("order-service")));

    assertEquals("draft", response.status());
    assertEquals(80, response.qualityScore());
    assertTrue(response.tags().contains("order-service"));
    assertTrue(response.symptoms().size() > 0);
    assertTrue(response.resolutionSteps().size() > 0);
  }

  @Test
  void rejectDuplicateCaseForPostmortem() {
    FakeIncidentCaseRepository caseRepository = new FakeIncidentCaseRepository();
    FakePostmortemRepository postmortemRepository = new FakePostmortemRepository();

    IncidentCaseService service =
        new IncidentCaseService(
            caseRepository,
            postmortemRepository,
            new IncidentCaseDraftBuilder());

    service.createFromPostmortem(
        "tenant_1",
        "pmr_1",
        new IncidentCaseCreateFromPostmortemRequest("alice", 80, List.of()));

    assertThrows(
        AppException.class,
        () ->
            service.createFromPostmortem(
                "tenant_1",
                "pmr_1",
                new IncidentCaseCreateFromPostmortemRequest("alice", 80, List.of())));
  }

  @Test
  void publishCase() {
    FakeIncidentCaseRepository caseRepository = new FakeIncidentCaseRepository();
    FakePostmortemRepository postmortemRepository = new FakePostmortemRepository();

    IncidentCaseService service =
        new IncidentCaseService(
            caseRepository,
            postmortemRepository,
            new IncidentCaseDraftBuilder());

    var created =
        service.createFromPostmortem(
            "tenant_1",
            "pmr_1",
            new IncidentCaseCreateFromPostmortemRequest("alice", 80, List.of()));

    var published =
        service.publish(
            "tenant_1",
            created.id(),
            new IncidentCasePublishRequest("reviewer"));

    assertEquals("published", published.status());
    assertEquals("reviewer", published.reviewedBy());
  }

  @Test
  void rejectPublishWhenQualityTooLow() {
    FakeIncidentCaseRepository caseRepository = new FakeIncidentCaseRepository();
    FakePostmortemRepository postmortemRepository = new FakePostmortemRepository();

    IncidentCaseService service =
        new IncidentCaseService(
            caseRepository,
            postmortemRepository,
            new IncidentCaseDraftBuilder());

    var created =
        service.createFromPostmortem(
            "tenant_1",
            "pmr_1",
            new IncidentCaseCreateFromPostmortemRequest("alice", 30, List.of()));

    assertThrows(
        AppException.class,
        () ->
            service.publish(
                "tenant_1",
                created.id(),
                new IncidentCasePublishRequest("reviewer")));
  }

  private static class FakeIncidentCaseRepository implements IncidentCaseRepository {
    IncidentCaseRecord caseRecord;
    final List<IncidentCaseSymptomRecord> symptoms = new ArrayList<>();
    final List<IncidentCaseResolutionStepRecord> steps = new ArrayList<>();
    final List<IncidentCaseTagRecord> tags = new ArrayList<>();

    @Override
    public void createCase(IncidentCaseCreateCommand command) {
      caseRecord =
          new IncidentCaseRecord(
              command.id(),
              command.tenantId(),
              command.sourcePostmortemId(),
              command.incidentId(),
              command.status(),
              command.severity(),
              command.title(),
              command.summary(),
              command.rootCause(),
              command.resolution(),
              command.prevention(),
              command.qualityScore(),
              command.createdBy(),
              null,
              null,
              null,
              OffsetDateTime.now(),
              OffsetDateTime.now());
    }

    @Override
    public void createSymptom(IncidentCaseSymptomCreateCommand command) {
      symptoms.add(
          new IncidentCaseSymptomRecord(
              command.id(),
              command.tenantId(),
              command.caseId(),
              command.symptomType(),
              command.name(),
              command.description(),
              OffsetDateTime.now()));
    }

    @Override
    public void createResolutionStep(IncidentCaseResolutionStepCreateCommand command) {
      steps.add(
          new IncidentCaseResolutionStepRecord(
              command.id(),
              command.tenantId(),
              command.caseId(),
              command.stepOrder(),
              command.title(),
              command.description(),
              command.actionType(),
              command.sourceRefId(),
              OffsetDateTime.now()));
    }

    @Override
    public void createTag(IncidentCaseTagCreateCommand command) {
      tags.add(
          new IncidentCaseTagRecord(
              command.id(),
              command.tenantId(),
              command.caseId(),
              command.tag(),
              OffsetDateTime.now()));
    }

    @Override
    public Optional<IncidentCaseRecord> findCase(String tenantId, String caseId) {
      return Optional.ofNullable(caseRecord).filter(item -> item.id().equals(caseId));
    }

    @Override
    public Optional<IncidentCaseRecord> findByPostmortem(String tenantId, String postmortemId) {
      return Optional.ofNullable(caseRecord)
          .filter(item -> item.sourcePostmortemId().equals(postmortemId));
    }

    @Override
    public Optional<IncidentCaseRecord> findLatestByIncident(String tenantId, String incidentId) {
      return Optional.ofNullable(caseRecord).filter(item -> item.incidentId().equals(incidentId));
    }

    @Override
    public List<IncidentCaseRecord> listCases(String tenantId, String status, String tag, int limit) {
      return caseRecord == null ? List.of() : List.of(caseRecord);
    }

    @Override
    public List<IncidentCaseSymptomRecord> listSymptoms(String tenantId, String caseId) {
      return symptoms;
    }

    @Override
    public List<IncidentCaseResolutionStepRecord> listResolutionSteps(String tenantId, String caseId) {
      return steps;
    }

    @Override
    public List<IncidentCaseTagRecord> listTags(String tenantId, String caseId) {
      return tags;
    }

    @Override
    public boolean publish(String tenantId, String caseId, String reviewer) {
      if (caseRecord == null || !"draft".equals(caseRecord.status())) {
        return false;
      }

      caseRecord =
          new IncidentCaseRecord(
              caseRecord.id(),
              caseRecord.tenantId(),
              caseRecord.sourcePostmortemId(),
              caseRecord.incidentId(),
              "published",
              caseRecord.severity(),
              caseRecord.title(),
              caseRecord.summary(),
              caseRecord.rootCause(),
              caseRecord.resolution(),
              caseRecord.prevention(),
              caseRecord.qualityScore(),
              caseRecord.createdBy(),
              reviewer,
              OffsetDateTime.now(),
              null,
              caseRecord.createdAt(),
              OffsetDateTime.now());
      return true;
    }

    @Override
    public boolean archive(String tenantId, String caseId) {
      if (caseRecord == null) {
        return false;
      }

      caseRecord =
          new IncidentCaseRecord(
              caseRecord.id(),
              caseRecord.tenantId(),
              caseRecord.sourcePostmortemId(),
              caseRecord.incidentId(),
              "archived",
              caseRecord.severity(),
              caseRecord.title(),
              caseRecord.summary(),
              caseRecord.rootCause(),
              caseRecord.resolution(),
              caseRecord.prevention(),
              caseRecord.qualityScore(),
              caseRecord.createdBy(),
              caseRecord.reviewedBy(),
              caseRecord.publishedAt(),
              OffsetDateTime.now(),
              caseRecord.createdAt(),
              OffsetDateTime.now());
      return true;
    }
  }

  private static class FakePostmortemRepository implements PostmortemRepository {
    @Override
    public Optional<PostmortemReportRecord> findReport(String tenantId, String postmortemId) {
      return Optional.of(
          new PostmortemReportRecord(
              postmortemId,
              tenantId,
              "inc_1",
              "generated",
              "high",
              "Postmortem - Order service error",
              "summary",
              "impact",
              "db timeout",
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
              OffsetDateTime.now()));
    }

    @Override
    public List<PostmortemSectionRecord> listSections(String tenantId, String postmortemId) {
      return List.of(
          new PostmortemSectionRecord(
              "pms_1",
              tenantId,
              postmortemId,
              1,
              "impact",
              "Impact",
              "High error rate",
              "{}",
              OffsetDateTime.now()),
          new PostmortemSectionRecord(
              "pms_2",
              tenantId,
              postmortemId,
              2,
              "resolution",
              "Resolution",
              "Restart service",
              "{}",
              OffsetDateTime.now()));
    }

    @Override
    public List<PostmortemActionItemRecord> listActionItems(String tenantId, String postmortemId) {
      return List.of();
    }

    @Override public void createReport(PostmortemReportCreateCommand command) {}
    @Override public void createSection(PostmortemSectionCreateCommand command) {}
    @Override public Optional<PostmortemReportRecord> findLatestByIncident(String tenantId, String incidentId) { return Optional.empty(); }
    @Override public void createActionItem(PostmortemActionItemCreateCommand command) {}
    @Override public Optional<PostmortemActionItemRecord> findActionItem(String tenantId, String actionItemId) { return Optional.empty(); }
    @Override public boolean updateActionItemStatus(String tenantId, String actionItemId, String status) { return false; }
  }
}
```

---

## 13.3 `JooqIncidentCaseRepositoryGeneratedSqlTest.java`

路径：

```txt id="g5k21g"
modules/aiops-execution/src/test/java/io/aegisops/execution/JooqIncidentCaseRepositoryGeneratedSqlTest.java
```

```java id="9tw5j9"
package io.aegisops.execution;

import static org.junit.jupiter.api.Assertions.assertTrue;

import io.aegisops.execution.dto.IncidentCaseCreateCommand;
import io.aegisops.execution.dto.IncidentCaseResolutionStepCreateCommand;
import io.aegisops.execution.dto.IncidentCaseSymptomCreateCommand;
import io.aegisops.execution.dto.IncidentCaseTagCreateCommand;
import io.aegisops.persistence.JooqTestSupport;
import java.util.List;
import org.jooq.Query;
import org.junit.jupiter.api.Test;

class JooqIncidentCaseRepositoryGeneratedSqlTest {
  @Test
  void createCaseUsesIncidentCaseTable() {
    var dsl = JooqTestSupport.dsl();
    var repository = new JooqIncidentCaseRepository(dsl);

    repository.createCase(
        new IncidentCaseCreateCommand(
            "icase_1",
            "tenant_1",
            "pmr_1",
            "inc_1",
            "draft",
            "high",
            "title",
            "summary",
            "root cause",
            "resolution",
            "prevention",
            80,
            "alice"));

    String sql = renderedSql(dsl.queries());

    assertTrue(sql.contains("incident_case"));
    assertTrue(sql.contains("source_postmortem_id"));
  }

  @Test
  void createCasePartsUseCaseTables() {
    var dsl = JooqTestSupport.dsl();
    var repository = new JooqIncidentCaseRepository(dsl);

    repository.createSymptom(
        new IncidentCaseSymptomCreateCommand(
            "icsym_1",
            "tenant_1",
            "icase_1",
            "impact",
            "High error rate",
            "desc"));

    repository.createResolutionStep(
        new IncidentCaseResolutionStepCreateCommand(
            "icstep_1",
            "tenant_1",
            "icase_1",
            1,
            "Restart service",
            "desc",
            "manual",
            "pms_1"));

    repository.createTag(
        new IncidentCaseTagCreateCommand(
            "ictag_1",
            "tenant_1",
            "icase_1",
            "order-service"));

    String sql = renderedSql(dsl.queries());

    assertTrue(sql.contains("incident_case_symptom"));
    assertTrue(sql.contains("incident_case_resolution_step"));
    assertTrue(sql.contains("incident_case_tag"));
  }

  private String renderedSql(List<Query> queries) {
    return queries.stream()
        .map(Query::getSQL)
        .reduce("", (a, b) -> a + "\n" + b)
        .toLowerCase();
  }
}
```

---

# 14. 文档

路径：

```txt id="uw753a"
docs/mvp/design/phase6.1-incident-case-library.md
```

```md id="k6qd08"
# Phase6.1 Incident Case Library

## 目标

Phase6.1 将 Postmortem Report 沉淀为可复用事故案例库。

## 不做

- 不做向量检索
- 不做 Embedding
- 不接 Milvus / pgvector
- 不做 Agent Memory
- 不影响执行链路

## 新增表

- incident_case
- incident_case_symptom
- incident_case_resolution_step
- incident_case_tag

## API

- POST /api/postmortems/{postmortemId}/cases
- GET /api/incidents/{incidentId}/cases/latest
- GET /api/incident-cases/{caseId}
- GET /api/incident-cases
- POST /api/incident-cases/{caseId}/publish
- POST /api/incident-cases/{caseId}/archive

## 状态

- draft
- published
- archived

## 发布规则

- 只有 draft 可以发布
- quality_score >= 60
- reviewer 必填

## 验收标准

1. generated / reviewed postmortem 可以生成 draft incident case。
2. 同一个 postmortem 只能生成一个 case。
3. case 包含 symptoms。
4. case 包含 resolution steps。
5. case 包含 tags。
6. draft case 可以 publish。
7. low quality case 不能 publish。
8. case 可以 archive。
9. 不引入向量检索。
10. 不新增执行能力。
```

---

# 15. 验证命令

```powershell id="2e0k6g"
mvn -pl modules/aiops-persistence -am generate-sources
mvn -pl modules/aiops-execution -am test
mvn -pl apps/aiops-server -am test
```

全量：

```powershell id="0kn73o"
mvn test
```

---

# 16. 验收标准

```txt id="r2oydu"
1. incident_case 表存在。
2. incident_case_symptom 表存在。
3. incident_case_resolution_step 表存在。
4. incident_case_tag 表存在。
5. generated / reviewed postmortem 可以生成 draft case。
6. archived postmortem 不能生成 case。
7. 同一 postmortem 不能重复生成 case。
8. case 自动提取 symptoms。
9. case 自动提取 resolution steps。
10. case 自动提取 tags。
11. quality_score 低于 60 不能 publish。
12. reviewer 为空不能 publish。
13. draft case 可以 publish。
14. published case 可以 archive。
15. 可以按 incident 查询 latest case。
16. 可以按 status/tag/list 查询 case。
17. 不引入向量检索。
18. 不引入 embedding。
19. 不新增执行能力。
```

---

# 17. 建议提交信息

```txt id="qocvwp"
feat(case-library): add incident case library
```

---

# 18. 下一步 Phase6.2

Phase6.1 完成后，进入：

```txt id="kopqx0"
Phase6.2 Knowledge Base & Vector Retrieval
```

Phase6.2 再做：

```txt id="h9un0z"
case chunking
embedding job
vector index
hybrid search
case retrieval API
agent evidence tool：search_cases
```

不要把向量检索提前塞进 Phase6.1。
