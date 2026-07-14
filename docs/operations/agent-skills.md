---
title: Agent Skill 管理
type: operation
status: accepted
phase: global
owner: ai
created: 2026-07-14
updated: 2026-07-14
related:
  - AGENTS.md
  - .agents/skills/aegisops/SKILL.md
  - .agents/skills/portal/SKILL.md
---

# Agent Skill 管理

仓库只自动启用两个项目 Skill：

- `aegisops`：全仓规则和单一真相源。
- `portal`：`web/portal` 专用前端约定。

通用 Skill 保存在 `.agents/skill-library/`，仅作离线参考，不参与项目规则优先级。确需恢复时，将对应目录移回 `.agents/skills/`；优先使用执行环境已提供的同名 Skill，避免再次复制和分叉维护。
