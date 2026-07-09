import { useEffect, useState } from 'react'
import { useMutation, useQuery } from '@tanstack/react-query'
import { useNavigate, useParams } from '@tanstack/react-router'
import {
  getWorkRecord,
  listTemplateVersionFields,
  loadRuntimeDictOptions,
  updateWorkRecord,
} from './api'
import { RecordRuntimeForm } from './record-runtime-form'
import { buildInitialFormValue } from './schema'
import type { RuntimeDictOptions, WorkRecordRuntimeFormValue } from './types'

export function EditRecordPage() {
  const navigate = useNavigate()
  const { recordId } = useParams({ strict: false }) as { recordId: string }

  const recordQuery = useQuery({
    queryKey: ['work-record-runtime-record', recordId],
    queryFn: () => getWorkRecord(recordId),
  })

  const [value, setValue] = useState<WorkRecordRuntimeFormValue>(
    buildInitialFormValue({})
  )
  const [dictOptions, setDictOptions] = useState<RuntimeDictOptions>({})

  const fieldsQuery = useQuery({
    queryKey: ['work-record-runtime-fields', recordQuery.data],
    queryFn: () => {
      const record = recordQuery.data
      if (!record) throw new Error('record not loaded')
      return listTemplateVersionFields(
        record.templateId,
        record.templateVersionId
      )
    },
    enabled: Boolean(
      recordQuery.data?.templateId && recordQuery.data?.templateVersionId
    ),
  })

  useEffect(() => {
    if (!recordQuery.data) return
    const record = recordQuery.data
    queueMicrotask(() => setValue(buildInitialFormValue({ record })))
  }, [recordQuery.data])

  useEffect(() => {
    if (!fieldsQuery.data) return
    loadRuntimeDictOptions(fieldsQuery.data).then(setDictOptions)
  }, [fieldsQuery.data])

  const updateMutation = useMutation({
    mutationFn: (next: WorkRecordRuntimeFormValue) =>
      updateWorkRecord(recordId, next),
    onSuccess: async (record) => {
      await navigate({
        to: '/work-records/$recordId',
        params: { recordId: record.id },
      })
    },
  })

  const submitWithStatus = (status: 'draft' | 'done') => {
    updateMutation.mutate({
      ...value,
      status,
    })
  }

  if (recordQuery.isLoading || fieldsQuery.isLoading) {
    return <main className='p-6 text-sm text-muted-foreground'>加载中...</main>
  }

  if (!recordQuery.data) {
    return <main className='p-6 text-sm text-red-600'>记录不存在</main>
  }

  return (
    <RecordRuntimeForm
      mode='edit'
      templates={[]}
      fields={fieldsQuery.data ?? []}
      dictOptions={dictOptions}
      value={value}
      submitting={updateMutation.isPending}
      onChange={setValue}
      onSaveDraft={() => submitWithStatus('draft')}
      onSubmitDone={() => submitWithStatus('done')}
      onCancel={() => history.back()}
    />
  )
}
