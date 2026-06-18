# Phase5.7：Ansible Live Execution with Approval Guard

> Phase5.7 目标：在 Phase5.6 `ansible-playbook --check` sandbox 基础上，**受控开放 Ansible live execution**。
> 注意：这不是 SSH Adapter，也不是自由命令执行。Runner 仍然只执行注册过的 Ansible playbook，且必须经过审批快照、policy、credential ref、stdout/stderr 脱敏、artifact 审计。

---

# 1. Phase5.7 定位

前置阶段：

```txt id="1lxx40"
Phase5.4 Webhook Adapter
Phase5.5 Ansible Adapter dry-run preview
Phase5.6 Ansible Sandbox Execution：ansible-playbook --check
```

Phase5.7 新增：

```txt id="sazywl"
1. live execution approval snapshot
2. ansible credential reference model
3. ansible live policy guard
4. ansible-playbook live argv execution
5. stdout/stderr sensitive masking
6. live execution artifact
7. ProcessBuilder 支持 requireCheck=false，但只允许 ansible-playbook
8. live 仍然禁止自由 inventory/playbook
```

---

# 2. 安全边界

## 2.1 允许

```txt id="p0dihk"
ansible-playbook <registered inventory> <registered playbook>
```

## 2.2 禁止

```txt id="s67izs"
1. SSH Adapter
2. 自由 shell
3. Runtime.exec
4. sh -c / bash -c / cmd /c / powershell
5. 自由 playbook 路径
6. 自由 inventory 路径
7. 未注册 playbook
8. 未注册 inventory
9. 明文 credential
10. Vault secret 明文
11. 未审批 live execution
12. critical 默认 live
```

---

# 3. Phase5.7 live 执行条件

Ansible live execution 必须同时满足：

```txt id="68fd74"
1. execution_run.mode = live
2. aiops.execution.live-enabled = true
3. automation_plan 已审批通过
4. execution_run 有审批快照 approval_id / approval_snapshot
5. ansible_execution_policy.enabled = true
6. ansible_execution_policy.allow_live = true
7. ansible_execution_policy.live_requires_approval = true
8. plan_risk_level 在 policy.allowed_live_risk_levels 内
9. inventory.enabled = true
10. playbook.enabled = true
11. inventoryId 在 allowed_inventory_ids 内
12. tags 在 allowed_tags 内
13. extraVars 在 allowed_extra_vars 内
14. extraVars 不含 secret-like key
15. credentialRefId 如果存在，必须在 allowed_credential_ref_ids 内
16. ProcessBuilder argv[0] 必须是 ansible-playbook
17. argv 不允许 shell
```

---

# 4. 数据库设计

## 4.1 修改 execution_run

新增审批快照字段：

```txt id="m3gox6"
approval_id
approval_snapshot
plan_risk_level
live_guard_passed_at
```

原因：runner 执行 live 时，`automation_plan.status` 可能已经被 server 从 `approved` 改成 `executing`，所以不能只看 plan 当前状态；必须保存创建 execution 时的审批快照。

---

## 4.2 新增 ansible_credential_ref

不存明文，只存引用：

```txt id="90mx04"
secret_ref
```

比如未来可以指向：

```txt id="g7w3bb"
vault://tenant/prod/ssh-key
kms://xxx
secrets-manager://xxx
```

Phase5.7 不实现 secret 读取，只校验 ref 存在与启用。

---

## 4.3 扩展 ansible_execution_policy

新增：

```txt id="jfl8t4"
live_requires_approval
allowed_live_risk_levels
allowed_credential_ref_ids
stdout_stderr_masking_enabled
live_timeout_seconds
```

---

# 5. Migration

路径：

```txt id="qwb7b3"
apps/aiops-server/src/main/resources/db/migration/V17__phase5_7_ansible_live_guard.sql
```

```sql id="79ue7c"
-- Phase 5.7: Ansible live execution with approval guard.
-- This phase allows controlled ansible-playbook live execution only when:
-- 1. execution_run.mode = live
-- 2. approval snapshot is present
-- 3. ansible policy allow_live = true
-- 4. live risk level is allowed
-- 5. runner live-enabled = true

alter table execution_run
  add column if not exists approval_id varchar(64),
  add column if not exists approval_snapshot jsonb not null default '{}'::jsonb,
  add column if not exists plan_risk_level varchar(32),
  add column if not exists live_guard_passed_at timestamptz;

create index if not exists idx_execution_run_approval
  on execution_run(tenant_id, approval_id);

create table if not exists ansible_credential_ref (
  id varchar(64) primary key,
  tenant_id varchar(64) not null references tenant(id) on delete cascade,
  name varchar(160) not null,
  description text,
  credential_type varchar(32) not null default 'ssh_key',
  secret_ref varchar(512) not null,
  enabled boolean not null default true,
  created_by varchar(64) not null,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  constraint ck_ansible_credential_type
    check (credential_type in ('ssh_key', 'password', 'token', 'vault_ref'))
);

create unique index if not exists uq_ansible_credential_ref_tenant_name
  on ansible_credential_ref(tenant_id, name);

create index if not exists idx_ansible_credential_ref_tenant_enabled
  on ansible_credential_ref(tenant_id, enabled);

alter table ansible_execution_policy
  add column if not exists live_requires_approval boolean not null default true,
  add column if not exists allowed_live_risk_levels jsonb not null default '["low", "medium"]'::jsonb,
  add column if not exists allowed_credential_ref_ids jsonb not null default '[]'::jsonb,
  add column if not exists stdout_stderr_masking_enabled boolean not null default true,
  add column if not exists live_timeout_seconds int not null default 1800;

alter table ansible_execution_policy
  add constraint ck_ansible_policy_live_timeout_seconds
    check (live_timeout_seconds >= 30 and live_timeout_seconds <= 86400);

create index if not exists idx_ansible_policy_live
  on ansible_execution_policy(tenant_id, allow_live, live_requires_approval, enabled);
```

---

# 6. jOOQ Codegen

路径：

```txt id="r3rlmk"
modules/aiops-persistence/src/main/resources/jooq-codegen.xml
```

`<includes>` 追加：

```txt id="dps3mb"
ansible_credential_ref
```

完整 includes 建议：

```xml id="7p8veo"
<includes>
  tenant | sys_user | sys_role | sys_permission | sys_user_role | sys_role_permission |
  datasource | datasource_sync_run | asset | asset_relation | alert_event | incident |
  incident_event | incident_timeline | audit_log | rca_analysis | ai_diagnosis |
  agent_run | agent_run_step | agent_eval_result | log_event | change_event |
  runbook | runbook_step_template | automation_plan | automation_plan_step |
  approval_policy | automation_approval | approval_decision |
  execution_run | execution_step | execution_artifact |
  webhook_connector | webhook_execution_policy |
  ansible_inventory | ansible_playbook | ansible_execution_policy | ansible_credential_ref
</includes>
```

---

# 7. DTO 修改与新增

## 7.1 `ExecutionApprovalSnapshotRecord.java`

路径：

```txt id="pmqmu4"
modules/aiops-execution/src/main/java/io/aegisops/execution/dto/ExecutionApprovalSnapshotRecord.java
```

```java id="rc4p9f"
package io.aegisops.execution.dto;

import java.time.OffsetDateTime;

public record ExecutionApprovalSnapshotRecord(
    String approvalId,
    String planId,
    String status,
    String requestedBy,
    int requiredApprovals,
    int approvedCount,
    OffsetDateTime approvedAt) {}
```

---

## 7.2 修改 `ExecutionRunCreateCommand.java`

路径：

```txt id="g4qvzf"
modules/aiops-execution/src/main/java/io/aegisops/execution/dto/ExecutionRunCreateCommand.java
```

```java id="poicj0"
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
    String planRiskLevel) {}
```

---

## 7.3 修改 `ExecutionRunRecord.java`

```java id="cofi4h"
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
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt) {}
```

---

## 7.4 修改 `ExecutionRunResponse.java`

```java id="p7vwl3"
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
    List<ExecutionStepResponse> steps,
    List<ExecutionArtifactResponse> artifacts,
    OffsetDateTime startedAt,
    OffsetDateTime finishedAt,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt) {}
```

---

## 7.5 修改 `AnsiblePolicyCreateCommand.java`

```java id="kg9oqg"
package io.aegisops.execution.dto;

public record AnsiblePolicyCreateCommand(
    String id,
    String tenantId,
    String playbookId,
    boolean allowLive,
    boolean allowCheckExecution,
    boolean liveRequiresApproval,
    boolean defaultCheckMode,
    String allowedInventoryIdsJson,
    String allowedExtraVarsJson,
    String allowedLiveRiskLevelsJson,
    String allowedCredentialRefIdsJson,
    boolean stdoutStderrMaskingEnabled,
    int maxExtraVarsBytes,
    int timeoutSeconds,
    int liveTimeoutSeconds,
    boolean enabled) {}
```

---

## 7.6 修改 `AnsiblePolicyRecord.java`

```java id="tmd5ku"
package io.aegisops.execution.dto;

import java.time.OffsetDateTime;

public record AnsiblePolicyRecord(
    String id,
    String tenantId,
    String playbookId,
    boolean allowLive,
    boolean allowCheckExecution,
    boolean liveRequiresApproval,
    boolean defaultCheckMode,
    String allowedInventoryIdsJson,
    String allowedExtraVarsJson,
    String allowedLiveRiskLevelsJson,
    String allowedCredentialRefIdsJson,
    boolean stdoutStderrMaskingEnabled,
    int maxExtraVarsBytes,
    int timeoutSeconds,
    int liveTimeoutSeconds,
    boolean enabled,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt) {}
```

