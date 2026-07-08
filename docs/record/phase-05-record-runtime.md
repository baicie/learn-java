---
title: 工作记录 Phase 05 动态表单运行态
type: design
status: review
phase: work-record
owner: ai
created: 2026-07-07
updated: 2026-07-08
related:
  - docs/record/phase-03-work-record-template.md
  - docs/record/phase-04-formily-designer.md
  - docs/record/phase-02a-platform-calendar.md
---

# Phase 05：动态表单运行态

## 1. 目标

把“记录填写”从 JSON 编辑器升级为真正的动态表单。

交付：

```text
1. 新建记录页。
2. 编辑记录页。
3. 记录详情页。
4. Formily-compatible schema runtime。
5. 字典选项注入。
6. 只读详情渲染。
7. 前后端双重校验。
```

可选增强：

```text
1. 日报类模板可以通过 aiops-platform/calendar 判断当前日期是否工作日。
2. 非工作日默认不强制填写日报。
3. 调休工作日可以按工作日处理。
```

但本 Phase 的记录填写主流程不能依赖工作日历存在。没有日历数据时，仍然允许用户创建、编辑、查看记录。

## 2. 页面流程

### 2.1 新建记录

```text
进入 /work-records/new
  ↓
选择模板
  ↓
加载模板 schema_json
  ↓
扫描 dictCode
  ↓
加载字典项 enabled=true
  ↓
注入选项
  ↓
渲染动态表单
  ↓
填写内置字段 + 自定义字段
  ↓
提交
  ↓
后端校验并保存
  ↓
跳转记录详情
```

### 2.2 编辑记录

```text
进入 /work-records/$recordId/edit
  ↓
加载记录
  ↓
加载记录对应 template
  ↓
加载 dict items
  ↓
用 custom_data_json 回填表单
  ↓
提交更新
```

### 2.3 详情页

```text
进入 /work-records/$recordId
  ↓
加载记录
  ↓
加载模板字段索引
  ↓
加载字典项 includeDisabled=true
  ↓
按字段顺序展示
```

## 3. 运行态组件

```text
features/work-records/components/runtime/
├─ record-create-page.tsx
├─ record-edit-page.tsx
├─ record-detail-page.tsx
├─ record-runtime-form.tsx
├─ record-readonly-view.tsx
├─ schema-runtime-renderer.tsx
├─ dict-option-loader.ts
├─ record-value-normalizer.ts
└─ record-value-formatter.ts
```

## 4. 内置字段

内置字段不放进 custom_data_json：

```text
title
templateId
status
ownerId
recordTime
```

新建页表单布局：

```text
基础信息
  模板
  标题
  负责人
  记录时间

记录内容
  动态字段

操作区
  保存草稿
  提交/保存
  取消
```

## 5. 动态字段值规范

```text
text          string
textarea      string
number        number
date          YYYY-MM-DD
datetime      ISO string
select        string
multi_select  string[]
user          string
boolean       boolean
```

空值策略：

```text
未填写字段不写入 custom_data_json
必填字段由前端和后端共同校验
空字符串视为未填写
multi_select 空数组视为未填写
boolean false 是有效值，不可当成空
```

## 6. 字典注入

扫描 schema：

```text
找到 x-work-record.optionSource=dict
收集 dictCode
去重
并发请求字典项
```

新建/编辑：

```text
includeDisabled=false
```

详情页：

```text
includeDisabled=true
```

注入格式：

```json
[{ "label": "P2", "value": "P2" }]
```

## 7. Runtime Renderer

第一版不直接暴露 Formily 生态复杂度，做薄封装：

```text
SchemaRuntimeRenderer
  输入 schema
  输入 initialValues
  输入 dictionaries
  输出 values
```

内部可以使用：

```text
Formily core/react
或 react-hook-form + schema adapter
```

推荐：

```text
Formily runtime
```

但控件必须映射到 portal shadcn/ui：

```text
Input
Textarea
Select
Checkbox / Switch
DatePicker
```

## 8. 前端校验

前端校验用于用户体验，不作为安全边界。

必须校验：

```text
必填
数字类型
日期格式
select 选项
multi_select 选项
JSON 结构
```

错误展示：

```text
字段级错误显示在字段下方。
提交级错误由 toast 展示。
```

## 9. 后端校验

后端是最终边界。

保存时：

```text
1. 读取 template fields。
2. 拒绝未知字段。
3. 拒绝禁用字段写入。
4. 校验 required。
5. 校验 field_type。
6. 校验 static options。
7. 校验 dict enabled items。
8. 服务端生成 tenant_id、creator_id、created_at。
9. 写审计。
```

## 10. 详情页展示

详情页不能直接 dump JSON。

必须：

```text
按模板字段顺序展示。
展示 field_name。
select/multi_select 显示 label。
user 显示用户名/显示名。
date/datetime 格式化。
禁用字段仍展示历史值。
未知字段进入“历史遗留字段”区域。
```

### 10.1 历史遗留字段

如果 custom_data_json 中存在模板中已无定义字段：

```text
展示在折叠区域：
  历史遗留字段
```

原因：

```text
不能静默吞掉历史数据。
```

## 11. 编辑限制

第一版允许编辑：

```text
title
status
ownerId
recordTime
custom_data_json 中仍启用的字段
```

不允许编辑：

```text
templateId
creatorId
tenantId
createdAt
已禁用字段
```

## 12. 草稿与提交

第一版状态：

```text
draft
submitted
done
archived
```

如果当前后端已有：

```text
draft
processing
done
archived
```

则需要做一次统一决策：

```text
推荐改为 draft/submitted/done/archived
```

因为工作记录不是工单，“processing”语义偏流程。

## 13. 测试

前端：

```text
schema 注入字典
required 字段显示错误
select 保存 value
multi_select 保存 string[]
详情页 value -> label
禁用字典项历史展示
未知字段进入历史遗留区域
```

后端：

```text
required 校验
类型校验
select 非法值拒绝
multi_select 非法值拒绝
禁用字段拒绝写入
普通用户不能编辑别人记录
管理员可以编辑租户记录
```

## 14. 验收

```text
1. 用户可以选择模板新建记录。
2. 动态字段以控件形式展示，不是 JSON 文本框。
3. 字典字段展示 label、保存 value。
4. 编辑页能回填历史值。
5. 详情页按模板顺序展示。
6. 禁用字典项不影响历史详情。
7. 后端拒绝非法数据。
```
