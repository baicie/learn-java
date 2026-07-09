---
title: 工作记录表单设计器详细设计（@formily/core only）
type: design
status: review
phase: work-record
owner: ai
created: 2026-07-08
updated: 2026-07-08
related:
  - docs/adr/0005-work-record-designer-formily-core-only.md
  - docs/record/index.md §4 / §10 / §20 Phase WR-3
  - docs/record/phase-04-formily-designer.md
  - docs/record/phase-05-record-runtime.md
  - .agents/skills/aegisops/SKILL.md §4.1 / §6.15.5
---

# 工作记录表单设计器 · 详细设计

> 本文档是 `docs/adr/0005-...md` 的实施级延伸。ADR 已锁定包级白/黑名单与边界；本文档落地：组件契约、状态机、数据结构、交互流、异常路径与测试矩阵。
> 阅读顺序建议：先 ADR → 本文档 §1 §2 → phase-04 → phase-05。

## 0. 阅读层次

```text
ADR 0005
  └─ 做什么 / 不做什么 / 包级白黑名单 / CI 守卫
     ↓
本文档（本文件）
  ├─ 模块 / 组件契约
  ├─ 状态机与数据流
  ├─ Schema 双向映射（含边界情况）
  ├─ Setter 与属性面板语义
  ├─ 字典绑定与回显
  ├─ 预览与运行态共享路径
  └─ 测试矩阵
     ↓
phase-04 实施计划
phase-05 运行态
```

## 1. 目标与非目标

### 1.1 第一版目标

```text
记录管理员在 portal 内不写 JSON 即可配置工作记录模板；
生成 Formily-compatible schema_json；
同步 wr_template_field；
运行态用同一 schema 渲染表单。
```

### 1.2 非目标（再次重申，避免漂移）

```text
页面级低代码 / 拖拽布局
公式字段 / 联动显示 / 条件必填 / 子表单 / 远程接口字段
字段级权限 / 模板版本管理
引入 @formily/antd*、@formily/designable-setters、@formily/fusion 等任何 UI 适配包
引入 dnd-kit / react-dnd 鼠标拖拽
自行实现 user picker（依赖 /users 查找能力）
异步多选搜索框（cmdk / multi-select 组合）
```

## 2. 模块划分与文件布局

```text
web/portal/src/features/work-records/
├─ data/
│  ├─ field-types.ts                          9 类字段枚举（与后端对齐）
│  ├─ reserved-field-codes.ts                 保留字段码白名单
│  ├─ formily-schema.ts                       schema → wr_template_field 抽取
│  └─ designer/
│     ├─ field-types.ts                       FieldDescriptor + defaultSchemaProperty
│     ├─ schema-builder.ts                    纯函数：add/remove/move/update/setDict
│     └─ palette.ts                           字段库元数据（图标/i18n key）
├─ components/
│  ├─ formily-designer-shell.tsx              四区布局编排 + 状态归属
│  ├─ formily-runtime-form.tsx                运行时（被预览/填写页共用）
│  ├─ dict-schema-injector.ts                 纯函数：dictCode → options 注入
│  ├─ template-designer-page.tsx              路由层：加载模板、保存桥接
│  └─ designer/
│     ├─ designer-palette.tsx                 左栏：9 类字段卡片
│     ├─ designer-canvas.tsx                  中栏：字段排序/选中/删除
│     ├─ designer-property-panel.tsx          右上半：属性 setter 表单
│     ├─ designer-preview.tsx                 右下半：实时 Formily 预览
│     └─ setter/
│        ├─ types.ts                          SetterProps<T> 契约
│        ├─ index.ts                          barrel 导出
│        ├─ text-setter.tsx
│        ├─ number-setter.tsx
│        ├─ boolean-setter.tsx
│        ├─ select-setter.tsx                 静态 enum 编辑（行内）
│        └─ dict-setter.tsx                   字典码选择
```

**为什么 `data/designer/` 单独目录？** `data/` 是与运行态共享的真相源；`data/designer/` 只服务设计态编排。它不引 `formily-runtime-form`、不引 setter，方便单测。

