import { useMemo } from 'react'
import { useQuery } from '@tanstack/react-query'
import { useNavigate, useParams } from '@tanstack/react-router'
import { useTranslation } from 'react-i18next'
import {
  getWorkRecord,
  listTemplateVersionFields,
  listWorkRecordHistory,
  parseCustomData,
} from '@/api/work-records/records'
import { useDictionaryItemsMap } from '@/hooks/dictionaries/dictionary-query'
import {
  EmptyState,
  ErrorState,
  PageLoadingState,
} from '@/components/feedback/async-state'
import { RecordReadonlyView } from './record-readonly-view'

export function DetailRecordPage() {
  const navigate = useNavigate()
  const { recordId } = useParams({ strict: false }) as { recordId: string }
  const { t } = useTranslation()

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

  const fields = useMemo(() => fieldsQuery.data ?? [], [fieldsQuery.data])

  const dictCodes = useMemo(
    () =>
      Array.from(
        new Set(
          fields
            .map((field) => field.dictCode)
            .filter((code): code is string => Boolean(code))
        )
      ),

    [fields]
  )

  const dictionaries = useDictionaryItemsMap(dictCodes, true)

  if (recordQuery.isLoading || fieldsQuery.isLoading || dictionaries.loading) {
    return <PageLoadingState />
  }

  const blockingError =
    recordQuery.error ?? fieldsQuery.error ?? dictionaries.error

  if (blockingError && !recordQuery.data) {
    return (
      <main className='p-4 md:p-6'>
        <ErrorState
          error={blockingError}
          onRetry={() => {
            void Promise.all([
              recordQuery.refetch(),
              fieldsQuery.refetch(),
              dictionaries.refetch(),
            ])
          }}
        />
      </main>
    )
  }

  if (!recordQuery.data) {
    return (
      <main className='p-4 md:p-6'>
        <EmptyState title={t('workRecords.form.recordNotFound')} />
      </main>
    )
  }

  const history = historyQuery.data ?? []

  return (
    <RecordReadonlyView
      record={recordQuery.data}
      fields={fields}
      dictOptions={dictionaries.items}
      customData={parseCustomData(recordQuery.data)}
      canEdit={recordQuery.data.status !== 'archived'}
      history={history}
      historyLoading={historyQuery.isLoading}
      historyError={historyQuery.error as Error | null}
      onBack={() => navigate({ to: '/work-records' } as never)}
      onEdit={() =>
        navigate({
          to: '/work-records/$recordId/edit',
          params: { recordId: recordQuery.data!.id },
        })
      }
    />
  )
}
