import { useMemo, useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Check, Plus, Upload } from 'lucide-react'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { notify } from '@/components/feedback/app-toaster'
import { useConfirm } from '@/components/feedback/confirm-provider'
import { PermissionGate } from '@/components/permission-gate'
import {
  createCalendar,
  getDefaultCalendar,
  importCalendarCsv,
  listCalendarDays,
  listCalendars,
  setDefaultCalendar,
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
  const confirm = useConfirm()
  const now = new Date()
  const [year, setYear] = useState(now.getFullYear())
  const [month, setMonth] = useState(now.getMonth() + 1)
  const [selectedCalendarId, setSelectedCalendarId] = useState('')

  const calendars = useQuery({
    queryKey: ['platform-calendars'],
    queryFn: listCalendars,
  })

  const yearCalendars = useMemo(
    () => (calendars.data ?? []).filter((calendar) => calendar.year === year),
    [calendars.data, year]
  )

  const defaultCalendar = useQuery({
    queryKey: ['platform-default-calendar', year],
    queryFn: () => getDefaultCalendar(year),
    enabled: yearCalendars.length > 0,
    retry: false,
  })

  const selectedCalendar = useMemo(() => {
    if (!yearCalendars.length) return undefined
    const explicit = yearCalendars.find(
      (calendar) => calendar.id === selectedCalendarId
    )
    if (explicit) return explicit
    const defaultForYear = yearCalendars.find(
      (calendar) => calendar.id === defaultCalendar.data?.id
    )
    return defaultForYear ?? yearCalendars[0]
  }, [yearCalendars, selectedCalendarId, defaultCalendar.data?.id])

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

  const invalidate = async () => {
    await queryClient.invalidateQueries({
      queryKey: ['platform-calendars'],
    })
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
    onSuccess: async () => {
      notify.success('年度日历创建成功')
      await invalidate()
    },
    onError: (error) => notify.error(error, '年度日历创建失败'),
  })

  const importMutation = useMutation({
    mutationFn: (calendarId: string) =>
      importCalendarCsv(
        calendarId,
        `date,dayType,isWorkday,holidayName,remark
${year}-01-01,HOLIDAY,false,元旦,
`
      ),
    onSuccess: async () => {
      notify.success('工作日历导入成功')
      await invalidate()
    },
    onError: (error) => notify.error(error, '工作日历导入失败'),
  })

  const defaultMutation = useMutation({
    mutationFn: (calendarId: string) => setDefaultCalendar(calendarId),
    onSuccess: async () => {
      notify.success('默认日历已更新')
      await queryClient.invalidateQueries({
        queryKey: ['platform-default-calendar'],
      })
      await queryClient.invalidateQueries({
        queryKey: ['work-record-workday-summary'],
      })
      await queryClient.invalidateQueries({
        queryKey: ['work-record-list'],
      })
    },
    onError: (error) => notify.error(error, '默认日历更新失败'),
  })

  const updateDayMutation = useMutation({
    mutationFn: ({
      calendarId,
      date,
      input,
    }: {
      calendarId: string
      date: string
      input: Parameters<typeof updateCalendarDay>[2]
    }) => updateCalendarDay(calendarId, date, input),

    onSuccess: async () => {
      notify.success('日期状态更新成功')
      await queryClient.invalidateQueries({
        queryKey: ['platform-calendar-days'],
      })
      await queryClient.invalidateQueries({
        queryKey: ['work-record-workday-summary'],
      })
      await queryClient.invalidateQueries({
        queryKey: ['work-record-list'],
      })
    },

    onError: (error) => notify.error(error, '日期状态更新失败'),
  })

  const submitUpdateDay = async (
    calendarId: string,
    day: {
      calendarDate: string
      workday: boolean
    }
  ) => {
    const accepted = await confirm({
      title: '修改日期状态',
      description: '该修改会影响最近工作日、工作日统计和后续日报缺失判断。',
      details: (
        <div>
          {day.calendarDate}：
          {day.workday ? '工作日 → 非工作日' : '非工作日 → 工作日'}
        </div>
      ),
      confirmText: '确认修改',
      variant: 'warning',
    })

    if (!accepted) return

    updateDayMutation.mutate({
      calendarId,
      date: day.calendarDate,
      input: {
        dayType: day.workday ? 'HOLIDAY' : 'ADJUSTED_WORKDAY',
        workday: !day.workday,
        holidayName: day.workday ? '手工设置假期' : undefined,
        sourceType: 'manual',
        remark: 'portal override',
      },
    })
  }

  return (
    <main className='grid gap-4 p-4 md:gap-6 md:p-6'>
      <header className='flex flex-col gap-4 sm:flex-row sm:items-start sm:justify-between'>
        <div>
          <h1 className='text-xl font-semibold md:text-2xl'>工作日历</h1>
          <p className='text-sm text-muted-foreground'>
            维护工作日、节假日和调休工作日，供日报、月报、值班和统计使用。
          </p>
        </div>
      </header>

      <Card>
        <CardHeader className='flex flex-row items-center justify-between'>
          <CardTitle>日历</CardTitle>
          <div className='flex flex-wrap gap-2'>
            <input
              className='w-24 rounded-md border px-2 py-1 text-sm'
              type='number'
              value={year}
              onChange={(event) => {
                const next = Number(event.target.value)
                if (next !== year) {
                  setSelectedCalendarId('')
                }
                setYear(next)
              }}
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
            <PermissionGate any={['platform:calendar:write']}>
              <Button
                size='sm'
                onClick={() => createMutation.mutate()}
                disabled={createMutation.isPending}
              >
                <Plus className='mr-1 size-4' />
                创建年度日历
              </Button>
            </PermissionGate>
            {selectedCalendar ? (
              <PermissionGate any={['platform:calendar:write']}>
                <Button
                  size='sm'
                  variant='outline'
                  onClick={() => importMutation.mutate(selectedCalendar.id)}
                  disabled={importMutation.isPending}
                >
                  <Upload className='mr-1 size-4' />
                  导入示例 CSV
                </Button>
              </PermissionGate>
            ) : null}
          </div>
        </CardHeader>
        <CardContent className='grid gap-4'>
          {yearCalendars.length === 0 ? (
            <p className='text-sm text-muted-foreground'>
              当前年份还没有工作日历，请先创建。
            </p>
          ) : (
            <>
              <select
                className='w-full rounded-md border px-2 py-1 text-sm'
                value={selectedCalendar?.id ?? ''}
                onChange={(event) => setSelectedCalendarId(event.target.value)}
              >
                {yearCalendars.map((calendar) => (
                  <option key={calendar.id} value={calendar.id}>
                    {calendar.calendarName}
                  </option>
                ))}
              </select>

              {selectedCalendar ? (
                <div className='flex items-center gap-2 text-sm'>
                  <span className='text-muted-foreground'>
                    {defaultCalendar.isError
                      ? '默认日历未配置'
                      : `默认日历：${
                          defaultCalendar.data?.calendarName ?? '加载中…'
                        }`}
                  </span>
                  {defaultCalendar.data?.id === selectedCalendar.id ? (
                    <Badge variant='secondary'>
                      <Check className='mr-1 size-3' />
                      当前默认日历
                    </Badge>
                  ) : (
                    <PermissionGate any={['platform:calendar:write']}>
                      <Button
                        size='sm'
                        variant='outline'
                        disabled={defaultMutation.isPending}
                        onClick={() =>
                          defaultMutation.mutate(selectedCalendar.id)
                        }
                      >
                        设为默认日历
                      </Button>
                    </PermissionGate>
                  )}
                </div>
              ) : null}
            </>
          )}

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
                        <PermissionGate any={['platform:calendar:write']}>
                          <Button
                            size='sm'
                            variant='outline'
                            disabled={updateDayMutation.isPending}
                            onClick={() =>
                              void submitUpdateDay(selectedCalendar.id, day)
                            }
                          >
                            切换
                          </Button>
                        </PermissionGate>
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
