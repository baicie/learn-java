---
title: Architecture Overview
type: architecture
status: accepted
phase: global
owner: ai
created: 2026-06-30
updated: 2026-06-30
related:
  - .agents/skills/aegisops/SKILL.md
  - docs/data-model.md
  - docs/adr/0002-mvp-fourth-app-justification.md
---

# Architecture Overview

# Architecture Overview

> **单一真相源：`.agents/skills/aegisops/SKILL.md` §3（架构原则）+ §5（仓库结构）** — 本文件是
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
│  ├─ aiops-server/            # SKILL §3.2 三 app 之一
│  ├─ aiops-worker/            # SKILL §3.2 三 app 之一
│  ├─ aiops-runner/            # SKILL §3.2 三 app 之一
│
├─ modules/                    # 业务模块化单体
│  ├─ aiops-common / web / persistence / security / tenant / user
│  ├─ aiops-zabbix-adapter / datasource / asset / alert / incident
│  ├─ aiops-rca / aiops-ai-client / evidence / report
│  ├─ aiops-runbook / execution / automation / audit / notification
│  └─ aiops-observability
│
├─ web/console/                # React + Vite + shadcn/ui（PR8 待办）
│
├─ infra/                      # docker-compose 等基础设施
│
└─ docs/                       # 项目文档（详见 docs/mvp-roadmap.md §4）
```

完整模块列表见 SKILL §5。

## 3. 三 app 边界（SKILL §3.2）

| App          | 职责                                                          |
| ------------ | ------------------------------------------------------------- |
| aiops-server | HTTP 入口、租户、权限、业务 API、outbox 写入                  |
| aiops-worker | 异步任务消费（outbox poller、incident 聚合、RCA、复盘草稿）   |
| aiops-runner | 自动化执行隔离层（Ansible / SSH / Webhook），不接 DB 直接读写 |

详见 SKILL §3.2 与 AGENTS §3.2。

## 4. 禁止项（SKILL §3.9 / AGENTS §3.9）

MVP 阶段禁止：

- 兼容垫片 / 双写 / 灰度回退开关
- `_legacy` / `v1_` / `_old` 命名的包、Controller、字段、API 路径
- 把 demo 业务混入 aiops-server 模块
- 任何外部系统直连（必须经过 `aiops-*-adapter`）

## 5. 历史架构文档（已 deprecated）

`docs/architecture/phase-z0-module-boundaries.md`（status=accepted, phase=z0）内容已被 SKILL §3 + §5
覆盖，仅作历史查阅；本文件为其事实源入口。
