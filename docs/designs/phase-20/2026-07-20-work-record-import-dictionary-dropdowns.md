---
title: 工作记录导入模板字典下拉设计
type: design
status: review
phase: work-record-20
owner: ai
created: 2026-07-20
updated: 2026-07-20
related:
  - docs/api/work-record-phase20.md
  - modules/aiops-work-record/src/main/java/io/aegisops/workrecord/application/service/ExcelImportTemplateService.java
---

# 工作记录导入模板字典下拉设计

## 范围

在现有动态 Excel 导入模板中，为 `optionSource=dict` 的 `select` 和 `multi_select`
字段增加数据验证下拉。只改变模板生成，不修改导入 API、解析规则、数据库结构或 Portal 交互。

## 设计

模板服务通过 `WorkRecordDictionaryPort` 按当前租户和字段 `dictCode` 查询启用字典项。
工作簿将字典项 `value` 写入隐藏的 `字典选项` 工作表，每个字段使用独立命名区域；
`records` 工作表从第 2 行到允许导入的第 20,001 行引用该命名区域。使用命名区域而非
内联列表，以兼容较长列表以及包含逗号等字符的字典值。

下拉保存字典 `value`，不保存 label，与工作记录持久化契约一致。禁用项仅用于历史展示，
不进入新模板；空字典不创建无效的数据验证。字典字段仍由后端在导入时执行合法值校验。

原生 `.xlsx` 数据验证只能单选，且安全边界不允许引入 VBA 宏。`multi_select` 字段因此提供
单值下拉辅助；多个值继续使用现有的逗号分隔输入格式。

## 测试

- 单选和多选字典字段生成下拉验证、隐藏选项表及命名区域。
- 字典查询携带当前租户与 `dictCode`。
- 空字典不生成无效下拉。
- 数据验证范围与导入最大行数保持一致。
