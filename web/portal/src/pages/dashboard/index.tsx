import { useQuery } from '@tanstack/react-query'
import { Link } from '@tanstack/react-router'
import {
  ArrowRight,
  BellRing,
  CalendarDays,
  ClipboardList,
  FilePlus2,
  LayoutTemplate,
  Siren,
} from 'lucide-react'
import { listCalendars } from '@/api/calendars'
import { fetchRecordList, fetchWorkdaySummary } from '@/api/work-records/list'
import { listTemplates } from '@/api/work-records/templates'
import { useAuthStore } from '@/stores/auth-store'
import { formatDateTime } from '@/lib/date-format'
import type { AlertEvent, Incident } from '@/lib/operations/operations'
import { useAlerts, useIncidents } from '@/hooks/operations/use-operations'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import {
  Card,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle,
} from '@/components/ui/card'
import { Skeleton } from '@/components/ui/skeleton'
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from '@/components/ui/table'
import {
  AlertSeverityBadge,
  AlertStatusBadge,
} from '@/components/alerts/alert-badges'
import { EmptyState } from '@/components/feedback/async-state'
import {
  IncidentSeverityBadge,
  IncidentStatusBadge,
} from '@/components/incidents/list/incident-state'
import { buildEmptyListQuery } from '@/components/work-records/list/types'
import { buildDashboardMetrics } from './dashboard-metrics'

const statusText = {
  draft: '草稿',
  processing: '处理中',
  done: '已完成',
  archived: '已归档',
} as const

const EMPTY_PERMISSIONS: string[] = []

