---
title: MVP Roadmap
type: phase
status: accepted
phase: global
owner: ai
created: 2026-06-30
updated: 2026-06-30
related:
  - .agents/skills/aegisops/SKILL.md
  - docs/adr/0001-use-java-spring-boot.md
  - docs/adr/0002-mvp-fourth-app-justification.md
  - docs/fixes/phase-5/2026-06-29-second-review-remediation.md
---

# MVP Roadmap

# MVP Roadmap

> **单一真相源：`.agents/skills/aegisops/SKILL.md` §14** — 本文件不重复 phase 内容，只列索引与
> 当前 PR 推进位次。如 §14 内容更新，本文件无需同步；如本文与 §14 冲突，以 §14 为准。

## 1. Phase 路线（指向 SKILL §14）

| Phase   | 主题              | SKILL 章节        | 当前状态（2026-06-30）       |
| ------- | ----------------- | ----------------- | ---------------------------- |
| Phase 0 | Foundation        | SKILL §14 Phase 0 | 已完成                       |
| Phase 1 | Zabbix ingestion  | SKILL §14 Phase 1 | 已完成                       |
| Phase 2 | Incident center   | SKILL §14 Phase 2 | 已完成                       |
| Phase 3 | RCA engine        | SKILL §14 Phase 3 | 已完成                       |
| Phase 4 | AI diagnosis      | SKILL §14 Phase 4 | 已完成                       |
| Phase 5 | Runbook / Ansible | SKILL §14 Phase 5 | **进行中（PR1~PR7 已落地）** |
| Phase 6 | Postmortem        | SKILL §14 Phase 6 | 未开始                       |

详细 Goal / Deliverables / Acceptance 见 SKILL §14。

## 2. 当前 PR 推进位次（PR-mega-1）

| PR  | 主题                                  | 等级  | 状态                |
| --- | ------------------------------------- | ----- | ------------------- |
| PR1 | JooqPersistenceConfiguration          | L2    | 已完成              |
| PR2 | Flyway clean-slate schema             | L3    | 已完成              |
| PR3 | Outbox 派单表 + worker 骨架           | L3    | 已完成（PR5）       |
| PR4 | ArchUnit 守门                         | L2    | 已完成              |
| PR5 | Worker full skeleton + dispatch chain | L3    | 已完成              |
| PR6 | demo-order ADR + profile              | L1+L2 | 已完成              |
| PR7 | 文档治理加固                          | L1    | **进行中**（本 PR） |
| PR8 | web/console React 骨架                | L1    | 待办                |
| PR9 | aiops-agent 边界 ADR                  | L2    | 待办                |

完整 PR 计划见 `docs/fixes/phase-5/2026-06-29-second-review-remediation.md`。

## 3. 历史 roadmap 文档（已 deprecated）

以下三份历史 roadmap 内容已被本文件 + SKILL §14 覆盖，仅作历史查阅：

| 路径                   | 状态       | 替代文档           |
| ---------------------- | ---------- | ------------------ |
| `docs/mvp/roadmap.md`  | deprecated | 本文件 + SKILL §14 |
| `docs/mvp/roadmap2.md` | deprecated | 本文件 + SKILL §14 |
| `docs/mvp/roadmap3.md` | deprecated | 本文件 + SKILL §14 |

## 4. 相关设计文档

每个 phase 在 `docs/mvp/design/` 下有详细设计文档（phase0 ~ phase8 命名），由 `scripts/docs.ts new design ... --phase phase-N` 生成；命名规则见
`.agents/skills/aegisops/references/doc-governance.md`。

## 5. 相关 ADR

- ADR 0001 — Java Spring Boot 选型（待写）
- ADR 0002 — MVP 第四个后端应用 demo-order-service 边界与可见性（`docs/adr/0002-mvp-fourth-app-justification.md`）
