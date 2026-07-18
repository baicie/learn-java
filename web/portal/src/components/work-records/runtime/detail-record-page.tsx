import { useMemo } from 'react'
import { useQuery } from '@tanstack/react-query'
import { useNavigate, useParams } from '@tanstack/react-router'
import { useTranslation } from 'react-i18next'
import { fetchRecordUserNames } from '@/api/work-records/list'
import {
  getWorkRecord,
  listTemplateVersionFields,
  listWorkRecordHistory,
  parseCustomData,
} from '@/api/work-records/records'
import { listTemplates } from '@/api/work-records/templates'
import { useDictionaryItemsMap } from '@/hooks/dictionaries/dictionary-query'
import {
  EmptyState,
  ErrorState,
  PageLoadingState,
} from '@/components/feedback/async-state'
import { RecordExtensionPanel } from './record-extension-panel'
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

  const templatesQuery = useQuery({
    queryKey: ['work-record-templates'],
    queryFn: listTemplates,
  })

  const userIds = recordQuery.data
    ? [recordQuery.data.creatorId, recordQuery.data.ownerId].filter(
        (value): value is string => Boolean(value)
      )
    : []
  const userNamesQuery = useQuery({
    queryKey: ['work-record-user-names', userIds],
    queryFn: () => fetchRecordUserNames(userIds),
    enabled: userIds.length > 0,
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

  if (
    recordQuery.isLoading ||
    fieldsQuery.isLoading ||
    templatesQuery.isLoading ||
    userNamesQuery.isLoading ||
    dictionaries.loading
  ) {
    return <PageLoadingState />
  }

  const blockingError =
    recordQuery.error ??
    fieldsQuery.error ??
    templatesQuery.error ??
    userNamesQuery.error ??
    dictionaries.error

  if (blockingError && !recordQuery.data) {
    return (
      <main className='p-4 md:p-6'>
        <ErrorState
          error={blockingError}
          onRetry={() => {
            void Promise.all([
              recordQuery.refetch(),
              fieldsQuery.refetch(),
              templatesQuery.refetch(),
              userNamesQuery.refetch(),
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
    <>
      <RecordReadonlyView
        record={recordQuery.data}
        template={templatesQuery.data?.find(
          (template) => template.id === recordQuery.data?.templateId
        )}
        userNames={userNamesQuery.data ?? {}}
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
      <RecordExtensionPanel recordId={recordId} />
    </>
  )
}
