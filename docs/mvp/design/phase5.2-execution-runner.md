# Phase5.2 Execution Runner

## 目标

Phase5.2 将 approved AutomationPlan 转成 execution_run，并由 aiops-runner 消费 queued run。

## 安全边界

- aiops-server 只创建 queued run
- aiops-runner 才处理 execution_step
- 默认仅支持 dry-run
- shell live 默认禁用
- 不使用 SSH
- 不使用 Ansible
- 不使用 Webhook
- 不使用 ProcessBuilder
- 不使用 Runtime.exec

## 状态机

AutomationPlan:

- approved
- executing
- succeeded
- failed

ExecutionRun:

- queued
- running
- succeeded
- failed
- cancelled

ExecutionStep:

- queued
- running
- succeeded
- failed
- skipped
- cancelled

## API

- POST /api/automation-plans/{planId}/executions
- GET /api/executions/{executionId}
- GET /api/automation-plans/{planId}/executions/latest
- POST /api/executions/{executionId}/cancel

## Runner

Runner polls queued execution_run and processes steps sequentially.

Phase5.2 supports:

- manual
- shell dry-run

Unsupported:

- ansible
- http
- shell live
