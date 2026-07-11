import { useTranslation } from 'react-i18next'
import type { ListQueryState, RecordListMeta } from './types'

type Props = {
  meta?: RecordListMeta
  query: ListQueryState
  onChange: (patch: Partial<ListQueryState>) => void
}

export function ListToolbar({ meta, query, onChange }: Props) {
  const { t } = useTranslation()
  return (
    <div className='grid gap-3 rounded-lg border p-4'>
      <div className='grid gap-3 md:grid-cols-4'>
        <input
          className='rounded-md border bg-background px-3 py-2 text-sm'
          placeholder={t('workRecords.list.searchPlaceholder')}
          value={query.keyword}
          onChange={(event) => onChange({ keyword: event.target.value })}
        />

        <select
          className='rounded-md border bg-background px-3 py-2 text-sm'
          value={query.templateId}
          onChange={(event) => onChange({ templateId: event.target.value })}
        >
          <option value=''>{t('workRecords.list.allTemplates')}</option>
          {(meta?.templates ?? []).map((template) => (
            <option key={template.id} value={template.id}>
              {template.name}
            </option>
          ))}
        </select>

        <input
          className='rounded-md border bg-background px-3 py-2 text-sm'
          placeholder={t('workRecords.field.owner')}
          value={query.ownerId}
          onChange={(event) => onChange({ ownerId: event.target.value })}
        />

        <input
          className='rounded-md border bg-background px-3 py-2 text-sm'
          placeholder={t('workRecords.field.creator')}
          value={query.creatorId}
          onChange={(event) => onChange({ creatorId: event.target.value })}
        />
      </div>

      <div className='grid gap-3 md:grid-cols-4'>
        <select
          className='rounded-md border bg-background px-3 py-2 text-sm'
          value={query.statuses[0] ?? ''}
          onChange={(event) =>
            onChange({
              statuses: event.target.value ? [event.target.value] : [],
            })
          }
        >
          <option value=''>{t('workRecords.list.allStatus')}</option>
          <option value='draft'>{t('workRecords.list.status.draft')}</option>
          <option value='processing'>
            {t('workRecords.list.status.processing')}
          </option>
          <option value='done'>{t('workRecords.list.status.done')}</option>
          <option value='archived'>
            {t('workRecords.list.status.archived')}
          </option>
        </select>

        <input
          className='rounded-md border bg-background px-3 py-2 text-sm'
          type='datetime-local'
          value={query.recordTimeFrom}
          onChange={(event) => onChange({ recordTimeFrom: event.target.value })}
        />

        <input
          className='rounded-md border bg-background px-3 py-2 text-sm'
          type='datetime-local'
          value={query.recordTimeTo}
          onChange={(event) => onChange({ recordTimeTo: event.target.value })}
        />

        <button
          type='button'
          className='rounded-md border px-3 py-2 text-sm'
          onClick={() =>
            onChange({
              keyword: '',
              templateId: '',
              statuses: [],
              ownerId: '',
              creatorId: '',
              recordTimeFrom: '',
              recordTimeTo: '',
              dynamicFilters: [],
            })
          }
        >
          {t('workRecords.list.activeFiltersLabel')}
        </button>
      </div>
    </div>
  )
}
