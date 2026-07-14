---
title: Formily 与 Shadcn 统一运行时完整代码
type: design
status: draft
phase: work-record
owner: ai
created: 2026-07-14
updated: 2026-07-14
related: []
---

# Formily + Shadcn 统一运行时完整代码

## 1. 依赖与原则

当前项目已经安装：

```text
@formily/core
@formily/json-schema
@formily/react
@formily/validator
```

不再增加第二套动态表单内核。React Hook Form 可以继续服务普通管理表单，但工作记录动态表单统一使用 Formily。

目录：

```text
web/portal/src/features/work-records/formily/
├── create-form.ts
├── schema-field.tsx
├── schema-form.tsx
├── context.tsx
├── schema-compatibility.ts
├── value-codec.ts
└── components/
    ├── form-item.tsx
    ├── input.tsx
    ├── textarea.tsx
    ├── number-input.tsx
    ├── select.tsx
    ├── multi-select.tsx
    ├── boolean-field.tsx
    ├── date-field.tsx
    ├── datetime-field.tsx
    ├── dict-select.tsx
    ├── user-select.tsx
    ├── section.tsx
    └── grid.tsx
```

## 2. Context

```tsx
import { createContext, useContext } from 'react'

export type RuntimeOption = {
  value: string
  label: string
  enabled: boolean
}

export type WorkRecordFormRuntimeContextValue = {
  mode: 'create' | 'edit' | 'readonly' | 'designer'
  dictionaryOptions: Record<string, RuntimeOption[]>
  userOptions: RuntimeOption[]
  disabledSuffix: string
}

const Context = createContext<WorkRecordFormRuntimeContextValue | null>(null)

export const WorkRecordFormRuntimeProvider = Context.Provider

export function useWorkRecordFormRuntime() {
  const value = useContext(Context)

  if (!value) {
    throw new Error('WorkRecordFormRuntimeProvider is required')
  }

  return value
}
```

## 3. Form 创建

```ts
import { createForm } from '@formily/core'

export function createWorkRecordForm(options: {
  mode: 'create' | 'edit' | 'readonly' | 'designer'
  initialValues?: Record<string, unknown>
}) {
  return createForm({
    readPretty: options.mode === 'readonly',
    initialValues: options.initialValues ?? {},
    validateFirst: true,
  })
}
```

## 4. FormItem

```tsx
import type { PropsWithChildren } from 'react'
import { useField } from '@formily/react'
import type { Field } from '@formily/core'
import { Label } from '@/components/ui/label'
import { cn } from '@/lib/utils'

export function FormilyFormItem({
  children,
  className,
}: PropsWithChildren<{ className?: string }>) {
  const field = useField<Field>()
  const fieldId = `field-${field.address.toString().replaceAll('.', '-')}`

  return (
    <div className={cn('grid gap-2', className)}>
      <Label htmlFor={fieldId}>
        {String(field.title ?? '')}
        {field.required ? (
          <span className="ml-1 text-destructive" aria-hidden="true">
            *
          </span>
        ) : null}
      </Label>

      <div id={fieldId}>{children}</div>

      {field.description ? (
        <p className="text-xs text-muted-foreground">{String(field.description)}</p>
      ) : null}

      {field.selfErrors.map((error) => (
        <p key={error} role="alert" className="text-xs text-destructive">
          {error}
        </p>
      ))}
    </div>
  )
}
```

## 5. Input

```tsx
import { connect, mapProps } from '@formily/react'
import { Input } from '@/components/ui/input'

export const FormilyInput = connect(
  Input,
  mapProps(
    {
      value: 'value',
      disabled: 'disabled',
      readOnly: 'readOnly',
      placeholder: 'placeholder',
    },
    (props, field) => ({
      ...props,
      value: field.value ?? '',
      onChange: (event: React.ChangeEvent<HTMLInputElement>) => {
        field.onInput(event.target.value)
      },
      onBlur: () => field.onBlur(),
      'aria-invalid': field.selfErrors.length > 0,
    })
  )
)
```

## 6. Textarea

```tsx
import { connect, mapProps } from '@formily/react'
import { Textarea } from '@/components/ui/textarea'

export const FormilyTextarea = connect(
  Textarea,
  mapProps(
    {
      value: 'value',
      disabled: 'disabled',
      readOnly: 'readOnly',
      placeholder: 'placeholder',
    },
    (props, field) => ({
      ...props,
      value: field.value ?? '',
      onChange: (event: React.ChangeEvent<HTMLTextAreaElement>) => {
        field.onInput(event.target.value)
      },
      onBlur: () => field.onBlur(),
      'aria-invalid': field.selfErrors.length > 0,
    })
  )
)
```

## 7. Number

```tsx
import { connect, mapProps } from '@formily/react'
import { Input } from '@/components/ui/input'

export const FormilyNumberInput = connect(
  Input,
  mapProps((props, field) => ({
    ...props,
    type: 'number',
    value: field.value ?? '',
    onChange: (event: React.ChangeEvent<HTMLInputElement>) => {
      const raw = event.target.value
      field.onInput(raw === '' ? undefined : Number(raw))
    },
    'aria-invalid': field.selfErrors.length > 0,
  }))
)
```