## 3. 核心数据结构

### 3.1 `FieldDescriptor`（设计态真相源）

```ts
// web/portal/src/features/work-records/data/designer/field-types.ts
export type FieldDescriptor = {
  fieldCode: string
  fieldType: WorkRecordFieldType
  title: string
  required: boolean
  listVisible: boolean
  filterable: boolean
  statistical: boolean
  optionSource: 'static' | 'dict'
  dictCode?: string
  options?: { label: string; value: string }[]
}
```

**事实判定规则**：

```text
- fieldCode：properties 的 key，不允许运行时改名（除非无历史记录）
- fieldType：决定 x-component 与 JSON 类型，见 §4.2
- title：渲染 label
- required：Formily 顶层 required
- listVisible / filterable / statistical：仅业务元数据，不影响渲染
- optionSource='dict' → dictCode 必填
- optionSource='static' → options 可选（不写则空枚举）
- multi_select 必有 enum；select 可只有 dict
```

### 3.2 `DesignerSchema`

```ts
export type DesignerSchema = {
  type: 'object'
  properties: Record<string, Record<string, unknown>>
} & Record<string, unknown>
```

**强约束**：

```text
- type 必须是 'object'
- properties 是 Map<fieldCode, property>
- property.title、property.required 来自 FieldDescriptor
- property['x-work-record'] 是 ADR §决策 6 的真相源
- property.enum 仅 optionSource='static' 时存在
- property['x-component'] 由 runtime 注入，不在 schema 中落库
```

### 3.3 `WorkRecordSchemaField`（抽取到后端的字段索引）

```ts
// web/portal/src/features/work-records/data/formily-schema.ts
export type WorkRecordSchemaField = {
  fieldCode: string
  fieldType: WorkRecordFieldType
  fieldName: string
  required: boolean
  optionSource: 'static' | 'dict'
  dictCode?: string
  listVisible: boolean
  filterable: boolean
  statistical: boolean
  sortOrder: number
  enabled: boolean
  optionsJson: string
}
```

**对应表**：`work_record.wr_template_field`（详见 `docs/record/index.md §12.3`）。

## 4. Schema 生成与映射

### 4.1 类型映射表（与 ADR §决策 6 对齐）

```text
fieldType        JSON type      JSON format   x-component      runtime 组件
text             string         -             (无)             FormilyInput
textarea         string         -             TextArea         FormilyTextarea
number           number         -             (无)             FormilyNumber
date             string         date          (无)             FormilyDate
datetime         string         date-time     (无)             FormilyDateTime
select           string         -             (无)             FormilySelect
multi_select     array          -             (无)             FormilyMultiSelect
user             string         -             (无)             FormilyUser
boolean          boolean        -             (无)             FormilyBoolean
```

设计态生成的 property 不写 `x-component`，由 `FormilyRuntimeForm.normalizeField()` 在运行时映射（避免运行时与设计态生成的 schema 互相覆盖）。

### 4.2 x-work-record 扩展契约（field descriptor 在 schema 中的存储）

```jsonc
{
  "type": "string",
  "title": "优先级",
  "required": true,
  "x-work-record": {
    "fieldCode": "priority",
    "fieldType": "select",
    "optionSource": "dict",
    "dictCode": "record_priority",
    "listVisible": true,
    "filterable": true,
    "statistical": false,
  },
}
```

**字段约束**（来自 `workRecordSchemaExtensionSchema`）：

```ts
workRecordSchemaExtensionSchema = {
  fieldCode: z.string().min(1),
  fieldType: z.enum(workRecordFieldTypes),
  optionSource: z.enum(['static', 'dict']).default('static'),
  dictCode: z.string().optional(),
  listVisible: z.boolean().default(false),
  filterable: z.boolean().default(false),
  statistical: z.boolean().default(false),
}
```

**不可写进 x-work-record**：`title` / `required` / `enum` / `x-component`。这些属于 Formily 顶层语义。

### 4.3 schema-builder 纯函数契约

