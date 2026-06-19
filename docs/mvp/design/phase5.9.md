# Phase5.8：Rollback Plan

> Phase5.8 目标：在已经开放 Ansible live execution 后，补上 **回滚计划、回滚审批、回滚执行绑定、回滚审计 artifact**。
> 本阶段不做“自动回滚”。回滚必须由人创建、提交、审批，然后才允许创建 rollback execution。

---

# 1. Phase5.8 定位

当前已有：

```txt id="n501yl"
Phase5.6  Ansible Sandbox Execution
Phase5.7  Ansible Live Execution with Approval Guard
```

Phase5.8 新增：

```txt id="1zopvb"
rollback_plan
rollback_plan_step
rollback_decision
execution_run.execution_kind
execution_run.rollback_plan_id
execution_run.rollback_of_execution_id

RollbackPlanService
RollbackApprovalService
RollbackExecutionService
RollbackPlanController
```

核心闭环：

```txt id="td60vx"
Live Execution
  -> Create RollbackPlan draft
  -> Submit rollback approval
  -> Approve / Reject
  -> Create rollback execution
  -> Runner execute existing action adapter
  -> rollback execution artifact
```

---

# 2. Phase5.8 安全边界

## 2.1 允许

```txt id="gj3j1s"
1. 从已完成的 live execution 生成 rollback_plan。
2. 从 source execution step 的显式 rollback payload 生成 rollback step。
3. 审批通过后创建 execution_kind=rollback 的 execution_run。
4. rollback execution 继续走现有 runner / step executor。
```

## 2.2 禁止

```txt id="glwqwu"
1. 自动回滚。
2. AI 直接执行回滚。
3. 未审批回滚。
4. 回滚 rollback execution。
5. 从没有 rollback payload 的 step 自动猜测回滚动作。
6. 绕过 execution_run / runner / artifact。
```

---

# 3. action_payload 新约定

从 Phase5.8 开始，支持在原 action payload 里声明 rollback：

```json id="4a84um"
{
  "inventoryId": "inv_1",
  "playbookId": "pb_deploy_order_service",
  "checkMode": false,
  "tags": ["deploy"],
  "extraVars": {
    "service_name": "order-service",
    "version": "v2"
  },
  "rollback": {
    "title": "Rollback order-service to v1",
    "actionType": "ansible",
    "targetType": "service",
    "actionPayload": {
      "inventoryId": "inv_1",
      "playbookId": "pb_rollback_order_service",
      "checkMode": false,
      "tags": ["rollback"],
      "extraVars": {
        "service_name": "order-service",
        "version": "v1"
      }
    }
  }
}
```

规则：

```txt id="bzcvud"
1. 只有显式 rollback 字段才会生成 rollback step。
2. rollback.actionType 必须存在。
3. rollback.actionPayload 必须存在。
4. rollback step 顺序按原执行 step 倒序生成。
```

---

# 4. 状态机

## 4.1 rollback_plan.status

```txt id="had8p2"
draft
pending_approval
approved
rejected
executing
succeeded
failed
cancelled
```

## 4.2 rollback_decision.decision

```txt id="hdougl"
approve
reject
```

## 4.3 execution_run.execution_kind

```txt id="2ag3e7"
normal
rollback
```

---

# 5. 数据库 Migration

路径：

```txt id="e4de7w"
apps/aiops-server/src/main/resources/db/migration/V18__phase5_8_rollback_plan.sql
```

```sql id="nlqdr3"
-- Phase 5.8: Rollback Plan.
-- Rollback is never automatic. A rollback plan must be created, submitted,
-- approved, and then bound to a rollback execution_run.

create table if not exists rollback_plan (
  id varchar(64) primary key,
  tenant_id varchar(64) not null references tenant(id) on delete cascade,
  incident_id varchar(64) not null,
  source_plan_id varchar(64) not null,
  source_execution_id varchar(64) not null references execution_run(id) on delete cascade,
  status varchar(32) not null default 'draft',
  risk_level varchar(32) not null default 'high',
  reason text,
  required_approvals int not null default 1,
  approved_count int not null default 0,
  rejected_count int not null default 0,
  created_by varchar(64) not null,
  submitted_by varchar(64),
  submitted_at timestamptz,
  decided_at timestamptz,
  approval_snapshot jsonb not null default '{}'::jsonb,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  constraint ck_rollback_plan_status
    check (status in (
      'draft',
      'pending_approval',
      'approved',
      'rejected',
      'executing',
      'succeeded',
      'failed',
      'cancelled'
    )),
  constraint ck_rollback_plan_risk_level
    check (risk_level in ('low', 'medium', 'high', 'critical')),
  constraint ck_rollback_plan_required_approvals
    check (required_approvals >= 1 and required_approvals <= 5)
);

create index if not exists idx_rollback_plan_tenant_source_execution
  on rollback_plan(tenant_id, source_execution_id, created_at desc);

create index if not exists idx_rollback_plan_tenant_status
  on rollback_plan(tenant_id, status, created_at desc);

create table if not exists rollback_plan_step (
  id varchar(64) primary key,
  tenant_id varchar(64) not null references tenant(id) on delete cascade,
  rollback_plan_id varchar(64) not null references rollback_plan(id) on delete cascade,
  source_step_id varchar(64),
  step_order int not null,
  title varchar(240) not null,
  description text,
  action_type varchar(64) not null,
  target_type varchar(64) not null,
  action_payload jsonb not null default '{}'::jsonb,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  constraint uq_rollback_plan_step_order unique (tenant_id, rollback_plan_id, step_order)
);

create index if not exists idx_rollback_plan_step_plan
  on rollback_plan_step(tenant_id, rollback_plan_id, step_order);

create table if not exists rollback_decision (
  id varchar(64) primary key,
  tenant_id varchar(64) not null references tenant(id) on delete cascade,
  rollback_plan_id varchar(64) not null references rollback_plan(id) on delete cascade,
  reviewer varchar(64) not null,
  decision varchar(16) not null,
  comment text,
  created_at timestamptz not null default now(),
  constraint ck_rollback_decision
    check (decision in ('approve', 'reject')),
  constraint uq_rollback_decision_reviewer
    unique (tenant_id, rollback_plan_id, reviewer)
);

create index if not exists idx_rollback_decision_plan
  on rollback_decision(tenant_id, rollback_plan_id, created_at);

alter table execution_run
  add column if not exists execution_kind varchar(32) not null default 'normal',
  add column if not exists rollback_plan_id varchar(64),
  add column if not exists rollback_of_execution_id varchar(64);

alter table execution_run
  add constraint ck_execution_run_kind
    check (execution_kind in ('normal', 'rollback'));

create index if not exists idx_execution_run_rollback_plan
  on execution_run(tenant_id, rollback_plan_id);

create index if not exists idx_execution_run_rollback_of
  on execution_run(tenant_id, rollback_of_execution_id);
```

---

# 6. jOOQ Codegen

路径：

```txt id="rq10fn"
modules/aiops-persistence/src/main/resources/jooq-codegen.xml
```

`<includes>` 增加：

```txt id="3b369c"
rollback_plan | rollback_plan_step | rollback_decision
```

完整 includes 建议：

```xml id="g51z31"
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
  rollback_plan | rollback_plan_step | rollback_decision
</includes>
```

---

# 7. DTO 完整代码

## 7.1 `RollbackPlanCreateRequest.java`

路径：

```txt id="jlqj09"
modules/aiops-execution/src/main/java/io/aegisops/execution/dto/RollbackPlanCreateRequest.java
```

```java id="lgvvt8"
package io.aegisops.execution.dto;

public record RollbackPlanCreateRequest(
    String reason,
    String riskLevel,
    Integer requiredApprovals,
    String createdBy) {}
```

---

## 7.2 `RollbackPlanSubmitRequest.java`

```java id="bwse21"
package io.aegisops.execution.dto;

public record RollbackPlanSubmitRequest(String submittedBy) {}
```

---

## 7.3 `RollbackDecisionRequest.java`

```java id="e1e4jv"
package io.aegisops.execution.dto;

public record RollbackDecisionRequest(
    String reviewer,
    String comment) {}
```

---

## 7.4 `RollbackExecutionCreateRequest.java`

```java id="qso856"
package io.aegisops.execution.dto;

public record RollbackExecutionCreateRequest(
    String requestedBy,
    Integer maxAttempts) {}
```

---

## 7.5 `RollbackPlanCreateCommand.java`

```java id="u89xq1"
package io.aegisops.execution.dto;

public record RollbackPlanCreateCommand(
    String id,
    String tenantId,
    String incidentId,
    String sourcePlanId,
    String sourceExecutionId,
    String status,
    String riskLevel,
    String reason,
    int requiredApprovals,
    String createdBy) {}
```

---

## 7.6 `RollbackPlanStepCreateCommand.java`

```java id="cbkwfv"
package io.aegisops.execution.dto;

public record RollbackPlanStepCreateCommand(
    String id,
    String tenantId,
    String rollbackPlanId,
    String sourceStepId,
    int stepOrder,
    String title,
    String description,
    String actionType,
    String targetType,
    String actionPayloadJson) {}
```

---

## 7.7 `RollbackDecisionCreateCommand.java`

```java id="hhqcr1"
package io.aegisops.execution.dto;

public record RollbackDecisionCreateCommand(
    String id,
    String tenantId,
    String rollbackPlanId,
    String reviewer,
    String decision,
    String comment) {}
```

---

## 7.8 `RollbackPlanRecord.java`

```java id="oqqqs5"
package io.aegisops.execution.dto;

import java.time.OffsetDateTime;

public record RollbackPlanRecord(
    String id,
    String tenantId,
    String incidentId,
    String sourcePlanId,
    String sourceExecutionId,
    String status,
    String riskLevel,
    String reason,
    int requiredApprovals,
    int approvedCount,
    int rejectedCount,
    String createdBy,
    String submittedBy,
    OffsetDateTime submittedAt,
    OffsetDateTime decidedAt,
    String approvalSnapshotJson,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt) {}
```

---

## 7.9 `RollbackPlanStepRecord.java`

```java id="d9mpt0"
package io.aegisops.execution.dto;

import java.time.OffsetDateTime;

public record RollbackPlanStepRecord(
    String id,
    String tenantId,
    String rollbackPlanId,
    String sourceStepId,
    int stepOrder,
    String title,
    String description,
    String actionType,
    String targetType,
    String actionPayloadJson,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt) {}
```

---

## 7.10 `RollbackDecisionRecord.java`

```java id="f9s7f1"
package io.aegisops.execution.dto;

import java.time.OffsetDateTime;

public record RollbackDecisionRecord(
    String id,
    String tenantId,
    String rollbackPlanId,
    String reviewer,
    String decision,
    String comment,
    OffsetDateTime createdAt) {}
```

