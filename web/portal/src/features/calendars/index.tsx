import { useMemo, useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Check, Plus, Upload } from 'lucide-react'
import { useTranslation } from 'react-i18next'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { notify } from '@/components/feedback/app-toaster'
import {
  EmptyState,
  ErrorState,
  PageLoadingState,
  QueryStateBoundary,
  TableLoadingState,
} from '@/components/feedback/async-state'
import { useConfirm } from '@/components/feedback/confirm-provider'
import { ResponsiveTable } from '@/components/layout/responsive-table'
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
  const { t } = useTranslation()
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
      notify.success(t('calendars.create.success'))
      await invalidate()
    },
    onError: (error) => notify.error(error, t('calendars.create.failed')),
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
      notify.success(t('calendars.import.success'))
      await invalidate()
    },
    onError: (error) => notify.error(error, t('calendars.import.failed')),
  })

  const defaultMutation = useMutation({
    mutationFn: (calendarId: string) => setDefaultCalendar(calendarId),
    onSuccess: async () => {
      notify.success(t('calendars.default.success'))
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
    onError: (error) => notify.error(error, t('calendars.default.failed')),
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
      notify.success(t('calendars.day.success'))
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

    onError: (error) => notify.error(error, t('calendars.day.failed')),
  })

  const confirmImport = async () => {
    if (!selectedCalendar) {
      return
    }

    const accepted = await confirm({
      title: t('calendars.import.confirmTitle'),
      description: t('calendars.import.confirmDescription'),
      details: (
        <div>
          {t('calendars.import.confirmTarget')}:{selectedCalendar.calendarName}
        </div>
      ),
      confirmText: t('calendars.import.confirmAction'),
      variant: 'warning',
    })

    if (accepted) {
      importMutation.mutate(selectedCalendar.id)
    }
  }

  const confirmDefault = async () => {
    if (!selectedCalendar) {
      return
    }

    const accepted = await confirm({
      title: t('calendars.default.confirmTitle'),
      description: t('calendars.default.confirmDescription'),
      details: (
        <div>
          {t('calendars.default.confirmNew')}:{selectedCalendar.calendarName}
        </div>
      ),
      confirmText: t('calendars.default.confirmAction'),
      variant: 'warning',
    })

    if (accepted) {
      defaultMutation.mutate(selectedCalendar.id)
    }
  }

  const submitUpdateDay = async (
    calendarId: string,
    day: {
      calendarDate: string
      workday: boolean
    }
  ) => {
    const accepted = await confirm({
      title: t('calendars.day.confirmTitle'),
      description: t('calendars.day.confirmDescription'),
      details: (
        <div>
          {day.calendarDate}:
          {day.workday
            ? t('calendars.day.workdayToOff')
            : t('calendars.day.offToWorkday')}
        </div>
      ),
      confirmText: t('calendars.day.confirmAction'),
      variant: 'warning',
    })

    if (!accepted) return

    updateDayMutation.mutate({
      calendarId,
      date: day.calendarDate,
      input: {
        dayType: day.workday ? 'HOLIDAY' : 'ADJUSTED_WORKDAY',
        workday: !day.workday,
        holidayName: day.workday ? t('calendars.day.manualHoliday') : undefined,
        sourceType: 'manual',
        remark: 'portal override',
      },
    })
  }

  if (calendars.isLoading) {
    return <PageLoadingState />
  }

  if (calendars.error && !calendars.data) {
    return (
      <main className='p-4 md:p-6'>
        <ErrorState
          error={calendars.error}
          onRetry={() => void calendars.refetch()}
        />
      </main>
    )
  }

  return (
    <main className='grid gap-4 p-4 md:gap-6 md:p-6'>
      <header className='flex flex-col gap-4 sm:flex-row sm:items-start sm:justify-between'>
        <div>
          <h1 className='text-xl font-semibold md:text-2xl'>
            {t('calendars.title')}
          </h1>
          <p className='text-sm text-muted-foreground'>
            {t('calendars.subtitle')}
          </p>
        </div>
      </header>

      <Card>
        <CardHeader className='flex flex-row items-center justify-between'>
          <CardTitle>{t('calendars.title')}</CardTitle>
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
                  {index + 1} {t('calendars.month')}
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
                {t('calendars.create.button')}
              </Button>
            </PermissionGate>
            {selectedCalendar ? (
              <PermissionGate any={['platform:calendar:write']}>
                <Button
                  size='sm'
                  variant='outline'
                  onClick={() => void confirmImport()}
                  disabled={importMutation.isPending}
                >
                  <Upload className='mr-1 size-4' />
                  {t('calendars.import.button')}
                </Button>
              </PermissionGate>
            ) : null}
          </div>
        </CardHeader>
        <CardContent className='grid gap-4'>
          {yearCalendars.length === 0 ? (
            <EmptyState
              title={t('calendars.emptyTitle')}
              description={t('calendars.emptyDescription')}
            />
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
                      ? t('calendars.default.unset')
                      : `${t('calendars.default.label')}：${
                          defaultCalendar.data?.calendarName ??
                          t('common.loading')
                        }`}
                  </span>
                  {defaultCalendar.data?.id === selectedCalendar.id ? (
                    <Badge variant='secondary'>
                      <Check className='mr-1 size-3' />
                      {t('calendars.default.currentBadge')}
                    </Badge>
                  ) : (
                    <PermissionGate any={['platform:calendar:write']}>
                      <Button
                        size='sm'
                        variant='outline'
                        disabled={defaultMutation.isPending}
                        onClick={() => void confirmDefault()}
                      >
                        {t('calendars.default.set')}
                      </Button>
                    </PermissionGate>
                  )}
                </div>
              ) : null}

              <QueryStateBoundary
                loading={days.isLoading}
                error={days.error}
                empty={!selectedCalendar || (days.data?.length ?? 0) === 0}
                loadingFallback={<TableLoadingState columns={6} />}
                errorFallback={
                  <ErrorState
                    compact
                    error={days.error}
                    onRetry={() => void days.refetch()}
                  />
                }
                emptyFallback={
                  <EmptyState
                    compact
                    title={t('calendars.days.emptyTitle')}
                    description={t('calendars.days.emptyDescription')}
                  />
                }
              >
                <ResponsiveTable>
                  <table className='w-full text-sm'>
                    <thead>
                      <tr className='border-b bg-muted/40'>
                        <th className='p-2 text-left'>
                          {t('calendars.days.columns.date')}
                        </th>
                        <th className='p-2 text-left'>
                          {t('calendars.days.columns.week')}
                        </th>
                        <th className='p-2 text-left'>
                          {t('calendars.days.columns.type')}
                        </th>
                        <th className='p-2 text-left'>
                          {t('calendars.days.columns.workday')}
                        </th>
                        <th className='p-2 text-left'>
                          {t('calendars.days.columns.holiday')}
                        </th>
                        <th className='p-2 text-left'>
                          {t('calendars.days.columns.action')}
                        </th>
                      </tr>
                    </thead>
                    <tbody>
                      {days.data?.map((day) => (
                        <tr key={day.id} className='border-b'>
                          <td className='p-2'>{day.calendarDate}</td>
                          <td className='p-2'>{day.dayOfWeek}</td>
                          <td className='p-2'>{day.dayType}</td>
                          <td className='p-2'>
                            {day.workday
                              ? t('calendars.days.workday')
                              : t('calendars.days.off')}
                          </td>
                          <td className='p-2'>{day.holidayName ?? '-'}</td>
                          <td className='p-2'>
                            {selectedCalendar ? (
                              <PermissionGate any={['platform:calendar:write']}>
                                <Button
                                  size='sm'
                                  variant='outline'
                                  disabled={updateDayMutation.isPending}
                                  onClick={() =>
                                    void submitUpdateDay(
                                      selectedCalendar.id,
                                      day
                                    )
                                  }
                                >
                                  {t('calendars.days.toggle')}
                                </Button>
                              </PermissionGate>
                            ) : null}
                          </td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </ResponsiveTable>
              </QueryStateBoundary>
            </>
          )}
        </CardContent>
      </Card>
    </main>
  )
}