```text
emptySchema()              → DesignerSchema
normalizeSchema(value)     → DesignerSchema（容错，非法输入归零）
listFieldCodes(schema)     → string[]
addField(schema, type, code, options?)  → DesignerSchema（已存在则抛 SchemaBuilderError）
removeField(schema, code)  → DesignerSchema（不存在则抛）
moveField(schema, code, toIndex)       → DesignerSchema
moveFieldUp(schema, code) / moveFieldDown(schema, code)
updateField(schema, code, patch)       → DesignerSchema（重命名时拒绝冲突）
setDictionaryCode(schema, code, dictCode|null)
```

**关键不变量**：

```text
- 所有函数都返回新对象，不修改入参
- 失败抛 SchemaBuilderError，shell 内捕获后用 toast 展示
- 重命名 fieldCode 仅在 updateField 中受支持，且需 recordCountFor(oldCode)=0
- 保留字段码（reserved-field-codes.ts）任何路径都不允许
```

### 4.4 schema → fields 抽取契约

```text
extractWorkRecordFields(schema):
  1. visit(schema) 深度优先遍历所有 object/array
  2. 遇到 x-work-record 即解析为 WorkRecordSchemaField
  3. sortOrder 以步长 10 递增（10, 20, 30...）便于后端插入
  4. optionsJson = JSON.stringify(node.enum ?? [])
  5. 抛错：reserved fieldCode / optionSource='dict' 但无 dictCode
```

## 5. 状态机与数据流

### 5.1 设计态状态归属

```text
FormilyDesignerShell
  ├─ schema（useState）          ← 单一真相源，所有面板订阅它
  ├─ selectedFieldCode           ← 画布与属性面板的连线
  └─ lastSavedSnapshot           ← dirty 比较

子组件：
  DesignerPalette  无内部 state，仅 emit onAdd(type)
  DesignerCanvas   纯函数渲染 schema
  DesignerPropertyPanel  纯函数渲染 selectedFieldCode 的 property + emit onUpdate
  DesignerPreview  纯函数渲染 schema 预览
```

**禁止的设计**：

```text
- 在画布里持有 schema 副本
- 在属性面板里持有 form 实例
- 用 useReducer 模拟 Formily reactions（直接 schema 重建 + setState）
```

### 5.2 状态流转图

```text
初始: initialSchema → setState → render 四区
  │
  ├─ 点击 Palette 字段卡
  │    handleAddField(type)
  │      ├─ 生成 fieldCode = `${type}_${nextIndex}`（去重）
  │      ├─ addField(schema, type, code)
  │      └─ setSelectedFieldCode(code)
  │
  ├─ 点击 Canvas 字段
  │    setSelectedFieldCode(code)
  │
  ├─ 上下箭头
  │    moveFieldUp/Down → setSchema
  │
  ├─ 字段删除按钮
  │    removeField → setSchema + 选中迁移
  │
  ├─ 属性面板变更
  │    onUpdate → updateField → setSchema
  │
  └─ 点击保存
       extractWorkRecordFields(schema) → fields
       onSave({ schema, fields }) → 父组件 → API
       成功：setLastSavedSnapshot(JSON.stringify(schema))
       失败：toast error，保持 dirty
```

### 5.3 dirty 比较

```ts
const schemaSnapshot = useMemo(() => JSON.stringify(schema), [schema])
const isDirty = schemaSnapshot !== lastSavedSnapshot
```

**为什么用 JSON 序列化？** schema 是浅对象树、字段量 ≤ 50，序列化成本可忽略；好处是无需手动遍历嵌套比较，且天然对 field 顺序敏感（object key 顺序由 schema-builder 保证稳定）。

### 5.4 props → state 同步

```text
父组件加载新模板 → 传 new initialSchema
FormilyDesignerShell useEffect 重置 schema/selectedFieldCode/snapshot
```

**意图**：明确的单向同步（props → internal state）。React 19 下 useEffect 内 setState 仍受 React 警告 `react-hooks/set-state-in-effect`，用 `eslint-disable` 局部豁免并写注释解释原因（当前实现已注释）。