---

## 7.11 `RollbackPlanResponse.java`

```java id="jftl4p"
package io.aegisops.execution.dto;

import java.time.OffsetDateTime;
import java.util.List;

public record RollbackPlanResponse(
    String id,
    String tenantId,
    String incidentId,
    String sourcePlanId,
    String sourceExecutionId,
    String status,
    String riskLevel,
    String reason,
    int requiredApprovals,
    int approvedCount,
    int rejectedCount,
    String createdBy,
    String submittedBy,
    OffsetDateTime submittedAt,
    OffsetDateTime decidedAt,
    String approvalSnapshotJson,
    List<RollbackPlanStepResponse> steps,
    List<RollbackDecisionResponse> decisions,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt) {}
```

---

## 7.12 `RollbackPlanStepResponse.java`

```java id="21dgrc"
package io.aegisops.execution.dto;

import java.time.OffsetDateTime;

public record RollbackPlanStepResponse(
    String id,
    String sourceStepId,
    int stepOrder,
    String title,
    String description,
    String actionType,
    String targetType,
    String actionPayloadJson,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt) {}
```

---

## 7.13 `RollbackDecisionResponse.java`

```java id="qid6di"
package io.aegisops.execution.dto;

import java.time.OffsetDateTime;

public record RollbackDecisionResponse(
    String id,
    String reviewer,
    String decision,
    String comment,
    OffsetDateTime createdAt) {}
```

---

# 8. Execution DTO 修改

## 8.1 修改 `ExecutionRunCreateCommand.java`

路径：

```txt id="lvazby"
modules/aiops-execution/src/main/java/io/aegisops/execution/dto/ExecutionRunCreateCommand.java
```

完整替换：

```java id="9lv2kn"
package io.aegisops.execution.dto;

public record ExecutionRunCreateCommand(
    String id,
    String tenantId,
    String incidentId,
    String planId,
    String status,
    String mode,
    String requestedBy,
    int attempt,
    int maxAttempts,
    String retryOfExecutionId,
    int timeoutSeconds,
    String approvalId,
    String approvalSnapshotJson,
    String planRiskLevel,
    String executionKind,
    String rollbackPlanId,
    String rollbackOfExecutionId) {}
```

---

## 8.2 修改 `ExecutionRunRecord.java`

```java id="2buur5"
package io.aegisops.execution.dto;

import java.time.OffsetDateTime;

public record ExecutionRunRecord(
    String id,
    String tenantId,
    String incidentId,
    String planId,
    String status,
    String mode,
    String requestedBy,
    String runnerId,
    OffsetDateTime startedAt,
    OffsetDateTime finishedAt,
    String errorMessage,
    String summary,
    int attempt,
    int maxAttempts,
    String retryOfExecutionId,
    OffsetDateTime leaseUntil,
    OffsetDateTime heartbeatAt,
    int timeoutSeconds,
    String approvalId,
    String approvalSnapshotJson,
    String planRiskLevel,
    OffsetDateTime liveGuardPassedAt,
    String executionKind,
    String rollbackPlanId,
    String rollbackOfExecutionId,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt) {}
```

---

## 8.3 修改 `ExecutionRunResponse.java`

```java id="e9a0js"
package io.aegisops.execution.dto;

import java.time.OffsetDateTime;
import java.util.List;

public record ExecutionRunResponse(
    String id,
    String tenantId,
    String incidentId,
    String planId,
    String status,
    String mode,
    String requestedBy,
    String runnerId,
    String errorMessage,
    String summary,
    int attempt,
    int maxAttempts,
    String retryOfExecutionId,
    OffsetDateTime leaseUntil,
    OffsetDateTime heartbeatAt,
    int timeoutSeconds,
    String approvalId,
    String approvalSnapshotJson,
    String planRiskLevel,
    OffsetDateTime liveGuardPassedAt,
    String executionKind,
    String rollbackPlanId,
    String rollbackOfExecutionId,
    List<ExecutionStepResponse> steps,
    List<ExecutionArtifactResponse> artifacts,
    OffsetDateTime startedAt,
    OffsetDateTime finishedAt,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt) {}
```

---

# 9. RollbackJson

路径：

```txt id="h1gbvf"
modules/aiops-execution/src/main/java/io/aegisops/execution/RollbackJson.java
```

```java id="74194z"
package io.aegisops.execution;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.aegisops.common.exception.AppException;
import java.util.Map;

public class RollbackJson {
  private static final TypeReference<Map<String, Object>> OBJECT_MAP = new TypeReference<>() {};

  private final ObjectMapper objectMapper;

  public RollbackJson(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  public String write(Object value) {
    try {
      if (value == null) {
        return "{}";
      }
      return objectMapper.writeValueAsString(value);
    } catch (Exception ex) {
      throw new AppException("ROLLBACK_JSON_WRITE_FAILED", "Failed to serialize rollback json");
    }
  }

  public Map<String, Object> readObjectMap(String json) {
    try {
      if (json == null || json.isBlank()) {
        return Map.of();
      }
      return objectMapper.readValue(json, OBJECT_MAP);
    } catch (Exception ex) {
      return Map.of();
    }
  }
}
```

---

# 10. RollbackRepository

路径：

```txt id="u8h1bg"
modules/aiops-execution/src/main/java/io/aegisops/execution/RollbackRepository.java
```

```java id="2esml8"
package io.aegisops.execution;

import io.aegisops.execution.dto.RollbackDecisionCreateCommand;
import io.aegisops.execution.dto.RollbackDecisionRecord;
import io.aegisops.execution.dto.RollbackPlanCreateCommand;
import io.aegisops.execution.dto.RollbackPlanRecord;
import io.aegisops.execution.dto.RollbackPlanStepCreateCommand;
import io.aegisops.execution.dto.RollbackPlanStepRecord;
import java.util.List;
import java.util.Optional;

public interface RollbackRepository {
  void createPlan(RollbackPlanCreateCommand command);

  void createStep(RollbackPlanStepCreateCommand command);

  Optional<RollbackPlanRecord> findPlan(String tenantId, String rollbackPlanId);

  Optional<RollbackPlanRecord> findLatestPlanBySourceExecution(
      String tenantId, String sourceExecutionId);

  List<RollbackPlanStepRecord> listSteps(String tenantId, String rollbackPlanId);

  List<RollbackDecisionRecord> listDecisions(String tenantId, String rollbackPlanId);

  boolean updatePlanStatus(String tenantId, String rollbackPlanId, String fromStatus, String toStatus);

  boolean submitPlan(String tenantId, String rollbackPlanId, String submittedBy);

  void createDecision(RollbackDecisionCreateCommand command);

  boolean decisionExists(String tenantId, String rollbackPlanId, String reviewer);

  boolean markApproved(String tenantId, String rollbackPlanId, String approvalSnapshotJson);

  boolean markRejected(String tenantId, String rollbackPlanId, String approvalSnapshotJson);

  boolean markExecuting(String tenantId, String rollbackPlanId);

  boolean markSucceeded(String tenantId, String rollbackPlanId);

  boolean markFailed(String tenantId, String rollbackPlanId);
}
```

---

# 11. JooqRollbackRepository

路径：

```txt id="3jii7c"
modules/aiops-execution/src/main/java/io/aegisops/execution/JooqRollbackRepository.java
```

