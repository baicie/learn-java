---
title: 工作记录导入导出与详情交互修复计划
type: design
status: accepted
phase: phase-20
owner: ai
created: 2026-07-19
updated: 2026-07-19
related:
  - docs/api/work-record-phase20.md
---

# Work Record Fixes Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use test-driven-development to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 修复工作记录 Excel 模板、历史字段导出、详情历史展示、编辑路由与字典项删除交互。

**Architecture:** 导入模板使用“可读标题 + 稳定字段编码”的单行表头并保持旧模板兼容；导出跨模板版本缺少字段元数据时，无值输出空单元格，有值按原始 JSON 值安全输出。Portal 复用现有 Accordion 与 AlertDialog，编辑页通过 TanStack Router 非嵌套路由约定解决父页面吞掉子页面的问题；字典删除继续映射为后端既有逻辑删除。

**Tech Stack:** Java 21、Spring Boot、Apache POI、JUnit 5、React 19、TanStack Router、Radix/shadcn、Vitest Browser。

---

### Task 1: Excel 模板显示标题并保留字段编码

**Files:**

- Modify: `modules/aiops-work-record/src/main/java/io/aegisops/workrecord/application/service/ExcelImportTemplateService.java`
- Modify: `modules/aiops-work-record/src/main/java/io/aegisops/workrecord/application/service/ExcelImportParser.java`
- Test: `modules/aiops-work-record/src/test/java/io/aegisops/workrecord/application/service/ExcelImportTemplateServiceTest.java`
- Test: `modules/aiops-work-record/src/test/java/io/aegisops/workrecord/application/service/ExcelImportParserTest.java`

- [ ] 将模板断言改为 `标题 [title]`、`状态 [status]`、`工时 [hours]` 并观察失败。
- [ ] 增加解析器测试，证明装饰表头按方括号中的稳定编码绑定，同时原始 `title`/`hours` 表头仍兼容。
- [ ] 模板生成器输出 `name + " [" + code + "]"`，解析器在去重和绑定前提取编码。
- [ ] 运行两项测试并确认通过。

### Task 2: 跨版本缺失字段元数据时兼容导出

**Files:**

- Modify: `modules/aiops-work-record/src/main/java/io/aegisops/workrecord/application/service/WorkRecordExportColumnResolver.java`
- Test: `modules/aiops-work-record/src/test/java/io/aegisops/workrecord/application/service/WorkRecordExportColumnResolverTest.java`
- Test: `modules/aiops-work-record/src/test/java/io/aegisops/workrecord/application/service/WorkRecordExportServiceTest.java`

- [ ] 新增 `change_id` 在所有记录版本均无元数据时仍能解析列的失败测试。
- [ ] 删除解析器的全局“元数据必须存在”前置拒绝，保留租户、可导出和字段权限校验。
- [ ] 证明无元数据且无值输出空单元格；有值但无历史元数据时按原始 JSON 值安全输出。
- [ ] 运行导出相关测试并确认通过。

### Task 3: 变更历史使用时间线与折叠面板

**Files:**

- Modify: `web/portal/src/components/work-records/runtime/record-history-card.tsx`
- Test: `web/portal/src/components/work-records/runtime/record-history-card.test.tsx`

- [ ] 新增测试，断言时间线事件摘要可见、变更前后值初始折叠，点击摘要后展开。
- [ ] 用现有 `Accordion` 包裹每个时间线事件，Trigger 展示动作、操作者与时间，Content 展示字段差异。
- [ ] 保留加载、错误、空状态和截断提示。
- [ ] 运行组件测试并确认通过。

### Task 4: 修复详情页编辑路由

**Files:**

- Move: `web/portal/src/routes/_authenticated/work-records/$recordId.edit.tsx` → `web/portal/src/routes/_authenticated/work-records/$recordId_.edit.tsx`
- Modify: `web/portal/src/routeTree.gen.ts`（由路由插件生成）
- Modify: `web/portal/src/routes/_authenticated/work-records/-route-permissions.test.ts`

- [ ] 增加路由层级断言，证明编辑路由不能以详情路由作为父节点，并观察失败。
- [ ] 使用 TanStack Router 尾随下划线约定解除嵌套，保持 URL `/work-records/$recordId/edit` 不变。
- [ ] 重新生成路由树并更新权限测试导入。
- [ ] 运行路由与详情页测试并确认通过。

### Task 5: 字典项增加逻辑删除能力

**Files:**

- Modify: `web/portal/src/api/dictionaries.ts`
- Modify: `web/portal/src/pages/dictionaries/index.tsx`
- Modify: `web/portal/src/pages/dictionaries/index.test.tsx`

- [ ] 新增写权限用户测试：点击“删除”后出现确认框，确认后调用 DELETE API；只读用户看不到删除操作。
- [ ] 将现有 DELETE 客户端命名为 `deleteDictItem`，明确返回的是 `enabled=false` 的逻辑删除结果。
- [ ] 使用现有 `AlertDialog` 增加“删除/恢复”，说明历史记录仍保留旧值；成功后刷新查询并提示结果。
- [ ] 运行字典页面与 API 测试并确认通过。

### Task 6: 综合验证

**Files:**

- Modify: `docs/designs/phase-20/2026-07-19-work-record-fixes-plan.md`（勾选执行结果）

- [ ] 运行工作记录和平台后端受影响测试。
- [ ] 运行 Portal format、lint、typecheck、目标测试与 build。
- [ ] 启动本地 Portal/Server 或使用浏览器测试关键路径：模板下载、导出、历史展开、编辑跳转、字典删除确认。
- [ ] 运行 `git diff --check` 并检查仅包含计划内文件。
