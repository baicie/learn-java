import { useState } from 'react'
import { SlidersHorizontal } from 'lucide-react'
import { useTranslation } from 'react-i18next'
import { Button } from '@/components/ui/button'
import {
  Sheet,
  SheetContent,
  SheetDescription,
  SheetHeader,
  SheetTitle,
  SheetTrigger,
} from '@/components/ui/sheet'
import type { RecordListFilterField, DynamicFilter } from '../data/schema'
import { DynamicFilterRow } from './dynamic-filter-row'

type DynamicFilterSheetProps = {
  filterFields: RecordListFilterField[]
  filters: DynamicFilter[]
  onFiltersChange: (filters: DynamicFilter[]) => void
}

export function DynamicFilterSheet({
  filterFields,
  filters,
  onFiltersChange,
}: DynamicFilterSheetProps) {
  const { t } = useTranslation()
  const [open, setOpen] = useState(false)

  const handleFilterChange = (index: number, filter: DynamicFilter | null) => {
    const newFilters = [...filters]
    if (filter === null) {
      newFilters.splice(index, 1)
    } else {
      newFilters[index] = filter
    }
    onFiltersChange(newFilters)
  }

  const handleAddFilter = () => {
    if (filterFields.length === 0) return
    const firstField = filterFields[0]
    const newFilter: DynamicFilter = {
      fieldCode: firstField.fieldCode,
      operator: firstField.operators[0] ?? 'eq',
      value: null,
      values: null,
    }
    onFiltersChange([...filters, newFilter])
  }

  const filterCount = filters.length

  return (
    <Sheet open={open} onOpenChange={setOpen}>
      <SheetTrigger asChild>
        <Button variant='outline' size='sm' className='gap-2'>
          <SlidersHorizontal className='h-4 w-4' />
          {t('workRecords.list.dynamicFilters')}
          {filterCount > 0 && (
            <span className='ml-1 rounded-full bg-primary px-1.5 py-0.5 text-xs text-primary-foreground'>
              {filterCount}
            </span>
          )}
        </Button>
      </SheetTrigger>
      <SheetContent className='w-[400px] sm:max-w-[400px]'>
        <SheetHeader>
          <SheetTitle>{t('workRecords.list.dynamicFilters')}</SheetTitle>
          <SheetDescription>
            {t('workRecords.list.dynamicFiltersDescription')}
          </SheetDescription>
        </SheetHeader>
        <div className='mt-6 flex flex-col gap-4'>
          {filters.map((filter, index) => (
            <DynamicFilterRow
              key={`${filter.fieldCode}-${index}`}
              filter={filter}
              filterFields={filterFields}
              onChange={(f) => handleFilterChange(index, f)}
              onRemove={() => handleFilterChange(index, null)}
            />
          ))}
          <Button
            variant='outline'
            size='sm'
            onClick={handleAddFilter}
            disabled={filterFields.length === 0}
            className='w-full'
          >
            {t('workRecords.list.addFilter')}
          </Button>
        </div>
      </SheetContent>
    </Sheet>
  )
}
