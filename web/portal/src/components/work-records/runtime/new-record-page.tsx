import { useEffect, useMemo, useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useNavigate } from '@tanstack/react-router'
import { useTranslation } from 'react-i18next'
import { fetchRecordUserOptions } from '@/api/work-records/list'
import {
  createWorkRecord,
  listPublishedTemplates,
  listTemplateVersionFields,
} from '@/api/work-records/records'
import { apiFieldErrors } from '@/lib/api-error'
import { useDictionaryItemsMap } from '@/hooks/dictionaries/dictionary-query'
import { useDirtySnapshot } from '@/hooks/use-dirty-snapshot'
import { useUnsavedChangesGuard } from '@/hooks/use-unsaved-changes-guard'
import { notify } from '@/components/feedback/app-toaster'
import {
  EmptyState,
  ErrorState,
  PageLoadingState,
} from '@/components/feedback/async-state'
import { focusFirstInvalidField } from '@/components/form/form-field-shell'
import {
  validateRecordForm,
  type RecordFormErrors,
} from './record-form-validation'
import { RecordRuntimeForm } from './record-runtime-form'
import { buildInitialFormValue, sanitizeCustomDataForSubmit } from './schema'
import type { WorkRecordRuntimeFormValue, WorkRecordStatus } from './types'

export function NewRecordPage() {
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const { t } = useTranslation()

  const empty = buildInitialFormValue({})

  const [initialized, setInitialized] = useState(false)
  const [initialValue, setInitialValue] = useState(empty)
  const [value, setValue] = useState(empty)
  const [errors, setErrors] = useState<RecordFormErrors>({})

  const templatesQuery = useQuery({
    queryKey: ['work-record-runtime-templates'],
    queryFn: listPublishedTemplates,
  })

  const userOptionsQuery = useQuery({
    queryKey: ['work-record-user-options'],
    queryFn: fetchRecordUserOptions,
  })

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
    if (initialized || !templatesQuery.data?.length) {
      return
    }

    const next = buildInitialFormValue({
      templates: templatesQuery.data,
    })

    queueMicrotask(() => {
      setInitialized(true)
      setInitialValue(next)
      setValue(next)
    })
  }, [initialized, templatesQuery.data])

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

  const { dirty, markSaved } = useDirtySnapshot(
    'new-record',
    initialValue,
    value
  )

  const createMutation = useMutation({
    mutationFn: (next: WorkRecordRuntimeFormValue) => createWorkRecord(next),

    onSuccess: async (record, submitted) => {
      markSaved(submitted)

      notify.success(t('workRecords.form.saveSuccess'))

      await queryClient.invalidateQueries({
        queryKey: ['work-record-list'],
      })

      await navigate({
        to: '/work-records/$recordId',
        params: { recordId: record.id },
      })
    },

    onError: (error) => {
      setErrors(apiFieldErrors(error))

      notify.error(error, t('workRecords.form.saveFailed'))
    },
  })

  useUnsavedChangesGuard(dirty && !createMutation.isPending)

  const changeValue = (next: WorkRecordRuntimeFormValue) => {
    setValue(next)
    setErrors({})
  }

  const changeTemplate = (templateId: string) => {
    const template = templatesQuery.data?.find((item) => item.id === templateId)

    changeValue({
      ...value,
      templateId,
      templateVersionId: template?.currentVersionId ?? '',
      customData: {},
    })
  }

  const submitWithStatus = (status: WorkRecordStatus) => {
    const candidate = sanitizeCustomDataForSubmit(
      {
        ...value,
        status,
      },
      fields
    )

    const nextErrors = validateRecordForm(candidate, fields, status)

    setErrors(nextErrors)

    if (Object.keys(nextErrors).length) {
      notify.warning(t('workRecords.form.validationFailed'))

      requestAnimationFrame(focusFirstInvalidField)

      return
    }

    createMutation.mutate(candidate)
  }

  if (
    templatesQuery.isLoading ||
    userOptionsQuery.isLoading ||
    fieldsQuery.isLoading ||
    dictionaries.loading
  ) {
    return <PageLoadingState />
  }

  const pageError =
    templatesQuery.error ??
    fieldsQuery.error ??
    dictionaries.error ??
    userOptionsQuery.error

  if (pageError) {
    return (
      <main className='p-4 md:p-6'>
        <ErrorState
          error={pageError}
          onRetry={() => {
            void Promise.all([
              templatesQuery.refetch(),
              userOptionsQuery.refetch(),
              fieldsQuery.refetch(),
              dictionaries.refetch(),
            ])
          }}
        />
      </main>
    )
  }

  if (!templatesQuery.data?.length) {
    return (
      <main className='p-4 md:p-6'>
        <EmptyState
          title={t('workRecords.form.notPublishedTitle')}
          description={t('workRecords.form.notPublishedHint')}
        />
      </main>
    )
  }

  return (
    <RecordRuntimeForm
      mode='create'
      templates={templatesQuery.data}
      fields={fields}
      dictOptions={dictionaries.items}
      userOptions={userOptionsQuery.data ?? []}
      value={value}
      errors={errors}
      dirty={dirty}
      submitting={createMutation.isPending}
      onTemplateChange={changeTemplate}
      onChange={changeValue}
      onSaveDraft={() => submitWithStatus('draft')}
      onSubmitDone={() => submitWithStatus('done')}
      onCancel={() => navigate({ to: '/work-records' } as never)}
    />
  )
}
