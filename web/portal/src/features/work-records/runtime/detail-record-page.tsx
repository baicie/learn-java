import { useEffect, useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { useNavigate, useParams } from '@tanstack/react-router'
import {
  getWorkRecord,
  listTemplateVersionFields,
  listWorkRecordHistory,
  loadRuntimeDictOptions,
  parseCustomData,
} from './api'
import { RecordReadonlyView } from './record-readonly-view'
import type { AuditEvent, RuntimeDictOptions } from './types'

export function DetailRecordPage() {
  const navigate = useNavigate()
  const { recordId } = useParams({ strict: false }) as { recordId: string }
  const [dictOptions, setDictOptions] = useState<RuntimeDictOptions>({})

  const recordQuery = useQuery({
    queryKey: ['work-record-runtime-record', recordId],
    queryFn: () => getWorkRecord(recordId),
  })

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

  const historyQuery = useQuery({
    queryKey: ['work-record-history', recordId],
    queryFn: () => listWorkRecordHistory(recordId),
    enabled: Boolean(recordId),
  })

  useEffect(() => {
    if (!fieldsQuery.data) return
    loadRuntimeDictOptions(fieldsQuery.data).then(setDictOptions)
  }, [fieldsQuery.data])

  if (recordQuery.isLoading || fieldsQuery.isLoading) {
    return <main className='p-6 text-sm text-muted-foreground'>加载中...</main>
  }

  if (!recordQuery.data) {
    return <main className='p-6 text-sm text-red-600'>记录不存在</main>
  }

  const history: AuditEvent[] = historyQuery.data ?? []

  return (
    <RecordReadonlyView
      record={recordQuery.data}
      fields={fieldsQuery.data ?? []}
      dictOptions={dictOptions}
      customData={parseCustomData(recordQuery.data)}
      canEdit={recordQuery.data.status !== 'archived'}
      history={history}
      historyLoading={historyQuery.isLoading}
      historyError={historyQuery.error as Error | null}
      onBack={() => globalThis.history.back()}
      onEdit={() =>
        navigate({
          to: '/work-records/$recordId/edit',
          params: { recordId: recordQuery.data!.id },
        })
      }
    />
  )
}
