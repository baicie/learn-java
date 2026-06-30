---
title: Phase5.3：Runner Lease / Heartbeat / Timeout / Retry / Artifact
type: design
status: accepted
phase: global
owner: ai
created: 2026-06-30
updated: 2026-06-30
related: []
---

# Phase5.3：Runner Lease / Heartbeat / Timeout / Retry / Artifact

> Phase5.3 目标：补齐 Phase5.2 执行器的可靠性能力。
> 本阶段仍然保持安全边界：**不引入真实 shell 执行、不 SSH、不 Ansible、不 Webhook**。
> `aiops-server` 仍只负责创建/重试 execution；`aiops-runner` 负责消费 queued execution。

---

## 1. Phase5.3 定位

Phase5.2 已经完成：

```txt
approved AutomationPlan -> execution_run queued
execution_step queued
aiops-runner claim queued run
manual step
shell dry-run step
unsupported step fail
run succeeded / failed
plan succeeded / failed
```

Phase5.3 增加：

```txt
1. runner lease
2. runner heartbeat
3. running execution timeout
4. failed execution retry
5. execution artifact
6. runner stuck recovery
7. 更多状态一致性测试
```

---

## 2. 本阶段安全边界

```txt
禁止：
1. ProcessBuilder
2. Runtime.exec
3. SSH / JSch / sshj
4. Ansible live run
5. HTTP Webhook live call
6. 直接修改生产资源

允许：
1. manual step 记录
2. shell dry-run 输出命令快照
3. unsupported step 失败
4. artifact 文本落库
5. failed run 重试生成新的 queued execution_run
```

---

## 3. 状态机增强

### 3.1 ExecutionRun

```txt
queued
running
succeeded
failed
cancelled
timeout
```

新增：

```txt
timeout：runner lease 超时后由 timeout sweeper 标记
```

### 3.2 ExecutionStep

```txt
queued
running
succeeded
failed
skipped
cancelled
timeout
```

新增：

```txt
timeout：run 超时后仍处于 queued/running 的 step 标记 timeout
```

---

## 4. Lease / Heartbeat 设计

### 4.1 claim

runner claim queued run 时：

```txt
execution_run.status = running
execution_run.runner_id = 当前 runner id
execution_run.started_at = now
execution_run.heartbeat_at = now
execution_run.lease_until = now + leaseSeconds
```

### 4.2 heartbeat

runner 每执行一个 step 前后更新：

```txt
heartbeat_at = now
lease_until = now + leaseSeconds
```

### 4.3 timeout sweeper

runner 定时扫描：

```txt
status = running
lease_until < now
```

然后标记：

```txt
execution_run.status = timeout
automation_plan.status = failed
execution_step.status in queued/running -> timeout
```

---

## 5. Retry 设计

Phase5.3 提供 server API：

```txt
POST /api/executions/{executionId}/retry
```

规则：

```txt
1. 只能 retry failed / timeout 的 execution_run。
2. retry 会创建新的 execution_run.status = queued。
3. 新 run.attempt = old.attempt + 1。
4. old.attempt < old.max_attempts 才允许 retry。
5. retry 会重新从 automation_plan_step 复制 step。
6. retry 后 automation_plan.status = executing。
```

---

## 6. Artifact 设计

新增表：

```txt
execution_artifact
```

用于记录：

```txt
1. dry-run shell command
2. manual confirmation text
3. unsupported action error context
4. 后续 Phase5.4 可扩展到 MinIO 文件
```

Phase5.3 暂时只存 text，不接 MinIO。

---

# 7. Migration

路径：

```txt
apps/aiops-server/src/main/resources/db/migration/V13__phase5_3_execution_reliability.sql
```

```sql
-- Phase 5.3: execution reliability.
-- Adds runner lease, heartbeat, timeout, retry and artifact metadata.

alter table execution_run
  drop constraint if exists ck_execution_run_status;

alter table execution_run
  add constraint ck_execution_run_status
    check (status in ('queued', 'running', 'succeeded', 'failed', 'cancelled', 'timeout'));

alter table execution_step
  drop constraint if exists ck_execution_step_status;

alter table execution_step
  add constraint ck_execution_step_status
    check (status in ('queued', 'running', 'succeeded', 'failed', 'skipped', 'cancelled', 'timeout'));

alter table execution_run
  add column if not exists attempt int not null default 1,
  add column if not exists max_attempts int not null default 1,
  add column if not exists retry_of_execution_id varchar(64) references execution_run(id) on delete set null,
  add column if not exists lease_until timestamptz,
  add column if not exists heartbeat_at timestamptz,
  add column if not exists timeout_seconds int not null default 1800;

alter table execution_run
  add constraint ck_execution_run_attempt
    check (attempt >= 1 and max_attempts >= 1 and attempt <= max_attempts);

alter table execution_run
  add constraint ck_execution_run_timeout_seconds
    check (timeout_seconds >= 30 and timeout_seconds <= 86400);

alter table execution_step
  add column if not exists attempt int not null default 1,
  add column if not exists timeout_seconds int not null default 300,
  add column if not exists artifact_count int not null default 0;

alter table execution_step
  add constraint ck_execution_step_attempt
    check (attempt >= 1);

alter table execution_step
  add constraint ck_execution_step_timeout_seconds
    check (timeout_seconds >= 1 and timeout_seconds <= 86400);

alter table execution_step
  add constraint ck_execution_step_artifact_count
    check (artifact_count >= 0);

create index if not exists idx_execution_run_running_lease
  on execution_run(status, lease_until)
  where status = 'running';

create index if not exists idx_execution_run_retry_of
  on execution_run(retry_of_execution_id);

create table if not exists execution_artifact (
  id varchar(64) primary key,
  tenant_id varchar(64) not null references tenant(id) on delete cascade,
  execution_id varchar(64) not null references execution_run(id) on delete cascade,
  step_id varchar(64) references execution_step(id) on delete cascade,
  artifact_type varchar(32) not null default 'text',
  name varchar(160) not null,
  content text,
  metadata jsonb not null default '{}'::jsonb,
  created_at timestamptz not null default now(),
  constraint ck_execution_artifact_type
    check (artifact_type in ('text', 'json', 'log'))
);

create index if not exists idx_execution_artifact_execution
  on execution_artifact(execution_id, created_at asc);

create index if not exists idx_execution_artifact_step
  on execution_artifact(step_id, created_at asc);
```

---

# 8. jOOQ Codegen

路径：

```txt
modules/aiops-persistence/src/main/resources/jooq-codegen.xml
```

在 `<includes>` 追加：

```txt
execution_artifact
```

建议完整 includes：

```xml
<includes>
  tenant | sys_user | sys_role | sys_permission | sys_user_role | sys_role_permission |
  datasource | datasource_sync_run | asset | asset_relation | alert_event | incident |
  incident_event | incident_timeline | audit_log | rca_analysis | ai_diagnosis |
  agent_run | agent_run_step | agent_eval_result | log_event | change_event |
  runbook | runbook_step_template | automation_plan | automation_plan_step |
  approval_policy | automation_approval | approval_decision |
  execution_run | execution_step | execution_artifact
</includes>
```

---

# 9. DTO 完整代码

## 9.1 `ExecutionCreateRequest.java`

路径：

```txt
modules/aiops-execution/src/main/java/io/aegisops/execution/dto/ExecutionCreateRequest.java
```

```java
package io.aegisops.execution.dto;

public record ExecutionCreateRequest(Boolean dryRun, String requestedBy, Integer maxAttempts) {
  public boolean dryRunEnabled() {
    return dryRun == null || dryRun;
  }

  public int normalizedMaxAttempts() {
    if (maxAttempts == null) {
      return 1;
    }
    return Math.max(1, Math.min(maxAttempts, 5));
  }
}
```

---

## 9.2 `ExecutionRetryRequest.java`

```java
package io.aegisops.execution.dto;

public record ExecutionRetryRequest(String requestedBy) {}
```

---

## 9.3 `ExecutionRunCreateCommand.java`

```java
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
    int timeoutSeconds) {}
```

---

## 9.4 `ExecutionStepCreateCommand.java`

```java
package io.aegisops.execution.dto;

public record ExecutionStepCreateCommand(
    String id,
    String tenantId,
    String executionId,
    String planStepId,
    int sequenceNo,
    String name,
    String actionType,
    String targetType,
    String status,
    String actionPayloadJson,
    String commandSnapshot,
    int attempt,
    int timeoutSeconds) {}
```

---

## 9.5 `ExecutionRunRecord.java`

```java
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
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt) {}
```

---

## 9.6 `ExecutionStepRecord.java`

```java
package io.aegisops.execution.dto;

import java.time.OffsetDateTime;

public record ExecutionStepRecord(
    String id,
    String tenantId,
    String executionId,
    String planStepId,
    int sequenceNo,
    String name,
    String actionType,
    String targetType,
    String status,
    String actionPayloadJson,
    String commandSnapshot,
    String output,
    String errorMessage,
    OffsetDateTime startedAt,
    OffsetDateTime finishedAt,
    int attempt,
    int timeoutSeconds,
    int artifactCount,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt) {}
```

---

## 9.7 `ExecutionArtifactCreateCommand.java`

```java
package io.aegisops.execution.dto;

public record ExecutionArtifactCreateCommand(
    String id,
    String tenantId,
    String executionId,
    String stepId,
    String artifactType,
    String name,
    String content,
    String metadataJson) {}
```

---

## 9.8 `ExecutionArtifactRecord.java`

```java
package io.aegisops.execution.dto;

import java.time.OffsetDateTime;

public record ExecutionArtifactRecord(
    String id,
    String tenantId,
    String executionId,
    String stepId,
    String artifactType,
    String name,
    String content,
    String metadataJson,
    OffsetDateTime createdAt) {}
```

---

## 9.9 `ExecutionArtifactResponse.java`

```java
package io.aegisops.execution.dto;

import java.time.OffsetDateTime;

public record ExecutionArtifactResponse(
    String id,
    String executionId,
    String stepId,
    String artifactType,
    String name,
    String content,
    String metadataJson,
    OffsetDateTime createdAt) {}
```

---

## 9.10 `ExecutionRunResponse.java`

```java
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
    List<ExecutionStepResponse> steps,
    List<ExecutionArtifactResponse> artifacts,
    OffsetDateTime startedAt,
    OffsetDateTime finishedAt,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt) {}
```

---

## 9.11 `ExecutionStepResponse.java`

```java
package io.aegisops.execution.dto;

import java.time.OffsetDateTime;

public record ExecutionStepResponse(
    String id,
    String executionId,
    String planStepId,
    int sequenceNo,
    String name,
    String actionType,
    String targetType,
    String status,
    String output,
    String errorMessage,
    int attempt,
    int timeoutSeconds,
    int artifactCount,
    OffsetDateTime startedAt,
    OffsetDateTime finishedAt,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt) {}
```

---

## 9.12 `ExecutionRunStatusUpdateCommand.java`

```java
package io.aegisops.execution.dto;

import java.time.OffsetDateTime;

public record ExecutionRunStatusUpdateCommand(
    String tenantId,
    String executionId,
    String status,
    String runnerId,
    OffsetDateTime startedAt,
    OffsetDateTime finishedAt,
    String errorMessage,
    String summary) {}
```

---

## 9.13 `ExecutionStepStatusUpdateCommand.java`

```java
package io.aegisops.execution.dto;

import java.time.OffsetDateTime;

public record ExecutionStepStatusUpdateCommand(
    String tenantId,
    String stepId,
    String status,
    OffsetDateTime startedAt,
    OffsetDateTime finishedAt,
    String output,
    String errorMessage) {}
```

---

## 9.14 `PlanForExecutionRecord.java`

```java
package io.aegisops.execution.dto;

public record PlanForExecutionRecord(
    String id,
    String tenantId,
    String incidentId,
    String status,
    String riskLevel,
    String title,
    String summary) {}
```

---

## 9.15 `PlanStepForExecutionRecord.java`

```java
package io.aegisops.execution.dto;

public record PlanStepForExecutionRecord(
    String id,
    String planId,
    int sequenceNo,
    String name,
    String actionType,
    String targetType,
    String actionPayloadJson,
    String description,
    String expectedResult,
    String rollbackHint,
    boolean requiresApproval,
    String status) {}
```

---

## 9.16 `TimelineCreateCommand.java`

