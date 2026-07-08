import { useMemo } from 'react'
import {
  createForm,
  type Form,
  createEffectHook,
} from '@formily/core'
import {
  FormProvider,
  createSchemaField,
} from '@formily/react'
import { Schema } from '@formily/json-schema'
import { Input } from '@/components/ui/input'
import { Textarea } from '@/components/ui/textarea'
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select'
import { Switch } from '@/components/ui/switch'
import { injectDictionaryOptions } from './dict-schema-injector'
import type { DictItem } from '@/features/dictionaries/data/schema'

// --- Portal UI → Formily component bridge ---

const FormilyInput = (props: {
  value?: string
  onChange?: (v: string) => void
  required?: boolean
  placeholder?: string
  [key: string]: unknown
}) => (
  <Input
    value={props.value ?? ''}
    onChange={(e) => props.onChange?.(e.target.value)}
    required={props.required}
    placeholder={props.placeholder}
  />
)

const FormilyTextarea = (props: {
  value?: string
  onChange?: (v: string) => void
  required?: boolean
  placeholder?: string
  [key: string]: unknown
}) => (
  <Textarea
    value={props.value ?? ''}
    onChange={(e) => props.onChange?.(e.target.value)}
    required={props.required}
    placeholder={props.placeholder}
  />
)

const FormilyNumber = (props: {
  value?: number | string
  onChange?: (v: number) => void
  [key: string]: unknown
}) => {
  const num = typeof props.value === 'number' ? props.value : ''
  return (
    <Input
      type='number'
      value={num}
      onChange={(e) => {
        const parsed = parseFloat(e.target.value)
        props.onChange?.(isNaN(parsed) ? 0 : parsed)
      }}
    />
  )
}

const FormilyDate = (props: {
  value?: string
  onChange?: (v: string) => void
  [key: string]: unknown
}) => (
  <Input
    type='date'
    value={props.value ? String(props.value).slice(0, 10) : ''}
    onChange={(e) => props.onChange?.(e.target.value)}
  />
)

const FormilyDateTime = (props: {
  value?: string
  onChange?: (v: string) => void
  [key: string]: unknown
}) => (
  <Input
    type='datetime-local'
    value={props.value ? String(props.value).slice(0, 16) : ''}
    onChange={(e) => props.onChange?.(e.target.value)}
  />
)

const FormilySelect = (props: {
  value?: string
  onChange?: (v: string) => void
  enum?: Array<{ label: string; value: unknown }>
  [key: string]: unknown
}) => (
  <Select
    value={props.value ?? ''}
    onValueChange={(v) => props.onChange?.(v)}
  >
    <SelectTrigger>
      <SelectValue />
    </SelectTrigger>
    <SelectContent>
      {(props.enum ?? []).map((opt, i) => (
        <SelectItem key={i} value={String(opt.value)}>
          {String(opt.label)}
        </SelectItem>
      ))}
    </SelectContent>
  </Select>
)

const FormilyBoolean = (props: {
  value?: boolean
  onChange?: (v: boolean) => void
  [key: string]: unknown
}) => (
  <Switch
    checked={props.value ?? false}
    onCheckedChange={(v) => props.onChange?.(v)}
  />
)

const FormilyUnknown = () => (
  <span className='text-muted-foreground text-sm'>未知控件</span>
)

const SchemaField = createSchemaField({
  components: {
    Input: FormilyInput,
    Textarea: FormilyTextarea,
    Number: FormilyNumber,
    Date: FormilyDate,
    DateTime: FormilyDateTime,
    Select: FormilySelect,
    Switch: FormilyBoolean,
    Unknown: FormilyUnknown,
  },
})

// --- JSON Schema → Formily schema normalization ---

const FIELD_TYPE_TO_COMPONENT: Record<string, string> = {
  text: 'Input',
  textarea: 'Textarea',
  number: 'Number',
  date: 'Date',
  datetime: 'DateTime',
  select: 'Select',
  multi_select: 'Textarea',
  boolean: 'Switch',
  user: 'Input',
}

