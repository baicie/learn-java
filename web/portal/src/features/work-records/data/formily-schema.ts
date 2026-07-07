import { z } from 'zod'
import { workRecordFieldTypes } from './field-types'
import { isReservedFieldCode } from './reserved-field-codes'

export const workRecordSchemaExtensionSchema = z.object({
  fieldCode: z.string().min(1),
  fieldType: z.enum(workRecordFieldTypes),
  optionSource: z.enum(['static', 'dict']).default('static'),
  dictCode: z.string().optional(),
  listVisible: z.boolean().default(false),
  filterable: z.boolean().default(false),
  statistical: z.boolean().default(false),
})

export type WorkRecordSchemaField = z.infer<
  typeof workRecordSchemaExtensionSchema
> & {
  fieldName: string
  required: boolean
  sortOrder: number
  enabled: boolean
  optionsJson: string
}

type JsonObject = Record<string, unknown>

function isObject(value: unknown): value is JsonObject {
  return typeof value === 'object' && value !== null && !Array.isArray(value)
}

export function extractWorkRecordFields(
  schema: JsonObject
): WorkRecordSchemaField[] {
  const fields: WorkRecordSchemaField[] = []

  function visit(node: unknown, order: { value: number }) {
    if (!isObject(node)) return

    const extension = node['x-work-record']
    if (extension) {
      const parsed = workRecordSchemaExtensionSchema.parse(extension)
      if (isReservedFieldCode(parsed.fieldCode)) {
        throw new Error(`reserved fieldCode: ${parsed.fieldCode}`)
      }
      if (parsed.optionSource === 'dict' && !parsed.dictCode) {
        throw new Error(`dictCode is required: ${parsed.fieldCode}`)
      }
      fields.push({
        ...parsed,
        fieldName: String(node.title ?? parsed.fieldCode),
        required: Boolean(node.required),
        sortOrder: order.value,
        enabled: true,
        optionsJson: JSON.stringify(node.enum ?? []),
      })
      order.value += 10
    }

    for (const value of Object.values(node)) {
      if (isObject(value) || Array.isArray(value)) visit(value, order)
    }
  }

  visit(schema, { value: 10 })
  return fields
}