```java
package io.aegisops.execution.dto;

import java.time.OffsetDateTime;

public record TimelineCreateCommand(
    String id,
    String incidentId,
    OffsetDateTime eventTime,
    String eventType,
    String title,
    String description,
    String source,
    String payloadJson) {}
```

---

# 10. ExecutionProperties

路径：

```txt
modules/aiops-execution/src/main/java/io/aegisops/execution/ExecutionProperties.java
```

```java
package io.aegisops.execution;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "aiops.execution")
public class ExecutionProperties {
  private boolean apiEnabled = true;
  private boolean liveEnabled = false;
  private int leaseSeconds = 60;
  private int runTimeoutSeconds = 1800;
  private int stepTimeoutSeconds = 300;
  private int maxRetryAttempts = 3;

  public boolean isApiEnabled() {
    return apiEnabled;
  }

  public void setApiEnabled(boolean apiEnabled) {
    this.apiEnabled = apiEnabled;
  }

  public boolean isLiveEnabled() {
    return liveEnabled;
  }

  public void setLiveEnabled(boolean liveEnabled) {
    this.liveEnabled = liveEnabled;
  }

  public int getLeaseSeconds() {
    return leaseSeconds;
  }

  public void setLeaseSeconds(int leaseSeconds) {
    this.leaseSeconds = leaseSeconds;
  }

  public int getRunTimeoutSeconds() {
    return runTimeoutSeconds;
  }

  public void setRunTimeoutSeconds(int runTimeoutSeconds) {
    this.runTimeoutSeconds = runTimeoutSeconds;
  }

  public int getStepTimeoutSeconds() {
    return stepTimeoutSeconds;
  }

  public void setStepTimeoutSeconds(int stepTimeoutSeconds) {
    this.stepTimeoutSeconds = stepTimeoutSeconds;
  }

  public int getMaxRetryAttempts() {
    return maxRetryAttempts;
  }

  public void setMaxRetryAttempts(int maxRetryAttempts) {
    this.maxRetryAttempts = maxRetryAttempts;
  }

  public int normalizedLeaseSeconds() {
    return Math.max(10, Math.min(leaseSeconds, 3600));
  }

  public int normalizedRunTimeoutSeconds() {
    return Math.max(30, Math.min(runTimeoutSeconds, 86400));
  }

  public int normalizedStepTimeoutSeconds() {
    return Math.max(1, Math.min(stepTimeoutSeconds, 86400));
  }

  public int normalizedMaxRetryAttempts() {
    return Math.max(1, Math.min(maxRetryAttempts, 5));
  }
}
```

---

# 11. ExecutionRepository

路径：

```txt
modules/aiops-execution/src/main/java/io/aegisops/execution/ExecutionRepository.java
```

```java
package io.aegisops.execution;

import io.aegisops.execution.dto.ExecutionArtifactCreateCommand;
import io.aegisops.execution.dto.ExecutionArtifactRecord;
import io.aegisops.execution.dto.ExecutionRunCreateCommand;
import io.aegisops.execution.dto.ExecutionRunRecord;
import io.aegisops.execution.dto.ExecutionRunStatusUpdateCommand;
import io.aegisops.execution.dto.ExecutionStepCreateCommand;
import io.aegisops.execution.dto.ExecutionStepRecord;
import io.aegisops.execution.dto.ExecutionStepStatusUpdateCommand;
import io.aegisops.execution.dto.PlanForExecutionRecord;
import io.aegisops.execution.dto.PlanStepForExecutionRecord;
import io.aegisops.execution.dto.TimelineCreateCommand;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

public interface ExecutionRepository {
  Optional<PlanForExecutionRecord> findPlan(String tenantId, String planId);

  List<PlanStepForExecutionRecord> listPlanSteps(String planId);

  Optional<ExecutionRunRecord> findLatestRunByPlan(String tenantId, String planId);

  Optional<ExecutionRunRecord> findRun(String tenantId, String executionId);

  List<ExecutionStepRecord> listExecutionSteps(String tenantId, String executionId);

  List<ExecutionArtifactRecord> listArtifacts(String tenantId, String executionId);

  void createRun(ExecutionRunCreateCommand command);

  void createSteps(List<ExecutionStepCreateCommand> commands);

  void createArtifact(ExecutionArtifactCreateCommand command);

  boolean incrementStepArtifactCount(String tenantId, String stepId);

  boolean updatePlanStatus(String tenantId, String planId, String status);

  boolean cancelRun(String tenantId, String executionId);

  boolean cancelExecutionSteps(String tenantId, String executionId);

  Optional<ExecutionRunRecord> claimNextQueuedRun(
      String runnerId, OffsetDateTime now, OffsetDateTime leaseUntil);

  boolean heartbeat(
      String tenantId,
      String executionId,
      String runnerId,
      OffsetDateTime heartbeatAt,
      OffsetDateTime leaseUntil);

  List<ExecutionRunRecord> findExpiredRunningRuns(OffsetDateTime now, int limit);

  boolean timeoutRun(String tenantId, String executionId, String errorMessage);

  boolean timeoutExecutionSteps(String tenantId, String executionId);

  boolean updateRunStatus(ExecutionRunStatusUpdateCommand command);

  boolean updateStepStatus(ExecutionStepStatusUpdateCommand command);

  void addTimeline(TimelineCreateCommand command);
}
```

---

# 12. JooqExecutionRepository

路径：

```txt
modules/aiops-execution/src/main/java/io/aegisops/execution/JooqExecutionRepository.java
```

> 下面是 Phase5.3 完整替换版。

