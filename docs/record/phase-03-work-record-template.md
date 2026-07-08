---
title: 工作记录 Phase 03 模板与字段契约
type: design
status: review
phase: work-record
owner: ai
created: 2026-07-07
updated: 2026-07-08
related:
  - docs/record/phase-00-baseline-and-contract.md
  - docs/record/phase-02-platform-dictionary.md
---

# Phase 03：模板与字段契约

## 1. 目标

本 Phase 定义工作记录模板的核心后端契约。

交付：

```text
1. 模板 CRUD。
2. 字段 CRUD。
3. schema_json 保存。
4. schema_json 与 wr_template_field 同步。
5. 字段编码、字段类型、选项来源校验。
6. 模板变更审计。
```

## 2. 核心设计：双结构

工作记录模板不能只存 schema，也不能只存字段表。

最终采用：

```text
wr_template.schema_json
  - 前端设计器还原
  - Formily runtime 渲染
  - 字段布局
  - 控件表现

wr_template_field
  - 后端校验
  - 列表列
  - 筛选白名单
  - 导出列
  - 简单统计
```

### 2.1 为什么双结构

只存 schema 的问题：

```text
1. 后端查询筛选需要解析复杂 JSON。
2. 导出列不稳定。
3. 权限和校验难做。
4. 索引和统计不可控。
```

只存字段表的问题：

```text
1. 表单布局和运行态表现不足。
2. 后续扩展 Formily 控件困难。
3. 设计器无法完整还原。
```

双结构代价：

```text
保存时必须同步。
需要测试防止 schema 与字段索引不一致。
```

这是值得的。

## 3. 数据库模型

### 3.1 wr_template

```sql
create table wr_template (
  id varchar(64) primary key,
  tenant_id varchar(64) not null,
  name varchar(128) not null,
  code varchar(64) not null,
  description text,
  enabled boolean not null default true,
  schema_json jsonb not null default '{}'::jsonb,
  created_by varchar(64) not null,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  unique (tenant_id, code)
);
```

### 3.2 wr_template_field

```sql
create table wr_template_field (
  id varchar(64) primary key,
  tenant_id varchar(64) not null,
  template_id varchar(64) not null,
  field_name varchar(128) not null,
  field_code varchar(128) not null,
  field_type varchar(32) not null,
  required boolean not null default false,
  default_value text,
  option_source varchar(32) not null default 'static',
  dict_code varchar(128),
  options_json jsonb not null default '[]'::jsonb,
  list_visible boolean not null default false,
  filterable boolean not null default false,
  statistical boolean not null default false,
  sort_order integer not null default 0,
  enabled boolean not null default true,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  unique (tenant_id, template_id, field_code)
);
```

## 4. 字段类型

第一版支持：

```text
text          string
textarea      string
number        number
date          YYYY-MM-DD
datetime      ISO datetime
select        string
multi_select  string[]
user          userId string
boolean       boolean
```

兼容：

```text
switch -> boolean
```

不支持：

```text
cascade
subform
formula
remote_select
file
rich_text
condition_visible
condition_required
```

## 5. 字段编码规则

字段编码是动态记录的稳定锚点。

必须满足：

```text
1. 小写字母开头。
2. 只允许 a-z、0-9、_。
3. 同模板唯一。
4. 创建后不可普通编辑。
5. 不允许使用保留字段。
```

保留字段：

```text
id
tenant_id
template_id
title
status
owner_id
creator_id
record_time
created_at
updated_at
deleted_at
custom_data_json
builtin_data_json
```

## 6. Schema 扩展协议

Formily schema 中每个业务字段必须携带：

```json
{
  "x-work-record": {
    "fieldCode": "priority",
    "fieldType": "select",
    "optionSource": "dict",
    "dictCode": "record_priority",
    "listVisible": true,
    "filterable": true,
    "statistical": false
  }
}
```

### 6.1 字段提取规则

保存 schema 时：

```text
1. 深度扫描 schema properties。
2. 发现 x-work-record 后提取字段。
3. 校验 fieldCode。
4. 校验 fieldType。
5. 校验 optionSource。
6. optionSource=dict 时校验 dictCode。
7. 将字段按 schema 顺序生成 sort_order。
8. 替换 wr_template_field。
9. 保存 schema_json。
10. 写审计。
```

### 6.2 替换还是增量更新

推荐保存 schema 时替换字段索引：

```text
delete existing fields
insert extracted fields
```

但要注意：

```text
如果字段有历史记录，不应因为从 schema 中移除就物理删除。
```

因此更好的实现是：

```text
1. 对比 old fields 与 new fields。
2. 新字段 insert。
3. 仍存在字段 update 元数据。
4. 缺失字段 enabled=false。
5. 不物理 delete。
```

第一阶段为了简单可以 replace，但产品级实现应演进为 diff-sync。

## 7. API 设计

```text
GET  /api/work-record/templates
POST /api/work-record/templates
GET  /api/work-record/templates/{templateId}
PUT  /api/work-record/templates/{templateId}

GET  /api/work-record/templates/{templateId}/fields
POST /api/work-record/templates/{templateId}/fields
PUT  /api/work-record/templates/{templateId}/fields/{fieldId}

POST /api/work-record/templates/{templateId}/schema
```

### 7.1 SaveTemplateSchemaRequest

```json
{
  "schemaJson": "{}",
  "fields": [
    {
      "fieldName": "优先级",
      "fieldCode": "priority",
      "fieldType": "select",
      "required": true,
      "defaultValue": null,
      "optionSource": "dict",
      "dictCode": "record_priority",
      "optionsJson": "[]",
      "listVisible": true,
      "filterable": true,
      "statistical": false,
      "sortOrder": 10,
      "enabled": true
    }
  ]
}
```

## 8. 后端服务设计

### 8.1 WorkRecordTemplateService

职责：

```text
模板创建/更新
字段创建/更新
schema 保存
字段索引同步
审计
```

不做：

```text
不处理记录填写。
不处理导出。
不访问字典 repository。
```

### 8.2 WorkRecordFieldValidator

职责：

```text
field_code 保留字校验
保存记录时字段值校验
```

### 8.3 Repository

所有 SQL 必须包含：

```text
tenant_id
template_id
```

## 9. 模板生命周期

```text
draft-like：
  template.enabled=false
  可设计
  不可用于新建记录

enabled：
  可用于新建记录

disabled：
  不可用于新建记录
  历史记录仍可展示
```

第一版不用单独 status 字段，用 enabled 表达。

## 10. 审计

动作：

```text
work_record.template.create
work_record.template.update
work_record.template.schema.update
work_record.field.create
work_record.field.update
```

payload：

```json
{
  "templateId": "tpl_1",
  "fieldCode": "priority",
  "fieldCount": 8
}
```

## 11. 测试

后端：

```text
createTemplate 校验 name/code
schemaJson 必须是 JSON object
fieldCode 保留字拒绝
fieldType 不支持时拒绝
dict 字段缺 dictCode 拒绝
optionsJson 必须是 JSON array
saveSchema 同步字段索引
saveSchema 写审计
```

前端：

```text
extractWorkRecordFields 正确提取
保留字段拒绝
dict 字段缺 dictCode 抛错
字段顺序生成 sortOrder
```

## 12. 验收

```bash
JAVA_HOME=$(/usr/libexec/java_home -v 21) mvn -pl modules/aiops-work-record -am test
cd web/portal && pnpm run test
```

验收标准：

```text
1. 模板可以创建。
2. 字段可以创建。
3. schema 保存后字段索引同步。
4. 非法字段被拒绝。
5. 变更审计存在。
```
