import { useRef, useState } from 'react'
import { CalendarIcon } from 'lucide-react'
import { zhCN } from 'react-day-picker/locale'
import { cn } from '@/lib/utils'
import { Calendar } from '@/components/ui/calendar'

type DateFieldProps = {
  id: string
  value: string
  onChange: (value: string) => void
  'aria-label': string
}

function parseIso(value: string): Date | undefined {
  const match = /^(\d{4})-(\d{2})-(\d{2})$/.exec(value)
  if (!match) return undefined
  return new Date(Number(match[1]), Number(match[2]) - 1, Number(match[3]))
}

function toIso(date: Date): string {
  const y = date.getFullYear()
  const m = String(date.getMonth() + 1).padStart(2, '0')
  const d = String(date.getDate()).padStart(2, '0')
  return `${y}-${m}-${d}`
}

export function DateField({ id, value, onChange, ...rest }: DateFieldProps) {
  const [open, setOpen] = useState(false)
  const rootRef = useRef<HTMLDivElement>(null)
  const selected = parseIso(value)

  const closeOnOutside = (event: React.PointerEvent) => {
    if (!rootRef.current?.contains(event.target as Node)) {
      setOpen(false)
    }
  }

  return (
    <div ref={rootRef} className='relative w-full md:max-w-[200px]'>
      <button
        id={id}
        type='button'
        aria-label={rest['aria-label']}
        aria-expanded={open}
        onClick={() => setOpen((prev) => !prev)}
        className={cn(
          'flex h-9 w-full items-center justify-between gap-2 rounded-md border border-input bg-transparent px-3 text-sm shadow-xs transition-[color,box-shadow] outline-none',
          'hover:bg-accent/50 focus-visible:border-ring focus-visible:ring-[3px] focus-visible:ring-ring/50',
          'dark:bg-input/30 dark:hover:bg-input/50'
        )}
      >
        <span className={cn(!selected && 'text-muted-foreground')}>
          {selected ? value.replace(/-/g, '/') : 'yyyy/mm/dd'}
        </span>
        <CalendarIcon className='size-4 shrink-0 text-muted-foreground' />
      </button>

      {open ? (
        <>
          <div
            className='fixed inset-0 z-40'
            data-testid={`${id}-backdrop`}
            onPointerDown={closeOnOutside}
          />
          <div className='absolute top-full left-0 z-50 mt-1 rounded-md border bg-popover shadow-md'>
            <Calendar
              mode='single'
              locale={zhCN}
              selected={selected}
              onSelect={(day) => {
                onChange(day ? toIso(day) : '')
                setOpen(false)
              }}
            />
          </div>
        </>
      ) : null}
    </div>
  )
}