export function Dashboard() {
  const permissions = useAuthStore(
    (state) => state.auth.principal?.permissions ?? EMPTY_PERMISSIONS
  )
  const canReadAlerts = permissions.includes('alert:read')
  const canReadIncidents = permissions.includes('incident:read')
  const records = useQuery({
    queryKey: ['dashboard-records'],
    queryFn: () => fetchRecordList({ ...buildEmptyListQuery(), pageSize: 6 }),
  })
  const templates = useQuery({
    queryKey: ['dashboard-templates'],
    queryFn: listTemplates,
  })
  const calendars = useQuery({
    queryKey: ['dashboard-calendars'],
    queryFn: listCalendars,
  })
  const workdays = useQuery({
    queryKey: ['dashboard-workday-summary'],
    queryFn: () => fetchWorkdaySummary(currentMonth()),
    retry: false,
  })
  const alerts = useAlerts(canReadAlerts)
  const incidents = useIncidents(canReadIncidents)
  const metrics = buildDashboardMetrics({
    recordTotal: records.data?.total ?? 0,
    recentStatuses: records.data?.items.map((record) => record.status) ?? [],
    templateStatuses: templates.data?.map((template) => template.status) ?? [],
    enabledCalendarCount:
      calendars.data?.filter((calendar) => calendar.enabled).length ?? 0,
    workdayCount: workdays.data?.workdayCount ?? 0,
    alertTotal: alerts.data?.length ?? 0,
    openAlertCount:
      alerts.data?.filter((alert) => alert.status === 'open').length ?? 0,
    incidentTotal: incidents.data?.length ?? 0,
    openIncidentCount:
      incidents.data?.filter((incident) =>
        ['open', 'investigating', 'mitigating'].includes(incident.status)
      ).length ?? 0,
  })
  const isLoading =
    records.isLoading ||
    templates.isLoading ||
    calendars.isLoading ||
    (canReadAlerts && alerts.isLoading) ||
    (canReadIncidents && incidents.isLoading)
  const hasError =
    records.isError ||
    templates.isError ||
    calendars.isError ||
    (canReadAlerts && alerts.isError) ||
    (canReadIncidents && incidents.isError)

  const retry = () => {
    void records.refetch()
    void templates.refetch()
    void calendars.refetch()
    void workdays.refetch()
    if (canReadAlerts) void alerts.refetch()
    if (canReadIncidents) void incidents.refetch()
  }

  return (
    <main className='grid gap-4 p-4 md:gap-6 md:p-6'>
      <div className='flex flex-col gap-4 sm:flex-row sm:items-end sm:justify-between'>
        <div>
          <h1 className='text-2xl font-bold tracking-tight'>工作台</h1>
          <p className='text-sm text-muted-foreground'>
            从这里创建记录、跟进进度并检查基础配置。
          </p>
        </div>
        <Button asChild>
          <Link to='/work-records/new'>
            <FilePlus2 data-icon='inline-start' />
            新建工作记录
          </Link>
        </Button>
      </div>

      {hasError ? (
        <div className='flex items-center justify-between gap-3 rounded-lg border border-destructive/40 bg-destructive/5 p-3 text-sm'>
          <span>部分工作台数据加载失败，请检查服务连接。</span>
          <Button size='sm' variant='outline' onClick={retry}>
            重试
          </Button>
        </div>
      ) : null}

      <div className='grid gap-4 sm:grid-cols-2 xl:grid-cols-4'>
        <MetricCard
          title='工作记录'
          value={metrics.recordTotal}
          description={`最近记录中 ${metrics.draftCount} 条草稿`}
          icon={ClipboardList}
          loading={isLoading}
        />
        <MetricCard
          title='已发布工作类型'
          value={metrics.publishedTemplateCount}
          description={`共 ${templates.data?.length ?? 0} 个工作类型`}
          icon={LayoutTemplate}
          loading={templates.isLoading}
        />
        <MetricCard
          title='本月工作日'
          value={workdays.isError ? '未配置' : metrics.workdayCount}
          description={workdays.data?.calendarName ?? '请设置默认工作日历'}
          icon={CalendarDays}
          loading={workdays.isLoading}
        />
        <MetricCard
          title='可用日历'
          value={metrics.enabledCalendarCount}
          description='已启用的工作日历'
          icon={CalendarDays}
          loading={calendars.isLoading}
        />
      </div>

      <AIOpsSummary
        alerts={alerts.data ?? []}
        incidents={incidents.data ?? []}
        metrics={metrics}
        canReadAlerts={canReadAlerts}
        canReadIncidents={canReadIncidents}
        loading={alerts.isLoading || incidents.isLoading}
      />

      <div className='grid gap-4 xl:grid-cols-[minmax(0,2fr)_minmax(280px,1fr)]'>
        <Card>
          <CardHeader className='flex flex-row items-center justify-between'>
            <div>
              <CardTitle>最近工作记录</CardTitle>
              <CardDescription>按记录时间展示最近更新的内容。</CardDescription>
            </div>
            <Button variant='ghost' size='sm' asChild>
              <Link
                to='/work-records'
                search={{ ...buildEmptyListQuery(), workdayCount: 5 }}
              >
                查看全部
                <ArrowRight data-icon='inline-end' />
              </Link>
            </Button>
          </CardHeader>
          <CardContent>
            <div className='overflow-x-auto rounded-md border'>
              <Table>
                <TableHeader>
                  <TableRow>
                    <TableHead>标题</TableHead>
                    <TableHead>状态</TableHead>
                    <TableHead>记录时间</TableHead>
                  </TableRow>
                </TableHeader>
                <TableBody>
                  {(records.data?.items ?? []).map((record) => (
                    <TableRow key={record.id}>
                      <TableCell className='font-medium'>
                        <Link
                          className='hover:underline'
                          to='/work-records/$recordId'
                          params={{ recordId: record.id }}
                        >
                          {record.title}
                        </Link>
                      </TableCell>
                      <TableCell>
                        <Badge variant='outline'>
                          {statusText[record.status]}
                        </Badge>
                      </TableCell>
                      <TableCell className='text-muted-foreground'>
                        {formatDateTime(record.recordTime)}
                      </TableCell>
                    </TableRow>
                  ))}
                  {!records.isLoading && !records.data?.items.length ? (
                    <TableRow>
                      <TableCell
                        colSpan={3}
                        className='h-24 text-center text-muted-foreground'
                      >
                        暂无工作记录，先创建第一条记录。
                      </TableCell>
                    </TableRow>
                  ) : null}
                </TableBody>
              </Table>
            </div>
          </CardContent>
        </Card>

        <Card>
          <CardHeader>
            <CardTitle>快捷入口</CardTitle>
            <CardDescription>常用配置和工作流程。</CardDescription>
          </CardHeader>
          <CardContent className='grid gap-2'>
            <QuickLink
              to='/work-records/new'
              title='新建工作记录'
              description='选择工作类型并填写工作内容'
            />
            <QuickLink
              to='/work-records/templates'
              title='工作类型管理'
              description='维护表单字段和发布版本'
            />
            <QuickLink
              to='/platform/dictionaries'
              title='字典管理'
              description='维护动态字段选项'
            />
            <QuickLink
              to='/platform/calendars'
              title='工作日历'
              description='设置工作日、节假日和调休'
            />
          </CardContent>
        </Card>
      </div>
    </main>
  )
}

