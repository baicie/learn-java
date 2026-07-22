import { useQuery } from '@tanstack/react-query'
import { Link } from '@tanstack/react-router'
import {
  ArrowRight,
  CalendarDays,
  ClipboardList,
  FilePlus2,
  LayoutTemplate,
} from 'lucide-react'
import { listCalendars } from '@/api/calendars'
import { fetchRecordList, fetchWorkdaySummary } from '@/api/work-records/list'
import { listTemplates } from '@/api/work-records/templates'
import { formatDateTime } from '@/lib/date-format'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import {
  Card,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle,
} from '@/components/ui/card'
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from '@/components/ui/table'
import { buildEmptyListQuery } from '@/components/work-records/list/types'
import { buildDashboardMetrics } from './dashboard-metrics'

const statusText = {
  draft: '草稿',
  processing: '处理中',
  done: '已完成',
  archived: '已归档',
} as const

export function Dashboard() {
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
  const metrics = buildDashboardMetrics({
    recordTotal: records.data?.total ?? 0,
    recentStatuses: records.data?.items.map((record) => record.status) ?? [],
    templateStatuses: templates.data?.map((template) => template.status) ?? [],
    enabledCalendarCount:
      calendars.data?.filter((calendar) => calendar.enabled).length ?? 0,
    workdayCount: workdays.data?.workdayCount ?? 0,
  })
  const isLoading =
    records.isLoading || templates.isLoading || calendars.isLoading
  const hasError = records.isError || templates.isError || calendars.isError

  const retry = () => {
    void records.refetch()
    void templates.refetch()
    void calendars.refetch()
    void workdays.refetch()
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
              title='模板管理'
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
        <div className='text-2xl font-bold'>{loading ? '—' : value}</div>
        <p className='mt-1 truncate text-xs text-muted-foreground'>
          {description}
        </p>
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