function normalizeField(
  fieldSchema: Record<string, unknown>
): Record<string, unknown> {
  const ext = fieldSchema['x-work-record'] as Record<string, unknown> | undefined
  const fieldType = (ext?.fieldType as string) ?? 'text'
  const component = FIELD_TYPE_TO_COMPONENT[fieldType] ?? 'Input'

  return {
    ...fieldSchema,
    'x-component': component,
  }
}

function normalizeProperties(
  props: Record<string, unknown> | undefined
): Record<string, unknown> | undefined {
  if (!props || typeof props !== 'object') return undefined
  const result: Record<string, unknown> = {}
  for (const [key, val] of Object.entries(props as Record<string, unknown>)) {
    if (val && typeof val === 'object') {
      result[key] = normalizeField(val as Record<string, unknown>)
    } else {
      result[key] = val
    }
  }
  return result
}

function normalizeSchema(
  schema: Record<string, unknown>
): Record<string, unknown> {
  return {
    ...schema,
    type: 'object',
    properties: normalizeProperties(
      schema.properties as Record<string, unknown> | undefined
    ),
  }
}

// --- Public component ---

export type FormilyRuntimeFormProps = {
  /**
   * Formily-compatible JSON schema (already parsed).
   * Supports `x-work-record` extension for field metadata.
   * Supports `enum` for select options (static or injected via `dictionaries`).
   */
  schema: Record<string, unknown>
  /**
   * Initial form values. Typically the record's `customDataJson` parsed object.
   */
  initialValues?: Record<string, unknown>
  /**
   * When true, form fields are read-only (no onChange).
   */
  readOnly?: boolean
  /**
   * Dictionary items keyed by dictCode.
   * These will be injected into schema fields with `x-work-record.optionSource = 'dict'`.
   */
  dictionaries?: Record<string, DictItem[]>
  /**
   * Callback fired on any value change.
   */
  onValuesChange?: (values: Record<string, unknown>) => void
}

/**
 * Formily runtime form for work records.
 *
 * Renders a Formily-compatible JSON schema using portal UI components,
 * with support for:
 * - Dictionary injection (dictCode → options)
 * - Read-only mode (detail view)
 * - Field type normalization (text/select/date/boolean/etc.)
 * - onValuesChange for external state sync
 *
 * Wraps the schema with FormProvider so all schema fields can access form state.
 */
export function FormilyRuntimeForm({
  schema,
  initialValues = {},
  readOnly = false,
  dictionaries = {},
  onValuesChange,
}: FormilyRuntimeFormProps) {
  // Use effects for onValuesChange callback
  const form = useMemo<Form>(
    () =>
      createForm({
        initialValues,
        readOnly,
        effects: (form) => {
          const onFormValuesChange = createEffectHook('onFormValuesChange')
          onFormValuesChange(() => {
            onValuesChange?.(form.values as Record<string, unknown>)
          })
        },
      }),
    // Re-create form only when these change
    // eslint-disable-next-line react-hooks/exhaustive-deps
    [JSON.stringify(initialValues), readOnly]
  )

  // Update form values when initialValues prop changes
  useMemo(() => {
    form.setValues(initialValues)
  }, [form, initialValues])

  // Build dict map for injectDictionaryOptions
  const dictMap = useMemo(
    () =>
      Object.fromEntries(
        Object.entries(dictionaries).map(([k, v]) => [k, v])
      ) as Record<string, DictItem[]>,
    [dictionaries]
  )

  // 1. Inject dictionary options
  const withDicts = useMemo(
    () =>
      injectDictionaryOptions(
        schema as Parameters<typeof injectDictionaryOptions>[0],
        dictMap
      ),
    [schema, dictMap]
  )

  // 2. Normalize schema (ensure x-component is set for each field)
  const normalizedSchema = useMemo(
    () => normalizeSchema(withDicts as Record<string, unknown>),
    [withDicts]
  )

  // Convert to ISchema for SchemaField
// compile JSON schema → Formily schema
  const formilySchema = useMemo(
    () => Schema.compile(normalizedSchema),
    [normalizedSchema]
  )

  return (
    <FormProvider form={form}>
      <SchemaField schema={formilySchema} />
    </FormProvider>
  )
}
