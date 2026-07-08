---
title: 工作记录 Phase 02 平台字典完整设计
type: design
status: review
phase: work-record
owner: ai
created: 2026-07-07
updated: 2026-07-08
related:
  - docs/record/phase-00-baseline-and-contract.md
---

# Phase 02：平台字典完整设计

## 1. 目标

平台字典是工作记录的基础能力，用于把动态表单里的枚举字段从代码里解耦出来。

本 Phase 要交付：

```text
1. 字典类型管理。
2. 字典项管理。
3. 默认字典初始化。
4. 禁用项历史展示。
5. 字典 API。
6. portal 字典管理页。
7. 字典变更审计。
```

## 2. 字典定位

平台字典不是工作记录私有数据。

原因：

```text
1. Incident、Runbook、Inspection 后续也可能复用字典。
2. 字典属于平台级配置，应放在 aiops-platform。
3. work-record 只引用 dict_code，不拥有字典生命周期。
```

后端落点：

```text
modules/aiops-platform/src/main/java/io/aegisops/platform/dictionary/
```

前端落点：

```text
web/portal/src/features/dictionaries/
```

## 3. 默认字典

第一批字典：

```text
record_type
  daily
  incident
  inspection
  change
  release
  handover

record_status
  draft
  submitted
  done
  archived

record_priority
  P0
  P1
  P2
  P3

env_type
  prod
  staging
  test
  dev

yes_no
  yes
  no

process_result
  success
  failed
  partial
  skipped
```

默认字典初始化规则：

```text
1. 对每个 active tenant 幂等初始化。
2. 已存在 dict_code 时不覆盖用户修改。
3. 缺失 item_value 时补齐。
4. system_builtin=true 的类型不能禁用。
5. system_builtin=true 的项可改 label，但不能物理删除。
```

## 4. 数据库模型

### 4.1 platform_dict_type

```sql
create table platform_dict_type (
  id varchar(64) primary key,
  tenant_id varchar(64) not null,
  dict_code varchar(128) not null,
  dict_name varchar(128) not null,
  description text,
  system_builtin boolean not null default false,
  enabled boolean not null default true,
  sort_order integer not null default 0,
  created_by varchar(64),
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  unique (tenant_id, dict_code)
);
```

### 4.2 platform_dict_item

```sql
create table platform_dict_item (
  id varchar(64) primary key,
  tenant_id varchar(64) not null,
  dict_type_id varchar(64) not null,
  item_label varchar(128) not null,
  item_value varchar(128) not null,
  color varchar(32),
  icon varchar(64),
  description text,
  system_builtin boolean not null default false,
  enabled boolean not null default true,
  sort_order integer not null default 0,
  extra_json jsonb not null default '{}'::jsonb,
  created_by varchar(64),
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  unique (tenant_id, dict_type_id, item_value)
);
```

## 5. 字典规则

### 5.1 value 与 label

记录数据只保存 value：

```json
{
  "priority": "P2"
}
```

展示时转 label：

```text
P2 -> 中
```

这样做的原因：

```text
1. label 可改，历史记录不用改。
2. 多语言可通过 value 再映射。
3. 导出可以选择 value 或 label。
```

### 5.2 禁用规则

禁用不是删除。

```text
enabled=false：
  新记录不能再选择。
  旧记录详情仍能显示。
  导出旧记录仍能翻译 label。
```

API 默认不返回禁用项：

```text
GET /items
```

历史展示显式包含禁用项：

```text
GET /items?includeDisabled=true
```

### 5.3 排序规则

```text
先按 sort_order asc
再按 created_at asc
```

## 6. API 设计

```text
GET  /api/platform/dictionaries
POST /api/platform/dictionaries
PUT  /api/platform/dictionaries/{dictCode}

GET  /api/platform/dictionaries/{dictCode}/items
GET  /api/platform/dictionaries/{dictCode}/items?includeDisabled=true
POST /api/platform/dictionaries/{dictCode}/items
PUT  /api/platform/dictionaries/{dictCode}/items/{itemId}
```

