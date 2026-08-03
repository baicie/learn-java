import type { ColumnFiltersState } from '@tanstack/react-table'
import { alertFilterValues } from '@/pages/alerts/alert-filter'
import { SearchIcon } from 'lucide-react'
import { Checkbox } from '@/components/ui/checkbox'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'

const statusOptions = [
  { value: 'open', label: '进行中' },
  { value: 'resolved', label: '已恢复' },
]

const severityOptions = [
  { value: 'disaster', label: '灾难' },
  { value: 'critical', label: '严重' },
  { value: 'high', label: '高' },
  { value: 'warning', label: '警告' },
  { value: 'medium', label: '中' },
  { value: 'low', label: '低' },
  { value: 'info', label: '信息' },
]

function toggleFilterValue(
  filters: ColumnFiltersState,
  id: string,
  value: string,
  checked: boolean
) {
  const values = alertFilterValues(filters, id)
  const nextValues = checked
    ? [...new Set([...values, value])]
    : values.filter((item) => item !== value)
  const withoutCurrent = filters.filter((filter) => filter.id !== id)

  return nextValues.length
    ? [...withoutCurrent, { id, value: nextValues }]
    : withoutCurrent
}

export function AlertFilterBar({
  keyword,
  onKeywordChange,
  columnFilters,
  onColumnFiltersChange,
}: {
  keyword: string
  onKeywordChange: (value: string) => void
  columnFilters: ColumnFiltersState
  onColumnFiltersChange: (filters: ColumnFiltersState) => void
}) {
  const statusValues = alertFilterValues(columnFilters, 'status')
  const severityValues = alertFilterValues(columnFilters, 'severity')
  const updateFilter = (id: string, value: string, checked: boolean) =>
    onColumnFiltersChange(toggleFilterValue(columnFilters, id, value, checked))

  return (
    <div className='grid gap-3 rounded-lg border bg-card p-3'>
      <div className='relative'>
        <SearchIcon className='absolute top-2.5 left-3 size-4 text-muted-foreground' />
        <Input
          aria-label='搜索告警'
          className='pl-9'
          value={keyword}
          placeholder='搜索告警标题、来源、状态或严重度'
          onChange={(event) => onKeywordChange(event.target.value)}
        />
      </div>
      <FilterGroup
        label='状态'
        options={statusOptions}
        selected={statusValues}
        onChange={(value, checked) => updateFilter('status', value, checked)}
      />
      <FilterGroup
        label='严重度'
        options={severityOptions}
        selected={severityValues}
        onChange={(value, checked) => updateFilter('severity', value, checked)}
      />
    </div>
  )
}

function FilterGroup({
  label,
  options,
  selected,
  onChange,
}: {
  label: string
  options: Array<{ value: string; label: string }>
  selected: string[]
  onChange: (value: string, checked: boolean) => void
}) {
  return (
    <fieldset className='flex flex-wrap items-center gap-x-4 gap-y-2'>
      <legend className='mr-1 text-sm font-medium'>{label}</legend>
      {options.map((option) => (
        <div key={option.value} className='flex items-center gap-2'>
          <Checkbox
            id={`${label}-${option.value}`}
            checked={selected.includes(option.value)}
            onCheckedChange={(checked) =>
              onChange(option.value, checked === true)
            }
          />
          <Label htmlFor={`${label}-${option.value}`} className='font-normal'>
            {option.label}
          </Label>
        </div>
      ))}
    </fieldset>
  )
}
