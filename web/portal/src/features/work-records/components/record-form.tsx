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
import { Textarea } from '@/components/ui/textarea'
import {
  createWorkRecord,
  updateWorkRecord,
  type WorkRecordPayload,
} from '../api/work-record-api'
import { type WorkRecord } from '../data/schema'
import { useTemplates } from '../hooks/use-record-template'

type RecordFormProps = {
  record?: WorkRecord
}

export function RecordForm({ record }: RecordFormProps) {
  const { t } = useTranslation()
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const templates = useTemplates()
  const [templateId, setTemplateId] = useState(record?.templateId ?? '')
  const [title, setTitle] = useState(record?.title ?? '')
  const [status, setStatus] = useState(record?.status ?? 'draft')
  const [ownerId, setOwnerId] = useState(record?.ownerId ?? '')
  const [customDataJson, setCustomDataJson] = useState(
    record?.customDataJson ?? '{}'
  )

  const selectedTemplate = useMemo(
    () => templates.data?.find((template) => template.id === templateId),
    [templateId, templates.data]
  )

  const mutation = useMutation({
    mutationFn: (payload: WorkRecordPayload) =>
      record ? updateWorkRecord(record.id, payload) : createWorkRecord(payload),
    onSuccess: async (saved) => {
      await queryClient.invalidateQueries({ queryKey: ['work-records'] })
      toast.success(t('common.save'))
      navigate({
        to: '/work-records/$recordId',
        params: { recordId: saved.id },
      })
    },
  })

  if (templates.isLoading) {
    return <Skeleton className='h-64 w-full' />
  }

  function handleSubmit(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault()
    JSON.parse(customDataJson)
    mutation.mutate({
      templateId,
      title,
      status,
      ownerId: ownerId || null,
      customDataJson,
    })
  }

  return (
    <form className='grid max-w-3xl gap-4' onSubmit={handleSubmit}>
      <div className='grid gap-2'>
        <Label htmlFor='template'>{t('workRecords.field.template')}</Label>
        <Select value={templateId} onValueChange={setTemplateId}>
          <SelectTrigger id='template'>
            <SelectValue placeholder='选择模板' />
          </SelectTrigger>
          <SelectContent>
            {(templates.data ?? []).map((template) => (
              <SelectItem key={template.id} value={template.id}>
                {template.name}
              </SelectItem>
            ))}
          </SelectContent>
        </Select>
      </div>
      <div className='grid gap-2'>
        <Label htmlFor='title'>{t('workRecords.field.title')}</Label>
        <Input
          id='title'
          value={title}
          onChange={(event) => setTitle(event.target.value)}
          required
        />
      </div>
      <div className='grid gap-2'>
        <Label htmlFor='status'>{t('workRecords.field.status')}</Label>
        <Select value={status} onValueChange={setStatus}>
          <SelectTrigger id='status'>
            <SelectValue />
          </SelectTrigger>
          <SelectContent>
            <SelectItem value='draft'>草稿</SelectItem>
            <SelectItem value='processing'>处理中</SelectItem>
            <SelectItem value='done'>完成</SelectItem>
            <SelectItem value='archived'>归档</SelectItem>
          </SelectContent>
        </Select>
      </div>
      <div className='grid gap-2'>
        <Label htmlFor='ownerId'>{t('workRecords.field.owner')}</Label>
        <Input
          id='ownerId'
          value={ownerId}
          onChange={(event) => setOwnerId(event.target.value)}
        />
      </div>
      <div className='grid gap-2'>
        <Label htmlFor='customData'>custom_data_json</Label>
        <Textarea
          id='customData'
          className='min-h-56 font-mono'
          value={customDataJson}
          onChange={(event) => setCustomDataJson(event.target.value)}
        />
      </div>
      {selectedTemplate ? (
        <div className='rounded-md border bg-muted/40 p-3 text-sm text-muted-foreground'>
          当前模板：{selectedTemplate.name}
        </div>
      ) : null}
      <div className='flex justify-end gap-2'>
        <Button
          type='button'
          variant='outline'
          onClick={() => navigate({ to: '/work-records' })}
        >
          {t('common.cancel')}
        </Button>
        <Button disabled={mutation.isPending}>{t('common.save')}</Button>
      </div>
    </form>
  )
}
