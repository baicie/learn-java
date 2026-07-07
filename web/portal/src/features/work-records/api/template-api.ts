import { z } from 'zod'
import { type WorkRecordSchemaField } from '../data/formily-schema'
import {
  apiResponseSchema,
  workRecordFieldSchema,
  workRecordTemplateSchema,
  type WorkRecordField,
  type WorkRecordTemplate,
} from '../data/schema'
import { workRecordHttp } from './http'

export async function listTemplates(): Promise<WorkRecordTemplate[]> {
  const { data } = await workRecordHttp.get('/api/work-record/templates')
  return apiResponseSchema(z.array(workRecordTemplateSchema)).parse(data).data
}

export async function getTemplate(
  templateId: string
): Promise<WorkRecordTemplate> {
  const templates = await listTemplates()
  const template = templates.find((item) => item.id === templateId)
  if (!template) throw new Error('template not found')
  return template
}

export async function listTemplateFields(
  templateId: string
): Promise<WorkRecordField[]> {
  const { data } = await workRecordHttp.get(
    `/api/work-record/templates/${templateId}/fields`
  )
  return apiResponseSchema(z.array(workRecordFieldSchema)).parse(data).data
}

export async function saveTemplateSchema(input: {
  templateId: string
  schemaJson: string
  fields: WorkRecordSchemaField[]
}): Promise<WorkRecordTemplate> {
  const { data } = await workRecordHttp.post(
    `/api/work-record/templates/${input.templateId}/schema`,
    {
      schemaJson: input.schemaJson,
      fields: input.fields.map((field) => ({
        fieldName: field.fieldName,
        fieldCode: field.fieldCode,
        fieldType: field.fieldType,
        required: field.required,
        defaultValue: null,
        optionSource: field.optionSource,
        dictCode: field.dictCode ?? null,
        optionsJson: field.optionsJson,
        listVisible: field.listVisible,
        filterable: field.filterable,
        statistical: field.statistical,
        sortOrder: field.sortOrder,
        enabled: field.enabled,
      })),
    }
  )
  return apiResponseSchema(workRecordTemplateSchema).parse(data).data
}
