---
title: 工作记录模板与表单体验修复计划
type: fix
status: accepted
phase: phase-21
owner: ai
created: 2026-07-15
updated: 2026-07-15
related:
  - docs/designs/phase-21/2026-07-14-portal-source-layout-and-work-record-completion.md
  - docs/api/work-record-templates.md
---

# Work Record Template Form UX Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use test-driven-development to implement this plan task-by-task.

**Goal:** 修复模板设计与记录填写中的错误校验、选项来源、字段布局、负责人选择、模板切换、默认模板及提交契约问题，并补齐可版本化的字段校验规则。

**Architecture:** 模板的默认标记保存于租户隔离的模板表；字段布局和校验规则保存在 Formily-compatible schema，并在发布时写入模板版本字段索引。Portal 负责即时交互反馈，后端继续作为最终契约校验方。校验规则不做成字典中心，避免中心规则变更破坏已发布模板版本的历史语义。

**Tech Stack:** React 19、TanStack Query、shadcn/ui、Vitest Browser、Spring Boot、PostgreSQL、Flyway。

## 文件结构

- `web/portal/src/components/work-records/designer/*`：字段编码即时校验、选项能力、宽度及校验规则配置与 schema 往返。
- `web/portal/src/components/work-records/runtime/*`：默认模板选择、模板切换保护、双列表单布局与客户端规则校验。
- `web/portal/src/components/work-records/list/*`：负责人下拉筛选。
- `web/portal/src/api/work-records/*`：默认模板、字段元数据和活跃用户查询契约。
- `modules/aiops-work-record/*`：模板默认值、字段布局/规则索引、用户查询与服务端值校验。
- `apps/aiops-server/src/main/resources/db/migration/V0037__work_record_template_form_ux.sql`：默认模板、字段宽度与规则 JSON 持久化。
- `docs/api/work-record-templates.md`：模板默认值与字段契约说明。

## Task 1：修复设计器字段契约

- [x] 先补失败测试：有效字段编码不显示错误，无效编码随输入显示错误。
- [x] 先补失败测试：只有 `select` / `multi_select` 展示选项来源，切换为其他类型会清理字典绑定。
- [x] 先补失败测试：schema 校验拒绝非选项字段的 `dict` 来源，复现并覆盖提交报错。
- [x] 最小实现并运行设计器定向测试。

## Task 2：加入字段布局与校验规则

- [x] 先补失败测试：字段宽度（半宽/整行）和文本/数字规则可以完成 schema 序列化与反序列化。
- [x] 先补失败测试：运行态在双列网格中按字段宽度占位。
- [x] 先补失败测试：客户端与后端都执行长度、正则、最小值、最大值规则。
- [x] 新增 V0037 migration，将 `column_span` 与 `validation_json` 写入版本字段索引。
- [x] 最小实现并运行前后端定向测试。

## Task 3：规范模板选择

- [x] 先补失败测试：新建记录优先选择租户默认模板，否则选择第一个可用模板。
- [x] 先补失败测试：只有当前模板动态字段存在非空值时，切换模板才二次确认。
- [x] 先补失败测试：设置默认模板在同租户内保持唯一并产生审计记录。
- [x] 在模板管理页加入“设为默认”，同步 API 文档与数据库约束。

## Task 4：负责人下拉

- [x] 工作记录读权限通过租户限定的 Adapter 查询活跃用户选项。
- [x] 先补失败测试：记录列表负责人筛选使用带清空项的下拉并提交用户 ID。

## Task 5：验证与交付

- [x] 运行 Portal 定向测试及 `lint`、`typecheck`、`test`、`knip`、`build`。
- [x] 运行 `aiops-work-record` 后端测试与数据库 migration 校验。
- [x] 运行文档检查及影响面 CI；记录任何既有阻断。
- [x] 检查 diff、提交中文 Conventional Commit、推送分支并创建 PR。

## 验证结果

- Portal：`lint`、`typecheck`、`build` 通过；76 个测试文件、301 项测试通过。
- 后端：`mvn -B -ntp -pl modules/aiops-work-record -am test` 通过；`aiops-work-record` 351 项测试通过。
- 文档：`scripts/ci/docs.ts` 与 `scripts/docs.ts check` 通过；143 篇文档 0 错误、0 警告，并已重新生成 `docs/INDEX.md`。
- `knip` 仍被基线中的 11 个未使用导出类型阻断，均位于本次 diff 之外，未在本修复中混入无关清理。
- Windows 当前 `bash` 指向不可用的 WSL，因此按 `scripts/ci/docs.sh` 内容直接执行了等价的 pnpm 文档命令。
