---
title: 工作记录模板设计器采用 @formily/core，禁用 Antd/Fusion setters
type: adr
status: accepted
phase: work-record
owner: ai
created: 2026-07-08
updated: 2026-07-08
related:
  - .agents/skills/aegisops/SKILL.md
  - .agents/skills/aegisops/SKILL.md §4.1
  - .agents/skills/aegisops/SKILL.md §6.15.5
  - .agents/skills/aegisops/SKILL.md §6.15.2
  - docs/record/index.md
  - docs/record/index.md §4
  - docs/record/index.md §10.1
  - docs/record/phase-04-formily-designer.md
  - docs/adr/0003-aiops-agent-boundary.md
---

# ADR 0005: 工作记录模板设计器采用 @formily/core，禁用 Antd/Fusion setters

## 状态

- **已接受（2026-07-08）**
- 触发来源：Phase WR-4（表单设计器）实现口径冲突
- 本 ADR 是阶段性决议，复杂度上升后再行评估是否引入 Designable core

## 背景

`docs/record/index.md §4` 既有口径为"第一版采用 portal 原生字段设计器，生成 Formily-compatible schema；不引入 Designable 作为主设计器"。该口径同时被 `SKILL.md §6.15.5` 强化："Formily / Designable 可作为后续复杂度上升后的选型评估，不是第一版默认依赖；完整 LowCodeEngine 第一版禁止引入"。

按字面执行，意味着第一版设计器不允许引任何 Formily 包，只用 portal 的 shadcn/ui 自己手搓画布、属性面板、字段库。但 `web/portal/package.json` 当前已经安装：

```text
@formily/core
@formily/react
@formily/json-schema
@formily/designer
```

其中 `@formily/designer`（即原 Designable 编排 API）和 `@formily/json-schema` 已经是运行态 `FormilyRuntimeForm` 不可或缺的部分。再叠加 `SKILL.md §4.1` 的运行时要求：

> 记录填写与详情运行态使用 Formily。

结果：

- **运行态**已用 `@formily/core` 系列；
- **设计态**被"全部手搓"卡住；
- 两者都基于同一份 schema，但设计态拿不到任何 Formily 的 schema 校验、Field 状态、订阅能力，工具链断层。

## 决策

1. **设计态采用 `@formily/core` 作为引擎**，允许使用以下包：
   - `@formily/core`（`createForm`、`setFieldState`、`subscribe`）
   - `@formily/react`（`FormProvider`、`useForm`、`<Field>` 适配）
   - `@formily/json-schema`（`ISchema` 编译、`Schema` 模型）
   - `@formily/designer`（designable 编排 API，若运行时证明可裁剪）

2. **设计态禁止引入以下包**：
   - `@formily/antd`、`@formily/antd-components`、`@formily/antd-setters`
   - `@formily/designable-setters`（Antd 风格的 setter 库）
   - `@formily/fusion`、`@formily/element-plus`、`@formily/next` 等任何 UI 框架适配
   - `@formily/antd-icons`、`@formily/icons`

3. **设计器 setter 必须由 portal 自行实现**，基于 shadcn/ui：
   - `TextSetter`（Input）
   - `NumberSetter`（Input type=number）
   - `BooleanSetter`（Switch）
   - `SelectSetter`（shadcn/ui Select）
   - `DictSetter`（平台字典下拉，复用 `useDictTypes`）
   - setter 契约：`{ value, onChange, props }`，与 Formily 解耦，可单独测试

4. **设计器面板（字段库 + 画布 + 属性 + 预览）由 portal 用 shadcn/ui 实现**，四区布局：
   - 左栏：9 类字段卡片（text / textarea / number / date / datetime / select / multi_select / user / boolean）
   - 中栏：字段排序、选中、删除（不引 `@dnd-kit/core`，用上下箭头按钮，符合 SKILL §6.15.5 "不做过低代码"）
   - 右栏上半：属性 setter 表单
   - 右栏下半：实时 Formily 预览（直接复用 `FormilyRuntimeForm`）

5. **第一版字段类型严格限定为 §6.15.5 列出的 9 类**，不做：
   - 级联选择、子表单、公式字段、联动显示、条件必填、复杂布局、远程接口字段
   - 字段级权限、模板版本管理
   - 拖拽排序（鼠标拖拽属于"页面级低代码平台"红线）

6. **schema 数据契约保持不变**：
   - 字段属性真相源仍为 `schema.properties[fieldCode].x-work-record.{required, listVisible, filterable, statistical, optionSource, dictCode, options}`；
   - `designer_json` 仅承担 UI 折叠/选中态等非业务状态，不复制字段属性；
   - 与运行态 `FormilyRuntimeForm` 解析路径完全一致。

7. **CI 守卫**（新增）：
   - `package.json` 中 `dependencies` 不得出现 `^@formily/(antd|antd-components|antd-setters|designable-setters|antd-icons|icons|fusion|element-plus|next)`；
   - 通过 `scripts/ci/check-formily-deps.sh` 在 CI 阶段 fail-fast；
   - 触发新增 dependency 时必须先修本 ADR。

## 备选

- **A. 完全不引任何 Formily 包**：与 SKILL §6.15.5 字面一致，但运行态与设计态工具链割裂，schema 校验、字段反应式状态全部要手搓，且 `@formily/core` 已安装却被浪费。**不采纳**。
- **B. 全套引入 Designable + Antd setters**：把 `@formily/designable-setters`、`@formily/antd-setters`、`@formily/antd` 全装上。设计器能力最完整，但会引入 Antd UI 体系，与 portal 的 shadcn-admin / Radix / Tailwind v4 风格撕裂，i18n / 主题 / 权限分层都变双 UI。**不采纳**（§6.15.5 与 §4.1 都反对）。
- **C. 引入 Designable core 但禁用 Antd setters**（即设计态用 `@formily/core` + 自写 setter）：与本决策一致，是本 ADR 的实选。**采纳**。
- **D. 切到 `@rjsf/core`、`react-json-schema-form`、`uniforms`**：偏离既有 Formily 运行态，技术栈二选一。**不采纳**。

## 影响

- 修改 `docs/record/index.md §4` / `§10.1` / `phase-04-formily-designer.md` 措辞，从"不引入 Formily"改为"使用 `@formily/core`，禁止引入 Antd/Fusion setters"，并在文中加本 ADR 引用。
- 重写 `web/portal/src/features/work-records/components/formily-designer-shell.tsx` 为四区布局。
- 新增 `web/portal/src/features/work-records/data/designer/` 与 `components/designer/` 子目录（详见实施计划）。
- `FormilyRuntimeForm` 不需要修改，但需要在 `formily-schema.ts` 暴露 `addField / removeField / moveField / updateField / setDictionaryCode` 等纯函数，供画布与属性面板复用。
- 不动后端、不动数据库 migration、不动运行态测试。

## 验证

- `pnpm --filter web/portal build` 通过：编译期 `@formily/antd*` 等被禁包不被引用。
- 新增 `scripts/ci/check-formily-deps.sh`：扫描 `web/portal/package.json` 与 `pnpm-lock.yaml`，匹配禁止列表；本地与 CI 任一命中即非零退出。
- 单元测试覆盖 `field-types.ts`、`schema-builder.ts` 纯函数。
- 组件测试覆盖 `designer-canvas.tsx` / `designer-palette.tsx` / `designer-property-panel.tsx` 的渲染、状态、回调。
- E2E（后续接入 vitest + Playwright 后）覆盖"新建模板 → 拖入 9 类字段 → 编辑属性 → 保存 → 在 `/work-records/new` 加载 → 提交记录"全链路。