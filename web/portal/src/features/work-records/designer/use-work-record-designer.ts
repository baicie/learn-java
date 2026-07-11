import { useEffect, useMemo, useState } from 'react'
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
  const [savedSchemaJson, setSavedSchemaJson] = useState('[]')
  const [selectedFieldId, setSelectedFieldId] = useState('')
  const [previewOpen, setPreviewOpen] = useState(true)
  const [publishValidation, setPublishValidation] =
    useState<TemplatePublishValidationResult | null>(null)
  const [hydratedTemplateKey, setHydratedTemplateKey] = useState('')

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

  useEffect(() => {
    if (!selectedTemplate) return

    const key = [
      selectedTemplate.id,
      selectedTemplate.updatedAt,
      selectedTemplate.draftSchemaJson,
      selectedTemplate.draftDesignerJson,
      selectedTemplate.currentVersionId ?? '',
      currentVersionFieldsQuery.dataUpdatedAt,
      publishValidation?.referencedRecordCount ?? 0,
    ].join('|')

    if (hydratedTemplateKey === key) return

    const parsed = parseDraftSchema(
      selectedTemplate.draftSchemaJson,
      selectedTemplate.draftDesignerJson
    )
    const merged = mergePublishedLocks(
      parsed,
      currentVersionFieldsQuery.data,
      publishValidation?.referencedRecordCount ?? 0
    )

    queueMicrotask(() => {
      setFields(merged)
      setSelectedFieldId((current) => {
        if (current && merged.some((item) => item.id === current))
          return current
        return merged[0]?.id ?? ''
      })
      setHydratedTemplateKey(key)
      setSavedSchemaJson(schemaToJson(merged))
    })
  }, [
    selectedTemplate,
    currentVersionFieldsQuery.data,
    currentVersionFieldsQuery.dataUpdatedAt,
    hydratedTemplateKey,
    publishValidation?.referencedRecordCount,
  ])

  const markDirty = (next: DesignerField[]) => {
    setPublishValidation(null)
    setFields(normalizeSortOrder(next))
  }

  const loadTemplate = (templateId: string) => {
    setSelectedTemplateId(templateId)
    setPublishValidation(null)
    setHydratedTemplateKey('')
    setSelectedFieldId('')
  }

  const addField = (fieldType: WorkRecordFieldType) => {
    const next = [...fields, newDesignerField(fieldType, fields.length)]
    markDirty(next)
    setSelectedFieldId(next[next.length - 1]?.id ?? '')
  }

  const updateField = (fieldId: string, patch: Partial<DesignerField>) => {
    setPublishValidation(null)
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
    markDirty(next)
  }

  const duplicateField = (fieldId: string) => {
    const field = fields.find((item) => item.id === fieldId)
    if (!field) return

    const copy: DesignerField = {
      ...field,
      id: `${field.id}_copy_${Date.now().toString(36)}`,
      fieldName: `${field.fieldName} 副本`,
      fieldCode: uniqueCopyCode(field.fieldCode, fields),
      locked: false,
      referenced: false,
      enabled: true,
      sortOrder: fields.length,
    }

    markDirty([...fields, copy])
    setSelectedFieldId(copy.id)
  }

  const removeOrDisableField = (fieldId: string) => {
    const field = fields.find((item) => item.id === fieldId)
    if (!field) return

    if (field.locked || field.referenced) {
      markDirty(
        fields.map((item) =>
          item.id === fieldId ? { ...item, enabled: false } : item
        )
      )
      return
    }

    const next = fields.filter((item) => item.id !== fieldId)
    markDirty(next)
    setSelectedFieldId(next[0]?.id ?? '')
  }

  const persistDraft = async () => {
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
  }

  const saveDraftMutation = useMutation({
    mutationFn: persistDraft,
    onSuccess: async () => {
      setSavedSchemaJson(schemaJson)
      await queryClient.invalidateQueries({
        queryKey: ['work-record-templates'],
      })
    },
  })

  const validatePublishMutation = useMutation({
    mutationFn: async () => {
      if (!selectedTemplate) throw new Error('请先选择模板')
      await persistDraft()
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

      await persistDraft()
      const result = await validateTemplatePublish(selectedTemplate.id)
      setPublishValidation(result)

      if (!result.valid) {
        throw new Error(result.errors[0] ?? '发布校验失败')
      }

      return publishTemplate(
        selectedTemplate.id,
        `v${new Date().toISOString().slice(0, 19).replace('T', ' ')}`
      )
    },
    onSuccess: async () => {
      setPublishValidation(null)
      setHydratedTemplateKey('')
      setSavedSchemaJson(schemaJson)
      await queryClient.invalidateQueries({
        queryKey: ['work-record-templates'],
      })
      await queryClient.invalidateQueries({
        queryKey: ['work-record-template-version-fields'],
      })
    },
  })

  const queryError =
    templatesQuery.error ??
    dictTypesQuery.error ??
    currentVersionFieldsQuery.error

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
    queryError,
    dirty: schemaJson !== savedSchemaJson,
    setPreviewOpen,
    setSelectedFieldId,
    loadTemplate,
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

function uniqueCopyCode(baseCode: string, fields: DesignerField[]) {
  const existing = new Set(fields.map((field) => field.fieldCode))
  let index = 1
  let candidate = `${baseCode}_copy`
  while (existing.has(candidate)) {
    index += 1
    candidate = `${baseCode}_copy_${index}`
  }
  return candidate
}
