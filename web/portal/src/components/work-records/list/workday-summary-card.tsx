import { CalendarDays } from 'lucide-react'
import { apiErrorCode, apiErrorMessage } from '@/lib/api-error'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Skeleton } from '@/components/ui/skeleton'
import type { RecordWorkdaySummary } from './types'

type Props = {
  summary?: RecordWorkdaySummary
  loading: boolean
  error?: unknown
}

export function WorkdaySummaryCard({ summary, loading, error }: Props) {
  if (loading) {
    return (
      <Card>
        <CardContent className='grid gap-2 p-4'>
          <Skeleton className='h-5 w-40' />
          <Skeleton className='h-8 w-24' />
        </CardContent>
      </Card>
    )
  }

  if (error) {
    const code = apiErrorCode(error)
    const detail = apiErrorMessage(error, '工作日历配置异常')

    return (
      <Card>
        <CardContent className='p-4 text-sm text-destructive'>
          工作日统计加载失败：{detail}
          {code ? (
            <span className='ml-2 rounded bg-destructive/10 px-1 text-xs text-destructive'>
              {code}
            </span>
          ) : null}
        </CardContent>
      </Card>
    )
  }

  if (!summary) {
    return null
  }

  return (
    <Card>
      <CardHeader className='pb-2'>
        <CardTitle className='flex items-center gap-2 text-base'>
          <CalendarDays className='size-4' />
          {summary.month} 工作月
        </CardTitle>
      </CardHeader>

      <CardContent className='grid gap-3 md:grid-cols-4'>
        <Metric label='工作日数量' value={`${summary.workdayCount} 天`} />

        <Metric
          label='统计区间'
          value={`${summary.periodStart} 至 ${summary.periodEnd}`}
        />

        <Metric label='首个工作日' value={summary.firstWorkday ?? '-'} />

        <Metric label='默认日历' value={summary.calendarName} />
      </CardContent>
    </Card>
  )
}

function Metric({ label, value }: { label: string; value: string }) {
  return (
    <div className='rounded-md border p-3'>
      <div className='text-xs text-muted-foreground'>{label}</div>
      <div className='mt-1 font-medium'>{value}</div>
    </div>
  )
}
