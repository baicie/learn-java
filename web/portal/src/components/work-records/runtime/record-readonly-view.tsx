import { useTranslation } from 'react-i18next'
import { Button } from '@/components/ui/button'
import {
  DetailPageLayout,
  DetailSection,
} from '@/components/layout/detail-page-layout'
import { DynamicFieldControl } from './dynamic-field-control'
import { RecordHistoryCard } from './record-history-card'
import { statusLabel } from './schema'
import type {
  AuditEvent,
  RuntimeDictOptions,
  WorkRecord,
  WorkRecordField,
  WorkRecordTemplate,
} from './types'

type RecordReadonlyViewProps = {
  record: WorkRecord
  template?: WorkRecordTemplate
  fields: WorkRecordField[]
  dictOptions: RuntimeDictOptions
  customData: Record<string, unknown>
  canEdit?: boolean
  history?: AuditEvent[]
  historyLoading?: boolean
  historyError?: Error | null
  onBack: () => void
  onEdit: () => void
}

export function RecordReadonlyView({
  record,
  template,
  fields,
  dictOptions,
  customData,
  canEdit = true,
  history = [],
  historyLoading = false,
  historyError = null,
  onBack,
  onEdit,
}: RecordReadonlyViewProps) {
  const { t } = useTranslation()
  const sortedFields = fields.slice().sort((a, b) => a.sortOrder - b.sortOrder)

  return (
    <DetailPageLayout
      title={record.title}
      description={t('workRecords.detail.title')}
      meta={
        <div className='flex flex-wrap gap-2 text-xs text-muted-foreground'>
          <span>
            {t('workRecords.field.status')}：{statusLabel(record.status)}
          </span>
          <span>·</span>
          <span>
            {t('workRecords.field.template')}：
            {template?.name ?? record.templateId}
          </span>
        </div>
      }
      actions={
        <>
          {canEdit ? (
            <Button type='button' onClick={onEdit}>
              {t('common.edit')}
            </Button>
          ) : null}
          <Button type='button' variant='outline' onClick={onBack}>
            {t('common.back')}
          </Button>
        </>
      }
    >
      <DetailSection
        title={t('workRecords.field.template')}
        description={t('workRecords.designer.emptyHint')}
      >
        <div className='grid gap-3 text-sm md:grid-cols-2'>
          <Info
            label={t('workRecords.field.template')}
            value={template?.name ?? record.templateId}
          />
          <Info
            label={t('workRecords.field.template')}
            value={record.templateVersionId}
          />
          <Info
            label={t('workRecords.field.status')}
            value={statusLabel(record.status)}
          />
          <Info
            label={t('workRecords.field.owner')}
            value={record.ownerId ?? '-'}
          />
          <Info
            label={t('workRecords.field.creator')}
            value={record.creatorId}
          />
          <Info
            label={t('workRecords.field.recordTime')}
            value={formatDateTime(record.recordTime)}
          />
        </div>
      </DetailSection>

      <DetailSection
        title={t('workRecords.form.recordContent')}
        description={t('workRecords.detail.legacyFields')}
      >
        {sortedFields.length === 0 ? (
          <div className='text-sm text-muted-foreground'>
            {t('common.empty')}
          </div>
        ) : (
          <div className='grid gap-4'>
            {sortedFields.map((field) => (
              <div key={field.id} className='grid gap-1 text-sm'>
                <span className='font-medium'>
                  {field.fieldName}
                  {!field.enabled ? (
                    <span className='ml-2 rounded bg-muted px-1.5 py-0.5 text-xs text-muted-foreground'>
                      {t('workRecords.detail.disabledFieldBadge')}
                    </span>
                  ) : null}
                </span>
                <DynamicFieldControl
                  field={field}
                  value={customData[field.fieldCode]}
                  dictOptions={dictOptions}
                  readonly
                  onChange={() => undefined}
                />
              </div>
            ))}
          </div>
        )}
      </DetailSection>

      <RecordHistoryCard
        events={history}
        loading={historyLoading}
        error={historyError}
      />
    </DetailPageLayout>
  )
}

function Info({ label, value }: { label: string; value: string }) {
  return (
    <div className='rounded-md border p-3'>
      <div className='text-xs text-muted-foreground'>{label}</div>
      <div className='mt-1 break-all'>{value}</div>
    </div>
  )
}

function formatDateTime(value: string) {
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return value
  return date.toLocaleString()
}
