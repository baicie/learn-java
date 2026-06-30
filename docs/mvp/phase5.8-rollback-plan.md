---
title: Phase5.8 Rollback Plan 实现说明
type: design
status: accepted
phase: global
owner: ai
created: 2026-06-30
updated: 2026-06-30
related: []
---
# Phase5.8 Rollback Plan 实现说明

> 本文档与 `docs/mvp/design/phase5.9.md` 设计稿配套,记录 Phase5.8 Rollback Plan 的最终实现落点,以及与设计稿的差异说明。

---

## 1. 实现总览

Phase5.8 已经完成 MVP 闭环:从 live execution 抽取 rollback payload → 生成 `rollback_plan` 草稿 → 多签审批 → 创建 `execution_kind=rollback` 的 execution run → 沿用现有 runner 执行并写回 `rollback_plan` 终态。

核心新增实体:

```txt id="i01a2b"
rollback_plan
rollback_plan_step
rollback_decision
execution_run.execution_kind
execution_run.rollback_plan_id
execution_run.rollback_of_execution_id
```

核心新增服务:

```txt id="i01a2c"
RollbackPlanService        计划生命周期
RollbackApprovalService    提交、审批、驳回
RollbackExecutionService   从已审批计划创建回滚 execution
RollbackPlanController     REST 端点
RollbackPayloadExtractor   从 step payload 抽取 rollback 块
RollbackJson               内部 JSON 序列化
```

---

## 2. 落点文件清单

### 2.1 数据库与 jOOQ

| 路径                                                                                | 说明                                 |
| ----------------------------------------------------------------------------------- | ------------------------------------ |
| `apps/aiops-server/src/main/resources/db/migration/V18__phase5_8_rollback_plan.sql` | 新增 3 张表 + 3 个 execution_run 列  |
| `modules/aiops-persistence/src/main/resources/jooq-codegen.xml`                     | 在 `<includes>` 中加入 rollback 三表 |

迁移脚本要点:

- 三表均带 `tenant_id`,租户隔离。
- `rollback_plan_step` 顺序倒序生成(`stepOrder` 由大到小),保证执行顺序与正向相反。
- `execution_run` 增加 `execution_kind`(`normal|rollback`,默认 `normal`)及 `ck_execution_run_kind` check 约束。
- `execution_run.rollback_plan_id` 与 `execution_run.rollback_of_execution_id` 不建外键,允许历史计划被删除时回溯执行记录仍可读。

### 2.2 DTO

`modules/aiops-execution/src/main/java/io/aegisops/execution/dto/` 新增:

```txt id="i01a2d"
RollbackPlanCreateRequest
RollbackPlanSubmitRequest
RollbackDecisionRequest
RollbackPlanCreateCommand
RollbackPlanStepCreateCommand
RollbackDecisionCreateCommand
RollbackPlanRecord
RollbackPlanStepRecord
RollbackDecisionRecord
RollbackPlanResponse
RollbackPlanStepResponse
RollbackDecisionResponse
RollbackExecutionCreateRequest
```

修改的既有 DTO:

```txt id="i01a2e"
ExecutionRunCreateCommand    +executionKind +rollbackPlanId +rollbackOfExecutionId
ExecutionRunRecord           +executionKind +rollbackPlanId +rollbackOfExecutionId
ExecutionRunResponse         +executionKind +rollbackPlanId +rollbackOfExecutionId
```

### 2.3 服务

| 类                         | 关键方法                                                |
| -------------------------- | ------------------------------------------------------- |
| `RollbackPlanService`      | `create` / `get` / `latestBySourceExecution` / `cancel` |
| `RollbackApprovalService`  | `submit` / `approve` / `reject`                         |
| `RollbackExecutionService` | `createExecution`                                       |
| `RollbackPayloadExtractor` | `extract(actionPayloadJson)` → `RollbackPayload`        |

### 2.4 Controller

`RollbackPlanController` 暴露:

```txt id="i01a2f"
POST   /api/rollback-plans                     create
GET    /api/rollback-plans/{id}                get
GET    /api/rollback-plans/by-execution/{id}   latestBySourceExecution
POST   /api/rollback-plans/{id}/submit         submit
POST   /api/rollback-plans/{id}/approve        approve
POST   /api/rollback-plans/{id}/reject         reject
POST   /api/rollback-plans/{id}/cancel         cancel
POST   /api/rollback-plans/{id}/executions     createExecution
```

### 2.5 Runner 侧

`RunnerExecutionService` 在 run 终态时:

- 成功 → `markSucceeded(tenantId, rollbackPlanId)`,**不再**写 `automation_plan` 状态(回滚只更新自己的 plan,不动原计划)。
- 失败 → `markFailed(tenantId, rollbackPlanId)`,同上。

未注入 `RollbackRepository` 的旧实例不再能复用,所有测试 fake 也必须同时实现 `ExecutionRepository` + `RollbackRepository`。

---

## 3. 关键设计决策

### 3.1 source execution 校验

只有满足以下全部条件才允许创建回滚计划:

- `executionKind = normal`
- `mode = live`
- `status ∈ {succeeded, failed}`

dry_run 永远不可回滚;`running` / `queued` 不可回滚(避免和正在执行的动作竞争)。

### 3.2 rollback payload 抽取

`RollbackPayloadExtractor` 仅在 `action_payload_json` 顶层存在 `rollback` Map 时返回非空 `RollbackPayload`。`actionType` 与 `actionPayload` 必填,否则抛 `AppException`。

如果整个 source execution 的 steps 全部没有 rollback 块,`create` 抛 `ROLLBACK_STEPS_EMPTY`,强制人工显式声明。

### 3.3 step 顺序

`stepOrder` 按源 step `sequenceNo` 倒序写入,执行时从 1 开始顺序触发。设计阶段曾讨论自然倒序写入,最终选择"执行顺序即数组下标"以便 runner 无差别消费。

### 3.4 风险等级

`risk_level` 默认 `high`,可由调用方覆盖。合法值:

```txt id="i01a2g"
low | medium | high | critical
```

回滚计划不重复 `automation_plan` 的 risk level 校验;回滚执行直接走 live + 已审批路径。

### 3.5 requiredApprovals

`requiredApprovals` 默认 `1`,可由调用方覆盖,合法范围 `[1, 5]`。`maxAttempts` 默认 `1`,合法范围 `[1, 3]`。

### 3.6 审批一致性

`RollbackApprovalService.approve` 内部写 decision 后,立即按 `approvedCount+1 >= requiredApprovals` 判断是否终态批准。同一 reviewer 第二次提交 decision 抛 `ROLLBACK_DECISION_EXISTS`,避免重复签。

`markApproved` 成功后 `rollback_plan.status` 变为 `approved`,之后才能 `createExecution`。

### 3.7 回滚执行的 plan status 流转

```txt id="i01a2h"
draft  --submit-->  pending_approval
pending_approval --approve--N 次-->  approved
pending_approval --reject-->  rejected
approved --createExecution-->  executing
executing --runner success-->  succeeded
executing --runner failure-->  failed
draft|pending_approval --cancel-->  cancelled
```

### 3.8 不复用 `ExecutionRequestService.createExecution`

`RollbackExecutionService.createExecution` 直接调用 `executionRepository.createRun` / `createSteps`,不经过 `ExecutionRequestService`,因为:

- 入参是 rollback_plan,不是 automation_plan;
- 需要透传 `executionKind=rollback`;
- `automation_plan` 不需要被联动修改。

---

## 4. 与设计稿的差异

| 设计稿                                      | 实现                                          | 说明                                                |
| ------------------------------------------- | --------------------------------------------- | --------------------------------------------------- |
| `RollbackPlanService.createFromExecution`   | `RollbackPlanService.create`                  | 方法名缩短,语义不变                                 |
| `RollbackPlanService.latestBySource`        | `RollbackPlanService.latestBySourceExecution` | 改名以和 controller 风格一致                        |
| `RollbackPlanController.batchCancel`        | 未实现                                        | 设计稿提到批量操作,MVP 暂不实现                     |
| `RollbackPlanStep.actionType` 默认 `manual` | 严格使用 `actionType`,无 fallback             | 设计要求显式声明                                    |
| `incident_timeline` 自动追加 rollback 事件  | 暂未追加,沿用 execution_run 自身 timeline     | runner 已有 execution lifecycle timeline,先收敛复用 |

> 上述差异在文档维护阶段保留,后续如有需求可补全。

---

## 5. 验证情况

```txt id="i01a2i"
mvn -pl modules/aiops-execution test        43 通过
mvn -pl apps/aiops-runner test              53 通过
mvn test                                    全部 BUILD SUCCESS
mvn spotless:check (相关文件)               0 违规
```

新增测试覆盖:

- `RollbackServicesTest`(17 个用例)覆盖 `RollbackPayloadExtractor`、`RollbackPlanService` 创建/取消/校验、`RollbackApprovalService` 提交/审批/驳回/重复 reviewer、`RollbackExecutionService` 创建回滚 execution。
- `RunnerExecutionServiceTest` 补 2 个用例,断言 rollback run 成功/失败时 `markSucceeded` / `markFailed` 被调用,`updatePlanStatus` 不再被调用。

---

## 6. 已知限制

- `incident_timeline` 未写 rollback 计划事件,排查时需在 `execution_run` 维度定位。
- 不支持 dry_run 回滚演练;如需演练请走 Phase5.6 dry_run 路径,不要走 Phase5.8。
- approval 决策没有"过期"机制,长期待审批不会自动回退;后续可加 cron 清理。
- `requiredApprovals > 1` 的多人审批没有按 reviewer 角色或部门过滤,完全按数量累计,符合 MVP 范围。

---

## 7. 后续阶段入口

- Phase5.9 / Phase5.10 计划在 `rollback_plan` 上加 `triggered_by` 与 `auto_rollback_policy`,本阶段不实现。
- V0.4 告警降噪增强中"影响面"会引用 rollback history,本阶段已记录 `rollbackOfExecutionId` 便于回溯。