---

## 7.7 修改 `AnsiblePlaybookCreateRequest.java`

```java id="hhx2wr"
package io.aegisops.execution.dto;

import java.util.List;
import java.util.Map;

public record AnsiblePlaybookCreateRequest(
    String name,
    String description,
    String playbookRef,
    String playbookContent,
    Map<String, Object> variablesSchema,
    List<String> allowedTags,
    List<String> allowedInventoryIds,
    List<String> allowedExtraVars,
    Boolean allowLive,
    Boolean allowCheckExecution,
    Boolean liveRequiresApproval,
    Boolean defaultCheckMode,
    List<String> allowedLiveRiskLevels,
    List<String> allowedCredentialRefIds,
    Boolean stdoutStderrMaskingEnabled,
    Integer maxExtraVarsBytes,
    Integer timeoutSeconds,
    Integer liveTimeoutSeconds,
    String createdBy) {}
```

---

## 7.8 修改 `AnsiblePlaybookResponse.java`

```java id="4grxxm"
package io.aegisops.execution.dto;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

public record AnsiblePlaybookResponse(
    String id,
    String name,
    String description,
    String playbookRef,
    String playbookContent,
    Map<String, Object> variablesSchema,
    List<String> allowedTags,
    boolean enabled,
    boolean allowLive,
    boolean allowCheckExecution,
    boolean liveRequiresApproval,
    boolean defaultCheckMode,
    List<String> allowedInventoryIds,
    List<String> allowedExtraVars,
    List<String> allowedLiveRiskLevels,
    List<String> allowedCredentialRefIds,
    boolean stdoutStderrMaskingEnabled,
    int maxExtraVarsBytes,
    int timeoutSeconds,
    int liveTimeoutSeconds,
    String createdBy,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt) {}
```

---

## 7.9 `AnsibleCredentialCreateRequest.java`

路径：

```txt id="avvi5i"
modules/aiops-execution/src/main/java/io/aegisops/execution/dto/AnsibleCredentialCreateRequest.java
```

```java id="5jg56s"
package io.aegisops.execution.dto;

public record AnsibleCredentialCreateRequest(
    String name,
    String description,
    String credentialType,
    String secretRef,
    String createdBy) {}
```

---

## 7.10 `AnsibleCredentialCreateCommand.java`

```java id="cq624k"
package io.aegisops.execution.dto;

public record AnsibleCredentialCreateCommand(
    String id,
    String tenantId,
    String name,
    String description,
    String credentialType,
    String secretRef,
    boolean enabled,
    String createdBy) {}
```

---

## 7.11 `AnsibleCredentialRecord.java`

```java id="4kf8uw"
package io.aegisops.execution.dto;

import java.time.OffsetDateTime;

public record AnsibleCredentialRecord(
    String id,
    String tenantId,
    String name,
    String description,
    String credentialType,
    String secretRef,
    boolean enabled,
    String createdBy,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt) {}
```

---

## 7.12 `AnsibleCredentialResponse.java`

```java id="zj9j8q"
package io.aegisops.execution.dto;

import java.time.OffsetDateTime;

public record AnsibleCredentialResponse(
    String id,
    String name,
    String description,
    String credentialType,
    String secretRef,
    boolean enabled,
    String createdBy,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt) {}
```

---

# 8. ExecutionRepository 修改

## 8.1 接口增加方法

路径：

```txt id="hw5fnj"
modules/aiops-execution/src/main/java/io/aegisops/execution/ExecutionRepository.java
```

新增：

```java id="el1q2o"
Optional<ExecutionApprovalSnapshotRecord> findLatestApprovedApprovalSnapshot(
    String tenantId, String planId);

boolean markLiveGuardPassed(String tenantId, String executionId);
```

需要 import：

```java id="vyw3bk"
import io.aegisops.execution.dto.ExecutionApprovalSnapshotRecord;
```

---

## 8.2 修改 `JooqExecutionRepository`

路径：

```txt id="5zfdf8"
modules/aiops-execution/src/main/java/io/aegisops/execution/JooqExecutionRepository.java
```

### 8.2.1 createRun 增加字段

```java id="hcybh6"
.set(EXECUTION_RUN.APPROVAL_ID, command.approvalId())
.set(EXECUTION_RUN.APPROVAL_SNAPSHOT, jsonbValue(command.approvalSnapshotJson()))
.set(EXECUTION_RUN.PLAN_RISK_LEVEL, command.planRiskLevel())
```

完整片段：

```java id="f2s7ya"
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
      .set(EXECUTION_RUN.APPROVAL_ID, command.approvalId())
      .set(EXECUTION_RUN.APPROVAL_SNAPSHOT, jsonbValue(command.approvalSnapshotJson()))
      .set(EXECUTION_RUN.PLAN_RISK_LEVEL, command.planRiskLevel())
      .set(EXECUTION_RUN.CREATED_AT, DSL.currentOffsetDateTime())
      .set(EXECUTION_RUN.UPDATED_AT, DSL.currentOffsetDateTime())
      .execute();
}
```

---

### 8.2.2 selectRun 增加字段

```java id="ilme9a"
EXECUTION_RUN.APPROVAL_ID,
EXECUTION_RUN.APPROVAL_SNAPSHOT.cast(String.class).as("approval_snapshot_json"),
EXECUTION_RUN.PLAN_RISK_LEVEL,
EXECUTION_RUN.LIVE_GUARD_PASSED_AT,
```

---

### 8.2.3 toRunRecord 增加字段

```java id="armcc5"
record.get(EXECUTION_RUN.APPROVAL_ID),
record.get("approval_snapshot_json", String.class),
record.get(EXECUTION_RUN.PLAN_RISK_LEVEL),
record.get(EXECUTION_RUN.LIVE_GUARD_PASSED_AT),
```

---

### 8.2.4 新增 `markLiveGuardPassed`

```java id="p7wjcz"
@Override
public boolean markLiveGuardPassed(String tenantId, String executionId) {
  return dsl.update(EXECUTION_RUN)
          .set(EXECUTION_RUN.LIVE_GUARD_PASSED_AT, DSL.currentOffsetDateTime())
          .set(EXECUTION_RUN.UPDATED_AT, DSL.currentOffsetDateTime())
          .where(EXECUTION_RUN.TENANT_ID.eq(tenantId))
          .and(EXECUTION_RUN.ID.eq(executionId))
          .and(EXECUTION_RUN.MODE.eq("live"))
          .execute()
      > 0;
}
```

---

### 8.2.5 新增 approval snapshot 查询

需要 static import：

```java id="o5ptpl"
import static io.aegisops.persistence.jooq.Tables.AUTOMATION_APPROVAL;
```

新增方法：

```java id="gs7ij8"
@Override
public Optional<ExecutionApprovalSnapshotRecord> findLatestApprovedApprovalSnapshot(
    String tenantId, String planId) {
  return dsl.select(
          AUTOMATION_APPROVAL.ID,
          AUTOMATION_APPROVAL.PLAN_ID,
          AUTOMATION_APPROVAL.STATUS,
          AUTOMATION_APPROVAL.REQUESTED_BY,
          AUTOMATION_APPROVAL.REQUIRED_APPROVALS,
          AUTOMATION_APPROVAL.APPROVED_COUNT,
          AUTOMATION_APPROVAL.UPDATED_AT)
      .from(AUTOMATION_APPROVAL)
      .where(AUTOMATION_APPROVAL.TENANT_ID.eq(tenantId))
      .and(AUTOMATION_APPROVAL.PLAN_ID.eq(planId))
      .and(AUTOMATION_APPROVAL.STATUS.eq("approved"))
      .orderBy(AUTOMATION_APPROVAL.UPDATED_AT.desc())
      .limit(1)
      .fetchOptional(
          record ->
              new ExecutionApprovalSnapshotRecord(
                  record.get(AUTOMATION_APPROVAL.ID),
                  record.get(AUTOMATION_APPROVAL.PLAN_ID),
                  record.get(AUTOMATION_APPROVAL.STATUS),
                  record.get(AUTOMATION_APPROVAL.REQUESTED_BY),
                  value(record.get(AUTOMATION_APPROVAL.REQUIRED_APPROVALS)),
                  value(record.get(AUTOMATION_APPROVAL.APPROVED_COUNT)),
                  record.get(AUTOMATION_APPROVAL.UPDATED_AT)));
}
```

> 如果你现有表字段不是 `REQUESTED_BY / APPROVED_COUNT`，按你实际生成的字段名调整；核心是返回 approved approval 的不可变快照。

---

# 9. ExecutionRequestService 修改

路径：

```txt id="aizn2w"
modules/aiops-execution/src/main/java/io/aegisops/execution/ExecutionRequestService.java
```

## 9.1 createExecution 生成 live approval snapshot

在 `createExecution(...)` 中创建 run 前加：

```java id="e15yf8"
ExecutionApprovalSnapshotRecord approvalSnapshot = null;
if (!normalized.dryRunEnabled()) {
  approvalSnapshot =
      repository
          .findLatestApprovedApprovalSnapshot(tenantId, planId)
          .orElseThrow(
              () ->
                  new AppException(
                      "EXECUTION_APPROVAL_REQUIRED",
                      "Live execution requires an approved automation approval"));
}
```

然后调用 `createQueuedRun(...)` 时传入 approvalSnapshot。

---

## 9.2 修改 `createQueuedRun(...)` 签名