```java id="rpaqfv"
package io.aegisops.execution;

import static io.aegisops.persistence.AegisJooq.jsonbValue;
import static io.aegisops.persistence.jooq.Tables.ROLLBACK_DECISION;
import static io.aegisops.persistence.jooq.Tables.ROLLBACK_PLAN;
import static io.aegisops.persistence.jooq.Tables.ROLLBACK_PLAN_STEP;

import io.aegisops.execution.dto.RollbackDecisionCreateCommand;
import io.aegisops.execution.dto.RollbackDecisionRecord;
import io.aegisops.execution.dto.RollbackPlanCreateCommand;
import io.aegisops.execution.dto.RollbackPlanRecord;
import io.aegisops.execution.dto.RollbackPlanStepCreateCommand;
import io.aegisops.execution.dto.RollbackPlanStepRecord;
import java.util.List;
import java.util.Optional;
import org.jooq.DSLContext;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Repository;

@Repository
public class JooqRollbackRepository implements RollbackRepository {
  private final DSLContext dsl;

  public JooqRollbackRepository(DSLContext dsl) {
    this.dsl = dsl;
  }

  @Override
  public void createPlan(RollbackPlanCreateCommand command) {
    dsl.insertInto(ROLLBACK_PLAN)
        .set(ROLLBACK_PLAN.ID, command.id())
        .set(ROLLBACK_PLAN.TENANT_ID, command.tenantId())
        .set(ROLLBACK_PLAN.INCIDENT_ID, command.incidentId())
        .set(ROLLBACK_PLAN.SOURCE_PLAN_ID, command.sourcePlanId())
        .set(ROLLBACK_PLAN.SOURCE_EXECUTION_ID, command.sourceExecutionId())
        .set(ROLLBACK_PLAN.STATUS, command.status())
        .set(ROLLBACK_PLAN.RISK_LEVEL, command.riskLevel())
        .set(ROLLBACK_PLAN.REASON, command.reason())
        .set(ROLLBACK_PLAN.REQUIRED_APPROVALS, command.requiredApprovals())
        .set(ROLLBACK_PLAN.CREATED_BY, command.createdBy())
        .set(ROLLBACK_PLAN.CREATED_AT, DSL.currentOffsetDateTime())
        .set(ROLLBACK_PLAN.UPDATED_AT, DSL.currentOffsetDateTime())
        .execute();
  }

  @Override
  public void createStep(RollbackPlanStepCreateCommand command) {
    dsl.insertInto(ROLLBACK_PLAN_STEP)
        .set(ROLLBACK_PLAN_STEP.ID, command.id())
        .set(ROLLBACK_PLAN_STEP.TENANT_ID, command.tenantId())
        .set(ROLLBACK_PLAN_STEP.ROLLBACK_PLAN_ID, command.rollbackPlanId())
        .set(ROLLBACK_PLAN_STEP.SOURCE_STEP_ID, command.sourceStepId())
        .set(ROLLBACK_PLAN_STEP.STEP_ORDER, command.stepOrder())
        .set(ROLLBACK_PLAN_STEP.TITLE, command.title())
        .set(ROLLBACK_PLAN_STEP.DESCRIPTION, command.description())
        .set(ROLLBACK_PLAN_STEP.ACTION_TYPE, command.actionType())
        .set(ROLLBACK_PLAN_STEP.TARGET_TYPE, command.targetType())
        .set(ROLLBACK_PLAN_STEP.ACTION_PAYLOAD, jsonbValue(command.actionPayloadJson()))
        .set(ROLLBACK_PLAN_STEP.CREATED_AT, DSL.currentOffsetDateTime())
        .set(ROLLBACK_PLAN_STEP.UPDATED_AT, DSL.currentOffsetDateTime())
        .execute();
  }

  @Override
  public Optional<RollbackPlanRecord> findPlan(String tenantId, String rollbackPlanId) {
    return selectPlan()
        .where(ROLLBACK_PLAN.TENANT_ID.eq(tenantId))
        .and(ROLLBACK_PLAN.ID.eq(rollbackPlanId))
        .fetchOptional(this::toPlanRecord);
  }

  @Override
  public Optional<RollbackPlanRecord> findLatestPlanBySourceExecution(
      String tenantId, String sourceExecutionId) {
    return selectPlan()
        .where(ROLLBACK_PLAN.TENANT_ID.eq(tenantId))
        .and(ROLLBACK_PLAN.SOURCE_EXECUTION_ID.eq(sourceExecutionId))
        .orderBy(ROLLBACK_PLAN.CREATED_AT.desc())
        .limit(1)
        .fetchOptional(this::toPlanRecord);
  }

  @Override
  public List<RollbackPlanStepRecord> listSteps(String tenantId, String rollbackPlanId) {
    return dsl.select(
            ROLLBACK_PLAN_STEP.ID,
            ROLLBACK_PLAN_STEP.TENANT_ID,
            ROLLBACK_PLAN_STEP.ROLLBACK_PLAN_ID,
            ROLLBACK_PLAN_STEP.SOURCE_STEP_ID,
            ROLLBACK_PLAN_STEP.STEP_ORDER,
            ROLLBACK_PLAN_STEP.TITLE,
            ROLLBACK_PLAN_STEP.DESCRIPTION,
            ROLLBACK_PLAN_STEP.ACTION_TYPE,
            ROLLBACK_PLAN_STEP.TARGET_TYPE,
            ROLLBACK_PLAN_STEP.ACTION_PAYLOAD.cast(String.class).as("action_payload_json"),
            ROLLBACK_PLAN_STEP.CREATED_AT,
            ROLLBACK_PLAN_STEP.UPDATED_AT)
        .from(ROLLBACK_PLAN_STEP)
        .where(ROLLBACK_PLAN_STEP.TENANT_ID.eq(tenantId))
        .and(ROLLBACK_PLAN_STEP.ROLLBACK_PLAN_ID.eq(rollbackPlanId))
        .orderBy(ROLLBACK_PLAN_STEP.STEP_ORDER.asc())
        .fetch(this::toStepRecord);
  }

  @Override
  public List<RollbackDecisionRecord> listDecisions(String tenantId, String rollbackPlanId) {
    return dsl.select(
            ROLLBACK_DECISION.ID,
            ROLLBACK_DECISION.TENANT_ID,
            ROLLBACK_DECISION.ROLLBACK_PLAN_ID,
            ROLLBACK_DECISION.REVIEWER,
            ROLLBACK_DECISION.DECISION,
            ROLLBACK_DECISION.COMMENT,
            ROLLBACK_DECISION.CREATED_AT)
        .from(ROLLBACK_DECISION)
        .where(ROLLBACK_DECISION.TENANT_ID.eq(tenantId))
        .and(ROLLBACK_DECISION.ROLLBACK_PLAN_ID.eq(rollbackPlanId))
        .orderBy(ROLLBACK_DECISION.CREATED_AT.asc())
        .fetch(this::toDecisionRecord);
  }

  @Override
  public boolean updatePlanStatus(
      String tenantId, String rollbackPlanId, String fromStatus, String toStatus) {
    return dsl.update(ROLLBACK_PLAN)
            .set(ROLLBACK_PLAN.STATUS, toStatus)
            .set(ROLLBACK_PLAN.UPDATED_AT, DSL.currentOffsetDateTime())
            .where(ROLLBACK_PLAN.TENANT_ID.eq(tenantId))
            .and(ROLLBACK_PLAN.ID.eq(rollbackPlanId))
            .and(ROLLBACK_PLAN.STATUS.eq(fromStatus))
            .execute()
        > 0;
  }

  @Override
  public boolean submitPlan(String tenantId, String rollbackPlanId, String submittedBy) {
    return dsl.update(ROLLBACK_PLAN)
            .set(ROLLBACK_PLAN.STATUS, "pending_approval")
            .set(ROLLBACK_PLAN.SUBMITTED_BY, submittedBy)
            .set(ROLLBACK_PLAN.SUBMITTED_AT, DSL.currentOffsetDateTime())
            .set(ROLLBACK_PLAN.UPDATED_AT, DSL.currentOffsetDateTime())
            .where(ROLLBACK_PLAN.TENANT_ID.eq(tenantId))
            .and(ROLLBACK_PLAN.ID.eq(rollbackPlanId))
            .and(ROLLBACK_PLAN.STATUS.eq("draft"))
            .execute()
        > 0;
  }

  @Override
  public void createDecision(RollbackDecisionCreateCommand command) {
    dsl.insertInto(ROLLBACK_DECISION)
        .set(ROLLBACK_DECISION.ID, command.id())
        .set(ROLLBACK_DECISION.TENANT_ID, command.tenantId())
        .set(ROLLBACK_DECISION.ROLLBACK_PLAN_ID, command.rollbackPlanId())
        .set(ROLLBACK_DECISION.REVIEWER, command.reviewer())
        .set(ROLLBACK_DECISION.DECISION, command.decision())
        .set(ROLLBACK_DECISION.COMMENT, command.comment())
        .set(ROLLBACK_DECISION.CREATED_AT, DSL.currentOffsetDateTime())
        .execute();
  }

  @Override
  public boolean decisionExists(String tenantId, String rollbackPlanId, String reviewer) {
    return dsl.fetchExists(
        ROLLBACK_DECISION,
        ROLLBACK_DECISION.TENANT_ID.eq(tenantId)
            .and(ROLLBACK_DECISION.ROLLBACK_PLAN_ID.eq(rollbackPlanId))
            .and(ROLLBACK_DECISION.REVIEWER.eq(reviewer)));
  }

  @Override
  public boolean markApproved(String tenantId, String rollbackPlanId, String approvalSnapshotJson) {
    return dsl.update(ROLLBACK_PLAN)
            .set(ROLLBACK_PLAN.STATUS, "approved")
            .set(ROLLBACK_PLAN.APPROVED_COUNT, ROLLBACK_PLAN.APPROVED_COUNT.plus(1))
            .set(ROLLBACK_PLAN.APPROVAL_SNAPSHOT, jsonbValue(approvalSnapshotJson))
            .set(ROLLBACK_PLAN.DECIDED_AT, DSL.currentOffsetDateTime())
            .set(ROLLBACK_PLAN.UPDATED_AT, DSL.currentOffsetDateTime())
            .where(ROLLBACK_PLAN.TENANT_ID.eq(tenantId))
            .and(ROLLBACK_PLAN.ID.eq(rollbackPlanId))
            .and(ROLLBACK_PLAN.STATUS.eq("pending_approval"))
            .execute()
        > 0;
  }

  @Override
  public boolean markRejected(String tenantId, String rollbackPlanId, String approvalSnapshotJson) {
    return dsl.update(ROLLBACK_PLAN)
            .set(ROLLBACK_PLAN.STATUS, "rejected")
            .set(ROLLBACK_PLAN.REJECTED_COUNT, ROLLBACK_PLAN.REJECTED_COUNT.plus(1))
            .set(ROLLBACK_PLAN.APPROVAL_SNAPSHOT, jsonbValue(approvalSnapshotJson))
            .set(ROLLBACK_PLAN.DECIDED_AT, DSL.currentOffsetDateTime())
            .set(ROLLBACK_PLAN.UPDATED_AT, DSL.currentOffsetDateTime())
            .where(ROLLBACK_PLAN.TENANT_ID.eq(tenantId))
            .and(ROLLBACK_PLAN.ID.eq(rollbackPlanId))
            .and(ROLLBACK_PLAN.STATUS.eq("pending_approval"))
            .execute()
        > 0;
  }

  @Override
  public boolean markExecuting(String tenantId, String rollbackPlanId) {
    return updatePlanStatus(tenantId, rollbackPlanId, "approved", "executing");
  }

  @Override
  public boolean markSucceeded(String tenantId, String rollbackPlanId) {
    return updatePlanStatus(tenantId, rollbackPlanId, "executing", "succeeded");
  }

  @Override
  public boolean markFailed(String tenantId, String rollbackPlanId) {
    return updatePlanStatus(tenantId, rollbackPlanId, "executing", "failed");
  }

  private org.jooq.SelectJoinStep<org.jooq.Record> selectPlan() {
    return dsl.select(
            ROLLBACK_PLAN.ID,
            ROLLBACK_PLAN.TENANT_ID,
            ROLLBACK_PLAN.INCIDENT_ID,
            ROLLBACK_PLAN.SOURCE_PLAN_ID,
            ROLLBACK_PLAN.SOURCE_EXECUTION_ID,
            ROLLBACK_PLAN.STATUS,
            ROLLBACK_PLAN.RISK_LEVEL,
            ROLLBACK_PLAN.REASON,
            ROLLBACK_PLAN.REQUIRED_APPROVALS,
            ROLLBACK_PLAN.APPROVED_COUNT,
            ROLLBACK_PLAN.REJECTED_COUNT,
            ROLLBACK_PLAN.CREATED_BY,
            ROLLBACK_PLAN.SUBMITTED_BY,
            ROLLBACK_PLAN.SUBMITTED_AT,
            ROLLBACK_PLAN.DECIDED_AT,
            ROLLBACK_PLAN.APPROVAL_SNAPSHOT.cast(String.class).as("approval_snapshot_json"),
            ROLLBACK_PLAN.CREATED_AT,
            ROLLBACK_PLAN.UPDATED_AT)
        .from(ROLLBACK_PLAN);
  }

  private RollbackPlanRecord toPlanRecord(org.jooq.Record record) {
    return new RollbackPlanRecord(
        record.get(ROLLBACK_PLAN.ID),
        record.get(ROLLBACK_PLAN.TENANT_ID),
        record.get(ROLLBACK_PLAN.INCIDENT_ID),
        record.get(ROLLBACK_PLAN.SOURCE_PLAN_ID),
        record.get(ROLLBACK_PLAN.SOURCE_EXECUTION_ID),
        record.get(ROLLBACK_PLAN.STATUS),
        record.get(ROLLBACK_PLAN.RISK_LEVEL),
        record.get(ROLLBACK_PLAN.REASON),
        value(record.get(ROLLBACK_PLAN.REQUIRED_APPROVALS)),
        value(record.get(ROLLBACK_PLAN.APPROVED_COUNT)),
        value(record.get(ROLLBACK_PLAN.REJECTED_COUNT)),
        record.get(ROLLBACK_PLAN.CREATED_BY),
        record.get(ROLLBACK_PLAN.SUBMITTED_BY),
        record.get(ROLLBACK_PLAN.SUBMITTED_AT),
        record.get(ROLLBACK_PLAN.DECIDED_AT),
        record.get("approval_snapshot_json", String.class),
        record.get(ROLLBACK_PLAN.CREATED_AT),
        record.get(ROLLBACK_PLAN.UPDATED_AT));
  }

  private RollbackPlanStepRecord toStepRecord(org.jooq.Record record) {
    return new RollbackPlanStepRecord(
        record.get(ROLLBACK_PLAN_STEP.ID),
        record.get(ROLLBACK_PLAN_STEP.TENANT_ID),
        record.get(ROLLBACK_PLAN_STEP.ROLLBACK_PLAN_ID),
        record.get(ROLLBACK_PLAN_STEP.SOURCE_STEP_ID),
        value(record.get(ROLLBACK_PLAN_STEP.STEP_ORDER)),
        record.get(ROLLBACK_PLAN_STEP.TITLE),
        record.get(ROLLBACK_PLAN_STEP.DESCRIPTION),
        record.get(ROLLBACK_PLAN_STEP.ACTION_TYPE),
        record.get(ROLLBACK_PLAN_STEP.TARGET_TYPE),
        record.get("action_payload_json", String.class),
        record.get(ROLLBACK_PLAN_STEP.CREATED_AT),
        record.get(ROLLBACK_PLAN_STEP.UPDATED_AT));
  }

  private RollbackDecisionRecord toDecisionRecord(org.jooq.Record record) {
    return new RollbackDecisionRecord(
        record.get(ROLLBACK_DECISION.ID),
        record.get(ROLLBACK_DECISION.TENANT_ID),
        record.get(ROLLBACK_DECISION.ROLLBACK_PLAN_ID),
        record.get(ROLLBACK_DECISION.REVIEWER),
        record.get(ROLLBACK_DECISION.DECISION),
        record.get(ROLLBACK_DECISION.COMMENT),
        record.get(ROLLBACK_DECISION.CREATED_AT));
  }

  private int value(Integer value) {
    return value == null ? 0 : value;
  }
}
```