```java
package io.aegisops.execution;

import static io.aegisops.persistence.AegisJooq.jsonbValue;
import static io.aegisops.persistence.jooq.Tables.AUTOMATION_PLAN;
import static io.aegisops.persistence.jooq.Tables.AUTOMATION_PLAN_STEP;
import static io.aegisops.persistence.jooq.Tables.EXECUTION_ARTIFACT;
import static io.aegisops.persistence.jooq.Tables.EXECUTION_RUN;
import static io.aegisops.persistence.jooq.Tables.EXECUTION_STEP;
import static io.aegisops.persistence.jooq.Tables.INCIDENT_TIMELINE;

import io.aegisops.execution.dto.ExecutionArtifactCreateCommand;
import io.aegisops.execution.dto.ExecutionArtifactRecord;
import io.aegisops.execution.dto.ExecutionRunCreateCommand;
import io.aegisops.execution.dto.ExecutionRunRecord;
import io.aegisops.execution.dto.ExecutionRunStatusUpdateCommand;
import io.aegisops.execution.dto.ExecutionStepCreateCommand;
import io.aegisops.execution.dto.ExecutionStepRecord;
import io.aegisops.execution.dto.ExecutionStepStatusUpdateCommand;
import io.aegisops.execution.dto.PlanForExecutionRecord;
import io.aegisops.execution.dto.PlanStepForExecutionRecord;
import io.aegisops.execution.dto.TimelineCreateCommand;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.jooq.DSLContext;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Repository;

@Repository
public class JooqExecutionRepository implements ExecutionRepository {
  private final DSLContext dsl;

  public JooqExecutionRepository(DSLContext dsl) {
    this.dsl = dsl;
  }

  @Override
  public Optional<PlanForExecutionRecord> findPlan(String tenantId, String planId) {
    return dsl.select(
            AUTOMATION_PLAN.ID,
            AUTOMATION_PLAN.TENANT_ID,
            AUTOMATION_PLAN.INCIDENT_ID,
            AUTOMATION_PLAN.STATUS,
            AUTOMATION_PLAN.RISK_LEVEL,
            AUTOMATION_PLAN.TITLE,
            AUTOMATION_PLAN.SUMMARY)
        .from(AUTOMATION_PLAN)
        .where(AUTOMATION_PLAN.TENANT_ID.eq(tenantId))
        .and(AUTOMATION_PLAN.ID.eq(planId))
        .fetchOptional(
            record ->
                new PlanForExecutionRecord(
                    record.get(AUTOMATION_PLAN.ID),
                    record.get(AUTOMATION_PLAN.TENANT_ID),
                    record.get(AUTOMATION_PLAN.INCIDENT_ID),
                    record.get(AUTOMATION_PLAN.STATUS),
                    record.get(AUTOMATION_PLAN.RISK_LEVEL),
                    record.get(AUTOMATION_PLAN.TITLE),
                    record.get(AUTOMATION_PLAN.SUMMARY)));
  }

  @Override
  public List<PlanStepForExecutionRecord> listPlanSteps(String planId) {
    return dsl.select(
            AUTOMATION_PLAN_STEP.ID,
            AUTOMATION_PLAN_STEP.PLAN_ID,
            AUTOMATION_PLAN_STEP.SEQUENCE_NO,
            AUTOMATION_PLAN_STEP.NAME,
            AUTOMATION_PLAN_STEP.ACTION_TYPE,
            AUTOMATION_PLAN_STEP.TARGET_TYPE,
            AUTOMATION_PLAN_STEP.ACTION_PAYLOAD.cast(String.class).as("action_payload_json"),
            AUTOMATION_PLAN_STEP.DESCRIPTION,
            AUTOMATION_PLAN_STEP.EXPECTED_RESULT,
            AUTOMATION_PLAN_STEP.ROLLBACK_HINT,
            AUTOMATION_PLAN_STEP.REQUIRES_APPROVAL,
            AUTOMATION_PLAN_STEP.STATUS)
        .from(AUTOMATION_PLAN_STEP)
        .where(AUTOMATION_PLAN_STEP.PLAN_ID.eq(planId))
        .orderBy(AUTOMATION_PLAN_STEP.SEQUENCE_NO.asc())
        .fetch(
            record ->
                new PlanStepForExecutionRecord(
                    record.get(AUTOMATION_PLAN_STEP.ID),
                    record.get(AUTOMATION_PLAN_STEP.PLAN_ID),
                    value(record.get(AUTOMATION_PLAN_STEP.SEQUENCE_NO)),
                    record.get(AUTOMATION_PLAN_STEP.NAME),
                    record.get(AUTOMATION_PLAN_STEP.ACTION_TYPE),
                    record.get(AUTOMATION_PLAN_STEP.TARGET_TYPE),
                    record.get("action_payload_json", String.class),
                    record.get(AUTOMATION_PLAN_STEP.DESCRIPTION),
                    record.get(AUTOMATION_PLAN_STEP.EXPECTED_RESULT),
                    record.get(AUTOMATION_PLAN_STEP.ROLLBACK_HINT),
                    Boolean.TRUE.equals(record.get(AUTOMATION_PLAN_STEP.REQUIRES_APPROVAL)),
                    record.get(AUTOMATION_PLAN_STEP.STATUS)));
  }

  @Override
  public Optional<ExecutionRunRecord> findLatestRunByPlan(String tenantId, String planId) {
    return selectRun(dsl)
        .where(EXECUTION_RUN.TENANT_ID.eq(tenantId))
        .and(EXECUTION_RUN.PLAN_ID.eq(planId))
        .orderBy(EXECUTION_RUN.CREATED_AT.desc())
        .limit(1)
        .fetchOptional(this::toRunRecord);
  }

  @Override
  public Optional<ExecutionRunRecord> findRun(String tenantId, String executionId) {
    return selectRun(dsl)
        .where(EXECUTION_RUN.TENANT_ID.eq(tenantId))
        .and(EXECUTION_RUN.ID.eq(executionId))
        .fetchOptional(this::toRunRecord);
  }

  @Override
  public List<ExecutionStepRecord> listExecutionSteps(String tenantId, String executionId) {
    return dsl.select(
            EXECUTION_STEP.ID,
            EXECUTION_STEP.TENANT_ID,
            EXECUTION_STEP.EXECUTION_ID,
            EXECUTION_STEP.PLAN_STEP_ID,
            EXECUTION_STEP.SEQUENCE_NO,
            EXECUTION_STEP.NAME,
            EXECUTION_STEP.ACTION_TYPE,
            EXECUTION_STEP.TARGET_TYPE,
            EXECUTION_STEP.STATUS,
            EXECUTION_STEP.ACTION_PAYLOAD.cast(String.class).as("action_payload_json"),
            EXECUTION_STEP.COMMAND_SNAPSHOT,
            EXECUTION_STEP.OUTPUT,
            EXECUTION_STEP.ERROR_MESSAGE,
            EXECUTION_STEP.STARTED_AT,
            EXECUTION_STEP.FINISHED_AT,
            EXECUTION_STEP.ATTEMPT,
            EXECUTION_STEP.TIMEOUT_SECONDS,
            EXECUTION_STEP.ARTIFACT_COUNT,
            EXECUTION_STEP.CREATED_AT,
            EXECUTION_STEP.UPDATED_AT)
        .from(EXECUTION_STEP)
        .where(EXECUTION_STEP.TENANT_ID.eq(tenantId))
        .and(EXECUTION_STEP.EXECUTION_ID.eq(executionId))
        .orderBy(EXECUTION_STEP.SEQUENCE_NO.asc())
        .fetch(this::toStepRecord);
  }

  @Override
  public List<ExecutionArtifactRecord> listArtifacts(String tenantId, String executionId) {
    return dsl.select(
            EXECUTION_ARTIFACT.ID,
            EXECUTION_ARTIFACT.TENANT_ID,
            EXECUTION_ARTIFACT.EXECUTION_ID,
            EXECUTION_ARTIFACT.STEP_ID,
            EXECUTION_ARTIFACT.ARTIFACT_TYPE,
            EXECUTION_ARTIFACT.NAME,
            EXECUTION_ARTIFACT.CONTENT,
            EXECUTION_ARTIFACT.METADATA.cast(String.class).as("metadata_json"),
            EXECUTION_ARTIFACT.CREATED_AT)
        .from(EXECUTION_ARTIFACT)
        .where(EXECUTION_ARTIFACT.TENANT_ID.eq(tenantId))
        .and(EXECUTION_ARTIFACT.EXECUTION_ID.eq(executionId))
        .orderBy(EXECUTION_ARTIFACT.CREATED_AT.asc())
        .fetch(
            record ->
                new ExecutionArtifactRecord(
                    record.get(EXECUTION_ARTIFACT.ID),
                    record.get(EXECUTION_ARTIFACT.TENANT_ID),
                    record.get(EXECUTION_ARTIFACT.EXECUTION_ID),
                    record.get(EXECUTION_ARTIFACT.STEP_ID),
                    record.get(EXECUTION_ARTIFACT.ARTIFACT_TYPE),
                    record.get(EXECUTION_ARTIFACT.NAME),
                    record.get(EXECUTION_ARTIFACT.CONTENT),
                    record.get("metadata_json", String.class),
                    record.get(EXECUTION_ARTIFACT.CREATED_AT)));
  }

  @Override
  public void createRun(ExecutionRunCreateCommand command) {
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
        .set(EXECUTION_RUN.CREATED_AT, DSL.currentOffsetDateTime())
        .set(EXECUTION_RUN.UPDATED_AT, DSL.currentOffsetDateTime())
        .execute();
  }

  @Override
  public void createSteps(List<ExecutionStepCreateCommand> commands) {
    for (ExecutionStepCreateCommand command : commands) {
      dsl.insertInto(EXECUTION_STEP)
          .set(EXECUTION_STEP.ID, command.id())
          .set(EXECUTION_STEP.TENANT_ID, command.tenantId())
          .set(EXECUTION_STEP.EXECUTION_ID, command.executionId())
          .set(EXECUTION_STEP.PLAN_STEP_ID, command.planStepId())
          .set(EXECUTION_STEP.SEQUENCE_NO, command.sequenceNo())
          .set(EXECUTION_STEP.NAME, command.name())
          .set(EXECUTION_STEP.ACTION_TYPE, command.actionType())
          .set(EXECUTION_STEP.TARGET_TYPE, command.targetType())
          .set(EXECUTION_STEP.STATUS, command.status())
          .set(EXECUTION_STEP.ACTION_PAYLOAD, jsonbValue(command.actionPayloadJson()))
          .set(EXECUTION_STEP.COMMAND_SNAPSHOT, command.commandSnapshot())
          .set(EXECUTION_STEP.ATTEMPT, command.attempt())
          .set(EXECUTION_STEP.TIMEOUT_SECONDS, command.timeoutSeconds())
          .set(EXECUTION_STEP.CREATED_AT, DSL.currentOffsetDateTime())
          .set(EXECUTION_STEP.UPDATED_AT, DSL.currentOffsetDateTime())
          .execute();
    }
  }

  @Override
  public void createArtifact(ExecutionArtifactCreateCommand command) {
    dsl.insertInto(EXECUTION_ARTIFACT)
        .set(EXECUTION_ARTIFACT.ID, command.id())
        .set(EXECUTION_ARTIFACT.TENANT_ID, command.tenantId())
        .set(EXECUTION_ARTIFACT.EXECUTION_ID, command.executionId())
        .set(EXECUTION_ARTIFACT.STEP_ID, command.stepId())
        .set(EXECUTION_ARTIFACT.ARTIFACT_TYPE, command.artifactType())
        .set(EXECUTION_ARTIFACT.NAME, command.name())
        .set(EXECUTION_ARTIFACT.CONTENT, command.content())
        .set(EXECUTION_ARTIFACT.METADATA, jsonbValue(command.metadataJson()))
        .set(EXECUTION_ARTIFACT.CREATED_AT, DSL.currentOffsetDateTime())
        .execute();
  }

  @Override
  public boolean incrementStepArtifactCount(String tenantId, String stepId) {
    return dsl.update(EXECUTION_STEP)
            .set(EXECUTION_STEP.ARTIFACT_COUNT, EXECUTION_STEP.ARTIFACT_COUNT.plus(1))
            .set(EXECUTION_STEP.UPDATED_AT, DSL.currentOffsetDateTime())
            .where(EXECUTION_STEP.TENANT_ID.eq(tenantId))
            .and(EXECUTION_STEP.ID.eq(stepId))
            .execute()
        > 0;
  }

  @Override
  public boolean updatePlanStatus(String tenantId, String planId, String status) {
    return dsl.update(AUTOMATION_PLAN)
            .set(AUTOMATION_PLAN.STATUS, status)
            .set(AUTOMATION_PLAN.UPDATED_AT, DSL.currentOffsetDateTime())
            .where(AUTOMATION_PLAN.TENANT_ID.eq(tenantId))
            .and(AUTOMATION_PLAN.ID.eq(planId))
            .execute()
        > 0;
  }

  @Override
  public boolean cancelRun(String tenantId, String executionId) {
    return dsl.update(EXECUTION_RUN)
            .set(EXECUTION_RUN.STATUS, "cancelled")
            .set(EXECUTION_RUN.FINISHED_AT, DSL.currentOffsetDateTime())
            .set(EXECUTION_RUN.UPDATED_AT, DSL.currentOffsetDateTime())
            .where(EXECUTION_RUN.TENANT_ID.eq(tenantId))
            .and(EXECUTION_RUN.ID.eq(executionId))
            .and(EXECUTION_RUN.STATUS.in("queued", "running"))
            .execute()
        > 0;
  }

  @Override
  public boolean cancelExecutionSteps(String tenantId, String executionId) {
    dsl.update(EXECUTION_STEP)
        .set(EXECUTION_STEP.STATUS, "cancelled")
        .set(EXECUTION_STEP.FINISHED_AT, DSL.currentOffsetDateTime())
        .set(EXECUTION_STEP.ERROR_MESSAGE, "Execution was cancelled.")
        .set(EXECUTION_STEP.UPDATED_AT, DSL.currentOffsetDateTime())
        .where(EXECUTION_STEP.TENANT_ID.eq(tenantId))
        .and(EXECUTION_STEP.EXECUTION_ID.eq(executionId))
        .and(EXECUTION_STEP.STATUS.in("queued", "running"))
        .execute();

    return true;
  }

  @Override
  public Optional<ExecutionRunRecord> claimNextQueuedRun(
      String runnerId, OffsetDateTime now, OffsetDateTime leaseUntil) {
    return dsl.transactionResult(
        config -> {
          DSLContext tx = DSL.using(config);

          Optional<String> id =
              tx.select(EXECUTION_RUN.ID)
                  .from(EXECUTION_RUN)
                  .where(EXECUTION_RUN.STATUS.eq("queued"))
                  .orderBy(EXECUTION_RUN.CREATED_AT.asc())
                  .limit(1)
                  .forUpdate()
                  .skipLocked()
                  .fetchOptional(EXECUTION_RUN.ID);

          if (id.isEmpty()) {
            return Optional.empty();
          }

          int updated =
              tx.update(EXECUTION_RUN)
                  .set(EXECUTION_RUN.STATUS, "running")
                  .set(EXECUTION_RUN.RUNNER_ID, runnerId)
                  .set(EXECUTION_RUN.STARTED_AT, now)
                  .set(EXECUTION_RUN.HEARTBEAT_AT, now)
                  .set(EXECUTION_RUN.LEASE_UNTIL, leaseUntil)
                  .set(EXECUTION_RUN.UPDATED_AT, DSL.currentOffsetDateTime())
                  .where(EXECUTION_RUN.ID.eq(id.get()))
                  .and(EXECUTION_RUN.STATUS.eq("queued"))
                  .execute();

          if (updated == 0) {
            return Optional.empty();
          }

          return selectRun(tx)
              .where(EXECUTION_RUN.ID.eq(id.get()))
              .fetchOptional(this::toRunRecord);
        });
  }

  @Override
  public boolean heartbeat(
      String tenantId,
      String executionId,
      String runnerId,
      OffsetDateTime heartbeatAt,
      OffsetDateTime leaseUntil) {
    return dsl.update(EXECUTION_RUN)
            .set(EXECUTION_RUN.HEARTBEAT_AT, heartbeatAt)
            .set(EXECUTION_RUN.LEASE_UNTIL, leaseUntil)
            .set(EXECUTION_RUN.UPDATED_AT, DSL.currentOffsetDateTime())
            .where(EXECUTION_RUN.TENANT_ID.eq(tenantId))
            .and(EXECUTION_RUN.ID.eq(executionId))
            .and(EXECUTION_RUN.RUNNER_ID.eq(runnerId))
            .and(EXECUTION_RUN.STATUS.eq("running"))
            .execute()
        > 0;
  }

  @Override
  public List<ExecutionRunRecord> findExpiredRunningRuns(OffsetDateTime now, int limit) {
    return selectRun(dsl)
        .where(EXECUTION_RUN.STATUS.eq("running"))
        .and(EXECUTION_RUN.LEASE_UNTIL.lt(now))
        .orderBy(EXECUTION_RUN.LEASE_UNTIL.asc())
        .limit(limit)
        .fetch(this::toRunRecord);
  }

  @Override
  public boolean timeoutRun(String tenantId, String executionId, String errorMessage) {
    return dsl.update(EXECUTION_RUN)
            .set(EXECUTION_RUN.STATUS, "timeout")
            .set(EXECUTION_RUN.FINISHED_AT, DSL.currentOffsetDateTime())
            .set(EXECUTION_RUN.ERROR_MESSAGE, errorMessage)
            .set(EXECUTION_RUN.SUMMARY, "Execution timed out.")
            .set(EXECUTION_RUN.UPDATED_AT, DSL.currentOffsetDateTime())
            .where(EXECUTION_RUN.TENANT_ID.eq(tenantId))
            .and(EXECUTION_RUN.ID.eq(executionId))
            .and(EXECUTION_RUN.STATUS.eq("running"))
            .execute()
        > 0;
  }

  @Override
  public boolean timeoutExecutionSteps(String tenantId, String executionId) {
    dsl.update(EXECUTION_STEP)
        .set(EXECUTION_STEP.STATUS, "timeout")
        .set(EXECUTION_STEP.FINISHED_AT, DSL.currentOffsetDateTime())
        .set(EXECUTION_STEP.ERROR_MESSAGE, "Execution lease timed out.")
        .set(EXECUTION_STEP.UPDATED_AT, DSL.currentOffsetDateTime())
        .where(EXECUTION_STEP.TENANT_ID.eq(tenantId))
        .and(EXECUTION_STEP.EXECUTION_ID.eq(executionId))
        .and(EXECUTION_STEP.STATUS.in("queued", "running"))
        .execute();

    return true;
  }

  @Override
  public boolean updateRunStatus(ExecutionRunStatusUpdateCommand command) {
    return dsl.update(EXECUTION_RUN)
            .set(EXECUTION_RUN.STATUS, command.status())
            .set(EXECUTION_RUN.RUNNER_ID, command.runnerId())
            .set(EXECUTION_RUN.STARTED_AT, command.startedAt())
            .set(EXECUTION_RUN.FINISHED_AT, command.finishedAt())
            .set(EXECUTION_RUN.ERROR_MESSAGE, command.errorMessage())
            .set(EXECUTION_RUN.SUMMARY, command.summary())
            .set(EXECUTION_RUN.UPDATED_AT, DSL.currentOffsetDateTime())
            .where(EXECUTION_RUN.TENANT_ID.eq(command.tenantId()))
            .and(EXECUTION_RUN.ID.eq(command.executionId()))
            .execute()
        > 0;
  }

  @Override
  public boolean updateStepStatus(ExecutionStepStatusUpdateCommand command) {
    return dsl.update(EXECUTION_STEP)
            .set(EXECUTION_STEP.STATUS, command.status())
            .set(EXECUTION_STEP.STARTED_AT, command.startedAt())
            .set(EXECUTION_STEP.FINISHED_AT, command.finishedAt())
            .set(EXECUTION_STEP.OUTPUT, command.output())
            .set(EXECUTION_STEP.ERROR_MESSAGE, command.errorMessage())
            .set(EXECUTION_STEP.UPDATED_AT, DSL.currentOffsetDateTime())
            .where(EXECUTION_STEP.TENANT_ID.eq(command.tenantId()))
            .and(EXECUTION_STEP.ID.eq(command.stepId()))
            .execute()
        > 0;
  }

  @Override
  public void addTimeline(TimelineCreateCommand command) {
    dsl.insertInto(INCIDENT_TIMELINE)
        .set(INCIDENT_TIMELINE.ID, command.id())
        .set(INCIDENT_TIMELINE.INCIDENT_ID, command.incidentId())
        .set(INCIDENT_TIMELINE.EVENT_TIME, command.eventTime())
        .set(INCIDENT_TIMELINE.EVENT_TYPE, command.eventType())
        .set(INCIDENT_TIMELINE.TITLE, command.title())
        .set(INCIDENT_TIMELINE.DESCRIPTION, command.description())
        .set(INCIDENT_TIMELINE.SOURCE, command.source())
        .set(INCIDENT_TIMELINE.PAYLOAD, jsonbValue(command.payloadJson()))
        .execute();
  }

  private org.jooq.SelectJoinStep<?> selectRun(DSLContext context) {
    return context
        .select(
            EXECUTION_RUN.ID,
            EXECUTION_RUN.TENANT_ID,
            EXECUTION_RUN.INCIDENT_ID,
            EXECUTION_RUN.PLAN_ID,
            EXECUTION_RUN.STATUS,
            EXECUTION_RUN.MODE,
            EXECUTION_RUN.REQUESTED_BY,
            EXECUTION_RUN.RUNNER_ID,
            EXECUTION_RUN.STARTED_AT,
            EXECUTION_RUN.FINISHED_AT,
            EXECUTION_RUN.ERROR_MESSAGE,
            EXECUTION_RUN.SUMMARY,
            EXECUTION_RUN.ATTEMPT,
            EXECUTION_RUN.MAX_ATTEMPTS,
            EXECUTION_RUN.RETRY_OF_EXECUTION_ID,
            EXECUTION_RUN.LEASE_UNTIL,
            EXECUTION_RUN.HEARTBEAT_AT,
            EXECUTION_RUN.TIMEOUT_SECONDS,
            EXECUTION_RUN.CREATED_AT,
            EXECUTION_RUN.UPDATED_AT)
        .from(EXECUTION_RUN);
  }

  private ExecutionRunRecord toRunRecord(org.jooq.Record record) {
    return new ExecutionRunRecord(
        record.get(EXECUTION_RUN.ID),
        record.get(EXECUTION_RUN.TENANT_ID),
        record.get(EXECUTION_RUN.INCIDENT_ID),
        record.get(EXECUTION_RUN.PLAN_ID),
        record.get(EXECUTION_RUN.STATUS),
        record.get(EXECUTION_RUN.MODE),
        record.get(EXECUTION_RUN.REQUESTED_BY),
        record.get(EXECUTION_RUN.RUNNER_ID),
        record.get(EXECUTION_RUN.STARTED_AT),
        record.get(EXECUTION_RUN.FINISHED_AT),
        record.get(EXECUTION_RUN.ERROR_MESSAGE),
        record.get(EXECUTION_RUN.SUMMARY),
        value(record.get(EXECUTION_RUN.ATTEMPT)),
        value(record.get(EXECUTION_RUN.MAX_ATTEMPTS)),
        record.get(EXECUTION_RUN.RETRY_OF_EXECUTION_ID),
        record.get(EXECUTION_RUN.LEASE_UNTIL),
        record.get(EXECUTION_RUN.HEARTBEAT_AT),
        value(record.get(EXECUTION_RUN.TIMEOUT_SECONDS)),
        record.get(EXECUTION_RUN.CREATED_AT),
        record.get(EXECUTION_RUN.UPDATED_AT));
  }

  private ExecutionStepRecord toStepRecord(org.jooq.Record record) {
    return new ExecutionStepRecord(
        record.get(EXECUTION_STEP.ID),
        record.get(EXECUTION_STEP.TENANT_ID),
        record.get(EXECUTION_STEP.EXECUTION_ID),
        record.get(EXECUTION_STEP.PLAN_STEP_ID),
        value(record.get(EXECUTION_STEP.SEQUENCE_NO)),
        record.get(EXECUTION_STEP.NAME),
        record.get(EXECUTION_STEP.ACTION_TYPE),
        record.get(EXECUTION_STEP.TARGET_TYPE),
        record.get(EXECUTION_STEP.STATUS),
        record.get("action_payload_json", String.class),
        record.get(EXECUTION_STEP.COMMAND_SNAPSHOT),
        record.get(EXECUTION_STEP.OUTPUT),
        record.get(EXECUTION_STEP.ERROR_MESSAGE),
        record.get(EXECUTION_STEP.STARTED_AT),
        record.get(EXECUTION_STEP.FINISHED_AT),
        value(record.get(EXECUTION_STEP.ATTEMPT)),
        value(record.get(EXECUTION_STEP.TIMEOUT_SECONDS)),
        value(record.get(EXECUTION_STEP.ARTIFACT_COUNT)),
        record.get(EXECUTION_STEP.CREATED_AT),
        record.get(EXECUTION_STEP.UPDATED_AT));
  }

  private int value(Integer value) {
    return value == null ? 0 : value;
  }
}
```