```java id="dedlhj"
private ExecutionRunResponse createQueuedRun(
    String tenantId,
    PlanForExecutionRecord plan,
    String mode,
    String requestedBy,
    int attempt,
    int maxAttempts,
    String retryOfExecutionId,
    ExecutionApprovalSnapshotRecord approvalSnapshot,
    String eventType,
    String title,
    String description)
```

---

## 9.3 createRun 增加审批快照

```java id="14121w"
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
        properties.normalizedRunTimeoutSeconds(),
        approvalSnapshot == null ? null : approvalSnapshot.approvalId(),
        approvalSnapshot == null ? "{}" : json.write(approvalSnapshot),
        plan.riskLevel()));
```

---

## 9.4 retry live 需要继承 approval snapshot

`retry(...)` 中如果 previous.mode 是 `live`，也要带原审批快照：

```java id="w3qdgr"
ExecutionApprovalSnapshotRecord approvalSnapshot =
    "live".equals(previous.mode())
        ? repository
            .findLatestApprovedApprovalSnapshot(tenantId, previous.planId())
            .orElseThrow(
                () ->
                    new AppException(
                        "EXECUTION_APPROVAL_REQUIRED",
                        "Live execution retry requires an approved automation approval"))
        : null;
```

然后传入 `createQueuedRun(...)`。

---

## 9.5 toResponse 增加字段

```java id="1atbe4"
run.approvalId(),
run.approvalSnapshotJson(),
run.planRiskLevel(),
run.liveGuardPassedAt(),
```

---

# 10. AnsibleRepository 修改

## 10.1 接口增加 credential 方法

路径：

```txt id="w6iasd"
modules/aiops-execution/src/main/java/io/aegisops/execution/AnsibleRepository.java
```

新增：

```java id="y7us1d"
void createCredential(AnsibleCredentialCreateCommand command);

List<AnsibleCredentialRecord> listCredentials(String tenantId, boolean includeDisabled);

Optional<AnsibleCredentialRecord> findCredential(String tenantId, String credentialId);

boolean setCredentialEnabled(String tenantId, String credentialId, boolean enabled);
```

需要 import：

```java id="o4gjva"
import io.aegisops.execution.dto.AnsibleCredentialCreateCommand;
import io.aegisops.execution.dto.AnsibleCredentialRecord;
```

---

## 10.2 JooqAnsibleRepository 增加 credential

路径：

```txt id="1x2prb"
modules/aiops-execution/src/main/java/io/aegisops/execution/JooqAnsibleRepository.java
```

新增 static import：

```java id="37l8h1"
import static io.aegisops.persistence.jooq.Tables.ANSIBLE_CREDENTIAL_REF;
```

新增方法：

```java id="e0s0hv"
@Override
public void createCredential(AnsibleCredentialCreateCommand command) {
  dsl.insertInto(ANSIBLE_CREDENTIAL_REF)
      .set(ANSIBLE_CREDENTIAL_REF.ID, command.id())
      .set(ANSIBLE_CREDENTIAL_REF.TENANT_ID, command.tenantId())
      .set(ANSIBLE_CREDENTIAL_REF.NAME, command.name())
      .set(ANSIBLE_CREDENTIAL_REF.DESCRIPTION, command.description())
      .set(ANSIBLE_CREDENTIAL_REF.CREDENTIAL_TYPE, command.credentialType())
      .set(ANSIBLE_CREDENTIAL_REF.SECRET_REF, command.secretRef())
      .set(ANSIBLE_CREDENTIAL_REF.ENABLED, command.enabled())
      .set(ANSIBLE_CREDENTIAL_REF.CREATED_BY, command.createdBy())
      .set(ANSIBLE_CREDENTIAL_REF.CREATED_AT, DSL.currentOffsetDateTime())
      .set(ANSIBLE_CREDENTIAL_REF.UPDATED_AT, DSL.currentOffsetDateTime())
      .execute();
}

@Override
public List<AnsibleCredentialRecord> listCredentials(String tenantId, boolean includeDisabled) {
  Condition condition = ANSIBLE_CREDENTIAL_REF.TENANT_ID.eq(tenantId);
  if (!includeDisabled) {
    condition = condition.and(ANSIBLE_CREDENTIAL_REF.ENABLED.isTrue());
  }

  return dsl.select(
          ANSIBLE_CREDENTIAL_REF.ID,
          ANSIBLE_CREDENTIAL_REF.TENANT_ID,
          ANSIBLE_CREDENTIAL_REF.NAME,
          ANSIBLE_CREDENTIAL_REF.DESCRIPTION,
          ANSIBLE_CREDENTIAL_REF.CREDENTIAL_TYPE,
          ANSIBLE_CREDENTIAL_REF.SECRET_REF,
          ANSIBLE_CREDENTIAL_REF.ENABLED,
          ANSIBLE_CREDENTIAL_REF.CREATED_BY,
          ANSIBLE_CREDENTIAL_REF.CREATED_AT,
          ANSIBLE_CREDENTIAL_REF.UPDATED_AT)
      .from(ANSIBLE_CREDENTIAL_REF)
      .where(condition)
      .orderBy(ANSIBLE_CREDENTIAL_REF.CREATED_AT.desc())
      .fetch(this::toCredentialRecord);
}

@Override
public Optional<AnsibleCredentialRecord> findCredential(String tenantId, String credentialId) {
  return dsl.select(
          ANSIBLE_CREDENTIAL_REF.ID,
          ANSIBLE_CREDENTIAL_REF.TENANT_ID,
          ANSIBLE_CREDENTIAL_REF.NAME,
          ANSIBLE_CREDENTIAL_REF.DESCRIPTION,
          ANSIBLE_CREDENTIAL_REF.CREDENTIAL_TYPE,
          ANSIBLE_CREDENTIAL_REF.SECRET_REF,
          ANSIBLE_CREDENTIAL_REF.ENABLED,
          ANSIBLE_CREDENTIAL_REF.CREATED_BY,
          ANSIBLE_CREDENTIAL_REF.CREATED_AT,
          ANSIBLE_CREDENTIAL_REF.UPDATED_AT)
      .from(ANSIBLE_CREDENTIAL_REF)
      .where(ANSIBLE_CREDENTIAL_REF.TENANT_ID.eq(tenantId))
      .and(ANSIBLE_CREDENTIAL_REF.ID.eq(credentialId))
      .fetchOptional(this::toCredentialRecord);
}

@Override
public boolean setCredentialEnabled(String tenantId, String credentialId, boolean enabled) {
  return dsl.update(ANSIBLE_CREDENTIAL_REF)
          .set(ANSIBLE_CREDENTIAL_REF.ENABLED, enabled)
          .set(ANSIBLE_CREDENTIAL_REF.UPDATED_AT, DSL.currentOffsetDateTime())
          .where(ANSIBLE_CREDENTIAL_REF.TENANT_ID.eq(tenantId))
          .and(ANSIBLE_CREDENTIAL_REF.ID.eq(credentialId))
          .execute()
      > 0;
}

private AnsibleCredentialRecord toCredentialRecord(org.jooq.Record record) {
  return new AnsibleCredentialRecord(
      record.get(ANSIBLE_CREDENTIAL_REF.ID),
      record.get(ANSIBLE_CREDENTIAL_REF.TENANT_ID),
      record.get(ANSIBLE_CREDENTIAL_REF.NAME),
      record.get(ANSIBLE_CREDENTIAL_REF.DESCRIPTION),
      record.get(ANSIBLE_CREDENTIAL_REF.CREDENTIAL_TYPE),
      record.get(ANSIBLE_CREDENTIAL_REF.SECRET_REF),
      Boolean.TRUE.equals(record.get(ANSIBLE_CREDENTIAL_REF.ENABLED)),
      record.get(ANSIBLE_CREDENTIAL_REF.CREATED_BY),
      record.get(ANSIBLE_CREDENTIAL_REF.CREATED_AT),
      record.get(ANSIBLE_CREDENTIAL_REF.UPDATED_AT));
}
```

---

# 11. AnsibleResourceService 修改

## 11.1 新增 credential 管理

路径：

```txt id="kjkwd5"
modules/aiops-execution/src/main/java/io/aegisops/execution/AnsibleResourceService.java
```

新增方法：

```java id="lqvsje"
public List<AnsibleCredentialResponse> listCredentials(String tenantId, boolean includeDisabled) {
  return repository.listCredentials(tenantId, includeDisabled).stream()
      .map(this::toCredentialResponse)
      .toList();
}

public AnsibleCredentialResponse getCredential(String tenantId, String credentialId) {
  return repository
      .findCredential(tenantId, credentialId)
      .map(this::toCredentialResponse)
      .orElseThrow(
          () ->
              new AppException(
                  "ANSIBLE_CREDENTIAL_NOT_FOUND", "Ansible credential reference not found"));
}

@Transactional
public AnsibleCredentialResponse createCredential(
    String tenantId, AnsibleCredentialCreateRequest request) {
  validateCredentialRequest(request);

  String id = newId("acred");

  repository.createCredential(
      new AnsibleCredentialCreateCommand(
          id,
          tenantId,
          request.name().trim(),
          request.description(),
          normalizeCredentialType(request.credentialType()),
          request.secretRef().trim(),
          true,
          blankToDefault(request.createdBy(), "system")));

  return getCredential(tenantId, id);
}

@Transactional
public AnsibleCredentialResponse setCredentialEnabled(
    String tenantId, String credentialId, boolean enabled) {
  boolean updated = repository.setCredentialEnabled(tenantId, credentialId, enabled);
  if (!updated) {
    throw new AppException(
        "ANSIBLE_CREDENTIAL_UPDATE_FAILED", "Ansible credential reference was not updated");
  }
  return getCredential(tenantId, credentialId);
}
```

