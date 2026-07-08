import { X } from 'lucide-react'
import { useTranslation } from 'react-i18next'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select'
import type {
  DynamicFilter,
  RecordListFilterField,
  DynamicFilterOperator,
} from '../data/schema'

type DynamicFilterRowProps = {
  filter: DynamicFilter
  filterFields: RecordListFilterField[]
  onChange: (filter: DynamicFilter) => void
  onRemove: () => void
}

export function DynamicFilterRow({
  filter,
  filterFields,
  onChange,
  onRemove,
}: DynamicFilterRowProps) {
  const { t } = useTranslation()

  const currentField = filterFields.find(
    (f) => f.fieldCode === filter.fieldCode
  )
  const operators = currentField?.operators ?? ['eq']
  const isMultiValue = filter.operator === 'in' || filter.operator === 'between'

  return (
    <div className='flex flex-col gap-2 rounded-lg border p-3'>
      <div className='flex items-center gap-2'>
        {/* Field selector */}
        <Select
          value={filter.fieldCode}
          onValueChange={(fieldCode) => {
            const newField = filterFields.find((f) => f.fieldCode === fieldCode)
            const newOperators = newField?.operators ?? ['eq']
            onChange({
              ...filter,
              fieldCode,
              operator: newOperators[0] as DynamicFilterOperator,
              value: null,
              values: null,
            })
          }}
        >
          <SelectTrigger className='w-[140px]'>
            <SelectValue />
          </SelectTrigger>
          <SelectContent>
            {filterFields.map((field) => (
              <SelectItem key={field.fieldCode} value={field.fieldCode}>
                {field.label}
              </SelectItem>
            ))}
          </SelectContent>
        </Select>

        {/* Operator selector */}
        <Select
          value={filter.operator}
          onValueChange={(operator) => {
            onChange({
              ...filter,
              operator: operator as DynamicFilterOperator,
              value: null,
              values: null,
            })
          }}
        >
          <SelectTrigger className='w-[100px]'>
            <SelectValue />
          </SelectTrigger>
          <SelectContent>
            {operators.map((op) => (
              <SelectItem key={op} value={op}>
                {t(`workRecords.filters.operator.${op}`)}
              </SelectItem>
            ))}
          </SelectContent>
        </Select>

        {/* Value input */}
        <div className='flex-1'>
          {isMultiValue ? (
            <Input
              placeholder={t('workRecords.filters.multiValuePlaceholder')}
              value={filter.values ? filter.values.join(', ') : ''}
              onChange={(e) => {
                const values = e.target.value
                  .split(',')
                  .map((v) => v.trim())
                  .filter(Boolean)
                onChange({
                  ...filter,
                  values: values.length > 0 ? values : null,
                })
              }}
            />
          ) : filter.operator === 'exists' ? (
            <span className='text-sm text-muted-foreground'>
              {t('workRecords.filters.existsHint')}
            </span>
          ) : (
            <Input
              placeholder={t('workRecords.filters.valuePlaceholder')}
              value={(filter.value as string) ?? ''}
              onChange={(e) => {
                onChange({
                  ...filter,
                  value: e.target.value || null,
                })
              }}
            />
          )}
        </div>

        {/* Remove button */}
        <Button
          variant='ghost'
          size='icon'
          onClick={onRemove}
          className='shrink-0'
        >
          <X className='h-4 w-4' />
        </Button>
      </div>
    </div>
  )
}
