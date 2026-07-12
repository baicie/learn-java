# Phase 20.7：审批流与 SLA

> 基线：`baicie/ai-ops`，分支 `feat/record-doc-portal`，提交 `43d16315cc4b68cc705177d2c8faba1d8e42644e`。
>
> 本文件由 Phase 20 总方案按主题拆分。Phase 20 开发前必须先完成 Phase 19 P0 修复。

## 数据库迁移

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

## 补充领域模型与 Repository 契约

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