新增 helper：

```java id="359urz"
private void validateCredentialRequest(AnsibleCredentialCreateRequest request) {
  if (request == null) {
    throw new AppException(
        "ANSIBLE_CREDENTIAL_REQUEST_REQUIRED", "Ansible credential request is required");
  }

  if (request.name() == null || request.name().isBlank()) {
    throw new AppException("ANSIBLE_CREDENTIAL_NAME_REQUIRED", "Ansible credential name is required");
  }

  if (request.secretRef() == null || request.secretRef().isBlank()) {
    throw new AppException("ANSIBLE_CREDENTIAL_SECRET_REF_REQUIRED", "Ansible credential secretRef is required");
  }

  String secretRef = request.secretRef().trim().toLowerCase();
  if (!secretRef.startsWith("vault://")
      && !secretRef.startsWith("kms://")
      && !secretRef.startsWith("secrets-manager://")) {
    throw new AppException(
        "ANSIBLE_CREDENTIAL_SECRET_REF_INVALID",
        "Ansible credential secretRef must be an external secret reference");
  }
}

private String normalizeCredentialType(String value) {
  String type = value == null || value.isBlank() ? "ssh_key" : value.trim().toLowerCase();
  if (!List.of("ssh_key", "password", "token", "vault_ref").contains(type)) {
    throw new AppException(
        "ANSIBLE_CREDENTIAL_TYPE_INVALID", "Unsupported credential type: " + type);
  }
  return type;
}

private AnsibleCredentialResponse toCredentialResponse(AnsibleCredentialRecord record) {
  return new AnsibleCredentialResponse(
      record.id(),
      record.name(),
      record.description(),
      record.credentialType(),
      record.secretRef(),
      record.enabled(),
      record.createdBy(),
      record.createdAt(),
      record.updatedAt());
}
```

---

## 11.2 createPlaybook 增加 live policy 字段

创建 policy 改成：

```java id="cylxml"
repository.createPolicy(
    new AnsiblePolicyCreateCommand(
        policyId,
        tenantId,
        playbookId,
        Boolean.TRUE.equals(request.allowLive()),
        request.allowCheckExecution() == null || request.allowCheckExecution(),
        request.liveRequiresApproval() == null || request.liveRequiresApproval(),
        request.defaultCheckMode() == null || request.defaultCheckMode(),
        json.write(allowedInventoryIds),
        json.write(request.allowedExtraVars() == null ? List.of() : request.allowedExtraVars()),
        json.write(
            request.allowedLiveRiskLevels() == null
                ? List.of("low", "medium")
                : request.allowedLiveRiskLevels()),
        json.write(request.allowedCredentialRefIds() == null ? List.of() : request.allowedCredentialRefIds()),
        request.stdoutStderrMaskingEnabled() == null || request.stdoutStderrMaskingEnabled(),
        normalizeMaxExtraVarsBytes(request.maxExtraVarsBytes()),
        normalizeTimeoutSeconds(request.timeoutSeconds()),
        normalizeTimeoutSeconds(request.liveTimeoutSeconds()),
        true));
```

并在创建 playbook 前校验 credential refs：

```java id="yyxn1j"
if (request.allowedCredentialRefIds() != null) {
  for (String credentialId : request.allowedCredentialRefIds()) {
    AnsibleCredentialRecord credential =
        repository
            .findCredential(tenantId, credentialId)
            .orElseThrow(
                () ->
                    new AppException(
                        "ANSIBLE_CREDENTIAL_NOT_FOUND",
                        "Allowed credential reference not found: " + credentialId));

    if (!credential.enabled()) {
      throw new AppException(
          "ANSIBLE_CREDENTIAL_DISABLED",
          "Allowed credential reference is disabled: " + credentialId);
    }
  }
}
```

---

## 11.3 toPlaybookResponse 增加字段

```java id="lmm4ch"
private AnsiblePlaybookResponse toPlaybookResponse(
    AnsiblePlaybookRecord playbook, AnsiblePolicyRecord policy) {
  return new AnsiblePlaybookResponse(
      playbook.id(),
      playbook.name(),
      playbook.description(),
      playbook.playbookRef(),
      playbook.playbookContent(),
      json.readObjectMap(playbook.variablesSchemaJson()),
      json.readStringList(playbook.allowedTagsJson()),
      playbook.enabled(),
      policy != null && policy.allowLive(),
      policy == null || policy.allowCheckExecution(),
      policy == null || policy.liveRequiresApproval(),
      policy == null || policy.defaultCheckMode(),
      policy == null ? List.of() : json.readStringList(policy.allowedInventoryIdsJson()),
      policy == null ? List.of() : json.readStringList(policy.allowedExtraVarsJson()),
      policy == null ? List.of("low", "medium") : json.readStringList(policy.allowedLiveRiskLevelsJson()),
      policy == null ? List.of() : json.readStringList(policy.allowedCredentialRefIdsJson()),
      policy == null || policy.stdoutStderrMaskingEnabled(),
      policy == null ? 32768 : policy.maxExtraVarsBytes(),
      policy == null ? 1800 : policy.timeoutSeconds(),
      policy == null ? 1800 : policy.liveTimeoutSeconds(),
      playbook.createdBy(),
      playbook.createdAt(),
      playbook.updatedAt());
}
```

---

# 12. Credential Controller

路径：

```txt id="li9wqk"
modules/aiops-execution/src/main/java/io/aegisops/execution/AnsibleCredentialController.java
```

```java id="uxgu7t"
package io.aegisops.execution;

import io.aegisops.common.api.ApiResponse;
import io.aegisops.common.tenant.TenantContext;
import io.aegisops.execution.dto.AnsibleCredentialCreateRequest;
import io.aegisops.execution.dto.AnsibleCredentialResponse;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AnsibleCredentialController {
  private final AnsibleResourceService service;

  public AnsibleCredentialController(AnsibleResourceService service) {
    this.service = service;
  }

  @GetMapping("/api/ansible-credentials")
  public ApiResponse<List<AnsibleCredentialResponse>> list(
      @RequestParam(defaultValue = "false") boolean includeDisabled) {
    return ApiResponse.ok(service.listCredentials(TenantContext.requireTenantId(), includeDisabled));
  }

  @PostMapping("/api/ansible-credentials")
  public ApiResponse<AnsibleCredentialResponse> create(
      @RequestBody AnsibleCredentialCreateRequest request) {
    return ApiResponse.ok(service.createCredential(TenantContext.requireTenantId(), request));
  }

  @GetMapping("/api/ansible-credentials/{credentialId}")
  public ApiResponse<AnsibleCredentialResponse> get(@PathVariable String credentialId) {
    return ApiResponse.ok(service.getCredential(TenantContext.requireTenantId(), credentialId));
  }

  @PostMapping("/api/ansible-credentials/{credentialId}/enable")
  public ApiResponse<AnsibleCredentialResponse> enable(@PathVariable String credentialId) {
    return ApiResponse.ok(service.setCredentialEnabled(TenantContext.requireTenantId(), credentialId, true));
  }

  @PostMapping("/api/ansible-credentials/{credentialId}/disable")
  public ApiResponse<AnsibleCredentialResponse> disable(@PathVariable String credentialId) {
    return ApiResponse.ok(service.setCredentialEnabled(TenantContext.requireTenantId(), credentialId, false));
  }
}
```

---

# 13. Runner 修改

## 13.1 修改 `AnsibleActionPayload.java`

路径：

```txt id="84g77o"
apps/aiops-runner/src/main/java/io/aegisops/runner/executor/ansible/AnsibleActionPayload.java
```

新增字段 `credentialRefId`：

```java id="shb38y"
package io.aegisops.runner.executor.ansible;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.aegisops.common.exception.AppException;
import java.util.List;
import java.util.Map;

public record AnsibleActionPayload(
    String inventoryId,
    String playbookId,
    String credentialRefId,
    Boolean checkMode,
    List<String> tags,
    Map<String, Object> extraVars) {
  private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};

  @SuppressWarnings("unchecked")
  public static AnsibleActionPayload parse(ObjectMapper objectMapper, String json) {
    try {
      Map<String, Object> map =
          objectMapper.readValue(json == null || json.isBlank() ? "{}" : json, MAP_TYPE);

      String inventoryId = stringValue(map.get("inventoryId"));
      String playbookId = stringValue(map.get("playbookId"));
      String credentialRefId = stringValue(map.get("credentialRefId"));

      if (inventoryId.isBlank()) {
        throw new AppException("ANSIBLE_INVENTORY_ID_REQUIRED", "Ansible inventoryId is required");
      }

      if (playbookId.isBlank()) {
        throw new AppException("ANSIBLE_PLAYBOOK_ID_REQUIRED", "Ansible playbookId is required");
      }

      Object tagsValue = map.get("tags");
      List<String> tags =
          tagsValue instanceof List<?> raw
              ? raw.stream().map(String::valueOf).map(String::trim).filter(item -> !item.isBlank()).toList()
              : List.of();

      Object varsValue = map.get("extraVars");
      Map<String, Object> vars =
          varsValue instanceof Map<?, ?> raw
              ? raw.entrySet().stream()
                  .collect(
                      java.util.stream.Collectors.toMap(
                          item -> String.valueOf(item.getKey()), Map.Entry::getValue))
              : Map.of();

      Object checkValue = map.get("checkMode");
      Boolean checkMode = checkValue instanceof Boolean bool ? bool : null;

      return new AnsibleActionPayload(
          inventoryId,
          playbookId,
          credentialRefId.isBlank() ? null : credentialRefId,
          checkMode,
          tags,
          vars);
    } catch (AppException ex) {
      throw ex;
    } catch (Exception ex) {
      throw new AppException("ANSIBLE_ACTION_PAYLOAD_INVALID", "Invalid Ansible action payload");
    }
  }

  private static String stringValue(Object value) {
    return value == null ? "" : String.valueOf(value).trim();
  }
}
```

