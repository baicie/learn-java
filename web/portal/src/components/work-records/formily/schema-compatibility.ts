import { z } from 'zod'
import type { ISchema } from '@formily/json-schema'

/**
 * The runtime schema lives in `x-work-record-schema-version` (numeric, default 1).
 * v1 used to ship a stringified `x-work-record` payload that needed to be unpacked
 * and routed to a Formily component. v2 stores a flat property bag with
 * `x-decorator` / `x-component` already set, so it passes through.
 */

const legacyPropertyMetaSchema = z
  .object({
    fieldType: z.string().default('text'),
    optionSource: z.string().optional(),
    dictCode: z.string().optional(),
  })
  .passthrough()

const legacyPropertySchema = z
  .object({
    'x-work-record': legacyPropertyMetaSchema.optional(),
  })
  .passthrough()

const runtimeSchemaRootSchema = z
  .object({
    'x-work-record-schema-version': z.number().int().optional(),
    properties: z.record(z.string(), z.unknown()).optional(),
  })
  .passthrough()

const FIELD_TYPE_TO_COMPONENT: Record<string, string> = {
  text: 'Input',
  textarea: 'Textarea',
  number: 'NumberInput',
  select: 'Select',
  multi_select: 'MultiSelect',
  boolean: 'Boolean',
  date: 'DatePicker',
  datetime: 'DateTimePicker',
  user: 'UserSelect',
}

function componentForFieldType(
  fieldType: string,
  optionSource?: string
): string {
  if (fieldType === 'select' && optionSource === 'dict') return 'DictSelect'
  return FIELD_TYPE_TO_COMPONENT[fieldType] ?? 'Input'
}

function upgradePropertyV1(property: unknown): ISchema {
  const parsed = legacyPropertySchema.parse(property ?? {})
  const meta = (parsed['x-work-record'] ?? {}) as {
    fieldType?: string
    optionSource?: string
    dictCode?: string
  }
  return {
    ...(parsed as ISchema),
    'x-decorator': parsed['x-decorator'] ?? 'FormItem',
    'x-component':
      parsed['x-component'] ??
      componentForFieldType(meta.fieldType ?? 'text', meta.optionSource),
    'x-component-props': {
      ...(parsed['x-component-props'] as Record<string, unknown> | undefined),
      ...(meta.optionSource === 'dict' && meta.dictCode
        ? { dictCode: meta.dictCode }
        : {}),
    },
  }
}

function upgradeV1ToV2(root: Record<string, unknown>): ISchema {
  const properties = (root.properties ?? {}) as Record<string, unknown>
  const upgraded: Record<string, ISchema> = {}
  for (const [name, property] of Object.entries(properties)) {
    upgraded[name] = upgradePropertyV1(property)
  }
  return {
    ...(root as ISchema),
    type: 'object',
    properties: upgraded,
  }
}

/**
 * Accept arbitrary input and produce a Formily ISchema. Throws on unknown
 * versions so callers fail loud instead of rendering a half-baked form.
 */
export function normalizeRuntimeSchema(input: unknown): ISchema {
  const root = runtimeSchemaRootSchema.parse(input)
  const version = root['x-work-record-schema-version'] ?? 1

  if (version === 1) return upgradeV1ToV2(root)
  if (version === 2) return root as ISchema

  throw new Error(`unsupported work-record schema version: ${version}`)
}