## 6. Setter 契约

### 6.1 公共契约

```ts
type SetterProps<TValue> = {
  value: TValue
  onChange: (next: TValue) => void
  label?: string
  description?: string
  disabled?: boolean
  required?: boolean
}

type SetterComponent<TValue> = (props: SetterProps<TValue>) => React.ReactNode
```

**铁律**：

```text
1. 受控组件：value 完全来自外部，组件内不持有 value 副本
2. onChange 即提交：每次值变化都调用，不做 debounce（schema 是同步内存对象）
3. 与 Formily 解耦：不知道 IField/field schema 的存在
4. 可独立测试：setter.test.tsx 用 @testing-library 渲染 + fireEvent.onChange 验证
```

### 6.2 Setter 清单

| Setter        | TValue         | 控件              | 校验                                    |
| ------------- | -------------- | ----------------- | --------------------------------------- |
| TextSetter    | string         | shadcn/ui Input   | required 为 true 时置 aria-required     |
| NumberSetter  | number \| null | Input type=number | 同上                                    |
| BooleanSetter | boolean        | shadcn/ui Switch  | -                                       |
| SelectSetter  | string \| null | shadcn/ui Select  | enum 由调用方传入；空 enum 退化为占位项 |
| DictSetter    | string \| null | shadcn/ui Select  | dictCodes 由调用方传入；空字典显示占位  |

**第一版范围**：

```text
✓ TextSetter / NumberSetter / BooleanSetter / DictSetter 已在 designer-property-panel 直接使用
✓ SelectSetter 通过 setter/index.tsx 的 SetterByFieldType(fieldType, optionSource, ...)
  在 select / multi_select(optionSource='static') 路径暴露给 runtime 桥接；
  designer-property-panel 第一版未启用 enum 行内编辑器（详见 §14 已知薄封装 §3）
```

### 6.3 不在 setter 范围

```text
- 富文本编辑器（不做）
- 颜色选择器（不做）
- 字段级权限编辑器（不做）
- 联动表达式编辑（不做）
- 远程接口字段配置器（不做）
```

## 7. 属性面板语义

### 7.1 字段分组

```text
基础
  ├─ title       （TextSetter）
  ├─ fieldCode   （原生 input，locked 时 readOnly + 提示）
  └─ fieldType   （只读，由 Palette 决定，运行时变更需要重建）

选项（仅 select / multi_select 出现）
  ├─ optionSource  （segmented: static / dict）
  └─ dictCode      （DictSetter，仅当 optionSource='dict'）

校验
  └─ required    （BooleanSetter）

列表/筛选/统计
  ├─ listVisible （BooleanSetter）
  ├─ filterable  （BooleanSetter）
  └─ statistical （BooleanSetter）
```

### 7.2 fieldCode 锁定策略

```text
recordCountByField[fieldCode] > 0 → 锁定（readOnly + 提示文案）
recordCountByField[fieldCode] === 0 → 允许编辑

锁定提示文案 i18n key: workRecords.designer.property.fieldCodeLockedHint
```

**为什么不在第一版提供"解锁"按钮？** ADR §决策 5 已明确"不做字段级权限 / 模板版本管理"。后续若需要走单独 ADR。

### 7.3 字典切换的二阶行为

```text
optionSource: static → dict
  ├─ 清空 enum（property.enum 删除）
  ├─ dictCode 必填（如已填则保留）
  └─ 校验必须在 updateField 内完成

optionSource: dict → static
  ├─ 清空 dictCode
  └─ options 暂不提供编辑器（设计态第一版不暴露，运行时缺 enum 时 Select 退化为空）
```

## 8. 字典绑定

### 8.1 字典码列表来源

```ts
// 父组件传入 dictCodes: ReadonlyArray<string>
<FALLBACK_DICT_CODES = ['record_type', 'record_priority', 'record_env', 'record_status']>
```

**真实环境**：父组件 `template-designer-page.tsx` 调用 `useDictionaries()` 拉取 platform_dict_type 列表，提取 `dictCode` 数组传入。

