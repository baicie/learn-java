---
title: 工作记录 Phase 04 Portal 原生模板设计器
type: design
status: review
phase: work-record
owner: ai
created: 2026-07-07
updated: 2026-07-08
related:
  - docs/record/phase-03-work-record-template.md
---

# Phase 04：Portal 原生模板设计器

## 1. 目标

本 Phase 交付一个真正可用的模板设计器。

不是目标：

```text
完整页面低代码
复杂拖拽画布
AntD 风格 Designable 全家桶
公式/联动/子表单
```

目标是：

```text
让记录管理员无需写 JSON，就能配置工作记录模板。
```

## 2. 最佳设计路线

推荐：

```text
portal-native field builder
  ↓
生成标准 Formily-compatible schema_json
  ↓
同步 wr_template_field
  ↓
运行态用同一 schema 渲染表单
```

### 2.1 为什么选择 portal-native

```text
1. 与 shadcn-admin 视觉一致。
2. 复杂度可控。
3. 字段设计器比画布更适合运维记录。
4. 更容易做测试。
5. 不把 AntD 依赖带入 portal 主体验。
```

### 2.2 Designable 的位置

Designable 不作为第一版主路径。

后续可作为高级模式：

```text
/work-records/designer?mode=advanced
```

前提：

```text
1. 写 ADR。
2. 明确样式隔离。
3. 不污染 portal 全局主题。
4. 输出同一 schema 协议。
```

## 3. 页面信息架构

桌面端：

```text
┌──────────────────────────────────────────────────────────────┐
│ 模板选择 / 新建模板 / 保存 / 预览 / 启用开关                 │
├───────────────┬───────────────────────────┬──────────────────┤
│ 字段组件库     │ 字段列表 / 排序             │ 属性面板          │
│               │                           │                  │
│ 单行文本       │ 1. 记录类型                 │ 字段名称          │
│ 多行文本       │ 2. 优先级                   │ 字段编码          │
│ 数字           │ 3. 处理结果                 │ 字段类型          │
│ 日期           │                           │ 必填              │
│ 单选           │                           │ 选项来源          │
│ 多选           │                           │ 字典绑定          │
│ 人员           │                           │ 列表展示          │
│ 开关           │                           │ 可筛选            │
└───────────────┴───────────────────────────┴──────────────────┘
```

移动端：

```text
Tabs:
  字段
  属性
  预览
```

## 4. 组件拆分

```text
features/work-records/components/designer/
├─ template-designer-page.tsx
├─ template-toolbar.tsx
├─ field-palette.tsx
├─ field-list.tsx
├─ field-list-item.tsx
├─ field-property-panel.tsx
├─ field-option-editor.tsx
├─ field-dict-binding.tsx
├─ template-preview.tsx
├─ template-save-dialog.tsx
└─ designer-state.ts
```

## 5. Designer State

```ts
type DesignerField = {
  id: string
  fieldName: string
  fieldCode: string
  fieldType: WorkRecordFieldType
  required: boolean
  defaultValue?: string
  optionSource: 'static' | 'dict'
  dictCode?: string
  options: Array<{ label: string; value: string }>
  listVisible: boolean
  filterable: boolean
  statistical: boolean
  enabled: boolean
}

type DesignerState = {
  templateId: string | null
  templateName: string
  templateCode: string
  selectedFieldId: string | null
  fields: DesignerField[]
  dirty: boolean
}
```

## 6. 字段组件库

第一版字段：

```text
单行文本     text
多行文本     textarea
数字         number
日期         date
日期时间     datetime
单选         select
多选         multi_select
人员         user
开关         boolean
```

每个组件库项包含：

```text
图标
名称
默认 fieldName
默认 fieldCode 前缀
默认 fieldType
默认 required=false
```

添加字段时：

```text
1. 生成临时 id。
2. 根据名称生成 fieldCode。
3. 如果冲突，加数字后缀。
4. 自动选中新字段。
5. 标记 dirty。
```

## 7. 属性面板

基础属性：

```text
字段名称
字段编码
字段类型
帮助说明
默认值
是否必填
是否启用
```

列表属性：

```text
列表展示
允许筛选
允许统计
排序
```

选项属性：

```text
optionSource:
  static
  dict

static:
  options editor

dict:
  dictCode select
```

字段编码编辑限制：

```text
新字段保存前可以编辑。
已保存字段默认锁定。
如需修改，必须点击“解锁高风险修改”，并提示会影响历史数据。
第一版可不提供解锁。
```

## 8. 预览

预览必须展示：

```text
运行态表单控件
必填标识
默认值
字典选项
禁用字段不展示
```

预览不是截图，也不是静态 JSON。

它应使用与记录填写页同一套 runtime renderer。

## 9. Schema 生成

由 DesignerState 生成 schema：

```json
{
  "type": "object",
  "properties": {
    "priority": {
      "type": "string",
      "title": "优先级",
      "required": true,
      "x-component": "Select",
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
  }
}
```

字段类型映射：

```text
text          Input
textarea      TextArea
number        NumberInput
date          DatePicker
datetime      DateTimePicker
select        Select
multi_select  MultiSelect
user          UserSelect
boolean       Switch
```

## 10. 保存流程

```text
点击保存
  ↓
前端校验 DesignerState
  ↓
生成 schema_json
  ↓
提取 wr_template_field
  ↓
POST /api/work-record/templates/{templateId}/schema
  ↓
后端再次校验
  ↓
保存 schema_json
  ↓
同步字段索引
  ↓
写审计
  ↓
invalidate templates / fields query
```

## 11. 前端校验

保存前必须校验：

```text
模板名称必填
模板编码必填
至少一个启用字段
fieldName 必填
fieldCode 必填
fieldCode 格式合法
fieldCode 不重复
fieldCode 非保留字段
select/multi_select 必须有 static options 或 dictCode
static options value 不重复
filterable 字段类型必须可筛选
```

可筛选字段类型：

```text
text
number
date
datetime
select
multi_select
user
boolean
```

## 12. 交互细节

### 12.1 离开确认

dirty=true 时：

```text
切换模板 -> confirm
离开页面 -> browser beforeunload
点击返回 -> confirm
```

### 12.2 排序

第一版可以用上移/下移按钮。

后续再加拖拽排序：

```text
dnd-kit
```

引入 dnd-kit 需要检查依赖体积，但不需要 ADR。

### 12.3 删除字段

删除按钮语义：

```text
未保存字段：从 state 删除。
已保存字段：enabled=false。
```

UI 文案用“禁用字段”，不要用“删除字段”。

## 13. 测试

纯函数：

```text
designerState -> schema
schema -> fields
fieldCode 生成
fieldCode 去重
static options 校验
dict binding 校验
```

组件：

```text
添加字段
编辑属性
上移下移
禁用字段
保存按钮 disabled/enabled
预览渲染字段
```

## 14. 验收

```text
1. 管理员可以不用写 JSON 创建模板。
2. 管理员可以添加、排序、配置字段。
3. 管理员可以绑定平台字典。
4. 预览与运行态一致。
5. 保存后后端字段索引正确。
6. 禁用字段不影响历史记录。
7. lint/test/build 通过。
```
