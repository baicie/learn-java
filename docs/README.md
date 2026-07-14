---
title: 项目文档导航
type: operation
status: accepted
phase: global
owner: ai
created: 2026-07-14
updated: 2026-07-14
related:
  - docs/INDEX.md
  - AGENTS.md
---

# 项目文档导航

项目规则以 [AegisOps Skill](../.agents/skills/aegisops/SKILL.md) 为准；完整文档清单由 [INDEX.md](INDEX.md) 自动生成。

## 优先阅读

1. [系统架构](architecture.md)
2. [MVP 路线图](mvp-roadmap.md)
3. [自动化安全边界](automation-safety.md)
4. [架构决策记录](adr/)
5. [API 契约](api/)

## 目录职责

| 目录                                        | 内容                                                       |
| ------------------------------------------- | ---------------------------------------------------------- |
| `architecture/`、`adr/`                     | 稳定架构与决策记录                                         |
| `api/`、`database/`、`integrations/`        | 对外契约与基础设施事实                                     |
| `designs/`、`phases/`、`reviews/`、`fixes/` | 按 Phase 管理的过程文档                                    |
| `operations/`、`runbooks/`                  | 部署、排障和操作手册                                       |
| `record/`                                   | 工作记录领域的现行契约与历史实施记录                       |
| `mvp/`、`scenarios/`                        | MVP 历史设计与端到端场景；新决策以 Skill、ADR 和路线图为准 |

## 维护命令

```bash
npx tsx scripts/docs.ts check
npx tsx scripts/docs.ts index
```

不要手工修改 `INDEX.md`，不要在仓库根目录新增项目文档。
