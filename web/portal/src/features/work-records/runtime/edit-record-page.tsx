import { useEffect, useMemo, useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useNavigate, useParams } from '@tanstack/react-router'
import { useTranslation } from 'react-i18next'
import { apiFieldErrors } from '@/lib/api-error'
import { useDirtySnapshot } from '@/hooks/use-dirty-snapshot'
import { useUnsavedChangesGuard } from '@/hooks/use-unsaved-changes-guard'
import { notify } from '@/components/feedback/app-toaster'
import {
  EmptyState,
  ErrorState,
  PageLoadingState,
} from '@/components/feedback/async-state'
import { focusFirstInvalidField } from '@/components/form/form-field-shell'
import { useDictionaryItemsMap } from '@/features/dictionaries/dictionary-query'
import {
  getWorkRecord,
  listTemplateVersionFields,
  updateWorkRecord,
} from './api'
import {
  validateRecordForm,
  type RecordFormErrors,
} from './record-form-validation'
import { RecordRuntimeForm } from './record-runtime-form'
import { buildInitialFormValue, sanitizeCustomDataForSubmit } from './schema'
import type {
  WorkRecordRuntimeFormValue,
  WorkRecordStatus,
  WorkRecordTemplate,
} from './types'

export function EditRecordPage() {
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const { recordId } = useParams({ strict: false }) as { recordId: string }
  const { t } = useTranslation()

  const [hydratedRecordId, setHydratedRecordId] = useState('')
  const [initialValue, setInitialValue] = useState<WorkRecordRuntimeFormValue>(
    buildInitialFormValue({})
  )
  const [value, setValue] = useState<WorkRecordRuntimeFormValue>(initialValue)
  const [errors, setErrors] = useState<RecordFormErrors>({})

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

  useEffect(() => {
    if (!recordQuery.data || hydratedRecordId === recordQuery.data.id) {
      return
    }

    const record = recordQuery.data
    queueMicrotask(() => {
      const next = buildInitialFormValue({ record })
      setHydratedRecordId(record.id)
      setInitialValue(next)
      setValue(next)
    })
  }, [hydratedRecordId, recordQuery.data])

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
    hydratedRecordId,
    initialValue,
    value
  )

  const updateMutation = useMutation({
    mutationFn: (next: WorkRecordRuntimeFormValue) =>
      updateWorkRecord(recordId, next),

    onSuccess: async (record, submitted) => {
      markSaved(submitted)

      notify.success(t('workRecords.form.saveSuccess'))

      await queryClient.invalidateQueries({
        queryKey: ['work-record-list'],
      })
      await queryClient.invalidateQueries({
        queryKey: ['work-record-runtime-record', recordId],
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

  useUnsavedChangesGuard(dirty && !updateMutation.isPending)

  const changeValue = (next: WorkRecordRuntimeFormValue) => {
    setValue(next)
    setErrors({})
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

    updateMutation.mutate(candidate)
  }

  if (recordQuery.isLoading || fieldsQuery.isLoading || dictionaries.loading) {
    return <PageLoadingState />
  }

  if (!recordQuery.data) {
    return (
      <main className='p-4 md:p-6'>
        <EmptyState
          title={t('workRecords.form.recordNotFound')}
          description={t('workRecords.form.recordNotFound')}
        />
      </main>
    )
  }

  if (recordQuery.error || fieldsQuery.error || dictionaries.error) {
    return (
      <main className='p-4 md:p-6'>
        <ErrorState
          error={recordQuery.error ?? fieldsQuery.error ?? dictionaries.error}
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

  const syntheticTemplate: WorkRecordTemplate = {
    id: recordQuery.data.templateId,
    tenantId: recordQuery.data.tenantId,
    code: recordQuery.data.templateId,
    name: recordQuery.data.templateId,
    description: null,
    status: 'published',
    enabled: true,
    currentVersionId: recordQuery.data.templateVersionId,
    draftSchemaJson: '{}',
    draftDesignerJson: '{}',
    createdBy: recordQuery.data.creatorId,
    createdAt: recordQuery.data.createdAt,
    updatedAt: recordQuery.data.updatedAt,
    deletedAt: null,
  }

  return (
    <RecordRuntimeForm
      mode='edit'
      templates={[syntheticTemplate]}
      fields={fields}
      dictOptions={dictionaries.items}
      value={value}
      errors={errors}
      dirty={dirty}
      submitting={updateMutation.isPending}
      onChange={changeValue}
      onSaveDraft={() => submitWithStatus('draft')}
      onSubmitDone={() => submitWithStatus('done')}
      onCancel={() =>
        navigate({
          to: '/work-records/$recordId',
          params: { recordId: recordQuery.data!.id },
        } as never)
      }
    />
  )
}
