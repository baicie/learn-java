import { useTranslation } from 'react-i18next'
import { Button } from '@/components/ui/button'

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