## 8. Select

Shadcn Select 不是原生 input，需要显式映射：

```tsx
import { connect, mapProps } from '@formily/react'
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select'

type Option = {
  label: string
  value: string
  disabled?: boolean
}

function SelectImpl({
  value,
  onChange,
  options = [],
  placeholder,
  disabled,
}: {
  value?: string
  onChange?: (value: string) => void
  options?: Option[]
  placeholder?: string
  disabled?: boolean
}) {
  return (
    <Select value={value ?? ''} onValueChange={onChange} disabled={disabled}>
      <SelectTrigger>
        <SelectValue placeholder={placeholder ?? '请选择'} />
      </SelectTrigger>
      <SelectContent>
        {options.map((option) => (
          <SelectItem key={option.value} value={option.value} disabled={option.disabled}>
            {option.label}
          </SelectItem>
        ))}
      </SelectContent>
    </Select>
  )
}

export const FormilySelect = connect(
  SelectImpl,
  mapProps(
    {
      dataSource: 'options',
      value: 'value',
      disabled: 'disabled',
    },
    (props, field) => ({
      ...props,
      onChange: (value: string) => field.onInput(value),
    })
  )
)
```

## 9. DictSelect

```tsx
import { useField } from '@formily/react'
import type { Field } from '@formily/core'
import { useWorkRecordFormRuntime } from '../context'

export function FormilyDictSelect({
  dictCode,
  placeholder,
}: {
  dictCode: string
  placeholder?: string
}) {
  const field = useField<Field>()
  const runtime = useWorkRecordFormRuntime()
  const source = runtime.dictionaryOptions[dictCode] ?? []
  const currentValue = field.value == null ? '' : String(field.value)

  const options = source.map((item) => ({
    value: item.value,
    label: item.enabled ? item.label : `${item.label}${runtime.disabledSuffix}`,
    disabled:
      !item.enabled &&
      !(runtime.mode === 'edit' && item.value === currentValue) &&
      runtime.mode !== 'readonly',
  }))

  return (
    <SelectImpl
      value={currentValue}
      options={options}
      placeholder={placeholder}
      disabled={field.disabled || field.readOnly}
      onChange={(value) => field.onInput(value)}
    />
  )
}
```

注意：历史禁用值在编辑和详情中保留；新建时不能选择任何禁用项。

## 10. Boolean

```tsx
import { Checkbox } from '@/components/ui/checkbox'
import { useField } from '@formily/react'
import type { Field } from '@formily/core'

export function FormilyBoolean() {
  const field = useField<Field>()

  return (
    <Checkbox
      checked={Boolean(field.value)}
      disabled={field.disabled || field.readOnly}
      onCheckedChange={(checked) => field.onInput(checked === true)}
    />
  )
}
```

## 11. Section 与 Grid

```tsx
export function FormSection({
  title,
  description,
  children,
}: PropsWithChildren<{ title?: string; description?: string }>) {
  return (
    <section className="rounded-lg border bg-card">
      {(title || description) && (
        <header className="border-b p-4">
          {title ? <h3 className="font-medium">{title}</h3> : null}
          {description ? <p className="mt-1 text-sm text-muted-foreground">{description}</p> : null}
        </header>
      )}
      <div className="grid gap-4 p-4">{children}</div>
    </section>
  )
}
```

```tsx
export function FormGrid({
  columns = 2,
  children,
}: PropsWithChildren<{ columns?: 1 | 2 | 3 | 4 }>) {
  const gridClass = {
    1: 'grid-cols-1',
    2: 'grid-cols-1 md:grid-cols-2',
    3: 'grid-cols-1 md:grid-cols-2 xl:grid-cols-3',
    4: 'grid-cols-1 md:grid-cols-2 xl:grid-cols-4',
  }[columns]

  return <div className={`grid gap-4 ${gridClass}`}>{children}</div>
}
```

## 12. SchemaField

```tsx
import { createSchemaField } from '@formily/react'

export const WorkRecordSchemaField = createSchemaField({
  components: {
    FormItem: FormilyFormItem,
    Input: FormilyInput,
    Textarea: FormilyTextarea,
    NumberInput: FormilyNumberInput,
    Select: FormilySelect,
    DictSelect: FormilyDictSelect,
    MultiSelect: FormilyMultiSelect,
    Boolean: FormilyBoolean,
    DatePicker: FormilyDatePicker,
    DateTimePicker: FormilyDateTimePicker,
    UserSelect: FormilyUserSelect,
    Section: FormSection,
    Grid: FormGrid,
  },
})
```

## 13. 统一 Schema Form