---

## 13.2 `AnsibleOutputMasker.java`

路径：

```txt id="9qt5c2"
apps/aiops-runner/src/main/java/io/aegisops/runner/executor/ansible/AnsibleOutputMasker.java
```

```java id="1jdrpl"
package io.aegisops.runner.executor.ansible;

import java.util.List;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

@Component
public class AnsibleOutputMasker {
  private static final List<Pattern> PATTERNS =
      List.of(
          Pattern.compile("(?i)(password\\s*[=:]\\s*)[^\\s,}]+"),
          Pattern.compile("(?i)(passwd\\s*[=:]\\s*)[^\\s,}]+"),
          Pattern.compile("(?i)(token\\s*[=:]\\s*)[^\\s,}]+"),
          Pattern.compile("(?i)(secret\\s*[=:]\\s*)[^\\s,}]+"),
          Pattern.compile("(?i)(private_key\\s*[=:]\\s*)[^\\s,}]+"),
          Pattern.compile("(?i)(api_key\\s*[=:]\\s*)[^\\s,}]+"));

  public String mask(String value) {
    if (value == null || value.isBlank()) {
      return "";
    }

    String result = value;
    for (Pattern pattern : PATTERNS) {
      result = pattern.matcher(result).replaceAll("$1***");
    }
    return result;
  }
}
```

---

## 13.3 修改 `AnsibleCommandPreviewBuilder.java`

确保 live 模式不加 `--check`：

```java id="px1odh"
public List<String> buildArgv(
    String binary,
    Path inventoryFile,
    Path playbookFile,
    AnsibleActionPayload payload,
    boolean checkMode) {
  List<String> argv = new ArrayList<>();
  argv.add(binary);

  if (checkMode) {
    argv.add("--check");
  }

  argv.add("-i");
  argv.add(inventoryFile.toString());
  argv.add(playbookFile.toString());

  if (payload.tags() != null && !payload.tags().isEmpty()) {
    argv.add("--tags");
    argv.add(String.join(",", payload.tags()));
  }

  if (payload.extraVars() != null && !payload.extraVars().isEmpty()) {
    argv.add("--extra-vars");
    argv.add(json.write(payload.extraVars()));
  }

  return argv;
}
```

这个方法 Phase5.6 已经基本是这样，确认不要强制加 `--check`。

---

## 13.4 修改 `AnsibleProcessRunner.java`

路径：

```txt id="wemqqb"
apps/aiops-runner/src/main/java/io/aegisops/runner/executor/ansible/AnsibleProcessRunner.java
```

```java id="evq13a"
package io.aegisops.runner.executor.ansible;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

public interface AnsibleProcessRunner {
  AnsibleProcessResult run(
      List<String> argv, Path workingDirectory, Duration timeout, boolean requireCheckMode);
}
```

---

## 13.5 修改 `ProcessBuilderAnsibleProcessRunner.java`

路径：

```txt id="vkgt30"
apps/aiops-runner/src/main/java/io/aegisops/runner/executor/ansible/ProcessBuilderAnsibleProcessRunner.java
```

完整替换：

```java id="0mequr"
package io.aegisops.runner.executor.ansible;

import io.aegisops.common.exception.AppException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.springframework.stereotype.Component;

@Component
public class ProcessBuilderAnsibleProcessRunner implements AnsibleProcessRunner {
  private final AnsibleRunnerProperties properties;

  public ProcessBuilderAnsibleProcessRunner(AnsibleRunnerProperties properties) {
    this.properties = properties;
  }

  @Override
  public AnsibleProcessResult run(
      List<String> argv, Path workingDirectory, Duration timeout, boolean requireCheckMode) {
    long started = System.currentTimeMillis();

    try {
      validateArgv(argv, requireCheckMode);

      ProcessBuilder builder = new ProcessBuilder(argv);
      builder.directory(workingDirectory.toFile());
      builder.redirectErrorStream(false);

      Process process = builder.start();
      var executor = Executors.newFixedThreadPool(2);

      try {
        Future<String> stdoutFuture = executor.submit(() -> readLimited(process.getInputStream()));
        Future<String> stderrFuture = executor.submit(() -> readLimited(process.getErrorStream()));

        boolean finished = process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS);

        if (!finished) {
          process.destroyForcibly();
          process.waitFor(2, TimeUnit.SECONDS);

          return new AnsibleProcessResult(
              -1,
              true,
              System.currentTimeMillis() - started,
              readFutureBestEffort(stdoutFuture),
              readFutureBestEffort(stderrFuture));
        }

        return new AnsibleProcessResult(
            process.exitValue(),
            false,
            System.currentTimeMillis() - started,
            readFutureBestEffort(stdoutFuture),
            readFutureBestEffort(stderrFuture));
      } finally {
        executor.shutdownNow();
      }
    } catch (AppException ex) {
      throw ex;
    } catch (Exception ex) {
      throw new AppException("ANSIBLE_PROCESS_FAILED", "Failed to execute ansible-playbook");
    }
  }

  private void validateArgv(List<String> argv, boolean requireCheckMode) {
    if (argv == null || argv.isEmpty()) {
      throw new AppException("ANSIBLE_ARGV_EMPTY", "Ansible argv is empty");
    }

    if (argv.stream().anyMatch(item -> item == null || item.isBlank())) {
      throw new AppException("ANSIBLE_ARGV_INVALID", "Ansible argv contains blank item");
    }

    validateBinary(argv.get(0));

    if (requireCheckMode && !argv.contains("--check")) {
      throw new AppException(
          "ANSIBLE_CHECK_MODE_REQUIRED", "Ansible sandbox execution must use --check");
    }

    if (argv.stream().anyMatch(this::isShellBinary)) {
      throw new AppException("ANSIBLE_SHELL_BLOCKED", "Shell execution is not allowed");
    }
  }

  private void validateBinary(String binary) {
    String baseName = baseName(binary);

    if (!"ansible-playbook".equals(baseName) && !"ansible-playbook.exe".equals(baseName)) {
      throw new AppException(
          "ANSIBLE_BINARY_NOT_ALLOWED",
          "Only ansible-playbook binary is allowed for Ansible execution");
    }
  }

  private boolean isShellBinary(String value) {
    String baseName = baseName(value);

    return List.of(
            "sh",
            "bash",
            "zsh",
            "dash",
            "fish",
            "cmd",
            "cmd.exe",
            "powershell",
            "powershell.exe",
            "pwsh",
            "pwsh.exe")
        .contains(baseName);
  }

  private String baseName(String value) {
    String normalized = value.replace('\\', '/');
    int index = normalized.lastIndexOf('/');
    String name = index >= 0 ? normalized.substring(index + 1) : normalized;
    return name.toLowerCase(Locale.ROOT);
  }

  private String readLimited(InputStream input) throws Exception {
    byte[] bytes = input.readAllBytes();
    String text = new String(bytes, StandardCharsets.UTF_8);
    int max = properties.normalizedMaxOutputChars();
    return text.length() <= max ? text : text.substring(0, max);
  }

  private String readFutureBestEffort(Future<String> future) {
    try {
      return future.get(1, TimeUnit.SECONDS);
    } catch (Exception ex) {
      return "";
    }
  }
}
```

---

## 13.6 修改 `AnsibleSafetyValidator.java`

路径：

```txt id="itkx4e"
apps/aiops-runner/src/main/java/io/aegisops/runner/executor/ansible/AnsibleSafetyValidator.java
```

新增 live 校验方法：

```java id="7kpg3r"
public void validateLive(
    AnsibleInventoryRecord inventory,
    AnsiblePlaybookRecord playbook,
    AnsiblePolicyRecord policy,
    AnsibleActionPayload payload,
    io.aegisops.execution.dto.ExecutionRunRecord run,
    AnsibleCredentialRecord credential) {
  validateCommon(inventory, playbook, policy, payload);

  if (!policy.allowLive()) {
    throw new AppException(
        "ANSIBLE_LIVE_NOT_ALLOWED", "Ansible live execution is not allowed by policy");
  }

  if (policy.liveRequiresApproval()) {
    if (run.approvalId() == null || run.approvalId().isBlank()) {
      throw new AppException(
          "ANSIBLE_LIVE_APPROVAL_REQUIRED", "Ansible live execution requires approval snapshot");
    }

    if (run.approvalSnapshotJson() == null || run.approvalSnapshotJson().isBlank()
        || "{}".equals(run.approvalSnapshotJson())) {
      throw new AppException(
          "ANSIBLE_LIVE_APPROVAL_REQUIRED", "Ansible live execution requires approval snapshot");
    }
  }

  validateLiveRiskLevel(policy, run.planRiskLevel());
  validateCredentialRef(policy, payload, credential);
}
```

新增 helper：

