---
title: Automation Safety Rules
type: operation
status: accepted
phase: global
owner: ai
created: 2026-06-30
updated: 2026-08-02
related:
  - .agents/skills/aegisops/SKILL.md
  - .agents/skills/aegisops/references/automation-safety.md
  - docs/data-model.md
---

# Automation Safety Rules

# Automation Safety Rules

> **单一真相源：`.agents/skills/aegisops/SKILL.md` §13** — 本文件为顶层入口；详细风险等级、
> 执行流程、禁止动作以 SKILL 为准。AGENTS 工程层规则见 `AGENTS.md` §3.6。

## 1. 风险等级（SKILL §13.1）

### Low risk（可不经审批，但必须审计）

```txt
- query logs
- query metrics
- run inspection
- create ticket
- send notification
- read process list / disk usage / service status
```

### Medium risk（默认需审批）

```txt
- restart stateless service
- clear temp files
- refresh cache
- scale replicas
```

### High risk（必须审批 + 回滚预案 + 审计）

```txt
- rollback deployment
- modify config
- traffic switch
- restart database
- delete files
```

### Forbidden by default（除非显式审批 + 安全设计，否则禁止生成对应代码）

```txt
- rm -rf
- drop database
- truncate table
- delete Kubernetes namespace
- stop core middleware
- modify firewall
- flush Redis
- format disk
```

## 2. 执行流程（SKILL §13.2）

```txt
AI suggestion
  ↓
Policy check
  ↓
AutomationJob created
  ↓
approval if required
  ↓
runner execution   ← aiops-runner 进程，禁止 aegisops-app 直跑 SSH/Ansible
  ↓
stream logs
  ↓
post-execution health check
  ↓
audit log         ← audit_log 表，必须落库
  ↓
Incident timeline update
```

## 3. 工程层硬要求（AGENTS §3.6）

```txt
- 任何 HTTP 出口必须设置 connectTimeout 与 readTimeout，缺省值 ≤ 10s
- 任何写接口必须经 TenantGuard / PermissionGuard 双层校验；缺一即不合规
- 写操作必须经 AuditLogger 落库 audit_log；缺失即视为绕过审计
- 自动化执行类动作必须走 aiops-runner；禁止 aegisops-app 直接 SSH / Ansible
- 凭据类字段（password、apiToken、secret）禁止写入普通日志；Logback Filter 必须 mask
```

## 4. Outbox + Worker 派单（PR5 落地）

```txt
aegisops-app（写 outbox）  →  automation_outbox 表
                              ↓
                            App 内 Worker runtime（OutboxPoller）
                              ↓
                            OutboxJob.handle()        ← 业务执行
                              ↓
                            AutomationJob created     ← 审批/状态/审计
                              ↓
                            aiops-runner              ← Ansible/SSH/Webhook
```

完整链路由 PR5 落地，本文件只列安全边界。
