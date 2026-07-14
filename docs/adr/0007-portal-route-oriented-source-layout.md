---
title: Portal 采用面向路由的源码分层
type: adr
status: accepted
phase: phase-21
owner: ai
created: 2026-07-14
updated: 2026-07-14
related:
  - docs/designs/phase-21/2026-07-14-portal-source-layout-and-work-record-completion.md
  - .agents/skills/portal/SKILL.md
---

# ADR 0007：Portal 采用面向路由的源码分层

## 决策

删除 `web/portal/src/features`。页面、接口、鉴权、复用组件、查询 Hook 和纯逻辑分别归入：

```text
src/pages
src/api
src/auth
src/components
src/hooks
src/lib
src/routes
src/stores
```

业务归属通过第二级目录表达，例如 `components/work-records`，不再通过顶层 `features` 聚合页面、API、状态和模型。

工作记录模板设计器使用独立资源路由：

```text
/work-records/templates
/work-records/templates/:templateId/designer
```

## 原因

当前 `features` 同时容纳模板示例、页面入口、HTTP 客户端、领域模型和复用组件，边界含义不稳定；工作记录设计器又通过组件内部选择模板，导致资源身份不在 URL 中。新分层复用现有 TanStack 文件路由与既有顶层目录，不引入新框架或兼容层。

## 约束

- CI 禁止重新创建 `src/features` 或 import `@/features/*`。
- 页面和业务组件优先使用 `src/components/ui` 中的 shadcn 原语。
- `src/components/ui` 是 Radix 封装边界；业务代码不得直接引用 `@radix-ui/*`。
- 服务端状态继续使用 TanStack Query，跨页面客户端状态继续使用 Zustand。
- 迁移不改变后端模块边界、数据库 schema 或权限判定位置。

## 后果

既有 import、覆盖率配置、Skill、Phase 文档和路由生成文件必须同步更新。迁移完成后不保留旧路径转发文件，以免 `features` 重新成为事实依赖。
