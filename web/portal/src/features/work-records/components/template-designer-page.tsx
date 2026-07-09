import { useMemo, useState } from 'react'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { AlertCircle, RefreshCw, WifiOff } from 'lucide-react'
import { useTranslation } from 'react-i18next'
import { toast } from 'sonner'
import { Button } from '@/components/ui/button'
import { Skeleton } from '@/components/ui/skeleton'
import { saveTemplateSchema } from '@/features/work-records/api/template-api'
import {
  emptySchema,
  normalizeSchema,
  type DesignerSchema,
} from '@/features/work-records/data/designer/schema-builder'
import { extractWorkRecordFields } from '@/features/work-records/data/formily-schema'
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

  const schema = useMemo<DesignerSchema>(() => {
    if (!initializedId || !templates.data) return emptySchema()
    const template = templates.data.find((item) => item.id === initializedId)
    if (!template) return emptySchema()
    return normalizeSchema(parseSchemaJson(template.schemaJson))
  }, [initializedId, templates.data])

  const mode: TemplateMode = templates.isLoading
    ? 'loading'
    : templates.isError || !templates.data
      ? 'offline'
      : templates.data.length === 0
        ? 'empty'
        : 'ready'

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

  if (mode === 'loading') {
    return <Skeleton className='h-64 w-full' />
  }

  return (
    <div className='grid gap-3'>
      {mode !== 'ready' ? (
        <OfflineBanner mode={mode} onRetry={templates.refetch} />
      ) : null}
      <div className='flex flex-wrap items-center gap-2'>
        <label
          htmlFor='designer-template-select'
          className='text-sm text-muted-foreground'
        >
          {t('workRecords.designer.toolbar.templateLabel')}
        </label>
        <select
          id='designer-template-select'
          data-testid='bridge-template-select'
          className='rounded-md border px-2 py-1 text-sm disabled:opacity-60'
          value={initializedId}
          onChange={(event) => setTemplateId(event.target.value)}
          disabled={mode !== 'ready' || (templates.data?.length ?? 0) === 0}
        >
          {mode === 'ready' && templates.data && templates.data.length > 0 ? (
            templates.data.map((tmpl) => (
              <option key={tmpl.id} value={tmpl.id}>
                {tmpl.name}
              </option>
            ))
          ) : (
            <option value=''>—</option>
          )}
        </select>
      </div>
      <FormilyDesignerShell
        initialSchema={schema}
        onSave={
          mode === 'ready' && initializedId
            ? async (payload) => {
                mutation.mutate({
                  templateId: initializedId,
                  schemaJson: JSON.stringify(payload.schema),
                  designerJson: JSON.stringify({ expandedProperties: [] }),
                  fields: extractWorkRecordFields(
                    payload.schema as unknown as Record<string, unknown>
                  ),
                })
              }
            : undefined
        }
        savePending={mutation.isPending}
      />
    </div>
  )
}

type TemplateMode = 'loading' | 'ready' | 'offline' | 'empty'

function OfflineBanner({
  mode,
  onRetry,
}: {
  mode: 'offline' | 'empty'
  onRetry: () => void
}) {
  const { t } = useTranslation()
  const messageKey =
    mode === 'offline'
      ? 'workRecords.designer.toolbar.offline'
      : 'workRecords.designer.toolbar.empty'
  const Icon = mode === 'offline' ? WifiOff : AlertCircle
  return (
    <div
      role='status'
      data-testid='designer-offline-banner'
      className='flex flex-wrap items-center justify-between gap-2 rounded-md border border-amber-300 bg-amber-50 px-3 py-2 text-amber-900 dark:border-amber-700 dark:bg-amber-950/40 dark:text-amber-200'
    >
      <div className='flex items-center gap-2 text-sm'>
        <Icon className='h-4 w-4' />
        <span>{t(messageKey)}</span>
      </div>
      <Button
        type='button'
        size='sm'
        variant='outline'
        onClick={() => {
          onRetry()
        }}
        data-testid='designer-retry'
      >
        <RefreshCw className='me-1 h-3.5 w-3.5' />
        {t('workRecords.designer.toolbar.retry')}
      </Button>
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
