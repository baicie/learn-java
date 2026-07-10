import { useMemo, useState } from 'react'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { useNavigate } from '@tanstack/react-router'
import { useTranslation } from 'react-i18next'
import { toast } from 'sonner'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select'
import { Skeleton } from '@/components/ui/skeleton'
import {
  createWorkRecord,
  updateWorkRecord,
  type WorkRecordPayload,
} from '../api/work-record-api'
import { type WorkRecord } from '../data/schema'
import { useRecordTemplate } from '../hooks/use-record-template'
import {
  useDefaultTemplate,
  useTemplateDictionaries,
} from '../hooks/use-template-dictionaries'
import { FormilyRuntimeForm } from './formily-runtime-form'

type RecordFormProps = {
  /** Existing record for edit mode, undefined for create mode. */
  record?: WorkRecord
}

function parseCustomData(json?: string): Record<string, unknown> {
  if (!json) return {}
  try {
    return JSON.parse(json)
  } catch {
    return {}
  }
}

/**
 * Record creation/edit form.
 *
 * - Create mode: loads default template on mount.
 * - Edit mode: uses the record's templateId and pre-fills values.
 * - Dynamic fields are rendered via FormilyRuntimeForm.
 * - Built-in fields (title, status, ownerId, recordTime) are rendered as native inputs.
 * - On submit, custom data is serialized to JSON and sent to the backend.
 */
