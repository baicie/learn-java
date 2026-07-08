import { useMemo, useState } from 'react'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'
import { toast } from 'sonner'
import { Skeleton } from '@/components/ui/skeleton'
import {
  normalizeSchema,
  type DesignerSchema,
} from '@/features/work-records/data/designer/schema-builder'
import { extractWorkRecordFields } from '@/features/work-records/data/formily-schema'
import { saveTemplateSchema } from '@/features/work-records/api/template-api'
import { useTemplates } from '@/features/work-records/hooks/use-record-template'
import { FormilyDesignerShell } from './formily-designer-shell'
import { WorkRecordsLayout } from './work-records-layout'

export function TemplateDesignerPage() {
  return (
    <WorkRecordsLayout
      titleKey='workRecords.designer.title'
      descriptionKey='workRecords.designer.description'
    >
      <TemplateDesignerBridge />
    </WorkRecordsLayout>
  )
}

function TemplateDesignerBridge() {
  const { t } = useTranslation()
  const templates = useTemplates()
  const queryClient = useQueryClient()
  const [templateId, setTemplateId] = useState<string>('')

  // Initialize once to the first template when data arrives.
  // This is derived state — we avoid storing schema in state and instead derive
  // it in useMemo so no cascading setState occurs.
  const initializedId = useMemo<string>(() => {
    if (templateId) return templateId
    if (templates.data?.length) return templates.data[0].id
    return ''
  }, [templateId, templates.data])

  const schema = useMemo<DesignerSchema | null>(() => {
    if (!initializedId || !templates.data) return null
    const template = templates.data.find((item) => item.id === initializedId)
    if (!template) return null
    return normalizeSchema(parseSchemaJson(template.schemaJson))
  }, [initializedId, templates.data])

  const mutation = useMutation({
    mutationFn: saveTemplateSchema,
    onSuccess: async () => {
      await queryClient.invalidateQueries({
        queryKey: ['work-record-templates'],
      })
      toast.success(t('common.save'))
    },
    onError: () => {
      toast.error(t('common.saveFailed'))
    },
  })

  if (templates.isLoading || !schema) {
    return <Skeleton className='h-64 w-full' />
  }

  return (
    <div className='grid gap-2'>
      <select
        data-testid='bridge-template-select'
        className='rounded-md border px-2 py-1 text-sm'
        value={initializedId}
        onChange={(event) => setTemplateId(event.target.value)}
      >
        {(templates.data ?? []).map((tmpl) => (
          <option key={tmpl.id} value={tmpl.id}>
            {tmpl.name}
          </option>
        ))}
      </select>
      <FormilyDesignerShell
        initialSchema={schema}
        onSave={async (payload) => {
          mutation.mutate({
            templateId: initializedId,
            schemaJson: JSON.stringify(payload.schema),
            designerJson: JSON.stringify({ expandedProperties: [] }),
            fields: extractWorkRecordFields(
              payload.schema as unknown as Record<string, unknown>
            ),
          })
        }}
        savePending={mutation.isPending}
      />
    </div>
  )
}

function parseSchemaJson(value: string | null | undefined): unknown {
  if (!value || value === '{}') return {}
  try {
    return JSON.parse(value)
  } catch {
    return {}
  }
}