### 6.1 CreateDictTypeRequest

```json
{
  "dictCode": "record_priority",
  "dictName": "记录优先级",
  "description": "用于工作记录优先级",
  "sortOrder": 10,
  "enabled": true
}
```

校验：

```text
dictCode 必填，小写字母开头，只允许 a-z、0-9、_
dictName 必填
sortOrder 默认 0
enabled 默认 true
同租户 dictCode 唯一
```

### 6.2 CreateDictItemRequest

```json
{
  "itemLabel": "P2",
  "itemValue": "P2",
  "color": "default",
  "icon": null,
  "description": null,
  "sortOrder": 20,
  "enabled": true,
  "extraJson": "{}"
}
```

校验：

```text
itemLabel 必填
itemValue 必填
extraJson 必须是 JSON object
同 dict_type 下 itemValue 唯一
```

## 7. 后端服务设计

### 7.1 DictionaryService

职责：

```text
参数校验
默认值归一
调用 repository
审计写入
禁用系统内置类型保护
```

不做：

```text
不处理工作记录业务。
不直接解析模板 schema。
```

### 7.2 DictionaryRepository

职责：

```text
所有 SQL 必须带 tenant_id。
listItems 支持 includeDisabled。
update 只做软禁用。
```

### 7.3 DefaultDictionaryInitializer

职责：

```text
应用启动后为租户补齐默认字典。
```

注意：

```text
不能在 Flyway 中写入真实租户数据。
因为 tenant 是运行时数据，不是 schema 数据。
```

## 8. 前端页面设计

### 8.1 页面布局

```text
字典管理
├─ Header
├─ 左侧：字典类型列表
└─ 右侧：字典项表格 + 操作区
```

桌面端：

```text
320px 左栏 + 右侧弹性表格
```

移动端：

```text
上方 Select 选择字典类型
下方字典项表格
```

### 8.2 字典类型列表

展示字段：

```text
dictName
dictCode
enabled
systemBuiltin
```

操作：

```text
新建类型
编辑类型
启用/禁用类型
```

### 8.3 字典项表格

列：

```text
itemLabel
itemValue
enabled
sortOrder
updatedAt
actions
```

操作：

```text
新建项
编辑项
启用/禁用项
```

## 9. 前端状态

```text
TanStack Query:
  ['dict-types']
  ['dict-items', dictCode, includeDisabled]

React local:
  selectedDictCode
  open dialog
  current row

URL search:
  dictCode 可选，用于刷新后保留选中项
```

推荐 URL：

```text
/platform/dictionaries?dictCode=record_priority
```

## 10. 审计

动作：

```text
platform.dict_type.create
platform.dict_type.update
platform.dict_item.create
platform.dict_item.update
```

payload：

```json
{
  "dictCode": "record_priority",
  "itemValue": "P2",
  "enabled": false
}
```

## 11. 测试

后端：

```text
createType 校验 dictCode
createType 校验 dictName
createItem 校验 itemValue
createItem 校验 extraJson object
listItems 默认隐藏 disabled
listItems includeDisabled 返回 disabled
systemBuiltin dict type 不能禁用
create/update 写审计
```

前端：

```text
API parse 成功
禁用项 badge 正确
选择 dictCode 后加载 items
新建/编辑 dialog 表单校验
includeDisabled 开关生效
```

## 12. 验收

```bash
JAVA_HOME=$(/usr/libexec/java_home -v 21) mvn -pl modules/aiops-platform -am test
cd web/portal && pnpm run lint && pnpm run test && pnpm run build
```

验收标准：

```text
1. 默认字典存在。
2. 字典类型可新建、编辑、禁用。
3. 字典项可新建、编辑、禁用。
4. 禁用项不会出现在新记录选择项中。
5. 历史展示可读取禁用项。
6. 所有变更有审计。
```