export function RecordForm({ record }: RecordFormProps) {
  const { t } = useTranslation()
  const navigate = useNavigate()
  const queryClient = useQueryClient()

  // Determine initial template - use lazy initialization to avoid setState in effect
  const { data: defaultTemplate, isLoading: isLoadingDefault } =
    useDefaultTemplate()
  const [selectedTemplateId, setSelectedTemplateId] = useState<string>(
    () => record?.templateId ?? ''
  )

  // Set default template once it loads (only in create mode)
  if (!record && !selectedTemplateId && defaultTemplate?.id) {
    setSelectedTemplateId(defaultTemplate.id)
  }

  // Load selected template
  const template = useRecordTemplate(selectedTemplateId || undefined)

  // Load dictionaries for the selected template
  const { dictionaries, isLoading: isLoadingDicts } = useTemplateDictionaries(
    template.data,
    false // edit/create: only enabled items
  )

  // Built-in fields - use record values directly
  const [title, setTitle] = useState(() => record?.title ?? '')
  const [status, setStatus] = useState(() => record?.status ?? 'draft')
  const [ownerId, setOwnerId] = useState(() => record?.ownerId ?? '')
  const [recordTime, setRecordTime] = useState(() => {
    if (record?.recordTime) {
      return new Date(record.recordTime).toISOString().slice(0, 16)
    }
    return new Date().toISOString().slice(0, 16)
  })

  // Dynamic field values (from Formily)
  const [customValues, setCustomValues] = useState<Record<string, unknown>>(
    () => parseCustomData(record?.customDataJson)
  )

  // Submit mutation
  const mutation = useMutation({
    mutationFn: (payload: WorkRecordPayload) =>
      record ? updateWorkRecord(record.id, payload) : createWorkRecord(payload),
    onSuccess: async (saved) => {
      await queryClient.invalidateQueries({ queryKey: ['work-records'] })
      toast.success(
        record ? t('common.save') : t('workRecords.form.submitSuccess')
      )
      navigate({
        to: '/work-records/$recordId',
        params: { recordId: saved.id },
      })
    },
    onError: () => {
      toast.error(t('common.saveFailed'))
    },
  })

  // Parse schema from template
  const schema = useMemo(() => {
    const schemaJson = template.data?.schemaJson
    if (!schemaJson) return {}
    try {
      return JSON.parse(schemaJson)
    } catch {
      return {}
    }
  }, [template.data])

  const isLoading = isLoadingDefault || template.isLoading || isLoadingDicts

  function handleSubmit(e: React.FormEvent<HTMLFormElement>) {
    e.preventDefault()

    if (!selectedTemplateId) {
      toast.error(t('workRecords.form.templateRequired'))
      return
    }
    if (!title.trim()) {
      toast.error(t('workRecords.form.titleRequired'))
      return
    }

    const payload: WorkRecordPayload = {
      templateId: selectedTemplateId,
      title: title.trim(),
      status,
      ownerId: ownerId || null,
      recordTime: new Date(recordTime).toISOString(),
      customDataJson: JSON.stringify(customValues),
    }

    mutation.mutate(payload)
  }

  if (isLoading) {
    return <Skeleton className='h-64 w-full' />
  }

  return (
    <form className='grid max-w-3xl gap-6' onSubmit={handleSubmit}>
      {/* Section: Basic Info */}
      <div className='space-y-4'>
        <h2 className='text-lg font-semibold'>
          {t('workRecords.form.basicInfo')}
        </h2>

        {/* Template selector (hidden in edit mode) */}
        {!record && (
          <div className='grid gap-2'>
            <Label htmlFor='template'>{t('workRecords.field.template')}</Label>
            <Select
              value={selectedTemplateId}
              onValueChange={setSelectedTemplateId}
            >
              <SelectTrigger id='template'>
                <SelectValue
                  placeholder={t('workRecords.form.selectTemplate')}
                />
              </SelectTrigger>
              <SelectContent>
                {template.data && (
                  <SelectItem key={template.data.id} value={template.data.id}>
                    {template.data.name}
                  </SelectItem>
                )}
              </SelectContent>
            </Select>
          </div>
        )}

        {/* Title */}
        <div className='grid gap-2'>
          <Label htmlFor='title'>
            {t('workRecords.field.title')}
            <span className='ml-1 text-destructive'>*</span>
          </Label>
          <Input
            id='title'
            value={title}
            onChange={(e) => setTitle(e.target.value)}
            required
            placeholder={t('workRecords.form.titlePlaceholder')}
          />
        </div>

        {/* Status */}
        <div className='grid gap-2'>
          <Label htmlFor='status'>{t('workRecords.field.status')}</Label>
          <Select value={status} onValueChange={setStatus}>
            <SelectTrigger id='status'>
              <SelectValue />
            </SelectTrigger>
            <SelectContent>
              <SelectItem value='draft'>
                {t('workRecords.status.draft')}
              </SelectItem>
              <SelectItem value='processing'>
                {t('workRecords.status.processing')}
              </SelectItem>
              <SelectItem value='done'>
                {t('workRecords.status.done')}
              </SelectItem>
              <SelectItem value='archived'>
                {t('workRecords.status.archived')}
              </SelectItem>
            </SelectContent>
          </Select>
        </div>

        {/* Owner */}
        <div className='grid gap-2'>
          <Label htmlFor='ownerId'>{t('workRecords.field.owner')}</Label>
          <Input
            id='ownerId'
            value={ownerId}
            onChange={(e) => setOwnerId(e.target.value)}
            placeholder={t('workRecords.form.ownerPlaceholder')}
          />
        </div>

        {/* Record Time */}
        <div className='grid gap-2'>
          <Label htmlFor='recordTime'>
            {t('workRecords.field.recordTime')}
          </Label>
          <Input
            id='recordTime'
            type='datetime-local'
            value={recordTime}
            onChange={(e) => setRecordTime(e.target.value)}
          />
        </div>
      </div>

      {/* Section: Dynamic Fields */}
      {schema.properties && Object.keys(schema.properties).length > 0 && (
        <div className='space-y-4'>
          <h2 className='text-lg font-semibold'>
            {t('workRecords.form.recordContent')}
          </h2>
          <div className='rounded-md border p-4'>
            <FormilyRuntimeForm
              schema={schema}
              initialValues={customValues}
              dictionaries={dictionaries}
              onValuesChange={setCustomValues}
            />
          </div>
        </div>
      )}

      {/* Actions */}
      <div className='flex justify-end gap-2'>
        <Button
          type='button'
          variant='outline'
          onClick={() =>
            navigate({
              to: '/work-records',
              search: {
                page: 1,
                pageSize: 20,
                quickView: 'all',
                workdayCount: 5,
                templateId: '',
                statuses: [],
                ownerId: '',
                creatorId: '',
                keyword: '',
                recordTimeFrom: '',
                recordTimeTo: '',
                sortBy: 'recordTime',
                sortDir: 'desc',
                dynamicFilters: [],
                visibleColumns: [],
              },
            })
          }
        >
          {t('common.cancel')}
        </Button>
        <Button
          type='button'
          variant='secondary'
          disabled={mutation.isPending}
          onClick={() => {
            setStatus('draft')
            // Trigger form submit manually after status update
            const form = document.querySelector('form')
            if (form) {
              const input = document.createElement('input')
              input.type = 'hidden'
              input.name = '_action'
              input.value = 'draft'
              form.appendChild(input)
              form.requestSubmit()
            }
          }}
        >
          {t('workRecords.form.saveDraft')}
        </Button>
        <Button type='submit' disabled={mutation.isPending}>
          {record ? t('common.save') : t('workRecords.form.submit')}
        </Button>
      </div>
    </form>
  )
}