```java id="um7c8x"
private void validateLiveRiskLevel(AnsiblePolicyRecord policy, String riskLevel) {
  String normalizedRisk = riskLevel == null ? "" : riskLevel.toLowerCase(Locale.ROOT);
  List<String> allowed =
      json.readStringList(policy.allowedLiveRiskLevelsJson()).stream()
          .map(item -> item.toLowerCase(Locale.ROOT))
          .toList();

  if (!allowed.contains(normalizedRisk)) {
    throw new AppException(
        "ANSIBLE_LIVE_RISK_NOT_ALLOWED",
        "Ansible live execution is not allowed for risk level: " + riskLevel);
  }
}

private void validateCredentialRef(
    AnsiblePolicyRecord policy, AnsibleActionPayload payload, AnsibleCredentialRecord credential) {
  if (payload.credentialRefId() == null || payload.credentialRefId().isBlank()) {
    return;
  }

  if (credential == null) {
    throw new AppException(
        "ANSIBLE_CREDENTIAL_NOT_FOUND", "Ansible credential reference not found");
  }

  if (!credential.enabled()) {
    throw new AppException(
        "ANSIBLE_CREDENTIAL_DISABLED", "Ansible credential reference is disabled");
  }

  List<String> allowed = json.readStringList(policy.allowedCredentialRefIdsJson());
  if (!allowed.contains(payload.credentialRefId())) {
    throw new AppException(
        "ANSIBLE_CREDENTIAL_NOT_ALLOWED",
        "Ansible credential reference is not allowed by policy");
  }
}
```

需要 import：

```java id="92u8si"
import io.aegisops.execution.dto.AnsibleCredentialRecord;
```

> 说明：Phase5.7 只做 credential ref 校验，不读取 secret、不注入 secret。

---

## 13.7 修改 `AnsibleStepExecutor.java`

路径：

```txt id="atx92x"
apps/aiops-runner/src/main/java/io/aegisops/runner/executor/ansible/AnsibleStepExecutor.java
```

完整替换：

```java id="w07z6i"
package io.aegisops.runner.executor.ansible;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.aegisops.execution.AnsibleJson;
import io.aegisops.execution.AnsibleRepository;
import io.aegisops.execution.ExecutionRepository;
import io.aegisops.execution.dto.AnsibleCredentialRecord;
import io.aegisops.execution.dto.AnsibleInventoryRecord;
import io.aegisops.execution.dto.AnsiblePlaybookRecord;
import io.aegisops.execution.dto.AnsiblePolicyRecord;
import io.aegisops.execution.dto.ExecutionArtifactCreateCommand;
import io.aegisops.execution.dto.ExecutionStepRecord;
import io.aegisops.runner.executor.StepExecutionContext;
import io.aegisops.runner.executor.StepExecutionResult;
import io.aegisops.runner.executor.StepExecutor;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class AnsibleStepExecutor implements StepExecutor {
  private final AnsibleRepository repository;
  private final ExecutionRepository executionRepository;
  private final AnsibleSafetyValidator validator;
  private final AnsibleCommandPreviewBuilder commandBuilder;
  private final AnsibleWorkspaceManager workspaceManager;
  private final AnsibleProcessRunner processRunner;
  private final AnsibleOutputMasker outputMasker;
  private final AnsibleRunnerProperties properties;
  private final ObjectMapper objectMapper;
  private final AnsibleJson json;

  public AnsibleStepExecutor(
      AnsibleRepository repository,
      ExecutionRepository executionRepository,
      AnsibleSafetyValidator validator,
      AnsibleCommandPreviewBuilder commandBuilder,
      AnsibleWorkspaceManager workspaceManager,
      AnsibleProcessRunner processRunner,
      AnsibleOutputMasker outputMasker,
      AnsibleRunnerProperties properties,
      ObjectMapper objectMapper) {
    this.repository = repository;
    this.executionRepository = executionRepository;
    this.validator = validator;
    this.commandBuilder = commandBuilder;
    this.workspaceManager = workspaceManager;
    this.processRunner = processRunner;
    this.outputMasker = outputMasker;
    this.properties = properties;
    this.objectMapper = objectMapper;
    this.json = new AnsibleJson(objectMapper);
  }

  @Override
  public boolean supports(String actionType) {
    return "ansible".equals(actionType);
  }

  @Override
  public StepExecutionResult execute(StepExecutionContext context, ExecutionStepRecord step) {
    AnsibleActionPayload payload = AnsibleActionPayload.parse(objectMapper, step.actionPayloadJson());

    AnsibleInventoryRecord inventory =
        repository
            .findInventory(step.tenantId(), payload.inventoryId())
            .orElseThrow(
                () ->
                    new io.aegisops.common.exception.AppException(
                        "ANSIBLE_INVENTORY_NOT_FOUND", "Ansible inventory not found"));

    AnsiblePlaybookRecord playbook =
        repository
            .findPlaybook(step.tenantId(), payload.playbookId())
            .orElseThrow(
                () ->
                    new io.aegisops.common.exception.AppException(
                        "ANSIBLE_PLAYBOOK_NOT_FOUND", "Ansible playbook not found"));

    AnsiblePolicyRecord policy =
        repository
            .findPolicy(step.tenantId(), playbook.id())
            .orElseThrow(
                () ->
                    new io.aegisops.common.exception.AppException(
                        "ANSIBLE_POLICY_NOT_FOUND", "Ansible execution policy not found"));

    AnsibleCredentialRecord credential =
        payload.credentialRefId() == null
            ? null
            : repository.findCredential(step.tenantId(), payload.credentialRefId()).orElse(null);

    if (context.dryRun()) {
      return executeCheck(context, step, inventory, playbook, policy, payload);
    }

    if (!context.liveEnabled()) {
      return StepExecutionResult.failure(
          "Live Ansible execution is disabled.",
          List.of(
              artifact(
                  step,
                  "ansible-live-disabled.json",
                  json.write(
                      Map.of(
                          "inventoryId", inventory.id(),
                          "playbookId", playbook.id(),
                          "live", false)))));
    }

    return executeLive(context, step, inventory, playbook, policy, payload, credential);
  }

  private StepExecutionResult executeCheck(
      StepExecutionContext context,
      ExecutionStepRecord step,
      AnsibleInventoryRecord inventory,
      AnsiblePlaybookRecord playbook,
      AnsiblePolicyRecord policy,
      AnsibleActionPayload payload) {
    if (!properties.isCheckExecutionEnabled()) {
      validator.validateDryRunPreview(inventory, playbook, policy, payload);
      return previewOnly(step, inventory, playbook, payload);
    }

    validator.validateCheckExecution(inventory, playbook, policy, payload);
    return executeProcess(step, inventory, playbook, policy, payload, true, false);
  }

  private StepExecutionResult executeLive(
      StepExecutionContext context,
      ExecutionStepRecord step,
      AnsibleInventoryRecord inventory,
      AnsiblePlaybookRecord playbook,
      AnsiblePolicyRecord policy,
      AnsibleActionPayload payload,
      AnsibleCredentialRecord credential) {
    validator.validateLive(inventory, playbook, policy, payload, context.run(), credential);

    boolean marked =
        executionRepository.markLiveGuardPassed(context.run().tenantId(), context.run().id());
    if (!marked) {
      throw new io.aegisops.common.exception.AppException(
          "ANSIBLE_LIVE_GUARD_UPDATE_FAILED", "Failed to mark Ansible live guard passed");
    }

    return executeProcess(step, inventory, playbook, policy, payload, false, true);
  }

  private StepExecutionResult previewOnly(
      ExecutionStepRecord step,
      AnsibleInventoryRecord inventory,
      AnsiblePlaybookRecord playbook,
      AnsibleActionPayload payload) {
    AnsibleWorkspace workspace = workspaceManager.create(inventory, playbook);
    try {
      List<String> argv =
          commandBuilder.buildArgv(
              properties.getBinary(),
              workspace.inventoryFile(),
              workspace.playbookFile(),
              payload,
              true);

      return StepExecutionResult.success(
          "DRY-RUN Ansible playbook preview generated.",
          List.of(
              artifact(
                  step,
                  "ansible-dry-run-preview.json",
                  json.write(
                      Map.of(
                          "commandPreview", commandBuilder.toDisplayCommand(argv),
                          "argv", argv,
                          "inventoryId", inventory.id(),
                          "playbookId", playbook.id(),
                          "checkMode", true,
                          "executed", false)))));
    } finally {
      workspaceManager.cleanup(workspace);
    }
  }

  private StepExecutionResult executeProcess(
      ExecutionStepRecord step,
      AnsibleInventoryRecord inventory,
      AnsiblePlaybookRecord playbook,
      AnsiblePolicyRecord policy,
      AnsibleActionPayload payload,
      boolean checkMode,
      boolean live) {
    AnsibleWorkspace workspace = workspaceManager.create(inventory, playbook);
    try {
      List<String> argv =
          commandBuilder.buildArgv(
              properties.getBinary(),
              workspace.inventoryFile(),
              workspace.playbookFile(),
              payload,
              checkMode);

      int timeoutSeconds = live ? policy.liveTimeoutSeconds() : policy.timeoutSeconds();

      AnsibleProcessResult result =
          processRunner.run(argv, workspace.root(), Duration.ofSeconds(timeoutSeconds), checkMode);

      String stdout =
          policy.stdoutStderrMaskingEnabled() ? outputMasker.mask(result.stdout()) : result.stdout();
      String stderr =
          policy.stdoutStderrMaskingEnabled() ? outputMasker.mask(result.stderr()) : result.stderr();

      ExecutionArtifactCreateCommand artifact =
          artifact(
              step,
              live ? "ansible-live-result.json" : "ansible-check-result.json",
              json.write(
                  Map.of(
                      "commandPreview", commandBuilder.toDisplayCommand(argv),
                      "argv", argv,
                      "inventoryId", inventory.id(),
                      "playbookId", playbook.id(),
                      "checkMode", checkMode,
                      "live", live,
                      "executed", true,
                      "exitCode", result.exitCode(),
                      "timedOut", result.timedOut(),
                      "durationMillis", result.durationMillis(),
                      "stdout", stdout,
                      "stderr", stderr)));

      if (result.success()) {
        return StepExecutionResult.success(
            live ? "Ansible live execution succeeded." : "Ansible check execution succeeded.",
            List.of(artifact));
      }

      return StepExecutionResult.failure(
          result.timedOut()
              ? (live ? "Ansible live execution timed out." : "Ansible check execution timed out.")
              : (live
                  ? "Ansible live execution failed with exit code " + result.exitCode()
                  : "Ansible check execution failed with exit code " + result.exitCode()),
          List.of(artifact));
    } finally {
      workspaceManager.cleanup(workspace);
    }
  }

  private ExecutionArtifactCreateCommand artifact(ExecutionStepRecord step, String name, String content) {
    return new ExecutionArtifactCreateCommand(
        newId("artifact"),
        step.tenantId(),
        step.executionId(),
        step.id(),
        "json",
        name,
        content,
        "{}");
  }

  private String newId(String prefix) {
    return prefix + "_" + UUID.randomUUID().toString().replace("-", "");
  }
}
```

