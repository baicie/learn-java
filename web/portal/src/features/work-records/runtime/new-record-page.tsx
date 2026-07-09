import { useEffect, useState } from 'react'
import { useMutation, useQuery } from '@tanstack/react-query'
import { useNavigate } from '@tanstack/react-router'
import {
  createWorkRecord,
  listPublishedTemplates,
  listTemplateVersionFields,
  loadRuntimeDictOptions,
} from './api'
import { RecordRuntimeForm } from './record-runtime-form'
import { buildInitialFormValue, sanitizeCustomDataForSubmit } from './schema'
import type {
  RuntimeDictOptions,
  WorkRecordField,
  WorkRecordRuntimeFormValue,
} from './types'

export function NewRecordPage() {
  const navigate = useNavigate()
  const templatesQuery = useQuery({
    queryKey: ['work-record-runtime-templates'],
    queryFn: listPublishedTemplates,
  })

  const [initialized, setInitialized] = useState(false)
  const [value, setValue] = useState<WorkRecordRuntimeFormValue>(
    buildInitialFormValue({})
  )
  const [dictOptions, setDictOptions] = useState<RuntimeDictOptions>({})

  const fieldsQuery = useQuery({
    queryKey: [
      'work-record-runtime-fields',
      value.templateId,
      value.templateVersionId,
    ],
    queryFn: () =>
      listTemplateVersionFields(value.templateId, value.templateVersionId),
    enabled: Boolean(value.templateId && value.templateVersionId),
  })

  useEffect(() => {
    if (initialized || !templatesQuery.data?.length) return
    const templates = templatesQuery.data
    queueMicrotask(() => {
      setInitialized(true)
      setValue(buildInitialFormValue({ templates }))
    })
  }, [initialized, templatesQuery.data])

  useEffect(() => {
    if (!fieldsQuery.data) return
    loadRuntimeDictOptions(fieldsQuery.data).then(setDictOptions)
  }, [fieldsQuery.data])

  const createMutation = useMutation({
    mutationFn: (next: WorkRecordRuntimeFormValue) => createWorkRecord(next),
    onSuccess: async (record) => {
      await navigate({
        to: '/work-records/$recordId',
        params: { recordId: record.id },
      })
    },
  })

  const changeTemplate = (templateId: string) => {
    const template = templatesQuery.data?.find(
      (item) => item.id === templateId
    )
    setValue({
      ...value,
      templateId,
      templateVersionId: template?.currentVersionId ?? '',
      customData: {},
    })
  }

  const submitWithStatus = (status: 'draft' | 'done') => {
    createMutation.mutate(
      sanitizeCustomDataForSubmit(
        {
          ...value,
          status,
        },
        fieldsQuery.data ?? []
      )
    )
  }

  if (templatesQuery.isLoading || fieldsQuery.isLoading) {
    return <main className='p-6 text-sm text-muted-foreground'>加载中...</main>
  }

  if (!templatesQuery.data?.length) {
    return (
      <main className='p-6 text-sm text-muted-foreground'>
        暂无已发布模板，请先发布工作记录模板。
      </main>
    )
  }

  return (
    <RecordRuntimeForm
      mode='create'
      templates={templatesQuery.data ?? []}
      fields={(fieldsQuery.data ?? []) as WorkRecordField[]}
      dictOptions={dictOptions}
      value={value}
      submitting={createMutation.isPending}
      onTemplateChange={changeTemplate}
      onChange={setValue}
      onSaveDraft={() => submitWithStatus('draft')}
      onSubmitDone={() => submitWithStatus('done')}
      onCancel={() => history.back()}
    />
  )
}