---

# 12. RollbackPayloadExtractor

路径：

```txt id="q7mztd"
modules/aiops-execution/src/main/java/io/aegisops/execution/RollbackPayloadExtractor.java
```

```java id="v5030x"
package io.aegisops.execution;

import io.aegisops.common.exception.AppException;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class RollbackPayloadExtractor {
  private final RollbackJson json;

  public RollbackPayloadExtractor(com.fasterxml.jackson.databind.ObjectMapper objectMapper) {
    this.json = new RollbackJson(objectMapper);
  }

  public RollbackPayload extract(String actionPayloadJson) {
    Map<String, Object> payload = json.readObjectMap(actionPayloadJson);
    Object rollbackValue = payload.get("rollback");

    if (!(rollbackValue instanceof Map<?, ?> rawRollback)) {
      return null;
    }

    Map<String, Object> rollback =
        rawRollback.entrySet().stream()
            .collect(
                java.util.stream.Collectors.toMap(
                    entry -> String.valueOf(entry.getKey()),
                    Map.Entry::getValue));

    String title = stringValue(rollback.get("title"));
    String description = stringValue(rollback.get("description"));
    String actionType = stringValue(rollback.get("actionType"));
    String targetType = stringValue(rollback.get("targetType"));

    Object rollbackActionPayload = rollback.get("actionPayload");
    if (actionType.isBlank()) {
      throw new AppException("ROLLBACK_ACTION_TYPE_REQUIRED", "Rollback actionType is required");
    }

    if (!(rollbackActionPayload instanceof Map<?, ?>)) {
      throw new AppException("ROLLBACK_ACTION_PAYLOAD_REQUIRED", "Rollback actionPayload is required");
    }

    return new RollbackPayload(
        title.isBlank() ? "Rollback step" : title,
        description,
        actionType,
        targetType.isBlank() ? "unknown" : targetType,
        json.write(rollbackActionPayload));
  }

  private String stringValue(Object value) {
    return value == null ? "" : String.valueOf(value).trim();
  }

  public record RollbackPayload(
      String title,
      String description,
      String actionType,
      String targetType,
      String actionPayloadJson) {}
}
```

---

# 13. RollbackPlanService

路径：

```txt id="myagkr"
modules/aiops-execution/src/main/java/io/aegisops/execution/RollbackPlanService.java
```

```java id="m7o5di"
package io.aegisops.execution;

import io.aegisops.common.exception.AppException;
import io.aegisops.execution.dto.ExecutionRunRecord;
import io.aegisops.execution.dto.ExecutionStepRecord;
import io.aegisops.execution.dto.RollbackDecisionRecord;
import io.aegisops.execution.dto.RollbackPlanCreateCommand;
import io.aegisops.execution.dto.RollbackPlanCreateRequest;
import io.aegisops.execution.dto.RollbackPlanRecord;
import io.aegisops.execution.dto.RollbackPlanResponse;
import io.aegisops.execution.dto.RollbackPlanStepCreateCommand;
import io.aegisops.execution.dto.RollbackPlanStepRecord;
import io.aegisops.execution.dto.RollbackPlanStepResponse;
import io.aegisops.execution.dto.RollbackDecisionResponse;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RollbackPlanService {
  private final RollbackRepository rollbackRepository;
  private final ExecutionRepository executionRepository;
  private final RollbackPayloadExtractor extractor;

  public RollbackPlanService(
      RollbackRepository rollbackRepository,
      ExecutionRepository executionRepository,
      RollbackPayloadExtractor extractor) {
    this.rollbackRepository = rollbackRepository;
    this.executionRepository = executionRepository;
    this.extractor = extractor;
  }

  @Transactional
  public RollbackPlanResponse create(
      String tenantId, String sourceExecutionId, RollbackPlanCreateRequest request) {
    ExecutionRunRecord source =
        executionRepository
            .findRun(tenantId, sourceExecutionId)
            .orElseThrow(
                () -> new AppException("EXECUTION_NOT_FOUND", "Source execution not found"));

    validateSourceExecution(source);

    List<ExecutionStepRecord> sourceSteps =
        executionRepository.listExecutionSteps(tenantId, source.id());

    List<RollbackPlanStepCreateCommand> rollbackSteps =
        buildRollbackSteps(tenantId, sourceSteps);

    if (rollbackSteps.isEmpty()) {
      throw new AppException(
          "ROLLBACK_STEPS_EMPTY",
          "Source execution does not contain explicit rollback payload");
    }

    String rollbackPlanId = newId("rbp");

    rollbackRepository.createPlan(
        new RollbackPlanCreateCommand(
            rollbackPlanId,
            tenantId,
            source.incidentId(),
            source.planId(),
            source.id(),
            "draft",
            normalizeRiskLevel(request == null ? null : request.riskLevel()),
            request == null ? null : request.reason(),
            normalizeRequiredApprovals(request == null ? null : request.requiredApprovals()),
            blankToDefault(request == null ? null : request.createdBy(), "system")));

    int order = 1;
    for (RollbackPlanStepCreateCommand step : rollbackSteps) {
      rollbackRepository.createStep(
          new RollbackPlanStepCreateCommand(
              step.id(),
              tenantId,
              rollbackPlanId,
              step.sourceStepId(),
              order++,
              step.title(),
              step.description(),
              step.actionType(),
              step.targetType(),
              step.actionPayloadJson()));
    }

    return get(tenantId, rollbackPlanId);
  }

  public RollbackPlanResponse get(String tenantId, String rollbackPlanId) {
    RollbackPlanRecord plan =
        rollbackRepository
            .findPlan(tenantId, rollbackPlanId)
            .orElseThrow(() -> new AppException("ROLLBACK_PLAN_NOT_FOUND", "Rollback plan not found"));

    return toResponse(
        plan,
        rollbackRepository.listSteps(tenantId, rollbackPlanId),
        rollbackRepository.listDecisions(tenantId, rollbackPlanId));
  }

  public RollbackPlanResponse latestBySourceExecution(String tenantId, String sourceExecutionId) {
    RollbackPlanRecord plan =
        rollbackRepository
            .findLatestPlanBySourceExecution(tenantId, sourceExecutionId)
            .orElseThrow(() -> new AppException("ROLLBACK_PLAN_NOT_FOUND", "Rollback plan not found"));

    return get(tenantId, plan.id());
  }

  @Transactional
  public RollbackPlanResponse cancel(String tenantId, String rollbackPlanId) {
    RollbackPlanRecord plan =
        rollbackRepository
            .findPlan(tenantId, rollbackPlanId)
            .orElseThrow(() -> new AppException("ROLLBACK_PLAN_NOT_FOUND", "Rollback plan not found"));

    if (!List.of("draft", "pending_approval").contains(plan.status())) {
      throw new AppException("ROLLBACK_CANCEL_STATUS_INVALID", "Only draft or pending rollback plan can be cancelled");
    }

    boolean updated =
        rollbackRepository.updatePlanStatus(tenantId, rollbackPlanId, plan.status(), "cancelled");

    if (!updated) {
      throw new AppException("ROLLBACK_CANCEL_FAILED", "Rollback plan was not cancelled");
    }

    return get(tenantId, rollbackPlanId);
  }

  private void validateSourceExecution(ExecutionRunRecord source) {
    if (!"normal".equals(source.executionKind())) {
      throw new AppException("ROLLBACK_SOURCE_KIND_INVALID", "Only normal execution can be rolled back");
    }

    if (!"live".equals(source.mode())) {
      throw new AppException("ROLLBACK_SOURCE_MODE_INVALID", "Only live execution can be rolled back");
    }

    if (!List.of("succeeded", "failed").contains(source.status())) {
      throw new AppException(
          "ROLLBACK_SOURCE_STATUS_INVALID",
          "Only succeeded or failed live execution can create rollback plan");
    }
  }

  private List<RollbackPlanStepCreateCommand> buildRollbackSteps(
      String tenantId, List<ExecutionStepRecord> sourceSteps) {
    return sourceSteps.stream()
        .sorted(Comparator.comparingInt(ExecutionStepRecord::stepOrder).reversed())
        .map(step -> toRollbackStep(tenantId, step))
        .filter(java.util.Objects::nonNull)
        .toList();
  }

  private RollbackPlanStepCreateCommand toRollbackStep(String tenantId, ExecutionStepRecord sourceStep) {
    RollbackPayloadExtractor.RollbackPayload payload =
        extractor.extract(sourceStep.actionPayloadJson());

    if (payload == null) {
      return null;
    }

    return new RollbackPlanStepCreateCommand(
        newId("rbps"),
        tenantId,
        "",
        sourceStep.id(),
        0,
        payload.title(),
        payload.description(),
        payload.actionType(),
        payload.targetType(),
        payload.actionPayloadJson());
  }

  private RollbackPlanResponse toResponse(
      RollbackPlanRecord plan,
      List<RollbackPlanStepRecord> steps,
      List<RollbackDecisionRecord> decisions) {
    return new RollbackPlanResponse(
        plan.id(),
        plan.tenantId(),
        plan.incidentId(),
        plan.sourcePlanId(),
        plan.sourceExecutionId(),
        plan.status(),
        plan.riskLevel(),
        plan.reason(),
        plan.requiredApprovals(),
        plan.approvedCount(),
        plan.rejectedCount(),
        plan.createdBy(),
        plan.submittedBy(),
        plan.submittedAt(),
        plan.decidedAt(),
        plan.approvalSnapshotJson(),
        steps.stream().map(this::toStepResponse).toList(),
        decisions.stream().map(this::toDecisionResponse).toList(),
        plan.createdAt(),
        plan.updatedAt());
  }

  private RollbackPlanStepResponse toStepResponse(RollbackPlanStepRecord step) {
    return new RollbackPlanStepResponse(
        step.id(),
        step.sourceStepId(),
        step.stepOrder(),
        step.title(),
        step.description(),
        step.actionType(),
        step.targetType(),
        step.actionPayloadJson(),
        step.createdAt(),
        step.updatedAt());
  }

  private RollbackDecisionResponse toDecisionResponse(RollbackDecisionRecord decision) {
    return new RollbackDecisionResponse(
        decision.id(),
        decision.reviewer(),
        decision.decision(),
        decision.comment(),
        decision.createdAt());
  }

  private String normalizeRiskLevel(String value) {
    String risk = value == null || value.isBlank() ? "high" : value.trim().toLowerCase();
    if (!List.of("low", "medium", "high", "critical").contains(risk)) {
      throw new AppException("ROLLBACK_RISK_LEVEL_INVALID", "Invalid rollback risk level");
    }
    return risk;
  }

  private int normalizeRequiredApprovals(Integer value) {
    if (value == null) {
      return 1;
    }
    return Math.max(1, Math.min(value, 5));
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

# 14. RollbackApprovalService

路径：

```txt id="9b45n3"
modules/aiops-execution/src/main/java/io/aegisops/execution/RollbackApprovalService.java
```

```java id="5yddvg"
package io.aegisops.execution;