### 8.2 字典项运行态注入

```text
FormilyRuntimeForm
  ↓
dict-schema-injector.ts (纯函数)
  扫描 schema → 收集所有 optionSource='dict' 的 dictCode
  ↓
合并外部传入的 dictionaries map
  ↓
将 enum 注入对应 property（保留原 order）
  ↓
输出新 schema → Schema.compile
```

**纯函数约束**：`injectDictionaryOptions(schema, dictMap)` 不引 React、不引 form、不引 fetch。

### 8.3 详情页 vs 编辑页的字典策略

```text
详情页：includeDisabled=true（保证历史 value 总能找到 label）
编辑/新建：includeDisabled=false（避免禁用项被新提交）
```

设计态**只关心 dictCode**，不关心启用项；启用项的过滤在运行态由后端列表接口完成。

## 9. 预览与运行态共享路径

### 9.1 共享栈

```text
DesignerPreview
  └─ FormilyRuntimeForm (readOnly, initialValues={})
       └─ 注入字典 → 归一化 → Schema.compile → SchemaField

RecordForm (新建/编辑)
  └─ FormilyRuntimeForm (可写, initialValues=customDataJson)

RecordReadonlyView
  └─ FormilyRuntimeForm (readOnly, initialValues=customDataJson)
```

**好处**：设计态预览 = 真实运行态组件；不存在"预览看着对、提交后错"。

### 9.2 预览的唯一差异

```text
设计态预览：
  - 不请求业务字典（dictCodes 是父组件传入的全集）
  - dictMap 通常为空（预览不展示选项）
  - 字段值全部空（initialValues={}）

运行态：
  - 注入真实 dictMap
  - initialValues 来自 customDataJson
```

**预览与运行态一致性**：当字段为 select + optionSource='dict' 时，预览不展示下拉内容；这是有意的——预览职责是验证 schema 结构与字段顺序，不是验证选项内容。

## 10. 异常路径与边界

### 10.1 保留字段码

```text
id / tenant_id / template_id / title / status / owner_id / creator_id /
record_time / created_at / updated_at / deleted_at / custom_data_json / builtin_data_json

任何路径添加/重命名为保留码：抛 SchemaBuilderError。
```

### 10.2 字段重命名冲突

```text
updateField(schema, 'priority', { fieldCode: 'env' })
  若 env 已存在 → 抛 "cannot rename priority to existing fieldCode env"
  若 env 不存在 → 删除旧 key，插入新 key（properties 顺序按重命名时间更新）
```

**为什么不直接 setState？** schema-builder 是纯函数，shell 直接调用抛错，由 shell 捕获后 toast 报错。

### 10.3 字典切换到 static 但无 options

```text
设计态允许 optionSource='static' 且 options=[]：
  - schema.enum 不写入（schema-builder 不写空数组）
  - 运行时 FormilySelect 渲染为空 Select
  - 后端 validator 拒绝提交空值（若 required=true）
```

**是否需要默认空选项？** 否；运维侧少见，避免误把 "未配置" 当成 "配置了空选项"。

### 10.3a 运行时字典注入失败

```text
dict-schema-injector.ts:
  optionSource='dict' 且 dictMap[dictCode] 缺失：
    → node.enum 写入 [] （覆盖任何原始 enum）
    → FormilySelect / FormilyMultiSelect 渲染为空
    → 后端 validator 拒绝提交空值（若 required=true）

optionSource='static':
    → dict-schema-injector 不覆盖原 enum（visit 条件仅命中 optionSource='dict'）
    → 运行时展示原 enum
```

**为什么 dict 路径会覆盖原 enum？** 该函数语义是"用真实字典项替换占位"，即使占位不存在也写空。这是显式选择——避免设计态残留 stale enum 误导用户。

### 10.4 模板切换未保存

```text
当前实现：FormilyDesignerShell 内部维护 dirty，外部调用方负责确认
未实现：beforeunload 拦截 / Confirm Dialog
原因：第一版未做（phase-04 §12.1 描述了语义但 ADR §决策 5 不强制）
```

