type Props = {
  value: string
  onChange: (value: string) => void
}

const labels: Record<string, string> = {
  mine: '我的记录',
  all: '全部记录',
  today: '今日记录',
  this_week: '本周记录',
  this_month: '本月记录',
  recent_workdays: '最近工作日',
}

export function QuickViewTabs({ value, onChange }: Props) {
  return (
    <div className='flex flex-wrap gap-2'>
      {Object.entries(labels).map(([key, label]) => (
        <button
          key={key}
          type='button'
          className={`rounded-md border px-3 py-1.5 text-sm ${
            value === key ? 'bg-primary text-primary-foreground' : 'bg-background'
          }`}
          onClick={() => onChange(key)}
        >
          {label}
        </button>
      ))}
    </div>
  )
}