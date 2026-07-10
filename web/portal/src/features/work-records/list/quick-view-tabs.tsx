import { Button } from '@/components/ui/button'

type Props = {
  value: string
  available: string[]
  onChange: (value: string) => void
}

const labels: Record<string, string> = {
  mine: '我的记录',
  all: '全部记录',
  today: '今日记录',
  this_week: '本周记录',
  this_month: '本月记录',
  this_work_month: '本工作月',
  recent_workdays: '最近工作日',
}

export function QuickViewTabs({
  value,
  available,
  onChange,
}: Props) {
  const views =
    available.length > 0
      ? available
      : Object.keys(labels)

  return (
    <div className='flex flex-wrap gap-2'>
      {views.map((key) => (
        <Button
          key={key}
          type='button'
          size='sm'
          variant={value === key ? 'default' : 'outline'}
          onClick={() => onChange(key)}
        >
          {labels[key] ?? key}
        </Button>
      ))}
    </div>
  )
}