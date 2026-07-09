---
title: 工作记录企业级重做路线图（对话记录）
type: research
status: draft
phase: work-record
owner: ai
created: 2026-07-09
updated: 2026-07-09
related:
  - docs/record/enterprise-roadmap.md
  - docs/record/index.md
---

# 企业级重做路线图

> 以下是工作记录企业级重做路线图的对话记录式说明。

## Phase 0：冻结现状，重新定边界

停止在当前不可用实现上继续堆功能。

主要工作：

1. 冻结当前实现
2. 明确 web/portal 是唯一前端入口
3. 明确 web/console 不再新增工作记录功能
4. 建立契约文档

## 完整路线图

详见 `docs/record/enterprise-roadmap.md`

## 验收命令

```bash
pnpm exec tsx scripts/ci/docs.ts
```
