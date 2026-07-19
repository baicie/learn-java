---
title: 工作记录动态导入模板下载实施计划
type: design
status: review
phase: work-record-20
owner: ai
created: 2026-07-19
updated: 2026-07-19
related:
  - docs/api/work-record-phase20.md
  - modules/aiops-work-record/src/main/java/io/aegisops/workrecord/api/ExcelImportController.java
  - web/portal/src/components/work-records/list/work-record-import-dialog.tsx
---

# 工作记录动态导入模板下载实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use `executing-plans` to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 用户选择已发布的工作记录表单模板后，可以下载字段与该模板当前版本一致的 Excel 导入模板，并继续提交现有异步导入任务。

**Architecture:** `aiops-work-record` 的 application service 校验租户内模板与版本归属，读取该版本的启用字段并用 Apache POI 生成 `.xlsx`。现有 `ExcelImportController` 暴露受 `work-record:import` 权限保护的只读下载端点；Portal 通过带类型 API 客户端下载 Blob，并在导入对话框中仅对已选模板启用下载按钮。

**Tech Stack:** Java 21、Spring Web MVC、Apache POI、JUnit 5、React 19、TypeScript、Vitest Browser、Radix/shadcn、i18next。

---

### Task 1: 后端动态 Excel 模板

**Files:**

- Create: `modules/aiops-work-record/src/main/java/io/aegisops/workrecord/application/service/ExcelImportTemplateService.java`
- Create: `modules/aiops-work-record/src/test/java/io/aegisops/workrecord/application/service/ExcelImportTemplateServiceTest.java`
- Modify: `modules/aiops-work-record/src/main/java/io/aegisops/workrecord/api/ExcelImportController.java`
- Modify: `modules/aiops-work-record/src/test/java/io/aegisops/workrecord/api/ExcelImportControllerWebTest.java`

- [x] **Step 1: Write the failing service test**

  构造属于 `template-1/version-1` 的模板版本，以及两个顺序相反的启用动态字段；断言生成工作簿的 `records` 表头固定为 `title/status/ownerId/recordTime`，动态字段按 `sortOrder` 追加，并在 `字段说明` 工作表中保留字段名称、编码、类型与必填性。

- [x] **Step 2: Run the service test to verify RED**

  Run: `mvn -B -ntp -pl modules/aiops-work-record -Dtest=ExcelImportTemplateServiceTest test`

  Expected: FAIL because `ExcelImportTemplateService` does not exist.

- [x] **Step 3: Implement the minimal generator**

  `generate(tenantId, templateId, templateVersionId)` 必须调用 `findByTemplateAndVersion` 守护租户和模板版本归属，再读取 `listEnabledByVersion`。工作簿只包含一个空数据表和一个字段说明表，不生成示例记录，避免示例行被误导入。

- [x] **Step 4: Run the service test to verify GREEN**

  Run: `mvn -B -ntp -pl modules/aiops-work-record -Dtest=ExcelImportTemplateServiceTest test`

  Expected: PASS.

- [x] **Step 5: Write the failing controller test**

  对 `GET /api/work-record/imports/template?templateId=template-1&templateVersionId=version-1` 断言 200、Excel Content-Type、UTF-8 attachment 文件名和服务返回的字节；无 `work-record:import` 权限时断言 403 且服务不被调用。

- [x] **Step 6: Implement and verify the controller endpoint**

  Run: `mvn -B -ntp -pl modules/aiops-work-record -Dtest=ExcelImportControllerWebTest test`

  Expected: PASS.

### Task 2: Portal 下载交互

**Files:**

- Modify: `web/portal/src/api/work-records/imports.ts`
- Modify: `web/portal/src/api/work-records/imports.test.ts`
- Modify: `web/portal/src/components/work-records/list/work-record-import-dialog.tsx`
- Create: `web/portal/src/components/work-records/list/work-record-import-dialog.test.tsx`
- Modify: `web/portal/src/i18n/locales/zh-CN/work-records.ts`
- Modify: `web/portal/src/i18n/locales/en-US/work-records.ts`

- [x] **Step 1: Write the failing API test**

  断言客户端以 `responseType: 'blob'` 请求模板端点并传递 `templateId/templateVersionId`，随后使用响应文件名下载。

- [x] **Step 2: Run the API test to verify RED**

  Run: `pnpm -C web/portal test -- src/api/work-records/imports.test.ts`

  Expected: FAIL because the download API does not exist.

- [x] **Step 3: Implement the Blob download API and verify GREEN**

  将返回值建模为 `{ blob, fileName }`，解析 RFC 5987 `Content-Disposition`，缺失响应头时回退到 `work-record-import-template.xlsx`；下载 helper 创建临时 anchor 并释放 Object URL。

  Run: `pnpm -C web/portal test -- src/api/work-records/imports.test.ts`

  Expected: PASS.

- [x] **Step 4: Write the failing dialog test**

  断言未选择模板时“下载所选模板”按钮禁用；选择具有 `currentVersionId` 的启用模板后点击按钮，会以模板 ID 和当前版本 ID 调用下载 API。

- [x] **Step 5: Implement the dialog action and verify GREEN**

  在模板选择器与文件选择器之间放置 outline Button；下载期间禁用并显示进度，错误通过现有 `notify.error` 呈现。导入提交逻辑保持不变。

  Run: `pnpm -C web/portal test -- src/components/work-records/list/work-record-import-dialog.test.tsx`

  Expected: PASS.

### Task 3: 契约文档与完整验证

**Files:**

- Modify: `docs/api/work-record-phase20.md`

- [x] **Step 1: Document the endpoint**

  在 Excel 导入表格加入 `GET /api/work-record/imports/template`，说明查询参数、权限、动态字段来源和工作簿结构，并将文档 `updated` 更新为 `2026-07-19`。

- [x] **Step 2: Run focused verification**

  Run: `mvn -B -ntp -pl modules/aiops-work-record test`

  Run: `pnpm -C web/portal test -- src/api/work-records/imports.test.ts src/components/work-records/list/work-record-import-dialog.test.tsx`

- [x] **Step 3: Run repository quality gates**

  Run: `bash scripts/ci/docs.sh`

  Run: `bash scripts/ci/backend.sh`

  Run: `bash scripts/ci/frontend.sh`

  Expected: all commands exit 0; if an unrelated baseline failure occurs, record the exact command and output instead of claiming completion.

## 验证记录

- 后端等价门禁 `mvn -B -ntp -pl modules/aiops-platform,modules/aiops-work-record,apps/aiops-server -am verify` 通过。
- Portal `format:check`、`lint`、`typecheck`、全量 `test:coverage`（95 个文件、370 项测试）与 `build` 通过。
- 文档检查和 GitHub Actions 资源策略的 PowerShell 等价检查通过；本机没有可用的 `/bin/bash`，因此未直接执行 shell 包装脚本。
- `knip` 仍报告现有 `extensions.ts`、`i18n/index.ts` 的 11 个未使用导出，并因缺少 `E2E_PORTAL_BASE_URL` 无法加载 Playwright 配置；报告不包含本次新增文件。
