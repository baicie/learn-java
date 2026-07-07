import { cn } from '@/lib/utils'
import { ScrollArea } from '@/components/ui/scroll-area'
import { type DictType } from '../data/schema'

type DictionaryTypeListProps = {
  items: DictType[]
  selectedCode?: string
  onSelect: (dictCode: string) => void
}

export function DictionaryTypeList({
  items,
  selectedCode,
  onSelect,
}: DictionaryTypeListProps) {
  return (
    <ScrollArea className='h-[520px] rounded-md border'>
      <div className='grid gap-1 p-2'>
        {items.map((item) => (
          <button
            key={item.id}
            type='button'
            onClick={() => onSelect(item.dictCode)}
            className={cn(
              'rounded-md px-3 py-2 text-start text-sm hover:bg-muted',
              selectedCode === item.dictCode && 'bg-muted'
            )}
          >
            <div className='font-medium'>{item.dictName}</div>
            <div className='text-muted-foreground'>{item.dictCode}</div>
          </button>
        ))}
      </div>
    </ScrollArea>
  )
}