---

# 13. ExecutionRequestService

路径：

```txt
modules/aiops-execution/src/main/java/io/aegisops/execution/ExecutionRequestService.java
```

```java
package io.aegisops.execution;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.aegisops.common.exception.AppException;
import io.aegisops.execution.dto.ExecutionArtifactRecord;
import io.aegisops.execution.dto.ExecutionArtifactResponse;
import io.aegisops.execution.dto.ExecutionCreateRequest;
import io.aegisops.execution.dto.ExecutionRetryRequest;
import io.aegisops.execution.dto.ExecutionRunCreateCommand;
import io.aegisops.execution.dto.ExecutionRunRecord;
import io.aegisops.execution.dto.ExecutionRunResponse;
import io.aegisops.execution.dto.ExecutionStepCreateCommand;
import io.aegisops.execution.dto.ExecutionStepRecord;
import io.aegisops.execution.dto.ExecutionStepResponse;
import io.aegisops.execution.dto.PlanForExecutionRecord;
import io.aegisops.execution.dto.PlanStepForExecutionRecord;
import io.aegisops.execution.dto.TimelineCreateCommand;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ExecutionRequestService {
  private final ExecutionRepository repository;
  private final ExecutionProperties properties;
  private final ExecutionJson json;

  public ExecutionRequestService(
      ExecutionRepository repository,
      ExecutionProperties properties,
      ObjectMapper objectMapper) {
    this.repository = repository;
    this.properties = properties;
    this.json = new ExecutionJson(objectMapper);
  }

  @Transactional
  public ExecutionRunResponse createExecution(
      String tenantId, String planId, ExecutionCreateRequest request) {
    ExecutionCreateRequest normalized =
        request == null ? new ExecutionCreateRequest(true, "system", 1) : request;

    PlanForExecutionRecord plan = loadPlan(tenantId, planId);

    if (!"approved".equals(plan.status())) {
      throw new AppException(
          "AUTOMATION_PLAN_NOT_APPROVED", "Only approved automation plan can be executed");
    }

    if (!normalized.dryRunEnabled() && !properties.isLiveEnabled()) {
      throw new AppException(
          "LIVE_EXECUTION_DISABLED", "Live execution is disabled by configuration");
    }

    var active = repository.findLatestRunByPlan(tenantId, planId);
    if (active.isPresent() && List.of("queued", "running").contains(active.get().status())) {
      return toResponse(
          active.get(),
          repository.listExecutionSteps(tenantId, active.get().id()),
          repository.listArtifacts(tenantId, active.get().id()));
    }

    int maxAttempts =
        Math.min(normalized.normalizedMaxAttempts(), properties.normalizedMaxRetryAttempts());

    return createQueuedRun(
        tenantId,
        plan,
        normalized.dryRunEnabled() ? "dry_run" : "live",
        blankToDefault(normalized.requestedBy(), "system"),
        1,
        maxAttempts,
        null,
        "execution_queued",
        "Execution queued",
        "Automation execution was queued for runner.");
  }

  @Transactional
  public ExecutionRunResponse retry(
      String tenantId, String executionId, ExecutionRetryRequest request) {
    ExecutionRunRecord previous =
        repository
            .findRun(tenantId, executionId)
            .orElseThrow(() -> new AppException("EXECUTION_NOT_FOUND", "Execution not found"));

    if (!List.of("failed", "timeout").contains(previous.status())) {
      throw new AppException("EXECUTION_RETRY_STATUS_INVALID", "Only failed or timeout execution can be retried");
    }

    if (previous.attempt() >= previous.maxAttempts()) {
      throw new AppException("EXECUTION_RETRY_EXHAUSTED", "Execution retry attempts exhausted");
    }

    PlanForExecutionRecord plan = loadPlan(tenantId, previous.planId());

    return createQueuedRun(
        tenantId,
        plan,
        previous.mode(),
        blankToDefault(request == null ? null : request.requestedBy(), previous.requestedBy()),
        previous.attempt() + 1,
        previous.maxAttempts(),
        previous.id(),
        "execution_retried",
        "Execution retried",
        "Automation execution retry was queued.");
  }

  public ExecutionRunResponse getExecution(String tenantId, String executionId) {
    ExecutionRunRecord run =
        repository
            .findRun(tenantId, executionId)
            .orElseThrow(() -> new AppException("EXECUTION_NOT_FOUND", "Execution not found"));

    return toResponse(
        run,
        repository.listExecutionSteps(tenantId, executionId),
        repository.listArtifacts(tenantId, executionId));
  }

  public ExecutionRunResponse latestByPlan(String tenantId, String planId) {
    ExecutionRunRecord run =
        repository
            .findLatestRunByPlan(tenantId, planId)
            .orElseThrow(() -> new AppException("EXECUTION_NOT_FOUND", "Execution not found"));

    return toResponse(
        run,
        repository.listExecutionSteps(tenantId, run.id()),
        repository.listArtifacts(tenantId, run.id()));
  }

  @Transactional
  public ExecutionRunResponse cancel(String tenantId, String executionId) {
    ExecutionRunRecord run =
        repository
            .findRun(tenantId, executionId)
            .orElseThrow(() -> new AppException("EXECUTION_NOT_FOUND", "Execution not found"));

    if (!List.of("queued", "running").contains(run.status())) {
      throw new AppException("EXECUTION_STATUS_INVALID", "Only queued or running execution can be cancelled");
    }

    ensureUpdated(
        repository.cancelRun(tenantId, executionId),
        "EXECUTION_CANCEL_FAILED",
        "Execution was not cancelled");

    ensureUpdated(
        repository.cancelExecutionSteps(tenantId, executionId),
        "EXECUTION_STEP_CANCEL_FAILED",
        "Execution steps were not cancelled");

    ensureUpdated(
        repository.updatePlanStatus(tenantId, run.planId(), "cancelled"),
        "AUTOMATION_PLAN_UPDATE_FAILED",
        "Automation plan status was not updated");

    repository.addTimeline(
        timeline(
            run.incidentId(),
            "execution_cancelled",
            "Execution cancelled",
            "Automation execution was cancelled.",
            Map.of("executionId", executionId, "planId", run.planId())));

    return getExecution(tenantId, executionId);
  }

  private ExecutionRunResponse createQueuedRun(
      String tenantId,
      PlanForExecutionRecord plan,
      String mode,
      String requestedBy,
      int attempt,
      int maxAttempts,
      String retryOfExecutionId,
      String eventType,
      String title,
      String description) {
    List<PlanStepForExecutionRecord> planSteps = repository.listPlanSteps(plan.id());
    if (planSteps.isEmpty()) {
      throw new AppException("AUTOMATION_PLAN_STEP_EMPTY", "Automation plan has no steps");
    }

    String executionId = newId("exec");

    repository.createRun(
        new ExecutionRunCreateCommand(
            executionId,
            tenantId,
            plan.incidentId(),
            plan.id(),
            "queued",
            mode,
            requestedBy,
            attempt,
            maxAttempts,
            retryOfExecutionId,
            properties.normalizedRunTimeoutSeconds()));

    repository.createSteps(
        planSteps.stream()
            .map(step -> toExecutionStep(tenantId, executionId, step, attempt))
            .toList());

    ensureUpdated(
        repository.updatePlanStatus(tenantId, plan.id(), "executing"),
        "AUTOMATION_PLAN_UPDATE_FAILED",
        "Automation plan status was not updated");

    repository.addTimeline(
        timeline(
            plan.incidentId(),
            eventType,
            title,
            description,
            Map.of(
                "executionId", executionId,
                "planId", plan.id(),
                "mode", mode,
                "requestedBy", requestedBy,
                "attempt", attempt,
                "maxAttempts", maxAttempts)));

    ExecutionRunRecord saved =
        repository
            .findRun(tenantId, executionId)
            .orElseThrow(() -> new AppException("EXECUTION_NOT_FOUND", "Execution not found"));

    return toResponse(
        saved,
        repository.listExecutionSteps(tenantId, executionId),
        repository.listArtifacts(tenantId, executionId));
  }

  private ExecutionStepCreateCommand toExecutionStep(
      String tenantId, String executionId, PlanStepForExecutionRecord step, int attempt) {
    String command = json.textValue(step.actionPayloadJson(), "command");

    return new ExecutionStepCreateCommand(
        newId("execstep"),
        tenantId,
        executionId,
        step.id(),
        step.sequenceNo(),
        step.name(),
        step.actionType(),
        step.targetType(),
        "queued",
        step.actionPayloadJson(),
        command,
        attempt,
        properties.normalizedStepTimeoutSeconds());
  }

  private PlanForExecutionRecord loadPlan(String tenantId, String planId) {
    return repository
        .findPlan(tenantId, planId)
        .orElseThrow(() -> new AppException("AUTOMATION_PLAN_NOT_FOUND", "Automation plan not found"));
  }

  private ExecutionRunResponse toResponse(
      ExecutionRunRecord run, List<ExecutionStepRecord> steps, List<ExecutionArtifactRecord> artifacts) {
    return new ExecutionRunResponse(
        run.id(),
        run.tenantId(),
        run.incidentId(),
        run.planId(),
        run.status(),
        run.mode(),
        run.requestedBy(),
        run.runnerId(),
        run.errorMessage(),
        run.summary(),
        run.attempt(),
        run.maxAttempts(),
        run.retryOfExecutionId(),
        run.leaseUntil(),
        run.heartbeatAt(),
        run.timeoutSeconds(),
        steps.stream().map(this::toStepResponse).toList(),
        artifacts.stream().map(this::toArtifactResponse).toList(),
        run.startedAt(),
        run.finishedAt(),
        run.createdAt(),
        run.updatedAt());
  }

  private ExecutionStepResponse toStepResponse(ExecutionStepRecord step) {
    return new ExecutionStepResponse(
        step.id(),
        step.executionId(),
        step.planStepId(),
        step.sequenceNo(),
        step.name(),
        step.actionType(),
        step.targetType(),
        step.status(),
        step.output(),
        step.errorMessage(),
        step.attempt(),
        step.timeoutSeconds(),
        step.artifactCount(),
        step.startedAt(),
        step.finishedAt(),
        step.createdAt(),
        step.updatedAt());
  }

  private ExecutionArtifactResponse toArtifactResponse(ExecutionArtifactRecord artifact) {
    return new ExecutionArtifactResponse(
        artifact.id(),
        artifact.executionId(),
        artifact.stepId(),
        artifact.artifactType(),
        artifact.name(),
        artifact.content(),
        artifact.metadataJson(),
        artifact.createdAt());
  }

  private TimelineCreateCommand timeline(
      String incidentId,
      String eventType,
      String title,
      String description,
      Map<String, Object> payload) {
    return new TimelineCreateCommand(
        newId("tl"),
        incidentId,
        OffsetDateTime.now(),
        eventType,
        title,
        description,
        "system",
        json.write(payload));
  }

  private void ensureUpdated(boolean updated, String code, String message) {
    if (!updated) {
      throw new AppException(code, message);
    }
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

# 14. ExecutionController

路径：

```txt
modules/aiops-execution/src/main/java/io/aegisops/execution/ExecutionController.java
```

```java
package io.aegisops.execution;