```tsx
import type { ISchema } from '@formily/json-schema'
import { FormProvider } from '@formily/react'
import { useMemo } from 'react'

export type WorkRecordSchemaFormProps = {
  schema: ISchema
  mode: 'create' | 'edit' | 'readonly' | 'designer'
  initialValues?: Record<string, unknown>
  dictionaryOptions: Record<string, RuntimeOption[]>
  userOptions?: RuntimeOption[]
  onSubmit?: (values: Record<string, unknown>) => Promise<void> | void
  footer?: React.ReactNode
}

export function WorkRecordSchemaForm({
  schema,
  mode,
  initialValues,
  dictionaryOptions,
  userOptions = [],
  onSubmit,
  footer,
}: WorkRecordSchemaFormProps) {
  const initialSignature = JSON.stringify(initialValues ?? {})

  const form = useMemo(
    () => createWorkRecordForm({ mode, initialValues }),
    [mode, initialSignature]
  )

  return (
    <WorkRecordFormRuntimeProvider
      value={{
        mode,
        dictionaryOptions,
        userOptions,
        disabledSuffix: '（已禁用）',
      }}
    >
      <FormProvider form={form}>
        <WorkRecordSchemaField schema={schema} />

        {footer ??
          (mode !== 'readonly' && mode !== 'designer' ? (
            <Button type="button" onClick={() => form.submit(onSubmit)}>
              提交
            </Button>
          ) : null)}
      </FormProvider>
    </WorkRecordFormRuntimeProvider>
  )
}
```

生产代码建议使用经过稳定序列化的 initialValues key，或者由上层以 `recordId + updatedAt` 控制 Form 重建，避免大型 JSON 每次 stringify。

## 14. Schema 兼容

```ts
import type { ISchema } from '@formily/json-schema'

export function normalizeRuntimeSchema(input: unknown): ISchema {
  const root = runtimeSchemaRootSchema.parse(input)
  const version = root['x-work-record-schema-version'] ?? 1

  if (version === 1) return upgradeV1ToV2(root)
  if (version === 2) return root as ISchema

  throw new Error(`unsupported work-record schema version: ${version}`)
}
```

v1 字段转换：

```ts
function upgradePropertyV1(property: LegacyProperty): ISchema {
  const meta = property['x-work-record']

  return {
    ...property,
    'x-decorator': property['x-decorator'] ?? 'FormItem',
    'x-component':
      property['x-component'] ?? componentForFieldType(meta.fieldType, meta.optionSource),
    'x-component-props': {
      ...property['x-component-props'],
      ...(meta.optionSource === 'dict' ? { dictCode: meta.dictCode } : {}),
    },
  }
}
```

## 15. 迁移运行态页面

新建页：

```tsx
<WorkRecordSchemaForm
  schema={normalizeRuntimeSchema(JSON.parse(version.schemaJson))}
  mode="create"
  initialValues={{}}
  dictionaryOptions={dictionaryOptions}
  userOptions={userOptions}
  onSubmit={submitRecord}
/>
```

编辑页 mode=`edit`，详情页 mode=`readonly`。删除旧 `RecordRuntimeForm` 中所有按 fieldType 的手写分支。

## 16. 完整组件测试

```tsx
describe('WorkRecordSchemaForm', () => {
  it('renders Shadcn Formily controls and submits typed values', async () => {
    const onSubmit = vi.fn()

    const screen = await render(
      <WorkRecordSchemaForm
        schema={{
          type: 'object',
          properties: {
            summary: {
              type: 'string',
              title: '工作总结',
              'x-decorator': 'FormItem',
              'x-component': 'Textarea',
              'x-validator': [{ required: true, message: '请输入工作总结' }],
            },
            hours: {
              type: 'number',
              title: '工作时长',
              'x-decorator': 'FormItem',
              'x-component': 'NumberInput',
            },
          },
        }}
        mode="create"
        dictionaryOptions={{}}
        onSubmit={onSubmit}
      />
    )

    await screen.getByLabelText('工作总结 *').fill('完成重构')
    await screen.getByLabelText('工作时长').fill('8')
    await screen.getByRole('button', { name: '提交' }).click()

    await expect.poll(() => onSubmit.mock.calls.length).toBe(1)
    expect(onSubmit).toHaveBeenCalledWith({
      summary: '完成重构',
      hours: 8,
    })
  })

  it('shows required error', async () => {
    const screen = await render(requiredTextSchemaForm())
    await screen.getByRole('button', { name: '提交' }).click()
    await expect.element(screen.getByRole('alert')).toHaveTextContent('请输入工作总结')
  })

  it('shows disabled historical dictionary label in readonly mode', async () => {
    const screen = await render(
      <WorkRecordSchemaForm
        schema={dictSchema('priority')}
        mode="readonly"
        initialValues={{ priority: 'P2' }}
        dictionaryOptions={{
          priority: [{ value: 'P2', label: '中', enabled: false }],
        }}
      />
    )

    await expect.element(screen.getByText('中（已禁用）')).toBeVisible()
  })
})
```

还需测试 Date、DateTime、Boolean、MultiSelect、UserSelect、Section、Grid、v1→v2。
