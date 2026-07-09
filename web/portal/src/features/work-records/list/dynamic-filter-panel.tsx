import type { DynamicFilter, RecordListColumn } from './types'

type Props = {
  fields: RecordListColumn[]
  filters: DynamicFilter[]
  onChange: (filters: DynamicFilter[]) => void
}

export function DynamicFilterPanel({ fields, filters, onChange }: Props) {
  const update = (fieldCode: string, value: string) => {
    const next = filters.filter((item) => item.fieldCode !== fieldCode)
    if (value.trim()) {
      next.push({ fieldCode, operator: 'eq', value })
    }
    onChange(next)
  }

  return (
    <details className='rounded-lg border p-4'>
      <summary className='cursor-pointer text-sm font-medium'>动态字段筛选</summary>
      <div className='mt-3 grid gap-3 md:grid-cols-3'>
        {fields.length === 0 ? (
          <div className='text-sm text-muted-foreground'>暂无可筛选动态字段</div>
        ) : null}

        {fields.map((field) => (
          <label key={field.key} className='grid gap-1 text-sm'>
            <span>{field.title}</span>
            <input
              className='rounded-md border bg-background px-3 py-2'
              value={String(filters.find((item) => item.fieldCode === field.fieldCode)?.value ?? '')}
              onChange={(event) => update(field.fieldCode!, event.target.value)}
            />
          </label>
        ))}
      </div>
    </details>
  )
}