function AIOpsSummary({
  alerts,
  incidents,
  metrics,
  canReadAlerts,
  canReadIncidents,
  loading,
}: {
  alerts: AlertEvent[]
  incidents: Incident[]
  metrics: ReturnType<typeof buildDashboardMetrics>
  canReadAlerts: boolean
  canReadIncidents: boolean
  loading: boolean
}) {
  if (!canReadAlerts && !canReadIncidents) return null

  return (
    <section className='grid gap-4'>
      <div className='flex flex-col gap-1 sm:flex-row sm:items-end sm:justify-between'>
        <div>
          <h2 className='text-lg font-semibold'>AIOps 故障态势</h2>
          <p className='text-sm text-muted-foreground'>
            从告警到 Incident 的当前租户运行概览。
          </p>
        </div>
        <div className='flex flex-wrap gap-2'>
          {canReadAlerts ? (
            <Button variant='ghost' size='sm' asChild>
              <Link
                to='/alerts'
                search={{
                  page: 1,
                  pageSize: 20,
                  keyword: '',
                  statuses: [],
                  severities: [],
                }}
              >
                告警中心
                <ArrowRight data-icon='inline-end' />
              </Link>
            </Button>
          ) : null}
          {canReadIncidents ? (
            <Button variant='ghost' size='sm' asChild>
              <Link
                to='/incidents'
                search={{
                  page: 1,
                  pageSize: 20,
                  keyword: '',
                  status: '',
                  severity: '',
                  source: '',
                }}
              >
                Incident 中心
                <ArrowRight data-icon='inline-end' />
              </Link>
            </Button>
          ) : null}
        </div>
      </div>

      <div className='grid gap-4 sm:grid-cols-2 xl:grid-cols-4'>
        {canReadAlerts ? (
          <MetricCard
            title='开放告警'
            value={metrics.openAlertCount}
            description={`最近共 ${metrics.alertTotal} 条告警`}
            icon={BellRing}
            loading={loading}
          />
        ) : null}
        {canReadIncidents ? (
          <MetricCard
            title='活动 Incident'
            value={metrics.openIncidentCount}
            description={`最近共 ${metrics.incidentTotal} 个 Incident`}
            icon={Siren}
            loading={loading}
          />
        ) : null}
      </div>

      <div className='grid gap-4 xl:grid-cols-2'>
        {canReadIncidents ? (
          <Card>
            <CardHeader className='flex flex-row items-center justify-between gap-3'>
              <div>
                <CardTitle>最近 Incident</CardTitle>
                <CardDescription>优先处理仍在活动状态的故障。</CardDescription>
              </div>
              <Siren className='size-4 text-muted-foreground' />
            </CardHeader>
            <CardContent>
              {loading ? (
                <AIOpsListLoading />
              ) : incidents.length ? (
                <div className='grid gap-2'>
                  {incidents.slice(0, 5).map((incident) => (
                    <div
                      key={incident.id}
                      className='flex flex-col gap-2 rounded-md border p-3 sm:flex-row sm:items-center sm:justify-between'
                    >
                      <div className='min-w-0'>
                        <Link
                          className='block truncate font-medium hover:underline'
                          to='/incidents/$incidentId'
                          params={{ incidentId: incident.id }}
                        >
                          {incident.title}
                        </Link>
                        <p className='text-xs text-muted-foreground'>
                          {incident.alertCount} 条告警 ·{' '}
                          {formatDateTime(incident.updatedAt)}
                        </p>
                      </div>
                      <div className='flex shrink-0 gap-2'>
                        <IncidentSeverityBadge severity={incident.severity} />
                        <IncidentStatusBadge status={incident.status} />
                      </div>
                    </div>
                  ))}
                </div>
              ) : (
                <EmptyState compact title='暂无 Incident' />
              )}
            </CardContent>
          </Card>
        ) : null}

        {canReadAlerts ? (
          <Card>
            <CardHeader className='flex flex-row items-center justify-between gap-3'>
              <div>
                <CardTitle>最近告警</CardTitle>
                <CardDescription>查看最新归一化事件状态。</CardDescription>
              </div>
              <BellRing className='size-4 text-muted-foreground' />
            </CardHeader>
            <CardContent>
              {loading ? (
                <AIOpsListLoading />
              ) : alerts.length ? (
                <div className='grid gap-2'>
                  {alerts.slice(0, 5).map((alert) => (
                    <div
                      key={alert.id}
                      className='flex flex-col gap-2 rounded-md border p-3 sm:flex-row sm:items-center sm:justify-between'
                    >
                      <div className='min-w-0'>
                        <p className='truncate font-medium'>{alert.title}</p>
                        <p className='text-xs text-muted-foreground'>
                          {alert.source} · {formatDateTime(alert.startsAt)}
                        </p>
                      </div>
                      <div className='flex shrink-0 gap-2'>
                        <AlertSeverityBadge severity={alert.severity} />
                        <AlertStatusBadge status={alert.status} />
                      </div>
                    </div>
                  ))}
                </div>
              ) : (
                <EmptyState compact title='暂无告警' />
              )}
            </CardContent>
          </Card>
        ) : null}
      </div>
    </section>
  )
}