import io.aegisops.common.exception.AppException;
import io.aegisops.execution.dto.RollbackDecisionCreateCommand;
import io.aegisops.execution.dto.RollbackDecisionRequest;
import io.aegisops.execution.dto.RollbackPlanRecord;
import io.aegisops.execution.dto.RollbackPlanResponse;
import io.aegisops.execution.dto.RollbackPlanSubmitRequest;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RollbackApprovalService {
  private final RollbackRepository rollbackRepository;
  private final RollbackPlanService rollbackPlanService;
  private final RollbackJson json;

  public RollbackApprovalService(
      RollbackRepository rollbackRepository,
      RollbackPlanService rollbackPlanService,
      com.fasterxml.jackson.databind.ObjectMapper objectMapper) {
    this.rollbackRepository = rollbackRepository;
    this.rollbackPlanService = rollbackPlanService;
    this.json = new RollbackJson(objectMapper);
  }

  @Transactional
  public RollbackPlanResponse submit(
      String tenantId, String rollbackPlanId, RollbackPlanSubmitRequest request) {
    RollbackPlanRecord plan = load(tenantId, rollbackPlanId);

    if (!"draft".equals(plan.status())) {
      throw new AppException("ROLLBACK_SUBMIT_STATUS_INVALID", "Only draft rollback plan can be submitted");
    }

    boolean updated =
        rollbackRepository.submitPlan(
            tenantId,
            rollbackPlanId,
            blankToDefault(request == null ? null : request.submittedBy(), "system"));

    if (!updated) {
      throw new AppException("ROLLBACK_SUBMIT_FAILED", "Rollback plan was not submitted");
    }

    return rollbackPlanService.get(tenantId, rollbackPlanId);
  }

  @Transactional
  public RollbackPlanResponse approve(
      String tenantId, String rollbackPlanId, RollbackDecisionRequest request) {
    RollbackPlanRecord plan = loadPending(tenantId, rollbackPlanId);
    String reviewer = requireReviewer(request);

    if (rollbackRepository.decisionExists(tenantId, rollbackPlanId, reviewer)) {
      throw new AppException("ROLLBACK_DECISION_EXISTS", "Reviewer has already decided");
    }

    rollbackRepository.createDecision(
        new RollbackDecisionCreateCommand(
            newId("rbd"),
            tenantId,
            rollbackPlanId,
            reviewer,
            "approve",
            request.comment()));

    int nextApprovedCount = plan.approvedCount() + 1;

    if (nextApprovedCount >= plan.requiredApprovals()) {
      boolean updated =
          rollbackRepository.markApproved(
              tenantId,
              rollbackPlanId,
              json.write(
                  Map.of(
                      "rollbackPlanId", rollbackPlanId,
                      "status", "approved",
                      "requiredApprovals", plan.requiredApprovals(),
                      "approvedCount", nextApprovedCount,
                      "reviewer", reviewer)));

      if (!updated) {
        throw new AppException("ROLLBACK_APPROVAL_FAILED", "Rollback plan was not approved");
      }
    }

    return rollbackPlanService.get(tenantId, rollbackPlanId);
  }

  @Transactional
  public RollbackPlanResponse reject(
      String tenantId, String rollbackPlanId, RollbackDecisionRequest request) {
    RollbackPlanRecord plan = loadPending(tenantId, rollbackPlanId);
    String reviewer = requireReviewer(request);

    if (rollbackRepository.decisionExists(tenantId, rollbackPlanId, reviewer)) {
      throw new AppException("ROLLBACK_DECISION_EXISTS", "Reviewer has already decided");
    }

    rollbackRepository.createDecision(
        new RollbackDecisionCreateCommand(
            newId("rbd"),
            tenantId,
            rollbackPlanId,
            reviewer,
            "reject",
            request.comment()));

    boolean updated =
        rollbackRepository.markRejected(
            tenantId,
            rollbackPlanId,
            json.write(
                Map.of(
                    "rollbackPlanId", rollbackPlanId,
                    "status", "rejected",
                    "reviewer", reviewer,
                    "reason", request.comment() == null ? "" : request.comment())));

    if (!updated) {
      throw new AppException("ROLLBACK_REJECT_FAILED", "Rollback plan was not rejected");
    }

    return rollbackPlanService.get(tenantId, rollbackPlanId);
  }

  private RollbackPlanRecord load(String tenantId, String rollbackPlanId) {
    return rollbackRepository
        .findPlan(tenantId, rollbackPlanId)
        .orElseThrow(() -> new AppException("ROLLBACK_PLAN_NOT_FOUND", "Rollback plan not found"));
  }

  private RollbackPlanRecord loadPending(String tenantId, String rollbackPlanId) {
    RollbackPlanRecord plan = load(tenantId, rollbackPlanId);

    if (!"pending_approval".equals(plan.status())) {
      throw new AppException("ROLLBACK_DECISION_STATUS_INVALID", "Rollback plan is not pending approval");
    }

    return plan;
  }

  private String requireReviewer(RollbackDecisionRequest request) {
    if (request == null || request.reviewer() == null || request.reviewer().isBlank()) {
      throw new AppException("ROLLBACK_REVIEWER_REQUIRED", "Rollback reviewer is required");
    }
    return request.reviewer().trim();
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

# 15. RollbackExecutionService

路径：

```txt id="y0l0gw"
modules/aiops-execution/src/main/java/io/aegisops/execution/RollbackExecutionService.java
```

```java id="hw84ti"
package io.aegisops.execution;

import io.aegisops.common.exception.AppException;
import io.aegisops.execution.dto.ExecutionRunCreateCommand;
import io.aegisops.execution.dto.ExecutionRunResponse;
import io.aegisops.execution.dto.ExecutionStepCreateCommand;
import io.aegisops.execution.dto.RollbackExecutionCreateRequest;
import io.aegisops.execution.dto.RollbackPlanRecord;
import io.aegisops.execution.dto.RollbackPlanStepRecord;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RollbackExecutionService {
  private final RollbackRepository rollbackRepository;
  private final ExecutionRepository executionRepository;
  private final ExecutionRequestService executionRequestService;
  private final ExecutionProperties properties;

  public RollbackExecutionService(
      RollbackRepository rollbackRepository,
      ExecutionRepository executionRepository,
      ExecutionRequestService executionRequestService,
      ExecutionProperties properties) {
    this.rollbackRepository = rollbackRepository;
    this.executionRepository = executionRepository;
    this.executionRequestService = executionRequestService;
    this.properties = properties;
  }

  @Transactional
  public ExecutionRunResponse createExecution(
      String tenantId, String rollbackPlanId, RollbackExecutionCreateRequest request) {
    RollbackPlanRecord plan =
        rollbackRepository
            .findPlan(tenantId, rollbackPlanId)
            .orElseThrow(() -> new AppException("ROLLBACK_PLAN_NOT_FOUND", "Rollback plan not found"));

    if (!"approved".equals(plan.status())) {
      throw new AppException("ROLLBACK_EXECUTION_STATUS_INVALID", "Only approved rollback plan can be executed");
    }

    List<RollbackPlanStepRecord> steps = rollbackRepository.listSteps(tenantId, rollbackPlanId);
    if (steps.isEmpty()) {
      throw new AppException("ROLLBACK_STEPS_EMPTY", "Rollback plan has no steps");
    }

    String executionId = newId("exec");

    executionRepository.createRun(
        new ExecutionRunCreateCommand(
            executionId,
            tenantId,
            plan.incidentId(),
            plan.sourcePlanId(),
            "queued",
            "live",
            blankToDefault(request == null ? null : request.requestedBy(), "system"),
            1,
            normalizeMaxAttempts(request == null ? null : request.maxAttempts()),
            null,
            properties.normalizedRunTimeoutSeconds(),
            null,
            "{}",
            plan.riskLevel(),
            "rollback",
            plan.id(),
            plan.sourceExecutionId()));

    for (RollbackPlanStepRecord step : steps) {
      executionRepository.createStep(
          new ExecutionStepCreateCommand(
              newId("execstep"),
              tenantId,
              executionId,
              step.id(),
              step.stepOrder(),
              step.title(),
              step.actionType(),
              step.targetType(),
              "queued",
              step.actionPayloadJson()));
    }

    boolean marked = rollbackRepository.markExecuting(tenantId, rollbackPlanId);
    if (!marked) {
      throw new AppException("ROLLBACK_MARK_EXECUTING_FAILED", "Rollback plan was not marked executing");
    }

    return executionRequestService.get(tenantId, executionId);
  }

  private int normalizeMaxAttempts(Integer value) {
    if (value == null) {
      return 1;
    }
    return Math.max(1, Math.min(value, 3));
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

# 16. ExecutionRepository 修改

## 16.1 `ExecutionRepository.java`

确保有：

```java id="72ht0m"
void createRun(ExecutionRunCreateCommand command);

void createStep(ExecutionStepCreateCommand command);
```

如果之前已经有，只需要更新 command 字段即可。

---

## 16.2 `JooqExecutionRepository.createRun(...)`

追加字段：

```java id="k9m3g9"
.set(EXECUTION_RUN.EXECUTION_KIND, command.executionKind())
.set(EXECUTION_RUN.ROLLBACK_PLAN_ID, command.rollbackPlanId())
.set(EXECUTION_RUN.ROLLBACK_OF_EXECUTION_ID, command.rollbackOfExecutionId())
```

完整关键片段：

```java id="7sicv4"
dsl.insertInto(EXECUTION_RUN)
    .set(EXECUTION_RUN.ID, command.id())
    .set(EXECUTION_RUN.TENANT_ID, command.tenantId())
    .set(EXECUTION_RUN.INCIDENT_ID, command.incidentId())
    .set(EXECUTION_RUN.PLAN_ID, command.planId())
    .set(EXECUTION_RUN.STATUS, command.status())
    .set(EXECUTION_RUN.MODE, command.mode())
    .set(EXECUTION_RUN.REQUESTED_BY, command.requestedBy())
    .set(EXECUTION_RUN.ATTEMPT, command.attempt())
    .set(EXECUTION_RUN.MAX_ATTEMPTS, command.maxAttempts())
    .set(EXECUTION_RUN.RETRY_OF_EXECUTION_ID, command.retryOfExecutionId())
    .set(EXECUTION_RUN.TIMEOUT_SECONDS, command.timeoutSeconds())
    .set(EXECUTION_RUN.APPROVAL_ID, command.approvalId())
    .set(EXECUTION_RUN.APPROVAL_SNAPSHOT, jsonbValue(command.approvalSnapshotJson()))
    .set(EXECUTION_RUN.PLAN_RISK_LEVEL, command.planRiskLevel())
    .set(EXECUTION_RUN.EXECUTION_KIND, command.executionKind())
    .set(EXECUTION_RUN.ROLLBACK_PLAN_ID, command.rollbackPlanId())
    .set(EXECUTION_RUN.ROLLBACK_OF_EXECUTION_ID, command.rollbackOfExecutionId())
    .set(EXECUTION_RUN.CREATED_AT, DSL.currentOffsetDateTime())
    .set(EXECUTION_RUN.UPDATED_AT, DSL.currentOffsetDateTime())
    .execute();
```

---

## 16.3 `selectRun()` / `toRunRecord()` / `toResponse()` 增加字段

需要在查询和 mapping 里补：

```java id="f4f9om"
EXECUTION_RUN.EXECUTION_KIND,
EXECUTION_RUN.ROLLBACK_PLAN_ID,
EXECUTION_RUN.ROLLBACK_OF_EXECUTION_ID,
```

`ExecutionRequestService.toResponse(...)` 也补：

```java id="n5x1ez"
run.executionKind(),
run.rollbackPlanId(),
run.rollbackOfExecutionId(),
```

---

# 17. Runner 完成后同步 rollback_plan 状态

为了让 rollback_plan 从 `executing` 进入 `succeeded/failed`，需要在 runner 完成 execution 时根据 `execution_kind` 更新 rollback_plan。

## 17.1 修改 `RunnerExecutionService`

路径：

```txt id="fsu20o"
apps/aiops-runner/src/main/java/io/aegisops/runner/RunnerExecutionService.java
```

注入：

```java id="janj3i"
private final RollbackRepository rollbackRepository;
```

构造函数补参数。

在原来 `succeedRunAndPlan(...)` 最后补：

```java id="h7sxae"
if ("rollback".equals(run.executionKind()) && run.rollbackPlanId() != null) {
  rollbackRepository.markSucceeded(run.tenantId(), run.rollbackPlanId());
}
```

在原来 `failRunAndPlan(...)` 最后补：

```java id="20cv7i"
if ("rollback".equals(run.executionKind()) && run.rollbackPlanId() != null) {
  rollbackRepository.markFailed(run.tenantId(), run.rollbackPlanId());
}
```

注意：不要把 source automation_plan 状态改成 succeeded/failed；rollback execution 是单独状态。

---

# 18. Controller

路径：

```txt id="wwas72"
modules/aiops-execution/src/main/java/io/aegisops/execution/RollbackPlanController.java
```

```java id="jny2v6"
package io.aegisops.execution;

import io.aegisops.common.api.ApiResponse;
import io.aegisops.common.tenant.TenantContext;
import io.aegisops.execution.dto.ExecutionRunResponse;
import io.aegisops.execution.dto.RollbackDecisionRequest;
import io.aegisops.execution.dto.RollbackExecutionCreateRequest;
import io.aegisops.execution.dto.RollbackPlanCreateRequest;
import io.aegisops.execution.dto.RollbackPlanResponse;
import io.aegisops.execution.dto.RollbackPlanSubmitRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class RollbackPlanController {
  private final RollbackPlanService planService;
  private final RollbackApprovalService approvalService;
  private final RollbackExecutionService executionService;

  public RollbackPlanController(
      RollbackPlanService planService,
      RollbackApprovalService approvalService,
      RollbackExecutionService executionService) {
    this.planService = planService;
    this.approvalService = approvalService;
    this.executionService = executionService;
  }

  @PostMapping("/api/executions/{executionId}/rollback-plans")
  public ApiResponse<RollbackPlanResponse> create(
      @PathVariable String executionId,
      @RequestBody RollbackPlanCreateRequest request) {
    return ApiResponse.ok(planService.create(TenantContext.requireTenantId(), executionId, request));
  }

  @GetMapping("/api/executions/{executionId}/rollback-plans/latest")
  public ApiResponse<RollbackPlanResponse> latest(@PathVariable String executionId) {
    return ApiResponse.ok(planService.latestBySourceExecution(TenantContext.requireTenantId(), executionId));
  }

  @GetMapping("/api/rollback-plans/{rollbackPlanId}")
  public ApiResponse<RollbackPlanResponse> get(@PathVariable String rollbackPlanId) {
    return ApiResponse.ok(planService.get(TenantContext.requireTenantId(), rollbackPlanId));
  }

  @PostMapping("/api/rollback-plans/{rollbackPlanId}/submit")
  public ApiResponse<RollbackPlanResponse> submit(
      @PathVariable String rollbackPlanId,
      @RequestBody RollbackPlanSubmitRequest request) {
    return ApiResponse.ok(approvalService.submit(TenantContext.requireTenantId(), rollbackPlanId, request));
  }

  @PostMapping("/api/rollback-plans/{rollbackPlanId}/approve")
  public ApiResponse<RollbackPlanResponse> approve(
      @PathVariable String rollbackPlanId,
      @RequestBody RollbackDecisionRequest request) {
    return ApiResponse.ok(approvalService.approve(TenantContext.requireTenantId(), rollbackPlanId, request));
  }

  @PostMapping("/api/rollback-plans/{rollbackPlanId}/reject")
  public ApiResponse<RollbackPlanResponse> reject(
      @PathVariable String rollbackPlanId,
      @RequestBody RollbackDecisionRequest request) {
    return ApiResponse.ok(approvalService.reject(TenantContext.requireTenantId(), rollbackPlanId, request));
  }

  @PostMapping("/api/rollback-plans/{rollbackPlanId}/cancel")
  public ApiResponse<RollbackPlanResponse> cancel(@PathVariable String rollbackPlanId) {
    return ApiResponse.ok(planService.cancel(TenantContext.requireTenantId(), rollbackPlanId));
  }

  @PostMapping("/api/rollback-plans/{rollbackPlanId}/executions")
  public ApiResponse<ExecutionRunResponse> createExecution(
      @PathVariable String rollbackPlanId,
      @RequestBody RollbackExecutionCreateRequest request) {
    return ApiResponse.ok(executionService.createExecution(TenantContext.requireTenantId(), rollbackPlanId, request));
  }
}
```

---

# 19. 单元测试

## 19.1 `RollbackPayloadExtractorTest.java`

路径：

```txt id="he0y20"
modules/aiops-execution/src/test/java/io/aegisops/execution/RollbackPayloadExtractorTest.java
```

```java id="ojjcji"
package io.aegisops.execution;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.aegisops.common.exception.AppException;
import org.junit.jupiter.api.Test;

class RollbackPayloadExtractorTest {
  private final RollbackPayloadExtractor extractor =
      new RollbackPayloadExtractor(new ObjectMapper());

  @Test
  void returnNullWhenNoRollbackPayload() {
    assertNull(extractor.extract("{\"a\":1}"));
  }

  @Test
  void extractRollbackPayload() {
    var payload =
        extractor.extract(
            """
            {
              "inventoryId": "inv_1",
              "rollback": {
                "title": "Rollback service",
                "actionType": "ansible",
                "targetType": "service",
                "actionPayload": {
                  "inventoryId": "inv_1",
                  "playbookId": "pb_rollback"
                }
              }
            }
            """);

    assertEquals("Rollback service", payload.title());
    assertEquals("ansible", payload.actionType());
    assertEquals("service", payload.targetType());
  }

  @Test
  void rejectRollbackWithoutActionPayload() {
    assertThrows(
        AppException.class,
        () ->
            extractor.extract(
                """
                {
                  "rollback": {
                    "title": "Rollback service",
                    "actionType": "ansible"
                  }
                }
                """));
  }
}
```

---

## 19.2 `RollbackPlanServiceTest.java`

路径：

```txt id="5b6hld"
modules/aiops-execution/src/test/java/io/aegisops/execution/RollbackPlanServiceTest.java
```

```java id="c46dk0"
package io.aegisops.execution;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.aegisops.common.exception.AppException;
import io.aegisops.execution.dto.*;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class RollbackPlanServiceTest {
  @Test
  void createRollbackPlanFromExplicitRollbackPayload() {
    FakeRollbackRepository rollbackRepository = new FakeRollbackRepository();
    FakeExecutionRepository executionRepository = new FakeExecutionRepository();

    RollbackPlanService service =
        new RollbackPlanService(
            rollbackRepository,
            executionRepository,
            new RollbackPayloadExtractor(new ObjectMapper()));

    RollbackPlanResponse response =
        service.create(
            "tenant_1",
            "exec_1",
            new RollbackPlanCreateRequest("bad deploy", "high", 1, "alice"));

    assertEquals("draft", response.status());
    assertEquals(1, response.steps().size());
    assertEquals("ansible", response.steps().get(0).actionType());
  }

  @Test
  void rejectRollbackOfDryRunExecution() {
    FakeRollbackRepository rollbackRepository = new FakeRollbackRepository();
    FakeExecutionRepository executionRepository = new FakeExecutionRepository();
    executionRepository.sourceMode = "dry_run";

    RollbackPlanService service =
        new RollbackPlanService(
            rollbackRepository,
            executionRepository,
            new RollbackPayloadExtractor(new ObjectMapper()));

    assertThrows(
        AppException.class,
        () ->
            service.create(
                "tenant_1",
                "exec_1",
                new RollbackPlanCreateRequest("bad deploy", "high", 1, "alice")));
  }

  @Test
  void rejectWhenNoRollbackPayload() {
    FakeRollbackRepository rollbackRepository = new FakeRollbackRepository();
    FakeExecutionRepository executionRepository = new FakeExecutionRepository();
    executionRepository.stepPayload = "{\"inventoryId\":\"inv_1\"}";

    RollbackPlanService service =
        new RollbackPlanService(
            rollbackRepository,
            executionRepository,
            new RollbackPayloadExtractor(new ObjectMapper()));

    assertThrows(
        AppException.class,
        () ->
            service.create(
                "tenant_1",
                "exec_1",
                new RollbackPlanCreateRequest("bad deploy", "high", 1, "alice")));
  }

  private static class FakeRollbackRepository implements RollbackRepository {
    RollbackPlanRecord plan;
    final List<RollbackPlanStepRecord> steps = new ArrayList<>();

    @Override
    public void createPlan(RollbackPlanCreateCommand command) {
      plan =
          new RollbackPlanRecord(
              command.id(),
              command.tenantId(),
              command.incidentId(),
              command.sourcePlanId(),
              command.sourceExecutionId(),
              command.status(),
              command.riskLevel(),
              command.reason(),
              command.requiredApprovals(),
              0,
              0,
              command.createdBy(),
              null,
              null,
              null,
              "{}",
              OffsetDateTime.now(),
              OffsetDateTime.now());
    }

    @Override
    public void createStep(RollbackPlanStepCreateCommand command) {
      steps.add(
          new RollbackPlanStepRecord(
              command.id(),
              command.tenantId(),
              command.rollbackPlanId(),
              command.sourceStepId(),
              command.stepOrder(),
              command.title(),
              command.description(),
              command.actionType(),
              command.targetType(),
              command.actionPayloadJson(),
              OffsetDateTime.now(),
              OffsetDateTime.now()));
    }

    @Override
    public Optional<RollbackPlanRecord> findPlan(String tenantId, String rollbackPlanId) {
      return Optional.ofNullable(plan);
    }

    @Override
    public Optional<RollbackPlanRecord> findLatestPlanBySourceExecution(String tenantId, String sourceExecutionId) {
      return Optional.ofNullable(plan);
    }

    @Override
    public List<RollbackPlanStepRecord> listSteps(String tenantId, String rollbackPlanId) {
      return steps;
    }

    @Override
    public List<RollbackDecisionRecord> listDecisions(String tenantId, String rollbackPlanId) {
      return List.of();
    }

    @Override public boolean updatePlanStatus(String tenantId, String rollbackPlanId, String fromStatus, String toStatus) { return true; }
    @Override public boolean submitPlan(String tenantId, String rollbackPlanId, String submittedBy) { return true; }
    @Override public void createDecision(RollbackDecisionCreateCommand command) {}
    @Override public boolean decisionExists(String tenantId, String rollbackPlanId, String reviewer) { return false; }
    @Override public boolean markApproved(String tenantId, String rollbackPlanId, String approvalSnapshotJson) { return true; }
    @Override public boolean markRejected(String tenantId, String rollbackPlanId, String approvalSnapshotJson) { return true; }
    @Override public boolean markExecuting(String tenantId, String rollbackPlanId) { return true; }
    @Override public boolean markSucceeded(String tenantId, String rollbackPlanId) { return true; }
    @Override public boolean markFailed(String tenantId, String rollbackPlanId) { return true; }
  }

  private static class FakeExecutionRepository extends RunnerFakeExecutionRepositoryBase {
    String sourceMode = "live";
    String stepPayload =
        """
        {
          "inventoryId": "inv_1",
          "playbookId": "pb_deploy",
          "rollback": {
            "title": "Rollback service",
            "actionType": "ansible",
            "targetType": "service",
            "actionPayload": {
              "inventoryId": "inv_1",
              "playbookId": "pb_rollback"
            }
          }
        }
        """;

    @Override
    public Optional<ExecutionRunRecord> findRun(String tenantId, String executionId) {
      return Optional.of(
          new ExecutionRunRecord(
              executionId,
              tenantId,
              "inc_1",
              "plan_1",
              "succeeded",
              sourceMode,
              "alice",
              "runner_1",
              OffsetDateTime.now(),
              OffsetDateTime.now(),
              null,
              null,
              1,
              1,
              null,
              null,
              null,
              1800,
              "approval_1",
              "{\"status\":\"approved\"}",
              "high",
              OffsetDateTime.now(),
              "normal",
              null,
              null,
              OffsetDateTime.now(),
              OffsetDateTime.now()));
    }

    @Override
    public List<ExecutionStepRecord> listExecutionSteps(String tenantId, String executionId) {
      return List.of(
          new ExecutionStepRecord(
              "step_1",
              tenantId,
              executionId,
              "planstep_1",
              1,
              "Deploy",
              "ansible",
              "service",
              "succeeded",
              stepPayload,
              null,
              null,
              null,
              null,
              null,
              1,
              300,
              0,
              OffsetDateTime.now(),
              OffsetDateTime.now()));
    }
  }
}
```

---

## 19.3 `RollbackApprovalServiceTest.java`

路径：

```txt id="dov44y"
modules/aiops-execution/src/test/java/io/aegisops/execution/RollbackApprovalServiceTest.java
```

```java id="w7bhvk"
package io.aegisops.execution;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.aegisops.common.exception.AppException;
import io.aegisops.execution.dto.*;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class RollbackApprovalServiceTest {
  @Test
  void approveRollbackPlan() {
    FakeRollbackRepository repository = new FakeRollbackRepository();
    RollbackPlanService planService = repository.planService();
    RollbackApprovalService approvalService =
        new RollbackApprovalService(repository, planService, new ObjectMapper());

    RollbackPlanResponse response =
        approvalService.approve(
            "tenant_1",
            "rbp_1",
            new RollbackDecisionRequest("bob", "ok"));

    assertEquals("approved", response.status());
    assertEquals(1, response.decisions().size());
  }

  @Test
  void rejectDuplicateDecision() {
    FakeRollbackRepository repository = new FakeRollbackRepository();
    repository.decisions.add(
        new RollbackDecisionRecord(
            "rbd_1", "tenant_1", "rbp_1", "bob", "approve", "ok", OffsetDateTime.now()));

    RollbackApprovalService approvalService =
        new RollbackApprovalService(repository, repository.planService(), new ObjectMapper());

    assertThrows(
        AppException.class,
        () ->
            approvalService.approve(
                "tenant_1",
                "rbp_1",
                new RollbackDecisionRequest("bob", "again")));
  }

  private static class FakeRollbackRepository implements RollbackRepository {
    RollbackPlanRecord plan =
        new RollbackPlanRecord(
            "rbp_1",
            "tenant_1",
            "inc_1",
            "plan_1",
            "exec_1",
            "pending_approval",
            "high",
            "bad deploy",
            1,
            0,
            0,
            "alice",
            "alice",
            OffsetDateTime.now(),
            null,
            "{}",
            OffsetDateTime.now(),
            OffsetDateTime.now());

    final List<RollbackDecisionRecord> decisions = new ArrayList<>();

    RollbackPlanService planService() {
      return new RollbackPlanService(this, new RunnerFakeExecutionRepositoryBase(), new RollbackPayloadExtractor(new ObjectMapper()));
    }

    @Override
    public Optional<RollbackPlanRecord> findPlan(String tenantId, String rollbackPlanId) {
      return Optional.of(plan);
    }

    @Override
    public void createDecision(RollbackDecisionCreateCommand command) {
      decisions.add(
          new RollbackDecisionRecord(
              command.id(),
              command.tenantId(),
              command.rollbackPlanId(),
              command.reviewer(),
              command.decision(),
              command.comment(),
              OffsetDateTime.now()));
    }

    @Override
    public boolean decisionExists(String tenantId, String rollbackPlanId, String reviewer) {
      return decisions.stream().anyMatch(item -> item.reviewer().equals(reviewer));
    }

    @Override
    public boolean markApproved(String tenantId, String rollbackPlanId, String approvalSnapshotJson) {
      plan =
          new RollbackPlanRecord(
              plan.id(),
              plan.tenantId(),
              plan.incidentId(),
              plan.sourcePlanId(),
              plan.sourceExecutionId(),
              "approved",
              plan.riskLevel(),
              plan.reason(),
              plan.requiredApprovals(),
              1,
              0,
              plan.createdBy(),
              plan.submittedBy(),
              plan.submittedAt(),
              OffsetDateTime.now(),
              approvalSnapshotJson,
              plan.createdAt(),
              OffsetDateTime.now());
      return true;
    }

    @Override
    public List<RollbackDecisionRecord> listDecisions(String tenantId, String rollbackPlanId) {
      return decisions;
    }

    @Override public void createPlan(RollbackPlanCreateCommand command) {}
    @Override public void createStep(RollbackPlanStepCreateCommand command) {}
    @Override public Optional<RollbackPlanRecord> findLatestPlanBySourceExecution(String tenantId, String sourceExecutionId) { return Optional.of(plan); }
    @Override public List<RollbackPlanStepRecord> listSteps(String tenantId, String rollbackPlanId) { return List.of(); }
    @Override public boolean updatePlanStatus(String tenantId, String rollbackPlanId, String fromStatus, String toStatus) { return true; }
    @Override public boolean submitPlan(String tenantId, String rollbackPlanId, String submittedBy) { return true; }
    @Override public boolean markRejected(String tenantId, String rollbackPlanId, String approvalSnapshotJson) { return true; }
    @Override public boolean markExecuting(String tenantId, String rollbackPlanId) { return true; }
    @Override public boolean markSucceeded(String tenantId, String rollbackPlanId) { return true; }
    @Override public boolean markFailed(String tenantId, String rollbackPlanId) { return true; }
  }
}
```

---

## 19.4 `RollbackExecutionServiceTest.java`

路径：

```txt id="d1v8t2"
modules/aiops-execution/src/test/java/io/aegisops/execution/RollbackExecutionServiceTest.java
```

```java id="r8f4cv"
package io.aegisops.execution;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.aegisops.common.exception.AppException;
import io.aegisops.execution.dto.*;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class RollbackExecutionServiceTest {
  @Test
  void createRollbackExecutionFromApprovedPlan() {
    FakeRollbackRepository rollbackRepository = new FakeRollbackRepository();
    FakeExecutionRepository executionRepository = new FakeExecutionRepository();

    RollbackExecutionService service =
        new RollbackExecutionService(
            rollbackRepository,
            executionRepository,
            new FakeExecutionRequestService(executionRepository),
            new ExecutionProperties());

    ExecutionRunResponse response =
        service.createExecution(
            "tenant_1",
            "rbp_1",
            new RollbackExecutionCreateRequest("alice", 1));

    assertEquals("rollback", executionRepository.createdRun.executionKind());
    assertEquals("rbp_1", executionRepository.createdRun.rollbackPlanId());
    assertEquals("exec_source", executionRepository.createdRun.rollbackOfExecutionId());
  }

  @Test
  void rejectNonApprovedRollbackPlan() {
    FakeRollbackRepository rollbackRepository = new FakeRollbackRepository();
    rollbackRepository.status = "pending_approval";

    RollbackExecutionService service =
        new RollbackExecutionService(
            rollbackRepository,
            new FakeExecutionRepository(),
            new FakeExecutionRequestService(new FakeExecutionRepository()),
            new ExecutionProperties());

    assertThrows(
        AppException.class,
        () ->
            service.createExecution(
                "tenant_1",
                "rbp_1",
                new RollbackExecutionCreateRequest("alice", 1)));
  }

  private static class FakeRollbackRepository implements RollbackRepository {
    String status = "approved";

    @Override
    public Optional<RollbackPlanRecord> findPlan(String tenantId, String rollbackPlanId) {
      return Optional.of(
          new RollbackPlanRecord(
              rollbackPlanId,
              tenantId,
              "inc_1",
              "plan_1",
              "exec_source",
              status,
              "high",
              "bad deploy",
              1,
              1,
              0,
              "alice",
              "alice",
              OffsetDateTime.now(),
              OffsetDateTime.now(),
              "{}",
              OffsetDateTime.now(),
              OffsetDateTime.now()));
    }

    @Override
    public List<RollbackPlanStepRecord> listSteps(String tenantId, String rollbackPlanId) {
      return List.of(
          new RollbackPlanStepRecord(
              "rbps_1",
              tenantId,
              rollbackPlanId,
              "step_1",
              1,
              "Rollback",
              "desc",
              "ansible",
              "service",
              "{\"inventoryId\":\"inv_1\",\"playbookId\":\"pb_rollback\"}",
              OffsetDateTime.now(),
              OffsetDateTime.now()));
    }

    @Override public boolean markExecuting(String tenantId, String rollbackPlanId) { return true; }
    @Override public void createPlan(RollbackPlanCreateCommand command) {}
    @Override public void createStep(RollbackPlanStepCreateCommand command) {}
    @Override public Optional<RollbackPlanRecord> findLatestPlanBySourceExecution(String tenantId, String sourceExecutionId) { return Optional.empty(); }
    @Override public List<RollbackDecisionRecord> listDecisions(String tenantId, String rollbackPlanId) { return List.of(); }
    @Override public boolean updatePlanStatus(String tenantId, String rollbackPlanId, String fromStatus, String toStatus) { return true; }
    @Override public boolean submitPlan(String tenantId, String rollbackPlanId, String submittedBy) { return true; }
    @Override public void createDecision(RollbackDecisionCreateCommand command) {}
    @Override public boolean decisionExists(String tenantId, String rollbackPlanId, String reviewer) { return false; }
    @Override public boolean markApproved(String tenantId, String rollbackPlanId, String approvalSnapshotJson) { return true; }
    @Override public boolean markRejected(String tenantId, String rollbackPlanId, String approvalSnapshotJson) { return true; }
    @Override public boolean markSucceeded(String tenantId, String rollbackPlanId) { return true; }
    @Override public boolean markFailed(String tenantId, String rollbackPlanId) { return true; }
  }

  private static class FakeExecutionRepository extends RunnerFakeExecutionRepositoryBase {
    ExecutionRunCreateCommand createdRun;

    @Override
    public void createRun(ExecutionRunCreateCommand command) {
      createdRun = command;
    }

    @Override
    public void createStep(ExecutionStepCreateCommand command) {}
  }

  private static class FakeExecutionRequestService extends ExecutionRequestService {
    private final FakeExecutionRepository repository;

    FakeExecutionRequestService(FakeExecutionRepository repository) {
      super(repository, null, null, null, null);
      this.repository = repository;
    }

    @Override
    public ExecutionRunResponse get(String tenantId, String executionId) {
      return new ExecutionRunResponse(
          executionId,
          tenantId,
          "inc_1",
          "plan_1",
          "queued",
          "live",
          "alice",
          null,
          null,
          null,
          1,
          1,
          null,
          null,
          null,
          1800,
          null,
          "{}",
          "high",
          null,
          "rollback",
          "rbp_1",
          "exec_source",
          List.of(),
          List.of(),
          null,
          null,
          OffsetDateTime.now(),
          OffsetDateTime.now());
    }
  }
}
```

> 如果 `ExecutionRequestService` 构造复杂，不建议继承；可以把 `RollbackExecutionService` 的返回改成直接组装 response 或抽一个 `ExecutionResponseMapper`。这里为了展示核心逻辑，使用最小伪实现。

---

## 19.5 RunnerExecutionServiceTest 增强

新增两个测试：

```java id="2ocgth"
@Test
void markRollbackPlanSucceededWhenRollbackRunSucceeded() {
  // given run.executionKind = rollback
  // and run.rollbackPlanId = rbp_1
  // when runner completes success
  // then rollbackRepository.markSucceeded called
}

@Test
void markRollbackPlanFailedWhenRollbackRunFailed() {
  // given run.executionKind = rollback
  // when runner completes failure
  // then rollbackRepository.markFailed called
}
```

核心断言：

```java id="26n103"
assertTrue(fakeRollbackRepository.succeeded);
assertTrue(fakeRollbackRepository.failed);
```

---

# 20. 文档

路径：

```txt id="zffz3e"
docs/mvp/design/phase5.8-rollback-plan.md
```

````md id="nnfa3z"
# Phase5.8 Rollback Plan

## 目标

Phase5.8 为 live execution 增加 rollback plan。

## 原则

- 不自动回滚
- 不由 AI 直接执行回滚
- 回滚必须先创建 rollback_plan
- rollback_plan 必须审批
- 审批通过后才允许创建 rollback execution
- rollback execution 仍走 runner
- rollback execution 使用 execution_kind=rollback 标记

## rollback action payload

源 execution step 的 action_payload 中必须显式声明 rollback：

```json
{
  "rollback": {
    "title": "Rollback service",
    "actionType": "ansible",
    "targetType": "service",
    "actionPayload": {
      "inventoryId": "inv_1",
      "playbookId": "pb_rollback"
    }
  }
}
```
````

## 状态机

rollback_plan:

- draft
- pending_approval
- approved
- rejected
- executing
- succeeded
- failed
- cancelled

## API

- POST /api/executions/{executionId}/rollback-plans
- GET /api/executions/{executionId}/rollback-plans/latest
- GET /api/rollback-plans/{rollbackPlanId}
- POST /api/rollback-plans/{rollbackPlanId}/submit
- POST /api/rollback-plans/{rollbackPlanId}/approve
- POST /api/rollback-plans/{rollbackPlanId}/reject
- POST /api/rollback-plans/{rollbackPlanId}/cancel
- POST /api/rollback-plans/{rollbackPlanId}/executions

## 不做

- SSH Adapter
- 自动回滚
- AI 直接回滚
- 未审批回滚

````

---

# 21. 验证命令

```powershell id="nm0wde"
mvn -pl modules/aiops-persistence -am generate-sources
mvn -pl modules/aiops-execution -am test
mvn -pl apps/aiops-runner -am test
mvn -pl apps/aiops-server -am test
````

全量：

```powershell id="z4nbf9"
mvn test
```

---

# 22. 验收标准

```txt id="1xu3o6"
1. rollback_plan 表存在。
2. rollback_plan_step 表存在。
3. rollback_decision 表存在。
4. execution_run 有 execution_kind。
5. execution_run 有 rollback_plan_id。
6. execution_run 有 rollback_of_execution_id。
7. 只有 normal + live + succeeded/failed execution 可以生成 rollback plan。
8. 没有 rollback payload 时拒绝生成 rollback plan。
9. rollback step 按 source step 倒序生成。
10. rollback plan draft 可以 submit。
11. pending_approval 可以 approve。
12. duplicate reviewer 被拒绝。
13. approved rollback plan 可以创建 rollback execution。
14. 非 approved rollback plan 不能创建 rollback execution。
15. rollback execution 标记 execution_kind=rollback。
16. rollback execution 绑定 rollback_plan_id。
17. runner 成功后 rollback_plan -> succeeded。
18. runner 失败后 rollback_plan -> failed。
19. 不存在自动 rollback。
20. 不引入 SSH Adapter。
```

---

# 23. 建议提交信息

```txt id="zf6yvv"
feat(rollback): add rollback plan approval and execution binding
```

---

# 24. Phase5.9 下一步

Phase5.8 完成后，下一步建议：

```txt id="olwdzc"
Phase5.9 Execution Report & Verification
```

而不是马上做 SSH。

原因：

```txt id="2ef56z"
现在已经有 live execution 和 rollback execution，
下一步需要把执行报告、前后验证、回滚结果汇总做完整。
```

Phase5.9 推荐做：

```txt id="s4lna0"
execution_report
execution_verification
before/after snapshot
rollback report
operator timeline
artifact summary
```

SSH Read-only Adapter 可以放到 Phase5.10。
