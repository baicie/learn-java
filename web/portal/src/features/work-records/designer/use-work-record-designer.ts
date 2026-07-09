import { useMemo, useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { listDictTypes } from '@/features/dictionaries/api'
import {
  listTemplateVersionFields,
  listTemplates,
  publishTemplate,
  saveTemplateDraft,
  validateTemplatePublish,
} from './api'
import {
  designerToJson,
  diffFields,
  mergePublishedLocks,
  newDesignerField,
  normalizeSortOrder,
  parseDraftSchema,
  schemaToJson,
  validateDesignerFields,
} from './schema'
import {
  type DesignerField,
  type TemplatePublishValidationResult,
  type WorkRecordFieldType,
} from './types'

export function useWorkRecordDesigner() {
  const queryClient = useQueryClient()
  const [selectedTemplateId, setSelectedTemplateId] = useState('')
  const [fields, setFields] = useState<DesignerField[]>([])
  const [selectedFieldId, setSelectedFieldId] = useState('')
  const [previewOpen, setPreviewOpen] = useState(true)
  const [publishValidation, setPublishValidation] =
    useState<TemplatePublishValidationResult | null>(null)

  const templatesQuery = useQuery({
    queryKey: ['work-record-templates'],
    queryFn: listTemplates,
  })

  const dictTypesQuery = useQuery({
    queryKey: ['platform-dictionaries'],
    queryFn: () => listDictTypes(true),
  })

  const selectedTemplate = useMemo(() => {
    const list = templatesQuery.data ?? []
    return list.find((item) => item.id === selectedTemplateId) ?? list[0]
  }, [selectedTemplateId, templatesQuery.data])

  const currentVersionFieldsQuery = useQuery({
    queryKey: [
      'work-record-template-version-fields',
      selectedTemplate?.id,
      selectedTemplate?.currentVersionId,
    ],
    queryFn: () =>
      listTemplateVersionFields(
        selectedTemplate!.id,
        selectedTemplate!.currentVersionId!
      ),
    enabled: Boolean(
      selectedTemplate?.id && selectedTemplate?.currentVersionId
    ),
  })

  const validationErrors = useMemo(
    () => validateDesignerFields(fields),
    [fields]
  )

  const schemaJson = useMemo(() => schemaToJson(fields), [fields])
  const designerJson = useMemo(() => designerToJson(fields), [fields])

  const schemaDiff = useMemo(
    () => diffFields(currentVersionFieldsQuery.data, fields),
    [currentVersionFieldsQuery.data, fields]
  )

  const selectedField = useMemo(
    () => fields.find((item) => item.id === selectedFieldId),
    [fields, selectedFieldId]
  )

  const loadTemplate = (templateId: string) => {
    setSelectedTemplateId(templateId)
    setPublishValidation(null)
    const template = templatesQuery.data?.find((item) => item.id === templateId)
    if (!template) return

    const parsed = parseDraftSchema(template.draftSchemaJson)
    setFields(normalizeSortOrder(parsed))
    setSelectedFieldId(parsed[0]?.id ?? '')
  }

  const hydrateCurrentTemplate = () => {
    if (!selectedTemplate) return
    const parsed = parseDraftSchema(selectedTemplate.draftSchemaJson)
    const merged = mergePublishedLocks(
      parsed,
      currentVersionFieldsQuery.data,
      publishValidation?.referencedRecordCount ?? 0
    )
    setFields(merged)
    setSelectedFieldId(merged[0]?.id ?? '')
  }

  const addField = (fieldType: WorkRecordFieldType) => {
    const next = normalizeSortOrder([
      ...fields,
      newDesignerField(fieldType, fields.length),
    ])
    setFields(next)
    setSelectedFieldId(next[next.length - 1]?.id ?? '')
  }

  const updateField = (fieldId: string, patch: Partial<DesignerField>) => {
    setFields((current) =>
      current.map((field) => {
        if (field.id !== fieldId) return field
        const next = { ...field, ...patch }
        if (field.locked) {
          next.fieldCode = field.fieldCode
          next.fieldType = field.fieldType
        }
        if (next.optionSource === 'static') {
          next.dictCode = ''
        }
        return next
      })
    )
  }

  const moveField = (fieldId: string, direction: 'up' | 'down') => {
    const index = fields.findIndex((field) => field.id === fieldId)
    if (index < 0) return
    const target = direction === 'up' ? index - 1 : index + 1
    if (target < 0 || target >= fields.length) return

    const next = [...fields]
    const [item] = next.splice(index, 1)
    next.splice(target, 0, item)
    setFields(normalizeSortOrder(next))
  }

  const duplicateField = (fieldId: string) => {
    const field = fields.find((item) => item.id === fieldId)
    if (!field) return

    const copy: DesignerField = {
      ...field,
      id: crypto.randomUUID?.() ?? `${field.id}_copy`,
      fieldName: `${field.fieldName} 副本`,
      fieldCode: `${field.fieldCode}_copy`,
      locked: false,
      referenced: false,
      sortOrder: fields.length,
    }

    const next = normalizeSortOrder([...fields, copy])
    setFields(next)
    setSelectedFieldId(copy.id)
  }

  const removeOrDisableField = (fieldId: string) => {
    const field = fields.find((item) => item.id === fieldId)
    if (!field) return

    if (field.locked || field.referenced) {
      setFields((current) =>
        normalizeSortOrder(
          current.map((item) =>
            item.id === fieldId ? { ...item, enabled: false } : item
          )
        )
      )
      return
    }

    const next = normalizeSortOrder(
      fields.filter((item) => item.id !== fieldId)
    )
    setFields(next)
    setSelectedFieldId(next[0]?.id ?? '')
  }

  const saveDraftMutation = useMutation({
    mutationFn: async () => {
      if (!selectedTemplate) throw new Error('请先选择模板')
      if (validationErrors.length) {
        throw new Error(validationErrors[0])
      }
      return saveTemplateDraft(selectedTemplate.id, {
        name: selectedTemplate.name,
        description: selectedTemplate.description ?? undefined,
        schemaJson,
        designerJson,
      })
    },
    onSuccess: async () => {
      await queryClient.invalidateQueries({
        queryKey: ['work-record-templates'],
      })
    },
  })

  const validatePublishMutation = useMutation({
    mutationFn: async () => {
      if (!selectedTemplate) throw new Error('请先选择模板')
      if (validationErrors.length) {
        throw new Error(validationErrors[0])
      }
      await saveTemplateDraft(selectedTemplate.id, {
        name: selectedTemplate.name,
        description: selectedTemplate.description ?? undefined,
        schemaJson,
        designerJson,
      })
      return validateTemplatePublish(selectedTemplate.id)
    },
    onSuccess: async (result) => {
      setPublishValidation(result)
      await queryClient.invalidateQueries({
        queryKey: ['work-record-templates'],
      })
    },
  })

  const publishMutation = useMutation({
    mutationFn: async () => {
      if (!selectedTemplate) throw new Error('请先选择模板')
      if (validationErrors.length) {
        throw new Error(validationErrors[0])
      }
      const result =
        publishValidation ??
        (await validateTemplatePublish(selectedTemplate.id))
      if (!result.valid) {
        throw new Error(result.errors[0] ?? '发布校验失败')
      }
      return publishTemplate(
        selectedTemplate.id,
        `v${new Date().toISOString().slice(0, 19).replace('T', ' ')}`
      )
    },
    onSuccess: async () => {
      await queryClient.invalidateQueries({
        queryKey: ['work-record-templates'],
      })
      await queryClient.invalidateQueries({
        queryKey: ['work-record-template-version-fields'],
      })
    },
  })

  return {
    templates: templatesQuery.data ?? [],
    dictTypes: (dictTypesQuery.data ?? []).map((item) => ({
      id: item.id,
      dictCode: item.dictCode,
      dictName: item.dictName,
      enabled: item.enabled,
    })),
    selectedTemplate,
    selectedTemplateId: selectedTemplate?.id ?? selectedTemplateId,
    selectedField,
    fields,
    previewOpen,
    publishValidation,
    validationErrors,
    schemaJson,
    designerJson,
    schemaDiff,
    loading:
      templatesQuery.isLoading ||
      dictTypesQuery.isLoading ||
      currentVersionFieldsQuery.isLoading,
    saving: saveDraftMutation.isPending,
    validating: validatePublishMutation.isPending,
    publishing: publishMutation.isPending,
    saveError: saveDraftMutation.error,
    publishError: publishMutation.error,
    setPreviewOpen,
    setSelectedFieldId,
    loadTemplate,
    hydrateCurrentTemplate,
    addField,
    updateField,
    moveField,
    duplicateField,
    removeOrDisableField,
    saveDraft: () => saveDraftMutation.mutateAsync(),
    validatePublish: () => validatePublishMutation.mutateAsync(),
    publish: () => publishMutation.mutateAsync(),
  }
}