---

# 14. 单元测试

## 14.1 `AnsibleSafetyValidatorTest.java`

新增测试：

```java id="qlhbmq"
@Test
void rejectLiveWithoutApprovalSnapshot() {
  assertThrows(
      AppException.class,
      () ->
          validator.validateLive(
              inventory(true),
              playbook(true, List.of("restart")),
              policy(true, true, true, List.of("inv_1"), List.of("service_name")),
              new AnsibleActionPayload(
                  "inv_1",
                  "pb_1",
                  null,
                  false,
                  List.of("restart"),
                  Map.of("service_name", "order-service")),
              run("live", null, "{}", "medium"),
              null));
}

@Test
void rejectLiveRiskNotAllowed() {
  assertThrows(
      AppException.class,
      () ->
          validator.validateLive(
              inventory(true),
              playbook(true, List.of("restart")),
              policy(true, true, true, List.of("inv_1"), List.of("service_name")),
              new AnsibleActionPayload(
                  "inv_1",
                  "pb_1",
                  null,
                  false,
                  List.of("restart"),
                  Map.of("service_name", "order-service")),
              run("live", "approval_1", "{\"status\":\"approved\"}", "critical"),
              null));
}

@Test
void allowLiveWithApprovalAndAllowedRisk() {
  assertDoesNotThrow(
      () ->
          validator.validateLive(
              inventory(true),
              playbook(true, List.of("restart")),
              policy(true, true, true, List.of("inv_1"), List.of("service_name")),
              new AnsibleActionPayload(
                  "inv_1",
                  "pb_1",
                  null,
                  false,
                  List.of("restart"),
                  Map.of("service_name", "order-service")),
              run("live", "approval_1", "{\"status\":\"approved\"}", "medium"),
              null));
}
```

新增 helper：

```java id="l8wcvw"
private ExecutionRunRecord run(String mode, String approvalId, String approvalSnapshot, String riskLevel) {
  return new ExecutionRunRecord(
      "exec_1",
      "tenant_1",
      "inc_1",
      "plan_1",
      "running",
      mode,
      "alice",
      "runner_1",
      OffsetDateTime.now(),
      null,
      null,
      null,
      1,
      1,
      null,
      OffsetDateTime.now().plusSeconds(60),
      OffsetDateTime.now(),
      1800,
      approvalId,
      approvalSnapshot,
      riskLevel,
      null,
      OffsetDateTime.now(),
      OffsetDateTime.now());
}

private AnsiblePolicyRecord policy(
    boolean enabled,
    boolean allowCheckExecution,
    boolean allowLive,
    List<String> inventories,
    List<String> extraVars) {
  return new AnsiblePolicyRecord(
      "apol_1",
      "tenant_1",
      "pb_1",
      allowLive,
      allowCheckExecution,
      true,
      true,
      json.write(inventories),
      json.write(extraVars),
      json.write(List.of("low", "medium")),
      json.write(List.of()),
      true,
      32768,
      1800,
      1800,
      enabled,
      OffsetDateTime.now(),
      OffsetDateTime.now());
}
```

---

## 14.2 `AnsibleOutputMaskerTest.java`

路径：

```txt id="zzn22c"
apps/aiops-runner/src/test/java/io/aegisops/runner/executor/ansible/AnsibleOutputMaskerTest.java
```

```java id="cd0npg"
package io.aegisops.runner.executor.ansible;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class AnsibleOutputMaskerTest {
  @Test
  void maskSensitiveValues() {
    AnsibleOutputMasker masker = new AnsibleOutputMasker();

    String masked =
        masker.mask(
            "password=123456 token:abcdef secret = qwerty api_key=xxx normal=value");

    assertFalse(masked.contains("123456"));
    assertFalse(masked.contains("abcdef"));
    assertFalse(masked.contains("qwerty"));
    assertFalse(masked.contains("api_key=xxx"));
    assertTrue(masked.contains("normal=value"));
  }
}
```

---

## 14.3 修改 `AnsibleProcessRunnerTest.java`

新增：

```java id="v6jntg"
@Test
void liveModeAllowsArgvWithoutCheckWhenBinaryIsAnsiblePlaybook() {
  AnsibleRunnerProperties properties = new AnsibleRunnerProperties();
  ProcessBuilderAnsibleProcessRunner runner = new ProcessBuilderAnsibleProcessRunner(properties);

  assertThrows(
      AppException.class,
      () ->
          runner.run(
              List.of("python", "-i", "inventory.ini", "playbook.yml"),
              tempDir,
              Duration.ofSeconds(1),
              false));
}

@Test
void checkModeRequiresCheckFlag() {
  AnsibleRunnerProperties properties = new AnsibleRunnerProperties();
  ProcessBuilderAnsibleProcessRunner runner = new ProcessBuilderAnsibleProcessRunner(properties);

  assertThrows(
      AppException.class,
      () ->
          runner.run(
              List.of("ansible-playbook", "-i", "inventory.ini", "playbook.yml"),
              tempDir,
              Duration.ofSeconds(1),
              true));
}
```

原来的 `runner.run(argv, dir, timeout)` 都要改成：

```java id="zwne0o"
runner.run(argv, dir, timeout, true)
```

---

## 14.4 `AnsibleStepExecutorLiveTest.java`

路径：

```txt id="3h8l0j"
apps/aiops-runner/src/test/java/io/aegisops/runner/executor/ansible/AnsibleStepExecutorLiveTest.java
```