import io.aegisops.common.api.ApiResponse;
import io.aegisops.common.tenant.TenantContext;
import io.aegisops.execution.dto.ExecutionCreateRequest;
import io.aegisops.execution.dto.ExecutionRetryRequest;
import io.aegisops.execution.dto.ExecutionRunResponse;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@EnableConfigurationProperties(ExecutionProperties.class)
@ConditionalOnProperty(prefix = "aiops.execution", name = "api-enabled", havingValue = "true", matchIfMissing = true)
public class ExecutionController {
  private final ExecutionRequestService service;

  public ExecutionController(ExecutionRequestService service) {
    this.service = service;
  }

  @PostMapping("/api/automation-plans/{planId}/executions")
  public ApiResponse<ExecutionRunResponse> create(
      @PathVariable String planId, @RequestBody(required = false) ExecutionCreateRequest request) {
    return ApiResponse.ok(service.createExecution(TenantContext.requireTenantId(), planId, request));
  }

  @PostMapping("/api/executions/{executionId}/retry")
  public ApiResponse<ExecutionRunResponse> retry(
      @PathVariable String executionId, @RequestBody(required = false) ExecutionRetryRequest request) {
    return ApiResponse.ok(service.retry(TenantContext.requireTenantId(), executionId, request));
  }

  @GetMapping("/api/executions/{executionId}")
  public ApiResponse<ExecutionRunResponse> get(@PathVariable String executionId) {
    return ApiResponse.ok(service.getExecution(TenantContext.requireTenantId(), executionId));
  }

  @GetMapping("/api/automation-plans/{planId}/executions/latest")
  public ApiResponse<ExecutionRunResponse> latestByPlan(@PathVariable String planId) {
    return ApiResponse.ok(service.latestByPlan(TenantContext.requireTenantId(), planId));
  }

  @PostMapping("/api/executions/{executionId}/cancel")
  public ApiResponse<ExecutionRunResponse> cancel(@PathVariable String executionId) {
    return ApiResponse.ok(service.cancel(TenantContext.requireTenantId(), executionId));
  }
}
```

---

# 15. RunnerProperties

路径：

```txt
apps/aiops-runner/src/main/java/io/aegisops/runner/RunnerProperties.java
```

```java
package io.aegisops.runner;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "aiops.runner")
public class RunnerProperties {
  private boolean enabled = true;
  private String runnerId = "local-runner";
  private long pollDelayMs = 5000;
  private long timeoutSweepDelayMs = 10000;
  private int maxRunsPerTick = 1;
  private int timeoutSweepLimit = 20;

  public boolean isEnabled() {
    return enabled;
  }

  public void setEnabled(boolean enabled) {
    this.enabled = enabled;
  }

  public String getRunnerId() {
    return runnerId;
  }

  public void setRunnerId(String runnerId) {
    this.runnerId = runnerId;
  }

  public long getPollDelayMs() {
    return pollDelayMs;
  }

  public void setPollDelayMs(long pollDelayMs) {
    this.pollDelayMs = pollDelayMs;
  }

  public long getTimeoutSweepDelayMs() {
    return timeoutSweepDelayMs;
  }

  public void setTimeoutSweepDelayMs(long timeoutSweepDelayMs) {
    this.timeoutSweepDelayMs = timeoutSweepDelayMs;
  }

  public int getMaxRunsPerTick() {
    return maxRunsPerTick;
  }

  public void setMaxRunsPerTick(int maxRunsPerTick) {
    this.maxRunsPerTick = maxRunsPerTick;
  }

  public int getTimeoutSweepLimit() {
    return timeoutSweepLimit;
  }

  public void setTimeoutSweepLimit(int timeoutSweepLimit) {
    this.timeoutSweepLimit = timeoutSweepLimit;
  }
}
```

---

# 16. Runner executor 结果增强

## 16.1 `StepExecutionResult.java`

路径：

```txt
apps/aiops-runner/src/main/java/io/aegisops/runner/executor/StepExecutionResult.java
```

```java
package io.aegisops.runner.executor;

import io.aegisops.execution.dto.ExecutionArtifactCreateCommand;
import java.util.List;

public record StepExecutionResult(
    boolean success,
    String output,
    String errorMessage,
    List<ExecutionArtifactCreateCommand> artifacts) {
  public static StepExecutionResult success(String output) {
    return new StepExecutionResult(true, output, null, List.of());
  }

  public static StepExecutionResult success(String output, List<ExecutionArtifactCreateCommand> artifacts) {
    return new StepExecutionResult(true, output, null, artifacts == null ? List.of() : artifacts);
  }

  public static StepExecutionResult failure(String errorMessage) {
    return new StepExecutionResult(false, null, errorMessage, List.of());
  }

  public static StepExecutionResult failure(
      String errorMessage, List<ExecutionArtifactCreateCommand> artifacts) {
    return new StepExecutionResult(false, null, errorMessage, artifacts == null ? List.of() : artifacts);
  }
}
```

---

## 16.2 `ManualStepExecutor.java`

```java
package io.aegisops.runner.executor;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.aegisops.execution.ExecutionJson;
import io.aegisops.execution.dto.ExecutionArtifactCreateCommand;
import io.aegisops.execution.dto.ExecutionStepRecord;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class ManualStepExecutor implements StepExecutor {
  private final ExecutionJson json;

  public ManualStepExecutor(ObjectMapper objectMapper) {
    this.json = new ExecutionJson(objectMapper);
  }

  @Override
  public boolean supports(String actionType) {
    return "manual".equals(actionType);
  }

  @Override
  public StepExecutionResult execute(StepExecutionContext context, ExecutionStepRecord step) {
    return StepExecutionResult.success(
        "Manual step recorded by runner. No external action was executed.",
        List.of(
            new ExecutionArtifactCreateCommand(
                newId("artifact"),
                step.tenantId(),
                step.executionId(),
                step.id(),
                "text",
                "manual-step.txt",
                "Manual step recorded. No external action was executed.",
                json.write(Map.of("actionType", step.actionType(), "dryRun", context.dryRun())))));
  }

  private String newId(String prefix) {
    return prefix + "_" + UUID.randomUUID().toString().replace("-", "");
  }
}
```

---

## 16.3 `ShellDryRunStepExecutor.java`

```java
package io.aegisops.runner.executor;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.aegisops.execution.ExecutionJson;
import io.aegisops.execution.dto.ExecutionArtifactCreateCommand;
import io.aegisops.execution.dto.ExecutionStepRecord;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class ShellDryRunStepExecutor implements StepExecutor {
  private final ExecutionJson json;

  public ShellDryRunStepExecutor(ObjectMapper objectMapper) {
    this.json = new ExecutionJson(objectMapper);
  }

  @Override
  public boolean supports(String actionType) {
    return "shell".equals(actionType);
  }

  @Override
  public StepExecutionResult execute(StepExecutionContext context, ExecutionStepRecord step) {
    String command = json.textValue(step.actionPayloadJson(), "command");

    if (context.dryRun()) {
      return StepExecutionResult.success(
          "DRY-RUN shell command: " + command,
          List.of(
              new ExecutionArtifactCreateCommand(
                  newId("artifact"),
                  step.tenantId(),
                  step.executionId(),
                  step.id(),
                  "text",
                  "dry-run-command.txt",
                  command,
                  json.write(Map.of("actionType", "shell", "dryRun", true)))));
    }

    if (!context.liveEnabled()) {
      return StepExecutionResult.failure("Live shell execution is disabled.");
    }

    return StepExecutionResult.failure(
        "Live shell execution is not implemented in Phase5.3. Use dry-run mode.");
  }

  private String newId(String prefix) {
    return prefix + "_" + UUID.randomUUID().toString().replace("-", "");
  }
}
```

---

## 16.4 `UnsupportedStepExecutor.java`

```java
package io.aegisops.runner.executor;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.aegisops.execution.ExecutionJson;
import io.aegisops.execution.dto.ExecutionArtifactCreateCommand;
import io.aegisops.execution.dto.ExecutionStepRecord;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class UnsupportedStepExecutor implements StepExecutor {
  private final ExecutionJson json;

  public UnsupportedStepExecutor(ObjectMapper objectMapper) {
    this.json = new ExecutionJson(objectMapper);
  }

  @Override
  public boolean supports(String actionType) {
    return true;
  }

  @Override
  public StepExecutionResult execute(StepExecutionContext context, ExecutionStepRecord step) {
    String message = "Unsupported action type in Phase5.3: " + step.actionType();

    return StepExecutionResult.failure(
        message,
        List.of(
            new ExecutionArtifactCreateCommand(
                newId("artifact"),
                step.tenantId(),
                step.executionId(),
                step.id(),
                "text",
                "unsupported-action.txt",
                message,
                json.write(Map.of("actionType", step.actionType())))));
  }

  private String newId(String prefix) {
    return prefix + "_" + UUID.randomUUID().toString().replace("-", "");
  }
}
```

---

# 17. RunnerExecutionService

路径：

```txt
apps/aiops-runner/src/main/java/io/aegisops/runner/RunnerExecutionService.java
```

```java
package io.aegisops.runner;

