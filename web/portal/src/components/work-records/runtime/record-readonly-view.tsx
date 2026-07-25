import { useTranslation } from 'react-i18next'
import { formatDateTime } from '@/lib/date-format'
import { Button } from '@/components/ui/button'
import {
  DetailPageLayout,
  DetailSection,
} from '@/components/layout/detail-page-layout'
import { DynamicFieldControl } from './dynamic-field-control'
import { statusLabel } from './schema'
import type {
  RuntimeDictOptions,
  WorkRecord,
  WorkRecordField,
  WorkRecordTemplate,
} from './types'

type RecordReadonlyViewProps = {
  record: WorkRecord
  template?: WorkRecordTemplate
  userNames?: Record<string, string>
  fields: WorkRecordField[]
  dictOptions: RuntimeDictOptions
  customData: Record<string, unknown>
  canEdit?: boolean
  onBack: () => void
  onEdit: () => void
}

export function RecordReadonlyView({
  record,
  template,
  userNames = {},
  fields,
  dictOptions,
  customData,
  canEdit = true,
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
      <DetailSection title={t('workRecords.detail.basicInfo')}>
        <div className='grid gap-3 text-sm md:grid-cols-2'>
          <Info
            label={t('workRecords.field.template')}
            value={template?.name ?? record.templateId}
          />
          <Info
            label={t('workRecords.field.status')}
            value={statusLabel(record.status)}
          />
          <Info
            label={t('workRecords.field.owner')}
            value={
              record.ownerId
                ? (userNames[record.ownerId] ?? record.ownerId)
                : '-'
            }
          />
          <Info
            label={t('workRecords.field.creator')}
            value={userNames[record.creatorId] ?? record.creatorId}
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