```java id="5yd3zz"
package io.aegisops.runner.executor.ansible;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.aegisops.execution.AnsibleJson;
import io.aegisops.execution.AnsibleRepository;
import io.aegisops.execution.ExecutionRepository;
import io.aegisops.execution.dto.*;
import io.aegisops.runner.executor.StepExecutionContext;
import java.nio.file.Path;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AnsibleStepExecutorLiveTest {
  @TempDir Path tempDir;

  private final ObjectMapper objectMapper = new ObjectMapper();
  private final AnsibleJson json = new AnsibleJson(objectMapper);

  @Test
  void liveExecutionRequiresApprovalSnapshot() {
    FakeAnsibleProcessRunner processRunner =
        new FakeAnsibleProcessRunner(new AnsibleProcessResult(0, false, 10, "ok", ""));
    FakeExecutionRepository executionRepository = new FakeExecutionRepository();

    AnsibleStepExecutor executor =
        executor(new FakeAnsibleRepository(), executionRepository, processRunner);

    var result =
        executor.execute(
            context("live", true, null, "{}", "medium"),
            step(payload()));

    assertFalse(result.success());
    assertFalse(processRunner.called);
  }

  @Test
  void liveExecutionRunsWithoutCheckAfterGuard() {
    FakeAnsibleProcessRunner processRunner =
        new FakeAnsibleProcessRunner(new AnsibleProcessResult(0, false, 10, "password=123456 ok", ""));
    FakeExecutionRepository executionRepository = new FakeExecutionRepository();

    AnsibleStepExecutor executor =
        executor(new FakeAnsibleRepository(), executionRepository, processRunner);

    var result =
        executor.execute(
            context("live", true, "approval_1", "{\"status\":\"approved\"}", "medium"),
            step(payload()));

    assertTrue(result.success());
    assertTrue(processRunner.called);
    assertFalse(processRunner.argv.contains("--check"));
    assertTrue(executionRepository.liveGuardPassed);
    assertTrue(result.artifacts().get(0).content().contains("password=***"));
  }

  private AnsibleStepExecutor executor(
      FakeAnsibleRepository repository,
      FakeExecutionRepository executionRepository,
      FakeAnsibleProcessRunner processRunner) {
    AnsibleRunnerProperties properties = new AnsibleRunnerProperties();
    properties.setWorkspaceRoot(tempDir.resolve("workspaces"));
    properties.setResourceRoot(tempDir.resolve("resources"));
    properties.setCleanupWorkspace(true);
    properties.setBinary("ansible-playbook");

    return new AnsibleStepExecutor(
        repository,
        executionRepository,
        new AnsibleSafetyValidator(objectMapper),
        new AnsibleCommandPreviewBuilder(objectMapper),
        new AnsibleWorkspaceManager(properties, new AnsibleContentSafetyScanner()),
        processRunner,
        new AnsibleOutputMasker(),
        properties,
        objectMapper);
  }

  private StepExecutionContext context(
      String mode,
      boolean liveEnabled,
      String approvalId,
      String approvalSnapshot,
      String riskLevel) {
    return new StepExecutionContext(
        new ExecutionRunRecord(
            "exec_1",
            "tenant_1",
            "inc_1",
            "plan_1",
            "running",
            mode,
            "alice",
            "runner_1",
            OffsetDateTime.now(),
            null,
            null,
            null,
            1,
            1,
            null,
            OffsetDateTime.now().plusSeconds(60),
            OffsetDateTime.now(),
            1800,
            approvalId,
            approvalSnapshot,
            riskLevel,
            null,
            OffsetDateTime.now(),
            OffsetDateTime.now()),
        liveEnabled);
  }

  private ExecutionStepRecord step(String payload) {
    return new ExecutionStepRecord(
        "step_1",
        "tenant_1",
        "exec_1",
        "planstep_1",
        1,
        "Restart service",
        "ansible",
        "service",
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

  private String payload() {
    return json.write(
        Map.of(
            "inventoryId", "inv_1",
            "playbookId", "pb_1",
            "checkMode", false,
            "tags", List.of("restart"),
            "extraVars", Map.of("service_name", "order-service")));
  }

  private class FakeAnsibleRepository implements AnsibleRepository {
    @Override
    public Optional<AnsibleInventoryRecord> findInventory(String tenantId, String inventoryId) {
      return Optional.of(
          new AnsibleInventoryRecord(
              "inv_1",
              tenantId,
              "prod",
              "desc",
              "inline",
              "[web]\n127.0.0.1 ansible_connection=local",
              null,
              true,
              "alice",
              OffsetDateTime.now(),
              OffsetDateTime.now()));
    }

    @Override
    public Optional<AnsiblePlaybookRecord> findPlaybook(String tenantId, String playbookId) {
      return Optional.of(
          new AnsiblePlaybookRecord(
              "pb_1",
              tenantId,
              "restart",
              "desc",
              null,
              """
              - hosts: all
                gather_facts: false
                tasks:
                  - debug:
                      msg: hello
              """,
              "{}",
              json.write(List.of("restart")),
              true,
              "alice",
              OffsetDateTime.now(),
              OffsetDateTime.now()));
    }

    @Override
    public Optional<AnsiblePolicyRecord> findPolicy(String tenantId, String playbookId) {
      return Optional.of(
          new AnsiblePolicyRecord(
              "apol_1",
              tenantId,
              playbookId,
              true,
              true,
              true,
              true,
              json.write(List.of("inv_1")),
              json.write(List.of("service_name")),
              json.write(List.of("low", "medium")),
              json.write(List.of()),
              true,
              32768,
              1800,
              1800,
              true,
              OffsetDateTime.now(),
              OffsetDateTime.now()));
    }

    @Override public Optional<AnsibleCredentialRecord> findCredential(String tenantId, String credentialId) { return Optional.empty(); }
    @Override public void createCredential(AnsibleCredentialCreateCommand command) {}
    @Override public List<AnsibleCredentialRecord> listCredentials(String tenantId, boolean includeDisabled) { return List.of(); }
    @Override public boolean setCredentialEnabled(String tenantId, String credentialId, boolean enabled) { return true; }
    @Override public void createInventory(AnsibleInventoryCreateCommand command) {}
    @Override public List<AnsibleInventoryRecord> listInventories(String tenantId, boolean includeDisabled) { return List.of(); }
    @Override public boolean setInventoryEnabled(String tenantId, String inventoryId, boolean enabled) { return true; }
    @Override public void createPlaybook(AnsiblePlaybookCreateCommand command) {}
    @Override public void createPolicy(AnsiblePolicyCreateCommand command) {}
    @Override public List<AnsiblePlaybookRecord> listPlaybooks(String tenantId, boolean includeDisabled) { return List.of(); }
    @Override public boolean setPlaybookEnabled(String tenantId, String playbookId, boolean enabled) { return true; }
  }

  private static class FakeExecutionRepository extends RunnerFakeExecutionRepositoryBase {
    boolean liveGuardPassed;

    @Override
    public boolean markLiveGuardPassed(String tenantId, String executionId) {
      liveGuardPassed = true;
      return true;
    }
  }

  private static class FakeAnsibleProcessRunner implements AnsibleProcessRunner {
    final AnsibleProcessResult result;
    boolean called;
    List<String> argv;

    FakeAnsibleProcessRunner(AnsibleProcessResult result) {
      this.result = result;
    }

    @Override
    public AnsibleProcessResult run(
        List<String> argv, Path workingDirectory, Duration timeout, boolean requireCheckMode) {
      this.called = true;
      this.argv = argv;
      return result;
    }
  }
}
```

> `RunnerFakeExecutionRepositoryBase` 需要补 `markLiveGuardPassed(...)` 默认实现。

---

## 14.5 修改 `RunnerFakeExecutionRepositoryBase.java`

新增：

```java id="k7ji5s"
@Override
public boolean markLiveGuardPassed(String tenantId, String executionId) {
  return true;
}

@Override
public Optional<ExecutionApprovalSnapshotRecord> findLatestApprovedApprovalSnapshot(
    String tenantId, String planId) {
  return Optional.empty();
}
```

需要 import：

```java id="vtj7gs"
import io.aegisops.execution.dto.ExecutionApprovalSnapshotRecord;
```

---

# 15. RunnerSafetyTest

Phase5.7 允许 live ansible 但仍然限制 ProcessBuilder。

```java id="qukni2"
@Test
void runnerUsesProcessBuilderOnlyInAnsibleProcessRunner() throws Exception {
  String content = readMainSources(Path.of("src/main/java"));

  assertFalse(content.contains("Runtime.getRuntime"));
  assertFalse(content.contains("JSch"));
  assertFalse(content.contains("sshj"));
  assertFalse(content.contains("sh -c"));
  assertFalse(content.contains("cmd /c"));

  int processBuilderCount = count(content, "new ProcessBuilder");
  assertTrue(processBuilderCount <= 1);

  Path runnerFile =
      Path.of(
          "src/main/java/io/aegisops/runner/executor/ansible/ProcessBuilderAnsibleProcessRunner.java");
  String runnerContent = Files.readString(runnerFile);

  assertTrue(runnerContent.contains("new ProcessBuilder(argv)"));
  assertTrue(runnerContent.contains("validateBinary"));
  assertTrue(runnerContent.contains("ansible-playbook"));
}
```

---

# 16. 文档

路径：

```txt id="6ja8gm"
docs/mvp/design/phase5.7-ansible-live-guard.md
```

```md id="phzkwl"
# Phase5.7 Ansible Live Execution with Approval Guard

## 目标

Phase5.7 在 Phase5.6 的 ansible-playbook --check sandbox 基础上，受控开放 live Ansible execution。

## 前提

Live execution 必须由 server 创建，并写入 approval snapshot。

## 安全要求

Live Ansible 必须满足：

- execution_run.mode = live
- aiops.execution.live-enabled = true
- execution_run.approval_id exists
- execution_run.approval_snapshot exists
- ansible_execution_policy.allow_live = true
- risk level in allowed_live_risk_levels
- inventory/playbook/policy enabled
- inventory in allowed_inventory_ids
- tags allowed
- extraVars allowed
- secret-like extraVars blocked
- optional credentialRefId must be allowed
- ProcessBuilder argv only
- no shell

## Credential Ref

Phase5.7 只保存 credential ref，不读取 secret：

- vault://
- kms://
- secrets-manager://

## Artifact

Runner writes:

- ansible-live-result.json
- stdout/stderr masked
- argv
- exitCode
- duration
- timedOut

## 不做

- SSH Adapter
- free command
- free playbook
- free inventory
- rollback auto-execution
```

---

# 17. 验证命令

```powershell id="kwbgck"
mvn -pl modules/aiops-persistence -am generate-sources
mvn -pl modules/aiops-execution -am test
mvn -pl apps/aiops-runner -am test
mvn -pl apps/aiops-server -am test
```

全量：

```powershell id="6rbxlg"
mvn test
```

---

# 18. 验收标准

```txt id="6fmsgm"
1. execution_run 有 approval_id / approval_snapshot / plan_risk_level。
2. 创建 live execution 时必须存在 approved approval snapshot。
3. runner 执行 live ansible 时再次检查 approval snapshot。
4. ansible_execution_policy.allow_live=false 时拒绝 live。
5. riskLevel 不在 allowed_live_risk_levels 时拒绝 live。
6. optional credentialRefId 必须存在、启用、在 allowedCredentialRefIds 内。
7. live ansible argv 不带 --check。
8. check ansible argv 必须带 --check。
9. ProcessBuilder 只允许 ansible-playbook binary。
10. stdout/stderr 写 artifact 前脱敏。
11. live 执行成功写 ansible-live-result.json。
12. live 执行失败写 ansible-live-result.json。
13. Runtime.exec 不存在。
14. shell 不存在。
15. SSH Adapter 仍未引入。
```

---

# 19. 建议提交信息

```txt id="unrm12"
feat(ansible): enable live execution with approval guard
```

---

# 20. Phase5.8 下一步

Phase5.8 建议做：

```txt id="bd03z6"
Rollback Plan
```

而不是马上 SSH Adapter。

原因：

```txt id="k4yx8q"
既然已经开放 Ansible live，下一步必须补回滚计划和执行报告，否则自动化执行闭环不完整。
```

Phase5.8 应该做：

```txt id="we8lz6"
rollback_plan
rollback_plan_step
rollback approval
rollback execution binding
execution before/after verification
rollback artifact
```

SSH Read-only Adapter 可以放到 Phase5.9。