import io.aegisops.common.exception.AppException;
import io.aegisops.execution.ExecutionProperties;
import io.aegisops.execution.ExecutionRepository;
import io.aegisops.execution.dto.ExecutionArtifactCreateCommand;
import io.aegisops.execution.dto.ExecutionRunRecord;
import io.aegisops.execution.dto.ExecutionRunStatusUpdateCommand;
import io.aegisops.execution.dto.ExecutionStepRecord;
import io.aegisops.execution.dto.ExecutionStepStatusUpdateCommand;
import io.aegisops.runner.executor.StepExecutionContext;
import io.aegisops.runner.executor.StepExecutionResult;
import io.aegisops.runner.executor.StepExecutor;
import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RunnerExecutionService {
  private final ExecutionRepository repository;
  private final ExecutionProperties executionProperties;
  private final RunnerProperties runnerProperties;
  private final List<StepExecutor> executors;

  public RunnerExecutionService(
      ExecutionRepository repository,
      ExecutionProperties executionProperties,
      RunnerProperties runnerProperties,
      List<StepExecutor> executors) {
    this.repository = repository;
    this.executionProperties = executionProperties;
    this.runnerProperties = runnerProperties;
    this.executors =
        executors.stream()
            .sorted(Comparator.comparing(executor -> executor.getClass().getSimpleName()))
            .toList();
  }

  @Transactional
  public boolean processNext() {
    OffsetDateTime now = OffsetDateTime.now();
    OffsetDateTime leaseUntil = now.plusSeconds(executionProperties.normalizedLeaseSeconds());

    Optional<ExecutionRunRecord> claimed =
        repository.claimNextQueuedRun(runnerProperties.getRunnerId(), now, leaseUntil);

    if (claimed.isEmpty()) {
      return false;
    }

    process(claimed.get());
    return true;
  }

  public int sweepTimeouts() {
    List<ExecutionRunRecord> expired =
        repository.findExpiredRunningRuns(
            OffsetDateTime.now(), Math.max(1, runnerProperties.getTimeoutSweepLimit()));

    int count = 0;
    for (ExecutionRunRecord run : expired) {
      timeout(run);
      count++;
    }
    return count;
  }

  public void process(ExecutionRunRecord run) {
    List<ExecutionStepRecord> steps = repository.listExecutionSteps(run.tenantId(), run.id());

    if (steps.isEmpty()) {
      failRunAndPlan(run, "Execution has no steps.");
      return;
    }

    boolean failed = false;
    String errorMessage = null;

    for (ExecutionStepRecord step : steps) {
      if (!"queued".equals(step.status())) {
        continue;
      }

      heartbeat(run);

      StepExecutionResult result = executeStep(run, step);
      persistArtifacts(result.artifacts());

      heartbeat(run);

      if (!result.success()) {
        failed = true;
        errorMessage = result.errorMessage();
        skipRemainingQueuedSteps(run, steps, step.sequenceNo());
        break;
      }
    }

    if (failed) {
      failRunAndPlan(run, errorMessage);
    } else {
      succeedRunAndPlan(run);
    }
  }

  private StepExecutionResult executeStep(ExecutionRunRecord run, ExecutionStepRecord step) {
    OffsetDateTime startedAt = OffsetDateTime.now();

    ensureUpdated(
        repository.updateStepStatus(
            new ExecutionStepStatusUpdateCommand(
                run.tenantId(),
                step.id(),
                "running",
                startedAt,
                null,
                null,
                null)),
        "EXECUTION_STEP_UPDATE_FAILED",
        "Execution step was not marked running");

    StepExecutor executor =
        executors.stream()
            .filter(item -> item.supports(step.actionType()))
            .findFirst()
            .orElseThrow(() -> new AppException("EXECUTOR_NOT_FOUND", "Executor not found"));

    StepExecutionResult result =
        executor.execute(new StepExecutionContext(run, executionProperties.isLiveEnabled()), step);

    OffsetDateTime finishedAt = OffsetDateTime.now();

    ensureUpdated(
        repository.updateStepStatus(
            new ExecutionStepStatusUpdateCommand(
                run.tenantId(),
                step.id(),
                result.success() ? "succeeded" : "failed",
                startedAt,
                finishedAt,
                result.output(),
                result.errorMessage())),
        "EXECUTION_STEP_UPDATE_FAILED",
        "Execution step final status was not updated");

    return result;
  }

  private void persistArtifacts(List<ExecutionArtifactCreateCommand> artifacts) {
    for (ExecutionArtifactCreateCommand artifact : artifacts) {
      repository.createArtifact(artifact);
      if (artifact.stepId() != null && !artifact.stepId().isBlank()) {
        ensureUpdated(
            repository.incrementStepArtifactCount(artifact.tenantId(), artifact.stepId()),
            "EXECUTION_ARTIFACT_COUNT_UPDATE_FAILED",
            "Execution step artifact count was not updated");
      }
    }
  }

  private void skipRemainingQueuedSteps(
      ExecutionRunRecord run, List<ExecutionStepRecord> steps, int failedSequenceNo) {
    for (ExecutionStepRecord step : steps) {
      if (step.sequenceNo() > failedSequenceNo && "queued".equals(step.status())) {
        ensureUpdated(
            repository.updateStepStatus(
                new ExecutionStepStatusUpdateCommand(
                    run.tenantId(),
                    step.id(),
                    "skipped",
                    null,
                    OffsetDateTime.now(),
                    null,
                    "Skipped because previous step failed.")),
            "EXECUTION_STEP_UPDATE_FAILED",
            "Execution step was not marked skipped");
      }
    }
  }

  private void heartbeat(ExecutionRunRecord run) {
    OffsetDateTime now = OffsetDateTime.now();
    OffsetDateTime leaseUntil = now.plusSeconds(executionProperties.normalizedLeaseSeconds());

    ensureUpdated(
        repository.heartbeat(
            run.tenantId(),
            run.id(),
            runnerProperties.getRunnerId(),
            now,
            leaseUntil),
        "EXECUTION_HEARTBEAT_FAILED",
        "Execution heartbeat failed");
  }

  private void timeout(ExecutionRunRecord run) {
    ensureUpdated(
        repository.timeoutRun(
            run.tenantId(),
            run.id(),
            "Execution lease timed out."),
        "EXECUTION_TIMEOUT_FAILED",
        "Execution run was not marked timeout");

    ensureUpdated(
        repository.timeoutExecutionSteps(run.tenantId(), run.id()),
        "EXECUTION_STEP_TIMEOUT_FAILED",
        "Execution steps were not marked timeout");

    ensureUpdated(
        repository.updatePlanStatus(run.tenantId(), run.planId(), "failed"),
        "AUTOMATION_PLAN_UPDATE_FAILED",
        "Automation plan status was not updated");
  }

  private void succeedRunAndPlan(ExecutionRunRecord run) {
    ensureUpdated(
        repository.updateRunStatus(
            new ExecutionRunStatusUpdateCommand(
                run.tenantId(),
                run.id(),
                "succeeded",
                runnerProperties.getRunnerId(),
                run.startedAt(),
                OffsetDateTime.now(),
                null,
                "Execution completed successfully.")),
        "EXECUTION_RUN_UPDATE_FAILED",
        "Execution run was not marked succeeded");

    ensureUpdated(
        repository.updatePlanStatus(run.tenantId(), run.planId(), "succeeded"),
        "AUTOMATION_PLAN_UPDATE_FAILED",
        "Automation plan status was not updated");
  }

  private void failRunAndPlan(ExecutionRunRecord run, String errorMessage) {
    ensureUpdated(
        repository.updateRunStatus(
            new ExecutionRunStatusUpdateCommand(
                run.tenantId(),
                run.id(),
                "failed",
                runnerProperties.getRunnerId(),
                run.startedAt(),
                OffsetDateTime.now(),
                errorMessage,
                "Execution failed.")),
        "EXECUTION_RUN_UPDATE_FAILED",
        "Execution run was not marked failed");

    ensureUpdated(
        repository.updatePlanStatus(run.tenantId(), run.planId(), "failed"),
        "AUTOMATION_PLAN_UPDATE_FAILED",
        "Automation plan status was not updated");
  }

  private void ensureUpdated(boolean updated, String code, String message) {
    if (!updated) {
      throw new AppException(code, message);
    }
  }
}
```

---

# 18. RunnerScheduler

路径：

```txt
apps/aiops-runner/src/main/java/io/aegisops/runner/RunnerScheduler.java
```

```java
package io.aegisops.runner;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "aiops.runner", name = "enabled", havingValue = "true", matchIfMissing = true)
public class RunnerScheduler {
  private final RunnerProperties properties;
  private final RunnerExecutionService service;

  public RunnerScheduler(RunnerProperties properties, RunnerExecutionService service) {
    this.properties = properties;
    this.service = service;
  }

  @Scheduled(fixedDelayString = "${aiops.runner.poll-delay-ms:5000}")
  public void poll() {
    int max = Math.max(1, properties.getMaxRunsPerTick());
    for (int i = 0; i < max; i++) {
      boolean processed = service.processNext();
      if (!processed) {
        return;
      }
    }
  }

  @Scheduled(fixedDelayString = "${aiops.runner.timeout-sweep-delay-ms:10000}")
  public void sweepTimeouts() {
    service.sweepTimeouts();
  }
}
```

---

# 19. Runner application.yml

路径：

```txt
apps/aiops-runner/src/main/resources/application.yml
```

```yaml
server:
  port: 8092

spring:
  application:
    name: aiops-runner

aiops:
  execution:
    api-enabled: false
    live-enabled: false
    lease-seconds: 60
    run-timeout-seconds: 1800
    step-timeout-seconds: 300
    max-retry-attempts: 3
  runner:
    enabled: true
    runner-id: local-runner
    poll-delay-ms: 5000
    timeout-sweep-delay-ms: 10000
    max-runs-per-tick: 1
    timeout-sweep-limit: 20
```

---

# 20. 单元测试

## 20.1 `FakeExecutionRepositoryBase.java`

路径：

```txt
modules/aiops-execution/src/test/java/io/aegisops/execution/FakeExecutionRepositoryBase.java
```

```java
package io.aegisops.execution;

import io.aegisops.execution.dto.ExecutionArtifactCreateCommand;
import io.aegisops.execution.dto.ExecutionArtifactRecord;
import io.aegisops.execution.dto.ExecutionRunCreateCommand;
import io.aegisops.execution.dto.ExecutionRunRecord;
import io.aegisops.execution.dto.ExecutionRunStatusUpdateCommand;
import io.aegisops.execution.dto.ExecutionStepCreateCommand;
import io.aegisops.execution.dto.ExecutionStepRecord;
import io.aegisops.execution.dto.ExecutionStepStatusUpdateCommand;
import io.aegisops.execution.dto.PlanForExecutionRecord;
import io.aegisops.execution.dto.PlanStepForExecutionRecord;
import io.aegisops.execution.dto.TimelineCreateCommand;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