function AIOpsListLoading() {
  return (
    <div className='grid gap-2' aria-label='AIOps 数据加载中' aria-busy='true'>
      {Array.from({ length: 3 }).map((_, index) => (
        <Skeleton key={index} className='h-16 w-full' />
      ))}
    </div>
  )
}

function MetricCard({
  title,
  value,
  description,
  icon: Icon,
  loading,
}: {
  title: string
  value: string | number
  description: string
  icon: typeof ClipboardList
  loading: boolean
}) {
  return (
    <Card>
      <CardHeader className='flex flex-row items-center justify-between gap-3 pb-2'>
        <CardTitle className='text-sm font-medium'>{title}</CardTitle>
        <Icon className='size-4 text-muted-foreground' />
      </CardHeader>
      <CardContent>
        {loading ? (
          <div className='grid gap-2' aria-label={`${title}加载中`}>
            <Skeleton className='h-8 w-16' />
            <Skeleton className='h-3 w-32 max-w-full' />
          </div>
        ) : (
          <>
            <div className='text-2xl font-bold'>{value}</div>
            <p className='mt-1 truncate text-xs text-muted-foreground'>
              {description}
            </p>
          </>
        )}
      </CardContent>
    </Card>
  )
}

function QuickLink({
  to,
  title,
  description,
}: {
  to:
    | '/work-records/new'
    | '/work-records/templates'
    | '/platform/dictionaries'
    | '/platform/calendars'
  title: string
  description: string
}) {
  return (
    <Button
      variant='ghost'
      className='h-auto justify-between p-3 text-left'
      asChild
    >
      <Link to={to}>
        <span className='grid gap-1'>
          <span className='font-medium'>{title}</span>
          <span className='text-xs font-normal text-muted-foreground'>
            {description}
          </span>
        </span>
        <ArrowRight />
      </Link>
    </Button>
  )
}

function currentMonth() {
  const now = new Date()
  return `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}`
}
