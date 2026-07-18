import { useMemo, useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useAuthorization } from '@/auth/use-authorization'
import { CalendarDays, Check, Clock3, MapPin, Plus, Upload } from 'lucide-react'
import { useTranslation } from 'react-i18next'
import {
  createCalendar,
  getDefaultCalendar,
  importCalendarCsv,
  listCalendarDays,
  listCalendars,
  setDefaultCalendar,
  updateCalendarDay,
} from '@/api/calendars'
import {
  buildDayMap,
  formatDayKey,
  getDayClassName,
  lookupDay,
} from '@/lib/calendars/calendar-helpers'
import { cn } from '@/lib/utils'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Calendar, type CalendarDayButton } from '@/components/ui/calendar'
import {
  Card,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle,
} from '@/components/ui/card'
import { Input } from '@/components/ui/input'
import {
  Select,
  SelectContent,
  SelectGroup,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select'
import { notify } from '@/components/feedback/app-toaster'
import {
  EmptyState,
  ErrorState,
  PageLoadingState,
  QueryStateBoundary,
} from '@/components/feedback/async-state'
import { useConfirm } from '@/components/feedback/confirm-provider'
import { PermissionGate } from '@/components/permission-gate'

const WRITE_PERMISSION = 'platform:calendar:write'
const IMPORT_PERMISSION = 'platform:calendar:import'

function monthRange(year: number, month: number) {
  const start = `${year}-${String(month).padStart(2, '0')}-01`
  const endDate = new Date(year, month, 0).getDate()
  const end = `${year}-${String(month).padStart(2, '0')}-${String(endDate).padStart(2, '0')}`
  return { start, end }
}

function fromMonthParts(year: number, month: number) {
  return new Date(year, month - 1, 1)
}

type DayCellProps = React.ComponentProps<typeof CalendarDayButton> & {
  dayData: ReturnType<typeof lookupDay>
  canWrite: boolean
  onToggleDay: (date: Date) => void
}

function CalendarDayCell({
  day,
  modifiers,
  dayData,
  canWrite,
  onToggleDay,
  className,
  ...props
}: DayCellProps) {
  const { t } = useTranslation()
  const dayNumber = day.date.getDate()
  const holidayLabel = dayData?.holidayName?.trim()
  const dayMark =
    dayData?.dayType === 'ADJUSTED_WORKDAY'
      ? '班'
      : dayData && !dayData.workday
        ? '休'
        : ''

  const handleClick = (event: React.MouseEvent<HTMLButtonElement>) => {
    if (!canWrite || modifiers.outside || !dayData) return
    event.preventDefault()
    onToggleDay(day.date)
  }

  const colorClass = getDayClassName({
    dayData,
    outside: Boolean(modifiers.outside),
    today: Boolean(modifiers.today),
  })

  return (
    <Button
      {...props}
      type='button'
      variant='ghost'
      size='icon'
      onClick={handleClick}
      data-day={formatDayKey(day.date)}
      data-day-type={dayData?.dayType ?? 'unknown'}
      data-readonly={!canWrite || !dayData}
      title={
        modifiers.outside
          ? undefined
          : (holidayLabel ??
            (canWrite && dayData ? t('calendars.day.tooltip') : undefined))
      }
      className={cn(
        'h-full w-full min-w-(--cell-size) flex-col gap-1 rounded-lg border border-transparent px-1 py-1.5 text-xs font-normal transition-transform hover:z-10 hover:scale-[1.03] hover:border-border hover:shadow-sm',
        colorClass,
        className
      )}
    >
      <span className='text-sm leading-none font-medium'>{dayNumber}</span>
      {!modifiers.outside && (holidayLabel || dayMark) ? (
        <span className='line-clamp-1 max-w-full rounded-full px-1.5 text-[10px] font-medium opacity-80'>
          {holidayLabel || dayMark}
        </span>
      ) : null}
    </Button>
  )
}

export function CalendarsPage() {
  const queryClient = useQueryClient()
  const confirm = useConfirm()
  const { t } = useTranslation()
  const principal = useAuthorization()
  const canWrite = principal?.permissions.includes(WRITE_PERMISSION) ?? false

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

  const dayMap = useMemo(() => buildDayMap(days.data ?? []), [days.data])

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

  const submitUpdateDay = async (dateObj: Date) => {
    if (!selectedCalendar) return
    const dayData = lookupDay(dayMap, dateObj)
    const date = formatDayKey(dateObj)
    if (!dayData) {
      notify.error(
        new Error(t('calendars.day.missing', { date })),
        t('calendars.day.failed')
      )
      return
    }

    const accepted = await confirm({
      title: t('calendars.day.confirmTitle'),
      description: t('calendars.day.confirmDescription'),
      details: (
        <div>
          {date}:
          {dayData.workday
            ? t('calendars.day.workdayToOff')
            : t('calendars.day.offToWorkday')}
        </div>
      ),
      confirmText: t('calendars.day.confirmAction'),
      variant: 'warning',
    })

    if (!accepted) return

    updateDayMutation.mutate({
      calendarId: selectedCalendar.id,
      date,
      input: {
        dayType: dayData.workday ? 'HOLIDAY' : 'ADJUSTED_WORKDAY',
        workday: !dayData.workday,
        holidayName: dayData.workday
          ? t('calendars.day.manualHoliday')
          : undefined,
        sourceType: 'manual',
        remark: 'portal override',
      },
    })
  }

  const confirmImport = async () => {
    if (!selectedCalendar) return

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
    if (!selectedCalendar) return

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

  const handleMonthChange = (next: Date) => {
    setYear(next.getFullYear())
    setMonth(next.getMonth() + 1)
  }

  const handleYearChange = (next: number) => {
    if (next === year) return
    setSelectedCalendarId('')
    setYear(next)
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

      <Card className='overflow-hidden'>
        <CardHeader className='border-b bg-muted/20'>
          <div className='flex flex-col gap-4 xl:flex-row xl:items-center xl:justify-between'>
            <div className='flex flex-wrap items-center gap-2'>
              <label className='flex items-center gap-2 text-sm'>
                <span className='text-muted-foreground'>
                  {t('calendars.year')}
                </span>
                <Input
                  className='w-24'
                  type='number'
                  value={year}
                  onChange={(event) =>
                    handleYearChange(Number(event.target.value))
                  }
                />
              </label>
              <PermissionGate any={[WRITE_PERMISSION]}>
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
                <PermissionGate any={[IMPORT_PERMISSION]}>
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
                  <PermissionGate any={[WRITE_PERMISSION]}>
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
          </div>
        </CardHeader>
        <CardContent className='grid gap-6 pt-6'>
          {yearCalendars.length === 0 ? (
            <EmptyState
              title={t('calendars.emptyTitle')}
              description={t('calendars.emptyDescription')}
            />
          ) : (
            <div className='grid gap-6 xl:grid-cols-[minmax(0,4fr)_minmax(15rem,1fr)] xl:items-start'>
              <QueryStateBoundary
                loading={days.isLoading}
                error={days.error}
                empty={!selectedCalendar || (days.data?.length ?? 0) === 0}
                loadingFallback={<CalendarSkeleton />}
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
                <div className='overflow-x-auto rounded-xl border bg-card p-2 shadow-sm sm:p-4'>
                  <div className='min-w-[680px]'>
                    <Calendar
                      month={fromMonthParts(year, month)}
                      onMonthChange={handleMonthChange}
                      onDayClick={(date, modifiers) => {
                        if (modifiers.outside) return
                        const dayData = lookupDay(dayMap, date)
                        if (!dayData) return
                        if (!canWrite) return
                        void submitUpdateDay(date)
                      }}
                      className='w-full [--cell-size:4.5rem]'
                      classNames={{
                        root: 'w-full',
                        months: 'w-full',
                        month: 'w-full',
                        month_grid: 'w-full table-fixed',
                        day: 'h-20 w-full p-0.5',
                        month_caption: 'mb-2 text-base font-semibold',
                        weekday: 'font-medium',
                        week: 'mt-1',
                      }}
                      components={{
                        DayButton: (
                          props: React.ComponentProps<typeof CalendarDayButton>
                        ) => (
                          <CalendarDayCell
                            {...props}
                            dayData={lookupDay(dayMap, props.day.date)}
                            canWrite={canWrite}
                            onToggleDay={submitUpdateDay}
                          />
                        ),
                      }}
                    />
                  </div>
                </div>
              </QueryStateBoundary>
              <aside className='grid gap-4'>
                <Card>
                  <CardHeader>
                    <CardTitle className='flex items-center gap-2 text-base'>
                      <CalendarDays className='size-4' />
                      当前日历
                    </CardTitle>
                    <CardDescription>选择需要维护的年度日历</CardDescription>
                  </CardHeader>
                  <CardContent className='flex flex-col gap-3'>
                    <Select
                      value={selectedCalendar?.id}
                      onValueChange={setSelectedCalendarId}
                    >
                      <SelectTrigger className='w-full'>
                        <SelectValue />
                      </SelectTrigger>
                      <SelectContent>
                        <SelectGroup>
                          {yearCalendars.map((calendar) => (
                            <SelectItem key={calendar.id} value={calendar.id}>
                              {calendar.calendarName}
                            </SelectItem>
                          ))}
                        </SelectGroup>
                      </SelectContent>
                    </Select>
                    {selectedCalendar ? (
                      <div className='flex flex-wrap gap-3 text-sm text-muted-foreground'>
                        <span className='flex items-center gap-1'>
                          <MapPin className='size-4' />
                          {selectedCalendar.regionCode}
                        </span>
                        <span className='flex items-center gap-1'>
                          <Clock3 className='size-4' />
                          {selectedCalendar.timezone}
                        </span>
                      </div>
                    ) : null}
                  </CardContent>
                </Card>
                <Card>
                  <CardHeader>
                    <CardTitle className='text-base'>日期图例</CardTitle>
                    <CardDescription>
                      {canWrite
                        ? '点击日期可切换工作日状态'
                        : '当前仅可查看日历'}
                    </CardDescription>
                  </CardHeader>
                  <CardContent>
                    <DayLegend canWrite={canWrite} />
                  </CardContent>
                </Card>
              </aside>
            </div>
          )}
        </CardContent>
      </Card>
    </main>
  )
}

function DayLegend({ canWrite }: { canWrite: boolean }) {
  const { t } = useTranslation()
  return (
    <div
      className='grid gap-3 text-xs text-muted-foreground'
      data-testid='calendar-legend'
    >
      <span className='flex items-center gap-1'>
        <span className='inline-block size-3 rounded-sm border bg-background' />
        {t('calendars.legend.workday')}
      </span>
      <span className='flex items-center gap-1'>
        <span className='inline-block size-3 rounded-sm border bg-red-100 dark:bg-red-950/40' />
        {t('calendars.legend.holiday')}
      </span>
      <span className='flex items-center gap-1'>
        <span className='inline-block size-3 rounded-sm border bg-emerald-100 dark:bg-emerald-950/40' />
        {t('calendars.legend.adjusted')}
      </span>
      <span className='border-t pt-3'>
        {canWrite
          ? t('calendars.legend.editable')
          : t('calendars.legend.readonly')}
      </span>
    </div>
  )
}

function CalendarSkeleton() {
  const { t } = useTranslation()
  return (
    <div
      role='status'
      aria-label='calendar-loading'
      className='grid h-[360px] w-full place-items-center rounded-lg border bg-muted/20 text-sm text-muted-foreground'
    >
      {t('calendars.calendar.loading')}
    </div>
  )
}
