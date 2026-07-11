import { useTranslation } from 'react-i18next'

type Props = {
  value: string
  available: string[]
  onChange: (value: string) => void
}

export function QuickViewTabs({ value, available, onChange }: Props) {
  const { t } = useTranslation()

  const labels: Record<string, string> = {
    mine: t('workRecords.quick.mine'),
    all: t('workRecords.quick.all'),
    today: t('workRecords.quick.today'),
    this_week: t('workRecords.quick.thisWeek'),
    this_month: t('workRecords.quick.thisMonth'),
    this_work_month: t('workRecords.quick.thisWorkMonth'),
    recent_workdays: t('workRecords.quick.recentWorkdays'),
  }

  const views = available.length > 0 ? available : Object.keys(labels)

  return (
    <div className='flex flex-wrap gap-2'>
      {views.map((key) => (
        <button
          key={key}
          type='button'
          className={`rounded-md border px-3 py-1 text-sm ${
            value === key
              ? 'bg-primary text-primary-foreground'
              : 'bg-background'
          }`}
          onClick={() => onChange(key)}
        >
          {labels[key] ?? key}
        </button>
      ))}
    </div>
  )
}