### 10.5 API 失败回滚

```text
点击保存
  → onSave({ schema, fields }) 抛错
  → toast.error
  → 不更新 lastSavedSnapshot
  → isDirty 保持 true
  → schema/selectedFieldCode 保持原值
```

## 11. 国际化

### 11.1 namespace 与 key

```text
workRecords.designer
  ├─ toolbar.{save, saving, dirty}
  ├─ palette.{region, text, textDescription, textarea, ..., boolean}
  ├─ canvas.{region, moveUp, moveDown, remove}
  ├─ property.{region, empty, title, fieldCode, fieldCodeLockedHint,
  │            fieldType, optionSource, optionSourceStatic, optionSourceDict,
  │            dictCode, required, indexes, listVisible, filterable, statistical}
  └─ preview.{region, title, empty}
```

**禁用动态 key 拼接**：

```text
✗ t(`workRecords.designer.palette.${fieldType}`)
✓ t(option.i18nKey)  （静态映射，已在 palette.ts 写死）
```

### 11.2 占位文案

```text
DictSetter 无字典：'暂无可用字典'（中文 hardcode，后续统一抽 i18n）
FormilyUnknown：'未知控件'
```

这些 hardcode 在 §已知薄封装 段统一登记。

## 12. 测试矩阵

### 12.1 纯函数（必跑）

```text
data/designer/field-types.test.ts
  - defaultFieldDescriptor 填充字段
  - defaultSchemaProperty 9 类映射
  - reserved fieldCode 拒绝

data/designer/schema-builder.test.ts
  - addField 重复抛错
  - removeField 不存在抛错
  - moveFieldUp / moveFieldDown 边界
  - moveField 中间 / 头 / 尾
  - updateField 重命名
  - updateField 重命名冲突抛错
  - setDictionaryCode 切换 static/dict
  - normalizeSchema 容忍非法输入

data/designer/palette.test.ts
  - 9 类字段元数据完整
  - getFieldTypeOption 未知抛错

data/formily-schema.test.ts
  - extractWorkRecordFields 顺序
  - reserved fieldCode 拒绝
  - dict 无 dictCode 拒绝

data/reserved-field-codes.test.ts
  - 大小写无关
  - 真实保留字命中
```

### 12.2 组件（必跑）

```text
components/designer/designer-palette.test.tsx
  - 9 类字段按钮渲染
  - onAdd 回调

components/designer/designer-canvas.test.tsx
  - 空态渲染
  - 上下箭头 disabled 边界
  - onMove / onRemove / onSelect 回调
  - 字段选中态视觉

components/designer/designer-property-panel.test.tsx
  - 空选中渲染
  - fieldCode 锁定 readOnly
  - optionSource 切换触发 update
  - dict 渲染 DictSetter

components/designer/designer-preview.test.tsx
  - 空态渲染
  - 非空态挂载 FormilyRuntimeForm

components/designer/setter/setter.test.tsx
  - 5 类 setter 受控与 onChange 协议

components/formily-designer-shell.test.tsx
  - 默认 4 区渲染
  - 添加字段
  - 删除字段
  - 上下移动
  - 选中切换
  - dirty 状态
  - 保存成功/失败回滚

components/dict-schema-injector.test.ts
  - dictCode 收集去重
  - enum 注入
  - 空 dictMap 不破坏 schema

components/formily-runtime-form.test.tsx
  - select 渲染
  - multi_select 渲染为 checkbox 组
  - user 渲染为 input
  - readOnly 不允许 onChange
```

### 12.3 集成（推荐，后续接入）

```text
template-designer-page 端到端
  - 加载空模板
  - 添加 9 类字段
  - 配置 dictCode
  - 保存
  - 重新加载 → schema 一致
  - 在 /work-records/new 提交 → 后端 validator 通过
  - 在 /work-records/$id 详情 → label 正确
```

## 13. 性能与可访问性

### 13.1 性能

