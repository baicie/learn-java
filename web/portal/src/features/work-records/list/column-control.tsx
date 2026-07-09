import type { RecordListColumn } from './types'

type Props = {
  columns: RecordListColumn[]
  visible: string[]
  onChange: (visible: string[]) => void
}

export function ColumnControl({ columns, visible, onChange }: Props) {
  const effectiveVisible = visible.length
    ? visible
    : columns.filter((column) => column.visibleByDefault).map((column) => column.key)

  const toggle = (key: string) => {
    if (effectiveVisible.includes(key)) {
      onChange(effectiveVisible.filter((item) => item !== key))
    } else {
      onChange([...effectiveVisible, key])
    }
  }

  return (
    <details className='rounded-lg border p-4'>
      <summary className='cursor-pointer text-sm font-medium'>列显示控制</summary>
      <div className='mt-3 grid gap-2 md:grid-cols-4'>
        {columns.map((column) => (
          <label key={column.key} className='flex items-center gap-2 text-sm'>
            <input
              type='checkbox'
              checked={effectiveVisible.includes(column.key)}
              onChange={() => toggle(column.key)}
            />
            {column.title}
          </label>
        ))}
      </div>
    </details>
  )
}