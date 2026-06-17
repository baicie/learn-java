# Phase5.0 Runbook Recommendation and AutomationPlan

## 目标

Phase5.0 负责根据 Incident / Alert / RCA / AI Diagnosis 推荐 Runbook，并生成 AutomationPlan 草案。

## 安全边界

- 不执行动作
- 不 SSH
- 不 Ansible
- 不 Webhook
- 所有 AutomationPlan.status = draft
- 所有 AutomationPlanStep.status = pending
- 所有 AutomationPlanStep.requiresApproval = true
- action_payload.executionAllowed = false

## API

- GET /api/runbooks
- POST /api/runbooks
- GET /api/runbooks/{runbookId}
- POST /api/runbooks/{runbookId}/enable
- POST /api/runbooks/{runbookId}/disable
- POST /api/incidents/{incidentId}/automation-plans/recommend
- GET /api/incidents/{incidentId}/automation-plans/latest
- GET /api/automation-plans/{planId}

## 后续

Phase5.1 增加审批流。
Phase5.2 增加 aiops-runner 执行器。
