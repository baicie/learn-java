import { useMemo, useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Plus, Upload } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import {
  createCalendar,
  importCalendarCsv,
  listCalendarDays,
  listCalendars,
  updateCalendarDay,
} from './api'

function monthRange(year: number, month: number) {
  const start = `${year}-${String(month).padStart(2, '0')}-01`
  const endDate = new Date(year, month, 0).getDate()
  const end = `${year}-${String(month).padStart(2, '0')}-${String(endDate).padStart(2, '0')}`
  return { start, end }
}

export function CalendarsPage() {
  const queryClient = useQueryClient()
  const now = new Date()
  const [year, setYear] = useState(now.getFullYear())
  const [month, setMonth] = useState(now.getMonth() + 1)
  const [selectedCalendarId, setSelectedCalendarId] = useState('')

  const calendars = useQuery({
    queryKey: ['platform-calendars'],
    queryFn: listCalendars,
  })

  const selectedCalendar = useMemo(() => {
    if (!calendars.data?.length) return undefined
    if (!selectedCalendarId) return calendars.data[0]
    return (
      calendars.data.find((item) => item.id === selectedCalendarId) ??
      calendars.data[0]
    )
  }, [selectedCalendarId, calendars.data])

  const range = monthRange(year, month)

  const days = useQuery({
    queryKey: [
      'platform-calendar-days',
      selectedCalendar?.id,
      range.start,
      range.end,
    ],
    queryFn: () =>
      listCalendarDays(selectedCalendar!.id, range.start, range.end),
    enabled: Boolean(selectedCalendar?.id),
  })

  const refresh = async () => {
    await queryClient.invalidateQueries({ queryKey: ['platform-calendars'] })
    await queryClient.invalidateQueries({
      queryKey: ['platform-calendar-days'],
    })
  }

  const createMutation = useMutation({
    mutationFn: () =>
      createCalendar({
        calendarCode: `CN_${year}`,
        calendarName: `中国大陆 ${year} 工作日历`,
        regionCode: 'CN',
        timezone: 'Asia/Shanghai',
        year,
        enabled: true,
        sourceType: 'manual',
      }),
    onSuccess: refresh,
  })

  const importMutation = useMutation({
    mutationFn: (calendarId: string) =>
      importCalendarCsv(
        calendarId,
        `date,dayType,isWorkday,holidayName,remark
${year}-01-01,HOLIDAY,false,元旦,
`
      ),
    onSuccess: refresh,
  })

  return (
    <main className='grid gap-4 p-6'>
      <div>
        <h1 className='text-2xl font-semibold'>工作日历</h1>
        <p className='text-sm text-muted-foreground'>
          维护工作日、节假日和调休工作日，供日报、月报、值班和统计使用。
        </p>
      </div>

      <Card>
        <CardHeader className='flex flex-row items-center justify-between'>
          <CardTitle>日历</CardTitle>
          <div className='flex flex-wrap gap-2'>
            <input
              className='w-24 rounded-md border px-2 py-1 text-sm'
              type='number'
              value={year}
              onChange={(event) => setYear(Number(event.target.value))}
            />
            <select
              className='rounded-md border px-2 py-1 text-sm'
              value={month}
              onChange={(event) => setMonth(Number(event.target.value))}
            >
              {Array.from({ length: 12 }).map((_, index) => (
                <option key={index + 1} value={index + 1}>
                  {index + 1} 月
                </option>
              ))}
            </select>
            <Button size='sm' onClick={() => createMutation.mutate()}>
              <Plus className='mr-1 size-4' />
              创建年度日历
            </Button>
            {selectedCalendar ? (
              <Button
                size='sm'
                variant='outline'
                onClick={() => importMutation.mutate(selectedCalendar.id)}
              >
                <Upload className='mr-1 size-4' />
                导入示例 CSV
              </Button>
            ) : null}
          </div>
        </CardHeader>
        <CardContent className='grid gap-4'>
          <select
            className='w-full rounded-md border px-2 py-1 text-sm'
            value={selectedCalendar?.id ?? ''}
            onChange={(event) => setSelectedCalendarId(event.target.value)}
          >
            {calendars.data?.map((calendar) => (
              <option key={calendar.id} value={calendar.id}>
                {calendar.calendarName}
              </option>
            ))}
          </select>

          <div className='overflow-x-auto rounded-md border'>
            <table className='w-full text-sm'>
              <thead>
                <tr className='border-b bg-muted/40'>
                  <th className='p-2 text-left'>日期</th>
                  <th className='p-2 text-left'>星期</th>
                  <th className='p-2 text-left'>类型</th>
                  <th className='p-2 text-left'>是否工作日</th>
                  <th className='p-2 text-left'>节日</th>
                  <th className='p-2 text-left'>操作</th>
                </tr>
              </thead>
              <tbody>
                {days.data?.map((day) => (
                  <tr key={day.id} className='border-b'>
                    <td className='p-2'>{day.calendarDate}</td>
                    <td className='p-2'>{day.dayOfWeek}</td>
                    <td className='p-2'>{day.dayType}</td>
                    <td className='p-2'>{day.workday ? '是' : '否'}</td>
                    <td className='p-2'>{day.holidayName ?? '-'}</td>
                    <td className='p-2'>
                      {selectedCalendar ? (
                        <Button
                          size='sm'
                          variant='outline'
                          onClick={() =>
                            updateCalendarDay(
                              selectedCalendar.id,
                              day.calendarDate,
                              {
                                dayType: day.workday ? 'HOLIDAY' : 'WORKDAY',
                                workday: !day.workday,
                                holidayName: day.workday
                                  ? '手工设置假期'
                                  : undefined,
                                sourceType: 'manual',
                                remark: 'portal override',
                              }
                            ).then(refresh)
                          }
                        >
                          切换
                        </Button>
                      ) : null}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </CardContent>
      </Card>
    </main>
  )
}
