import { z } from 'zod'
import { apiClient } from '@/lib/api-client'
import { apiResponseSchema } from '@/lib/api-response'
import {
  WORK_RECORD_FIELD_TYPES,
  type TemplatePublishValidationResult,
  type WorkRecordTemplate,
  type WorkRecordTemplateVersion,
  type WorkRecordVersionField,
} from './types'

const fieldTypeSchema = z.enum(WORK_RECORD_FIELD_TYPES)

const templateSchema = z.object({
  id: z.string(),
  tenantId: z.string(),
  code: z.string(),
  name: z.string(),
  description: z.string().nullable().optional(),
  status: z.enum(['draft', 'published', 'disabled', 'archived']),
  enabled: z.boolean(),
  currentVersionId: z.string().nullable().optional(),
  draftSchemaJson: z.string(),
  draftDesignerJson: z.string(),
  createdBy: z.string(),
  createdAt: z.string(),
  updatedAt: z.string(),
  deletedAt: z.string().nullable().optional(),
})

const templateVersionSchema = z.object({
  id: z.string(),
  tenantId: z.string(),
  templateId: z.string(),
  versionNo: z.number(),
  versionName: z.string().nullable().optional(),
  schemaJson: z.string(),
  designerJson: z.string(),
  fieldIndexJson: z.string(),
  publishedBy: z.string(),
  publishedAt: z.string(),
  createdAt: z.string(),
})

const versionFieldSchema = z.object({
  id: z.string(),
  tenantId: z.string(),
  templateId: z.string(),
  templateVersionId: z.string(),
  fieldName: z.string(),
  fieldCode: z.string(),
  fieldType: fieldTypeSchema,
  required: z.boolean(),
  defaultValue: z.string().nullable().optional(),
  optionSource: z.enum(['static', 'dict']),
  dictCode: z.string().nullable().optional(),
  optionsJson: z.string(),
  schemaPath: z.string().nullable().optional(),
  listVisible: z.boolean(),
  filterable: z.boolean(),
  exportable: z.boolean(),
  statistical: z.boolean(),
  sortOrder: z.number(),
  enabled: z.boolean(),
  createdAt: z.string(),
  updatedAt: z.string(),
})

const publishValidationSchema = z.object({
  valid: z.boolean(),
  schemaVersion: z.number(),
  fieldCount: z.number(),
  referencedRecordCount: z.number(),
  errors: z.array(z.string()),
  warnings: z.array(z.string()),
})

export async function listTemplates(): Promise<WorkRecordTemplate[]> {
  const { data } = await apiClient.get('/api/work-record/templates', {
    params: { includeDisabled: true },
  })
  return apiResponseSchema(z.array(templateSchema)).parse(data).data
}

export async function saveTemplateDraft(
  templateId: string,
  input: {
    name?: string
    description?: string
    schemaJson: string
    designerJson: string
  }
): Promise<WorkRecordTemplate> {
  const { data } = await apiClient.put(
    `/api/work-record/templates/${templateId}/draft`,
    input
  )
  return apiResponseSchema(templateSchema).parse(data).data
}

export async function validateTemplatePublish(
  templateId: string
): Promise<TemplatePublishValidationResult> {
  const { data } = await apiClient.post(
    `/api/work-record/templates/${templateId}/validate-publish`
  )
  return apiResponseSchema(publishValidationSchema).parse(data).data
}

export async function publishTemplate(
  templateId: string,
  versionName?: string
): Promise<WorkRecordTemplateVersion> {
  const { data } = await apiClient.post(
    `/api/work-record/templates/${templateId}/publish`,
    { versionName }
  )
  return apiResponseSchema(templateVersionSchema).parse(data).data
}

export async function listTemplateVersions(
  templateId: string
): Promise<WorkRecordTemplateVersion[]> {
  const { data } = await apiClient.get(
    `/api/work-record/templates/${templateId}/versions`
  )
  return apiResponseSchema(z.array(templateVersionSchema)).parse(data).data
}

export async function listTemplateVersionFields(
  templateId: string,
  versionId: string
): Promise<WorkRecordVersionField[]> {
  const { data } = await apiClient.get(
    `/api/work-record/templates/${templateId}/versions/${versionId}/fields`
  )
  return apiResponseSchema(z.array(versionFieldSchema)).parse(data).data
}