```text
- 画布字段量上限：~50，超出仅展示警告不阻断
- 属性面板切换字段：纯重建，无 transition（无需）
- 预览 schema 变更：key=serialized 触发 FormilyRuntimeForm 重挂载（实现细节，避免脏状态）
- DesignerPropertyPanel：纯函数 props，无 useState
```

### 13.2 可访问性

```text
- 画布字段项：role='button' + tabIndex=0 + Enter/Space 选中
- 上下箭头：aria-label 区分（moveUp / moveDown）
- 删除按钮：aria-label
- DictSetter：id + Label htmlFor 绑定
- selected 字段：aria-pressed
- panel 区域：aria-label
```

## 14. 已知薄封装与边界

```text
1. multi_select 在 FormilyRuntimeForm 渲染为 checkbox 组
   原因：决策 5 禁止复杂组件
   后续：若要"远程搜索 + 多选"，需新 ADR

2. user 在 FormilyRuntimeForm 渲染为单行 Input（value = userId）
   原因：决策 5 不做选人控件
   后续：若要 UserPicker，需评估 <Command+User> 或独立组件

3. optionSource='static' 时第一版不暴露 options 行内编辑器
   原因：当前 ADR §决策 5 仅要求运行时支持 static enum；设计态保持最瘦
   后续：phase-04 §6 已描述，但第一版未实现

4. DictSetter 无字典时 hardcode '暂无可用字典'
   原因：i18n key 未抽
   后续：补 t('workRecords.designer.dict.empty')

5. FormilyUnknown hardcode '未知控件'
   原因：同上
   后续：补 t('common.unknownField')

6. 模板切换 dirty 拦截未实现
   原因：phase-04 §12.1 描述但 ADR §决策 5 不强制
   后续：要么补 Confirm Dialog，要么 ADR 明确放弃

7. 画布上下移动不保留键盘焦点
   原因：第一版未实现 keyboard arrow reorder
   后续：phase-04 §12.2 仅提及 dnd-kit，不在第一版
```

## 15. 与 ADR 0005 / phase-04 的差异说明

```text
ADR 0005 §影响 第四点：
  "FormilyRuntimeForm 不需要修改，但需要在 formily-schema.ts 暴露
   addField / removeField / moveField / updateField / setDictionaryCode
   等纯函数"
实际情况：
  - 这些函数放在 data/designer/schema-builder.ts（与 formily-schema.ts 分层）
  - FormilyRuntimeForm 被追加了 multi_select / user 两个薄封装（已知薄封装 §1 §2）
  - ADR §实现注 已说明该修补

phase-04 §6：
  - 字段库描述与 ADR §决策 5 一致（9 类）
  - 但 phase-04 描述了"添加字段时生成临时 id"，
    实际实现未引入临时 id，直接用 `${type}_${nextIndex}` 作为 fieldCode

phase-04 §11：
  - "fieldCode 非保留字段"校验由 reserved-field-codes.ts 完成
  - "fieldCode 不重复"校验由 schema-builder.addField 完成
  - "static options value 不重复"第一版不暴露编辑器，跳过

phase-04 §10 保存流程：
  - 当前 FormilyDesignerShell.onSave 回调由父组件负责桥接 API
  - 父组件为 template-designer-page.tsx，未在本文件展开
```

## 16. 验收

```text
1. pnpm -C web/portal run test 通过（含 designer/* + setter/* + formily-*）
2. pnpm -C web/portal run build 通过（Vite 编译期校验）
3. scripts/ci/check-formily-deps.sh 退出码 0
4. /work-records/designer 可访问
5. 添加 9 类字段、配置 dictCode、上下移动、删除、保存完整链路通过
6. 在 /work-records/new 加载同一模板，提交记录，后端 validator 通过
7. 在 /work-records/$id 详情，字段顺序与设计态一致
8. 禁用字典项历史值仍可回显 label
9. ADR 引用、本文档引用、phase-04 引用三者一致
```

## 17. 变更记录

```text
2026-07-08  初版。
           - 与 ADR 0005 status: accepted-and-implemented 对齐
           - 与 phase-04 / phase-05 现有落地代码一致
           - 已知薄封装 §14 登记当前实现边界
```