abstract class FakeExecutionRepositoryBase implements ExecutionRepository {
  @Override
  public Optional<PlanForExecutionRecord> findPlan(String tenantId, String planId) {
    return Optional.empty();
  }

  @Override
  public List<PlanStepForExecutionRecord> listPlanSteps(String planId) {
    return List.of();
  }

  @Override
  public Optional<ExecutionRunRecord> findLatestRunByPlan(String tenantId, String planId) {
    return Optional.empty();
  }

  @Override
  public Optional<ExecutionRunRecord> findRun(String tenantId, String executionId) {
    return Optional.empty();
  }

  @Override
  public List<ExecutionStepRecord> listExecutionSteps(String tenantId, String executionId) {
    return List.of();
  }

  @Override
  public List<ExecutionArtifactRecord> listArtifacts(String tenantId, String executionId) {
    return List.of();
  }

  @Override
  public void createRun(ExecutionRunCreateCommand command) {}

  @Override
  public void createSteps(List<ExecutionStepCreateCommand> commands) {}

  @Override
  public void createArtifact(ExecutionArtifactCreateCommand command) {}

  @Override
  public boolean incrementStepArtifactCount(String tenantId, String stepId) {
    return true;
  }

  @Override
  public boolean updatePlanStatus(String tenantId, String planId, String status) {
    return true;
  }

  @Override
  public boolean cancelRun(String tenantId, String executionId) {
    return true;
  }

  @Override
  public boolean cancelExecutionSteps(String tenantId, String executionId) {
    return true;
  }

  @Override
  public Optional<ExecutionRunRecord> claimNextQueuedRun(
      String runnerId, OffsetDateTime now, OffsetDateTime leaseUntil) {
    return Optional.empty();
  }

  @Override
  public boolean heartbeat(
      String tenantId,
      String executionId,
      String runnerId,
      OffsetDateTime heartbeatAt,
      OffsetDateTime leaseUntil) {
    return true;
  }

  @Override
  public List<ExecutionRunRecord> findExpiredRunningRuns(OffsetDateTime now, int limit) {
    return List.of();
  }

  @Override
  public boolean timeoutRun(String tenantId, String executionId, String errorMessage) {
    return true;
  }

  @Override
  public boolean timeoutExecutionSteps(String tenantId, String executionId) {
    return true;
  }

  @Override
  public boolean updateRunStatus(ExecutionRunStatusUpdateCommand command) {
    return true;
  }

  @Override
  public boolean updateStepStatus(ExecutionStepStatusUpdateCommand command) {
    return true;
  }

  @Override
  public void addTimeline(TimelineCreateCommand command) {}
}
```

---

## 20.2 `ExecutionRequestServiceTest.java`

```java
package io.aegisops.execution;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.aegisops.common.exception.AppException;
import io.aegisops.execution.dto.ExecutionArtifactRecord;
import io.aegisops.execution.dto.ExecutionCreateRequest;
import io.aegisops.execution.dto.ExecutionRetryRequest;
import io.aegisops.execution.dto.ExecutionRunCreateCommand;
import io.aegisops.execution.dto.ExecutionRunRecord;
import io.aegisops.execution.dto.ExecutionStepCreateCommand;
import io.aegisops.execution.dto.ExecutionStepRecord;
import io.aegisops.execution.dto.PlanForExecutionRecord;
import io.aegisops.execution.dto.PlanStepForExecutionRecord;
import io.aegisops.execution.dto.TimelineCreateCommand;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class ExecutionRequestServiceTest {
  @Test
  void createExecutionCreatesAttemptMetadata() {
    FakeExecutionRepository repository = new FakeExecutionRepository();
    repository.plan = plan("approved");
    repository.planSteps.add(planStep());

    ExecutionProperties properties = new ExecutionProperties();
    properties.setMaxRetryAttempts(3);

    ExecutionRequestService service =
        new ExecutionRequestService(repository, properties, new ObjectMapper());

    var response =
        service.createExecution(
            "tenant_1", "plan_1", new ExecutionCreateRequest(true, "alice", 3));

    assertEquals("queued", response.status());
    assertEquals(1, response.attempt());
    assertEquals(3, response.maxAttempts());
    assertEquals("executing", repository.planStatus);
  }

  @Test
  void retryCreatesNextAttempt() {
    FakeExecutionRepository repository = new FakeExecutionRepository();
    repository.plan = plan("failed");
    repository.planSteps.add(planStep());
    repository.existingRun =
        run("exec_old", "failed", 1, 3, null);

    ExecutionProperties properties = new ExecutionProperties();
    properties.setMaxRetryAttempts(3);

    ExecutionRequestService service =
        new ExecutionRequestService(repository, properties, new ObjectMapper());

    var response =
        service.retry("tenant_1", "exec_old", new ExecutionRetryRequest("bob"));

    assertEquals("queued", response.status());
    assertEquals(2, response.attempt());
    assertEquals("exec_old", response.retryOfExecutionId());
    assertEquals("executing", repository.planStatus);
  }

  @Test
  void retryRejectsWhenAttemptsExhausted() {
    FakeExecutionRepository repository = new FakeExecutionRepository();
    repository.plan = plan("failed");
    repository.existingRun =
        run("exec_old", "failed", 3, 3, null);

    ExecutionRequestService service =
        new ExecutionRequestService(repository, new ExecutionProperties(), new ObjectMapper());

    assertThrows(
        AppException.class,
        () -> service.retry("tenant_1", "exec_old", new ExecutionRetryRequest("bob")));
  }

  private PlanForExecutionRecord plan(String status) {
    return new PlanForExecutionRecord(
        "plan_1", "tenant_1", "inc_1", status, "medium", "title", "summary");
  }

  private PlanStepForExecutionRecord planStep() {
    return new PlanStepForExecutionRecord(
        "planstep_1",
        "plan_1",
        1,
        "Check",
        "manual",
        "human",
        "{}",
        "desc",
        "ok",
        "rollback",
        true,
        "pending");
  }

  private ExecutionRunRecord run(
      String id, String status, int attempt, int maxAttempts, String retryOf) {
    return new ExecutionRunRecord(
        id,
        "tenant_1",
        "inc_1",
        "plan_1",
        status,
        "dry_run",
        "alice",
        null,
        null,
        null,
        null,
        null,
        attempt,
        maxAttempts,
        retryOf,
        null,
        null,
        1800,
        OffsetDateTime.now(),
        OffsetDateTime.now());
  }

  private static class FakeExecutionRepository extends FakeExecutionRepositoryBase {
    PlanForExecutionRecord plan;
    String planStatus;
    ExecutionRunRecord existingRun;
    ExecutionRunCreateCommand createdRun;
    final List<PlanStepForExecutionRecord> planSteps = new ArrayList<>();
    final List<ExecutionStepCreateCommand> createdSteps = new ArrayList<>();
    final List<TimelineCreateCommand> timelines = new ArrayList<>();

    @Override
    public Optional<PlanForExecutionRecord> findPlan(String tenantId, String planId) {
      return Optional.ofNullable(plan);
    }

    @Override
    public List<PlanStepForExecutionRecord> listPlanSteps(String planId) {
      return planSteps;
    }

    @Override
    public Optional<ExecutionRunRecord> findRun(String tenantId, String executionId) {
      if (createdRun != null && createdRun.id().equals(executionId)) {
        return Optional.of(
            new ExecutionRunRecord(
                createdRun.id(),
                createdRun.tenantId(),
                createdRun.incidentId(),
                createdRun.planId(),
                createdRun.status(),
                createdRun.mode(),
                createdRun.requestedBy(),
                null,
                null,
                null,
                null,
                null,
                createdRun.attempt(),
                createdRun.maxAttempts(),
                createdRun.retryOfExecutionId(),
                null,
                null,
                createdRun.timeoutSeconds(),
                OffsetDateTime.now(),
                OffsetDateTime.now()));
      }
      return Optional.ofNullable(existingRun);
    }

    @Override
    public void createRun(ExecutionRunCreateCommand command) {
      createdRun = command;
    }

    @Override
    public void createSteps(List<ExecutionStepCreateCommand> commands) {
      createdSteps.addAll(commands);
    }

    @Override
    public boolean updatePlanStatus(String tenantId, String planId, String status) {
      planStatus = status;
      return true;
    }

    @Override
    public List<ExecutionStepRecord> listExecutionSteps(String tenantId, String executionId) {
      return List.of();
    }

    @Override
    public List<ExecutionArtifactRecord> listArtifacts(String tenantId, String executionId) {
      return List.of();
    }

    @Override
    public void addTimeline(TimelineCreateCommand command) {
      timelines.add(command);
    }
  }
}
```

---

## 20.3 `RunnerExecutionServiceTest.java`

路径：

```txt
apps/aiops-runner/src/test/java/io/aegisops/runner/RunnerExecutionServiceTest.java
```

```java
package io.aegisops.runner;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.aegisops.execution.ExecutionProperties;
import io.aegisops.execution.dto.ExecutionArtifactCreateCommand;
import io.aegisops.execution.dto.ExecutionRunRecord;
import io.aegisops.execution.dto.ExecutionRunStatusUpdateCommand;
import io.aegisops.execution.dto.ExecutionStepRecord;
import io.aegisops.execution.dto.ExecutionStepStatusUpdateCommand;
import io.aegisops.runner.executor.ManualStepExecutor;
import io.aegisops.runner.executor.ShellDryRunStepExecutor;
import io.aegisops.runner.executor.UnsupportedStepExecutor;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class RunnerExecutionServiceTest {
  @Test
  void processRunWritesHeartbeatAndArtifacts() {
    FakeRunnerRepository repository = new FakeRunnerRepository();
    repository.claimed = run("exec_1", "running", "dry_run");
    repository.steps.add(step("step_1", 1, "manual", "{}"));

    ExecutionProperties executionProperties = new ExecutionProperties();
    executionProperties.setLeaseSeconds(60);

    RunnerProperties runnerProperties = new RunnerProperties();
    runnerProperties.setRunnerId("runner_1");

    RunnerExecutionService service =
        new RunnerExecutionService(
            repository,
            executionProperties,
            runnerProperties,
            List.of(
                new ManualStepExecutor(new ObjectMapper()),
                new ShellDryRunStepExecutor(new ObjectMapper()),
                new UnsupportedStepExecutor(new ObjectMapper())));

    boolean processed = service.processNext();

    assertTrue(processed);
    assertTrue(repository.heartbeatCount >= 2);
    assertEquals("succeeded", repository.runStatus);
    assertEquals("succeeded", repository.planStatus);
    assertEquals(1, repository.artifacts.size());
    assertEquals(1, repository.artifactIncrementCount);
  }

  @Test
  void timeoutSweepMarksRunStepsAndPlan() {
    FakeRunnerRepository repository = new FakeRunnerRepository();
    repository.expiredRuns.add(run("exec_1", "running", "dry_run"));

    ExecutionProperties executionProperties = new ExecutionProperties();

    RunnerProperties runnerProperties = new RunnerProperties();
    runnerProperties.setRunnerId("runner_1");

    RunnerExecutionService service =
        new RunnerExecutionService(
            repository,
            executionProperties,
            runnerProperties,
            List.of(new ManualStepExecutor(new ObjectMapper())));

    int count = service.sweepTimeouts();

    assertEquals(1, count);
    assertEquals("timeout", repository.timeoutRunStatus);
    assertEquals("failed", repository.planStatus);
    assertTrue(repository.stepsTimedOut);
  }

  private ExecutionRunRecord run(String id, String status, String mode) {
    return new ExecutionRunRecord(
        id,
        "tenant_1",
        "inc_1",
        "plan_1",
        status,
        mode,
        "alice",
        "runner_1",
        OffsetDateTime.now(),
        null,
        null,
        null,
        1,
        3,
        null,
        OffsetDateTime.now().plusSeconds(60),
        OffsetDateTime.now(),
        1800,
        OffsetDateTime.now(),
        OffsetDateTime.now());
  }

  private ExecutionStepRecord step(String id, int sequence, String actionType, String payload) {
    return new ExecutionStepRecord(
        id,
        "tenant_1",
        "exec_1",
        "planstep_" + sequence,
        sequence,
        "step " + sequence,
        actionType,
        "human",
        "queued",
        payload,
        "",
        null,
        null,
        null,
        null,
        1,
        300,
        0,
        OffsetDateTime.now(),
        OffsetDateTime.now());
  }

  private static class FakeRunnerRepository extends RunnerFakeExecutionRepositoryBase {
    ExecutionRunRecord claimed;
    final List<ExecutionStepRecord> steps = new ArrayList<>();
    final List<ExecutionRunRecord> expiredRuns = new ArrayList<>();
    final List<ExecutionArtifactCreateCommand> artifacts = new ArrayList<>();
    int heartbeatCount;
    int artifactIncrementCount;
    String runStatus;
    String planStatus;
    String timeoutRunStatus;
    boolean stepsTimedOut;

    @Override
    public Optional<ExecutionRunRecord> claimNextQueuedRun(
        String runnerId, OffsetDateTime now, OffsetDateTime leaseUntil) {
      return Optional.ofNullable(claimed);
    }

    @Override
    public List<ExecutionStepRecord> listExecutionSteps(String tenantId, String executionId) {
      return steps;
    }

    @Override
    public boolean heartbeat(
        String tenantId,
        String executionId,
        String runnerId,
        OffsetDateTime heartbeatAt,
        OffsetDateTime leaseUntil) {
      heartbeatCount++;
      return true;
    }

    @Override
    public void createArtifact(ExecutionArtifactCreateCommand command) {
      artifacts.add(command);
    }

    @Override
    public boolean incrementStepArtifactCount(String tenantId, String stepId) {
      artifactIncrementCount++;
      return true;
    }

    @Override
    public boolean updateStepStatus(ExecutionStepStatusUpdateCommand command) {
      return true;
    }

    @Override
    public boolean updateRunStatus(ExecutionRunStatusUpdateCommand command) {
      runStatus = command.status();
      return true;
    }

    @Override
    public boolean updatePlanStatus(String tenantId, String planId, String status) {
      planStatus = status;
      return true;
    }

    @Override
    public List<ExecutionRunRecord> findExpiredRunningRuns(OffsetDateTime now, int limit) {
      return expiredRuns;
    }

    @Override
    public boolean timeoutRun(String tenantId, String executionId, String errorMessage) {
      timeoutRunStatus = "timeout";
      return true;
    }

    @Override
    public boolean timeoutExecutionSteps(String tenantId, String executionId) {
      stepsTimedOut = true;
      return true;
    }
  }
}
```

---

## 20.4 `RunnerFakeExecutionRepositoryBase.java`

路径：

```txt
apps/aiops-runner/src/test/java/io/aegisops/runner/RunnerFakeExecutionRepositoryBase.java
```

```java
package io.aegisops.runner;

