import { useTranslation } from 'react-i18next'
import { Input } from '@/components/ui/input'
import type { DynamicFilter, RecordListColumn } from './types'

type Props = {
  fields: RecordListColumn[]
  filters: DynamicFilter[]
  onChange: (filters: DynamicFilter[]) => void
}

export function DynamicFilterPanel({ fields, filters, onChange }: Props) {
  const { t } = useTranslation()

  const update = (fieldCode: string, value: string) => {
    const next = filters.filter((item) => item.fieldCode !== fieldCode)
    if (value.trim()) {
      next.push({ fieldCode, operator: 'eq', value })
    }
    onChange(next)
  }

  return (
    <details className='rounded-lg border p-4'>
      <summary className='cursor-pointer text-sm font-medium'>
        {t('workRecords.list.dynamicFilters')}
      </summary>
      <div className='mt-3 grid gap-3 md:grid-cols-3'>
        {fields.length === 0 ? (
          <div className='text-sm text-muted-foreground'>
            {t('workRecords.list.dynamicFilterEmpty')}
          </div>
        ) : null}

        {fields.map((field) => (
          <label key={field.key} className='grid gap-1 text-sm'>
            <span>{field.title}</span>
            <Input
              value={String(
                filters.find((item) => item.fieldCode === field.fieldCode)
                  ?.value ?? ''
              )}
              onChange={(event) => update(field.fieldCode!, event.target.value)}
            />
          </label>
        ))}
      </div>
    </details>
  )
}
