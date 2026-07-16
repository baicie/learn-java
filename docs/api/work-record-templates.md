---
title: 工作记录模板 API
type: api
status: accepted
phase: phase-21
owner: ai
created: 2026-07-14
updated: 2026-07-15
related:
  - modules/aiops-work-record/src/main/java/io/aegisops/workrecord/api/WorkRecordTemplateController.java
  - docs/adr/0007-portal-route-oriented-source-layout.md
---

# 工作记录模板 API

统一前缀：`/api/work-record/templates`。所有接口要求有效租户上下文；读取需要 `work-record:template:read`，写操作需要 `work-record:template:write`。

| 方法 | 路径                                                                  | 用途                      |
| ---- | --------------------------------------------------------------------- | ------------------------- |
| GET  | `/api/work-record/templates?includeDisabled=true`                     | 查询模板列表              |
| GET  | `/api/work-record/templates/{templateId}`                             | 查询模板详情              |
| POST | `/api/work-record/templates`                                          | 新建模板                  |
| PUT  | `/api/work-record/templates/{templateId}`                             | 修改模板名称与说明        |
| PUT  | `/api/work-record/templates/{templateId}/draft`                       | 保存 schema/designer 草稿 |
| POST | `/api/work-record/templates/{templateId}/copy`                        | 复制模板                  |
| POST | `/api/work-record/templates/{templateId}/enable`                      | 启用模板                  |
| POST | `/api/work-record/templates/{templateId}/disable`                     | 禁用模板                  |
| POST | `/api/work-record/templates/{templateId}/default`                     | 设为租户默认模板          |
| POST | `/api/work-record/templates/{templateId}/archive`                     | 归档模板                  |
| POST | `/api/work-record/templates/{templateId}/validate-publish`            | 发布前校验                |
| POST | `/api/work-record/templates/{templateId}/publish`                     | 发布新版本                |
| GET  | `/api/work-record/templates/{templateId}/versions`                    | 查询版本列表              |
| GET  | `/api/work-record/templates/{templateId}/versions/{versionId}`        | 查询版本快照              |
| GET  | `/api/work-record/templates/{templateId}/versions/{versionId}/fields` | 查询版本字段              |

Portal 资源路由：

```text
/work-records/templates
/work-records/templates/:templateId/designer
```

模板身份必须来自路由参数；设计器不得默认选择列表第一项。

模板响应包含 `isDefault`。每个租户最多一个默认模板；只有已启用、已发布且存在当前版本的模板可以设为默认。新建记录优先选择默认模板，没有默认模板时回退到列表中的第一个可用模板。

版本字段响应新增：

| 字段             | 含义                                                      |
| ---------------- | --------------------------------------------------------- |
| `columnSpan`     | 两列表单占位，`1` 为半宽，`2` 为整行                      |
| `validationJson` | 随模板版本冻结的标准规则 JSON，支持长度、正则与数字上下限 |

校验规则属于模板版本契约，不引用平台字典或可变的中心规则。文本规则使用 `minLength`、`maxLength`、`pattern`；数字规则使用 `minimum`、`maximum`。Portal 提交前校验，服务端执行最终校验。