import io.aegisops.execution.ExecutionRepository;
import io.aegisops.execution.dto.ExecutionArtifactCreateCommand;
import io.aegisops.execution.dto.ExecutionArtifactRecord;
import io.aegisops.execution.dto.ExecutionRunCreateCommand;
import io.aegisops.execution.dto.ExecutionRunRecord;
import io.aegisops.execution.dto.ExecutionRunStatusUpdateCommand;
import io.aegisops.execution.dto.ExecutionStepCreateCommand;
import io.aegisops.execution.dto.ExecutionStepRecord;
import io.aegisops.execution.dto.ExecutionStepStatusUpdateCommand;
import io.aegisops.execution.dto.PlanForExecutionRecord;
import io.aegisops.execution.dto.PlanStepForExecutionRecord;
import io.aegisops.execution.dto.TimelineCreateCommand;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

abstract class RunnerFakeExecutionRepositoryBase implements ExecutionRepository {
  @Override
  public Optional<PlanForExecutionRecord> findPlan(String tenantId, String planId) {
    return Optional.empty();
  }

  @Override
  public List<PlanStepForExecutionRecord> listPlanSteps(String planId) {
    return List.of();
  }

  @Override
  public Optional<ExecutionRunRecord> findLatestRunByPlan(String tenantId, String planId) {
    return Optional.empty();
  }

  @Override
  public Optional<ExecutionRunRecord> findRun(String tenantId, String executionId) {
    return Optional.empty();
  }

  @Override
  public List<ExecutionStepRecord> listExecutionSteps(String tenantId, String executionId) {
    return List.of();
  }

  @Override
  public List<ExecutionArtifactRecord> listArtifacts(String tenantId, String executionId) {
    return List.of();
  }

  @Override
  public void createRun(ExecutionRunCreateCommand command) {}

  @Override
  public void createSteps(List<ExecutionStepCreateCommand> commands) {}

  @Override
  public void createArtifact(ExecutionArtifactCreateCommand command) {}

  @Override
  public boolean incrementStepArtifactCount(String tenantId, String stepId) {
    return true;
  }

  @Override
  public boolean updatePlanStatus(String tenantId, String planId, String status) {
    return true;
  }

  @Override
  public boolean cancelRun(String tenantId, String executionId) {
    return true;
  }

  @Override
  public boolean cancelExecutionSteps(String tenantId, String executionId) {
    return true;
  }

  @Override
  public Optional<ExecutionRunRecord> claimNextQueuedRun(
      String runnerId, OffsetDateTime now, OffsetDateTime leaseUntil) {
    return Optional.empty();
  }

  @Override
  public boolean heartbeat(
      String tenantId,
      String executionId,
      String runnerId,
      OffsetDateTime heartbeatAt,
      OffsetDateTime leaseUntil) {
    return true;
  }

  @Override
  public List<ExecutionRunRecord> findExpiredRunningRuns(OffsetDateTime now, int limit) {
    return List.of();
  }

  @Override
  public boolean timeoutRun(String tenantId, String executionId, String errorMessage) {
    return true;
  }

  @Override
  public boolean timeoutExecutionSteps(String tenantId, String executionId) {
    return true;
  }

  @Override
  public boolean updateRunStatus(ExecutionRunStatusUpdateCommand command) {
    return true;
  }

  @Override
  public boolean updateStepStatus(ExecutionStepStatusUpdateCommand command) {
    return true;
  }

  @Override
  public void addTimeline(TimelineCreateCommand command) {}
}
```

---

## 20.5 `JooqExecutionRepositoryGeneratedSqlTest.java`

新增测试：

```java
@Test
void createArtifactUsesExecutionArtifactTable() {
  AtomicReference<String> sqlRef = new AtomicReference<>();

  MockDataProvider provider =
      context -> {
        sqlRef.set(context.dsl().renderInlined(context.query()));
        return new MockResult[] {new MockResult(1, DSL.using(SQLDialect.POSTGRES).newResult())};
      };

  JooqExecutionRepository repository =
      new JooqExecutionRepository(DSL.using(new MockConnection(provider), SQLDialect.POSTGRES));

  repository.createArtifact(
      new ExecutionArtifactCreateCommand(
          "artifact_1",
          "tenant_1",
          "exec_1",
          "step_1",
          "text",
          "dry-run-command.txt",
          "systemctl status app",
          "{}"));

  String sql = sqlRef.get().toLowerCase();

  assertTrue(sql.contains("insert into"));
  assertTrue(sql.contains("execution_artifact"));
  assertTrue(sql.contains("dry-run-command.txt"));
}
```

需要 import：

```java
import io.aegisops.execution.dto.ExecutionArtifactCreateCommand;
```

---

## 20.6 `ExecutionSafetyTest.java`

保持 Phase5.2 的安全测试，并新增 artifact/lease 允许：

```java
package io.aegisops.execution;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class ExecutionSafetyTest {
  @Test
  void executionModuleDoesNotExecuteExternalCommands() throws Exception {
    String content = readMainSources(Path.of("src/main/java"));

    assertFalse(content.contains("ProcessBuilder"));
    assertFalse(content.contains("Runtime.getRuntime"));
    assertFalse(content.contains("JSch"));
    assertFalse(content.contains("sshj"));
    assertFalse(content.contains("Ansible"));
    assertFalse(content.contains("RestTemplate"));
    assertFalse(content.contains("WebClient.create"));

    assertTrue(content.contains("lease"));
    assertTrue(content.contains("artifact"));
  }

  private String readMainSources(Path root) throws Exception {
    StringBuilder builder = new StringBuilder();
    try (var paths = Files.walk(root)) {
      paths
          .filter(path -> path.toString().endsWith(".java"))
          .forEach(
              path -> {
                try {
                  builder.append(Files.readString(path)).append('\n');
                } catch (Exception ex) {
                  throw new IllegalStateException(ex);
                }
              });
    }
    return builder.toString();
  }
}
```

---

## 20.7 `RunnerSafetyTest.java`

```java
package io.aegisops.runner;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class RunnerSafetyTest {
  @Test
  void runnerDoesNotUseLiveExecutionLibrariesInPhase53() throws Exception {
    String content = readMainSources(Path.of("src/main/java"));

    assertFalse(content.contains("ProcessBuilder"));
    assertFalse(content.contains("Runtime.getRuntime"));
    assertFalse(content.contains("JSch"));
    assertFalse(content.contains("sshj"));
    assertFalse(content.contains("Ansible"));
    assertFalse(content.contains("WebClient.create"));
    assertFalse(content.contains("RestTemplate"));

    assertTrue(content.contains("heartbeat"));
    assertTrue(content.contains("timeout"));
  }

  private String readMainSources(Path root) throws Exception {
    StringBuilder builder = new StringBuilder();
    try (var paths = Files.walk(root)) {
      paths
          .filter(path -> path.toString().endsWith(".java"))
          .forEach(
              path -> {
                try {
                  builder.append(Files.readString(path)).append('\n');
                } catch (Exception ex) {
                  throw new IllegalStateException(ex);
                }
              });
    }
    return builder.toString();
  }
}
```

---

# 21. 文档

路径：

```txt
docs/mvp/design/phase5.3-runner-reliability.md
```

```md
# Phase5.3 Runner Reliability

## 目标

Phase5.3 为 Phase5.2 执行器增加可靠性能力：

- runner lease
- heartbeat
- timeout sweep
- retry
- artifact

## 安全边界

Phase5.3 仍然不做真实执行：

- 不 SSH
- 不 Ansible
- 不 Webhook
- 不 ProcessBuilder
- 不 Runtime.exec

## Lease

runner claim queued run 后设置：

- runner_id
- started_at
- heartbeat_at
- lease_until

runner 执行 step 前后 heartbeat。

## Timeout

timeout sweeper 扫描：

- execution_run.status = running
- lease_until < now

然后：

- execution_run.status = timeout
- execution_step queued/running -> timeout
- automation_plan.status = failed

## Retry

只允许 retry：

- failed
- timeout

retry 创建新的 queued execution_run：

- attempt = old.attempt + 1
- retry_of_execution_id = old.id

## Artifact

execution_artifact 存储：

- manual step text
- shell dry-run command
- unsupported action context
```

---

# 22. 验证命令

```powershell
mvn -pl modules/aiops-persistence -am generate-sources
mvn -pl modules/aiops-execution -am test
mvn -pl apps/aiops-runner -am test
mvn -pl apps/aiops-server -am test
```

全量：

```powershell
mvn test
```

---

# 23. 验收标准

```txt
1. execution_run 有 attempt / max_attempts / lease_until / heartbeat_at。
2. execution_step 有 attempt / timeout_seconds / artifact_count。
3. execution_artifact 可写入。
4. runner claim queued run 会设置 lease。
5. runner 执行 step 前后 heartbeat。
6. timeout sweeper 能把过期 running run 标记 timeout。
7. timeout run 对应 plan 标记 failed。
8. retry failed run 会创建新 queued run。
9. retry attempt 递增。
10. retry 超过 maxAttempts 会拒绝。
11. manual step 会写 artifact。
12. shell dry-run step 会写 dry-run command artifact。
13. 不存在 ProcessBuilder / Runtime.exec / SSH / Ansible / Webhook。
```

---

# 24. 建议提交信息

```txt
feat(execution): add runner lease heartbeat timeout retry and artifacts
```

---

# 25. Phase5.4 下一步

Phase5.4 建议做：

```txt
1. runner lease owner 校验增强。
2. execution artifact 接 MinIO。
3. step retry policy。
4. shell live 白名单模型。
5. ansible dry-run adapter。
6. runner metrics。
7. execution UI。
```
