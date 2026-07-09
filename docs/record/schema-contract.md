---
title: 工作记录 Schema 契约
type: database
status: draft
phase: work-record
owner: platform-team
created: 2026-07-09
updated: 2026-07-09
related:
  - docs/record/enterprise-roadmap.md
  - docs/record/api-contract.md
---

# 工作记录 Schema 契约

## 1. 总原则

工作记录采用双结构：

- `wr_template_version.schema_json` 负责运行态渲染、设计器还原、详情只读展示。
- `wr_template_field` 负责字段索引、筛选白名单、导出字段、后端校验、统计。

禁止：

1. 只存 schema_json，不建字段索引。
2. 只存 wr_template_field，不存 schema_json。
3. 前后端使用不同扩展协议。
4. fieldCode 创建后随意改名。

## 2. Schema 根结构

```json
{
  "type": "object",
  "properties": {}
}
```

## 3. 字段扩展协议

统一使用嵌套对象：

```json
{
  "type": "string",
  "title": "优先级",
  "x-component": "Select",
  "x-work-record": {
    "fieldCode": "priority",
    "fieldType": "select",
    "optionSource": "dict",
    "dictCode": "record_priority",
    "listVisible": true,
    "filterable": true,
    "exportable": true,
    "statistical": true
  }
}
```

禁止继续使用新的扁平扩展字段：

- x-work-record-field-code
- x-work-record-field-type
- x-work-record-option-source
- x-work-record-dict-code
- x-work-record-list-visible
- x-work-record-filterable
- x-work-record-statistical

后端可以在过渡期兼容读取旧字段，但新 schema 只能生成 `x-work-record` 对象。

## 4. fieldCode 规则

```regex
^[a-zA-Z][a-zA-Z0-9_]{0,63}$
```

保留字段：

```
id
tenant_id
template_id
template_version_id
title
status
owner_id
creator_id
record_time
builtin_data_json
custom_data_json
created_at
updated_at
deleted_at
```

约束：

1. fieldCode 必须唯一。
2. fieldCode 创建后不可随意修改。
3. 已发布模板版本中的 fieldCode 永久不可修改。
4. 已被记录引用的 fieldCode 不可修改。
5. 删除字段实际为 enabled=false。

## 5. 支持字段类型

```
text
textarea
number
date
datetime
select
multi_select
user
boolean
```

## 6. optionSource

```
static
dict
```

static 示例：

```json
{
  "type": "string",
  "title": "影响范围",
  "enum": [
    { "label": "无影响", "value": "none" },
    { "label": "部分影响", "value": "partial" },
    { "label": "严重影响", "value": "major" }
  ],
  "x-work-record": {
    "fieldCode": "impact_scope",
    "fieldType": "select",
    "optionSource": "static",
    "listVisible": true,
    "filterable": true,
    "exportable": true,
    "statistical": false
  }
}
```

dict 示例：

```json
{
  "type": "string",
  "title": "优先级",
  "x-work-record": {
    "fieldCode": "priority",
    "fieldType": "select",
    "optionSource": "dict",
    "dictCode": "record_priority",
    "listVisible": true,
    "filterable": true,
    "exportable": true,
    "statistical": true
  }
}
```

## 7. 字段索引同步

保存模板草稿时：

1. normalize schema。
2. validate schema。
3. 抽取 x-work-record。
4. 校验 fieldCode。
5. 校验 fieldType。
6. 校验 optionSource。
7. 校验 dictCode。
8. 同步 wr_template_field。

发布模板时：

1. 再次校验 schema。
2. 生成 wr_template_version。
3. 冻结 schema_json。
4. 冻结字段索引快照。
5. 后续记录绑定 template_version_id。

## 8. 动态字段值格式

```
text: string
textarea: string
number: number
date: YYYY-MM-DD
datetime: ISO_OFFSET_DATE_TIME
select: string
multi_select: string[]
user: userId string
boolean: boolean
```

datetime 示例：

```
2026-07-09T10:30:00+09:00
```

禁止提交：

```
2026-07-09T10:30
```

前端如使用 datetime-local，提交前必须转成带 offset 的 ISO 字符串。

## 9. 后端校验

保存记录时必须校验：

1. 未知字段拒绝。
2. 禁用字段拒绝。
3. 必填字段必填。
4. 类型必须匹配。
5. static select 值必须在 enum 里。
6. dict select 值必须在启用字典项里。
7. multi_select 每个值都必须合法。
8. user 字段必须是已存在用户。
9. date / datetime 格式必须合法。
