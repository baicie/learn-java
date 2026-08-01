---
title: Architecture Overview
type: architecture
status: accepted
phase: global
owner: ai
created: 2026-06-30
updated: 2026-08-02
related:
  - .agents/skills/aegisops/SKILL.md
  - .agents/skills/aegisops/references/architecture-boundaries.md
  - docs/data-model.md
  - docs/adr/0012-internal-mtls-task-grants.md
---

# Architecture Overview

> **单一真相源：`.agents/skills/aegisops/SKILL.md` §3（架构原则）+ §5（仓库结构）**。本文件是
> 顶层入口，不重复 SKILL 内容；详细章节引用以 SKILL 为准。

## 1. 核心原则（指向 SKILL §3）

```txt
1. Always design around Incident  （事件归一）
2. Modular monolith first, split later
3. External systems behind adapters
4. AI 不直接操作生产
5. Automation 必须有审批 / 策略 / 审计 / 回滚
6. Tenant 隔离是必选项
```

详细条文见 SKILL §3.1 ~ §3.6。

## 2. 仓库结构（指向 SKILL §5）

```txt
aegisops/
├─ apps/                       # 后端应用
│  ├─ aiops-server/            # 生产身份 aegisops-app，公共 API + Worker runtime
│  ├─ aiops-agent/             # 独立 Python AI 诊断工作流
│  └─ aiops-runner/            # 可选的高权限执行隔离进程
│
├─ modules/                    # 业务模块化单体
│  ├─ aiops-common / web / persistence / security / tenant / user
│  ├─ aiops-zabbix-adapter / datasource / asset / alert / incident
│  ├─ aiops-rca / aiops-ai-client / evidence / report
│  ├─ aiops-runbook / execution / automation / audit / notification
│  ├─ aiops-observability
│  └─ aiops-worker-runtime/    # 装入 aegisops-app，不独立部署
│
├─ web/portal/                 # React + Vite + shadcn/ui
│
├─ infra/                      # docker-compose 等基础设施
│
└─ docs/                       # 项目文档（详见 docs/mvp-roadmap.md §4）
```

完整模块列表见 SKILL §5。

## 3. 三个业务进程（SKILL §3.2）

这不是微服务架构，而是按运行时与权限边界隔离的三个业务进程：

| 进程         | 职责                                                               |
| ------------ | ------------------------------------------------------------------ |
| aegisops-app | 唯一 Java 控制面；公共 API、租户与权限、审批、Outbox 和后台作业    |
| aiops-agent  | Python AI 诊断与工作流；不访问业务数据库，不调用 Runner            |
| aiops-runner | 可选执行隔离层；使用受限数据库账号执行获批任务，不暴露公共业务 API |

App 与 Agent 使用 mTLS + 短期 Ed25519 Diagnosis Grant。App 签发 Ed25519 Execution Grant，
Runner 必须在调用执行器前验证 Grant、审批快照和不可变步骤摘要。完整通信方向和端口见
`.agents/skills/aegisops/references/architecture-boundaries.md`。

默认 `diagnostic` 部署为 App + Agent + PostgreSQL；只有显式选择 `automation` 模式时才启动
Runner。详见 `deploy/README.md`。

## 4. 禁止项

MVP 阶段禁止：

- 恢复独立 `aiops-worker` 生产进程
- 把 Runner 的 Shell、Ansible 或 SSH 执行能力合并进 App 或 Agent
- 让 Agent 直连 PostgreSQL、ClickHouse、MinIO 或 Runner
- 让 Runner 绕过 Execution Grant、审批快照或步骤摘要校验
- 任何外部系统直连（必须经过 `aiops-*-adapter`）

## 5. 历史架构文档（已 deprecated）

`docs/architecture/phase-z0-module-boundaries.md`（status=accepted, phase=z0）内容已被 SKILL §3 + §5
覆盖，仅作历史查阅；本文件为其事实源入口。
