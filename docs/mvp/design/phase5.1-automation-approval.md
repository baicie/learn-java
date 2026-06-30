---
title: Phase5.1 Automation Approval
type: design
status: accepted
phase: global
owner: ai
created: 2026-06-30
updated: 2026-06-30
related: []
---
# Phase5.1 Automation Approval

## 目标

Phase5.1 为 Phase5.0 生成的 AutomationPlan draft 增加审批流。

## 状态机

AutomationPlan:

- draft
- pending_approval
- approved
- rejected
- superseded
- cancelled

AutomationApproval:

- pending
- approved
- rejected
- cancelled

ApprovalDecision:

- approve
- reject

## 默认策略

- low: 0 approval, no comment
- medium: 1 approval, no comment
- high: 1 approval, comment required
- critical: 2 approvals, comment required

## 安全边界

Phase5.1 仍然不执行计划。

禁止：

- SSH
- Ansible
- Webhook
- ProcessBuilder
- Runtime.exec
- aiops-runner dispatch

## API

- POST /api/automation-plans/{planId}/submit
- GET /api/automation-plans/{planId}/approval/latest
- POST /api/automation-approvals/{approvalId}/approve
- POST /api/automation-approvals/{approvalId}/reject
- POST /api/automation-approvals/{approvalId}/cancel
- GET /api/automation-approvals/{approvalId}

## 后续

Phase5.2 才实现执行器。
