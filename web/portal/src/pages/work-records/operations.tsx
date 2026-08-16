import { useMemo } from 'react'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { useNavigate, useSearch } from '@tanstack/react-router'
import { Activity, Boxes, ClipboardCheck, UsersRound } from 'lucide-react'
import {
  actOnApprovalTask,
  requestMonthlyAiReport,
  requestWeeklyAiReport,
  reviewAiGeneration,
  type AiGeneration,
} from '@/api/work-records/extensions'
import { useAuthStore } from '@/stores/auth-store'
import {
  formatDateTime,
  startOfIsoWeek,
  toCalendarDate,
  toLocalOffsetDateTime,
} from '@/lib/date-format'
import { useWorkRecordOperations } from '@/hooks/work-records/use-work-record-operations'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import {
  Card,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle,
} from '@/components/ui/card'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Skeleton } from '@/components/ui/skeleton'
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from '@/components/ui/table'
import { ErrorState } from '@/components/feedback/async-state'
import { PermissionGate } from '@/components/permission-gate'
import { AiGenerationNotice } from '@/components/work-records/runtime/ai-generation-notice'

function defaultDates() {
  const now = new Date()
  const start = new Date(now.getFullYear(), now.getMonth(), 1)
  return {
    from: toLocalOffsetDateTime(start),
    to: toLocalOffsetDateTime(now),
  }
}

export function WorkRecordOperationsPage() {
  const queryClient = useQueryClient()
  const navigate = useNavigate()
  const search = useSearch({ strict: false }) as { from?: string; to?: string }
  const defaults = useMemo(() => defaultDates(), [])
  const from = validDateOrDefault(search.from, defaults.from)
  const to = validDateOrDefault(search.to, defaults.to)
  const tenantId = useAuthStore(
    (state) => state.auth.principal?.tenantId ?? null
  )
  const queries = useWorkRecordOperations(from, to)
  const fromDate = toCalendarDate(from)
  const toDate = toCalendarDate(to)
  const weekStart = startOfIsoWeek(from)
  const month = fromDate.slice(0, 7)
  const generateWeekly = useMutation({
    mutationFn: () => requestWeeklyAiReport(weekStart),
    onSuccess: async () => {
      await queryClient.invalidateQueries({
        queryKey: ['work-record-weekly-ai', tenantId, weekStart],
      })
    },
  })
  const generateMonthly = useMutation({
    mutationFn: () => requestMonthlyAiReport(fromDate),
    onSuccess: async () => {
      await queryClient.invalidateQueries({
        queryKey: ['work-record-monthly-ai', tenantId, month],
      })
    },
  })
  const actOnApproval = useMutation({
    mutationFn: ({ id, approved }: { id: string; approved: boolean }) =>
      actOnApprovalTask(id, approved, approved ? '同意' : '拒绝'),
    onSuccess: async () => {
      await queryClient.invalidateQueries({
        queryKey: ['work-record-approval-tasks', tenantId],
      })
    },
  })
  const reviewReport = useMutation({
    mutationFn: ({ id, accepted }: { id: string; accepted: boolean }) =>
      reviewAiGeneration(id, accepted),
    onSuccess: async () => {
      await Promise.all([
        queryClient.invalidateQueries({
          queryKey: ['work-record-weekly-ai', tenantId, weekStart],
        }),
        queryClient.invalidateQueries({
          queryKey: ['work-record-monthly-ai', tenantId, month],
        }),
      ])
    },
  })
  const error = queries.statistics.error ?? queries.workload.error

  const changeDate = (key: 'from' | 'to', value: string) => {
    if (!value) return
    void navigate({
      to: '/work-records/operations',
      search: {
        ...search,
        [key]: toLocalOffsetDateTime(new Date(`${value}T00:00:00`)),
      },
    } as never)
  }

  if (error) {
    return (
      <main className='p-4 md:p-6'>
        <ErrorState error={error} />
      </main>
    )
  }

  const statistics = queries.statistics.data
  return (
    <main className='flex flex-col gap-6 p-4 md:p-6'>
      <header className='flex flex-col justify-between gap-4 md:flex-row md:items-end'>
        <div>
          <h1 className='text-2xl font-semibold tracking-tight'>
            工作记录运营中心
          </h1>
          <p className='mt-1 text-sm text-muted-foreground'>
            统计、工作量、交接与模板市场的统一视图。
          </p>
        </div>
        <div className='grid grid-cols-2 gap-3' aria-label='统计周期'>
          <div className='grid gap-1.5'>
            <Label htmlFor='analytics-from'>开始日期</Label>
            <Input
              id='analytics-from'
              type='date'
              value={fromDate}
              onChange={(event) => changeDate('from', event.target.value)}
            />
          </div>
          <div className='grid gap-1.5'>
            <Label htmlFor='analytics-to'>结束日期</Label>
            <Input
              id='analytics-to'
              type='date'
              value={toDate}
              onChange={(event) => changeDate('to', event.target.value)}
            />
          </div>
        </div>
      </header>

      <section
        className='grid gap-4 sm:grid-cols-2 xl:grid-cols-4'
        aria-label='核心指标'
      >
        <Metric
          icon={Activity}
          label='记录总数'
          value={statistics?.totalRecords}
          loading={queries.statistics.isLoading}
        />
        <Metric
          icon={ClipboardCheck}
          label='已完成'
          value={statistics?.completedRecords}
          loading={queries.statistics.isLoading}
        />
        <Metric
          icon={UsersRound}
          label='参与人员'
          value={statistics?.distinctOwners}
          loading={queries.statistics.isLoading}
        />
        <Metric
          icon={Boxes}
          label='工作日'
          value={queries.workload.data?.workdayCount}
          loading={queries.workload.isLoading}
        />
      </section>

      <section className='grid gap-4 xl:grid-cols-[minmax(0,2fr)_minmax(18rem,1fr)]'>
        <Card>
          <CardHeader>
            <CardTitle>工作量明细</CardTitle>
            <CardDescription>按负责人聚合记录与完成情况。</CardDescription>
          </CardHeader>
          <CardContent>
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>负责人</TableHead>
                  <TableHead className='text-right'>记录</TableHead>
                  <TableHead className='text-right'>完成</TableHead>
                  <TableHead className='text-right'>日均</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {queries.workload.data?.users.map((user) => (
                  <TableRow key={user.userId}>
                    <TableCell className='font-medium'>
                      {user.displayName}
                    </TableCell>
                    <TableCell className='text-right'>
                      {user.recordCount}
                    </TableCell>
                    <TableCell className='text-right'>
                      {user.completedCount}
                    </TableCell>
                    <TableCell className='text-right'>
                      {user.recordsPerWorkday.toFixed(1)}
                    </TableCell>
                  </TableRow>
                ))}
                {queries.workload.data?.users.length === 0 && (
                  <TableRow>
                    <TableCell
                      colSpan={4}
                      className='py-8 text-center text-muted-foreground'
                    >
                      当前周期暂无工作记录
                    </TableCell>
                  </TableRow>
                )}
              </TableBody>
            </Table>
          </CardContent>
        </Card>
        <div className='flex flex-col gap-4'>
          <Card>
            <CardHeader>
              <CardTitle>待交接</CardTitle>
              <CardDescription>与当前用户相关的最近交接。</CardDescription>
            </CardHeader>
            <CardContent className='flex flex-col gap-3'>
              {queries.handovers.data?.slice(0, 5).map((item) => (
                <div
                  key={item.id}
                  className='flex items-start justify-between gap-3 border-b border-border pb-3 last:border-0 last:pb-0'
                >
                  <div className='min-w-0'>
                    <p className='truncate text-sm font-medium'>
                      {item.summary}
                    </p>
                    <p className='mt-1 text-xs text-muted-foreground'>
                      {item.fromUserId} → {item.toUserId}
                    </p>
                  </div>
                  <Badge variant='secondary'>{item.status}</Badge>
                </div>
              ))}
              {queries.handovers.data?.length === 0 && (
                <p className='text-sm text-muted-foreground'>暂无交接任务</p>
              )}
            </CardContent>
          </Card>
          <Card>
            <CardHeader>
              <CardTitle>模板市场</CardTitle>
              <CardDescription>当前可见的已发布模板包。</CardDescription>
            </CardHeader>
            <CardContent className='flex flex-col gap-3'>
              {queries.market.data?.slice(0, 5).map((item) => (
                <div
                  key={item.id}
                  className='flex items-center justify-between gap-3'
                >
                  <span className='truncate text-sm font-medium'>
                    {item.name}
                  </span>
                  <Badge variant='outline'>v{item.versionNo}</Badge>
                </div>
              ))}
              {queries.market.data?.length === 0 && (
                <p className='text-sm text-muted-foreground'>暂无可用模板包</p>
              )}
            </CardContent>
          </Card>
        </div>
      </section>
      {queries.canReadPeriodReports && (
        <section className='grid gap-4 xl:grid-cols-2'>
          <ReportCard
            title='AI 周报'
            description={`基于 ${weekStart} 起一周的可见工作记录生成待审核草稿。`}
            actionLabel='生成周报'
            pending={generateWeekly.isPending}
            canGenerate={queries.canGeneratePeriodReports}
            reviewPending={reviewReport.isPending}
            generations={queries.weeklyReports.data}
            onGenerate={() => generateWeekly.mutate()}
            onReview={(id, accepted) => reviewReport.mutate({ id, accepted })}
          />
          <ReportCard
            title='AI 月报'
            description={`基于 ${month} 的可见工作记录生成待审核草稿。`}
            actionLabel='生成月报'
            pending={generateMonthly.isPending}
            canGenerate={queries.canGeneratePeriodReports}
            reviewPending={reviewReport.isPending}
            generations={queries.monthlyReports.data}
            onGenerate={() => generateMonthly.mutate()}
            onReview={(id, accepted) => reviewReport.mutate({ id, accepted })}
          />
        </section>
      )}
      <PermissionGate any={['work-record:approval:act']}>
        <Card>
          <CardHeader>
            <CardTitle>待我审批</CardTitle>
            <CardDescription>
              仅展示按用户、角色或记录负责人分配给你的任务。
            </CardDescription>
          </CardHeader>
          <CardContent className='space-y-3'>
            {queries.approvals.data?.map((task) => (
              <div
                key={task.id}
                className='flex flex-col justify-between gap-3 rounded-md border p-4 md:flex-row md:items-center'
              >
                <div className='min-w-0'>
                  <p className='truncate text-sm font-medium'>
                    {task.recordTitle}
                  </p>
                  <p className='mt-1 text-xs text-muted-foreground'>
                    {task.dueAt
                      ? `截止 ${formatDateTime(task.dueAt)}`
                      : '无截止时间'}
                  </p>
                </div>
                <div className='flex gap-2'>
                  <Button
                    size='sm'
                    disabled={actOnApproval.isPending}
                    onClick={() =>
                      actOnApproval.mutate({ id: task.id, approved: true })
                    }
                  >
                    同意
                  </Button>
                  <Button
                    size='sm'
                    variant='outline'
                    disabled={actOnApproval.isPending}
                    onClick={() =>
                      actOnApproval.mutate({ id: task.id, approved: false })
                    }
                  >
                    拒绝
                  </Button>
                </div>
              </div>
            ))}
            {queries.approvals.data?.length === 0 && (
              <p className='text-sm text-muted-foreground'>暂无待审批任务</p>
            )}
          </CardContent>
        </Card>
      </PermissionGate>
    </main>
  )
}

function validDateOrDefault(value: string | undefined, fallback: string) {
  if (!value) return fallback
  try {
    toCalendarDate(value)
    return value
  } catch {
    return fallback
  }
}

function ReportCard({
  title,
  description,
  actionLabel,
  pending,
  canGenerate,
  reviewPending,
  generations,
  onGenerate,
  onReview,
}: {
  title: string
  description: string
  actionLabel: string
  pending: boolean
  canGenerate: boolean
  reviewPending: boolean
  generations?: AiGeneration[]
  onGenerate: () => void
  onReview: (id: string, accepted: boolean) => void
}) {
  return (
    <Card>
      <CardHeader className='flex-row items-start justify-between gap-4'>
        <div>
          <CardTitle>{title}</CardTitle>
          <CardDescription>{description}</CardDescription>
        </div>
        {canGenerate && (
          <Button disabled={pending} onClick={onGenerate}>
            {pending ? '创建中…' : actionLabel}
          </Button>
        )}
      </CardHeader>
      <CardContent className='flex flex-col gap-3'>
        {generations?.map((item) => (
          <div key={item.id} className='rounded-md border p-4'>
            <div className='mb-2 flex items-center justify-between gap-3'>
              <span className='text-sm font-medium'>{item.resourceId}</span>
              <Badge variant='secondary'>{item.status}</Badge>
            </div>
            {item.outputMarkdown && (
              <pre className='font-sans text-sm leading-6 whitespace-pre-wrap'>
                {item.outputMarkdown}
              </pre>
            )}
            <AiGenerationNotice generation={item} />
            {item.status === 'success' && (
              <PermissionGate any={['work-record:ai:review']}>
                <div className='mt-3 flex gap-2'>
                  <Button
                    size='sm'
                    disabled={reviewPending}
                    onClick={() => onReview(item.id, true)}
                  >
                    采纳
                  </Button>
                  <Button
                    size='sm'
                    variant='outline'
                    disabled={reviewPending}
                    onClick={() => onReview(item.id, false)}
                  >
                    拒绝
                  </Button>
                </div>
              </PermissionGate>
            )}
          </div>
        ))}
        {generations?.length === 0 && (
          <p className='text-sm text-muted-foreground'>当前周期尚未生成报告</p>
        )}
      </CardContent>
    </Card>
  )
}

function Metric({
  icon: Icon,
  label,
  value,
  loading,
}: {
  icon: typeof Activity
  label: string
  value?: number
  loading: boolean
}) {
  return (
    <Card>
      <CardContent className='flex items-center justify-between p-5'>
        <div>
          <p className='text-sm text-muted-foreground'>{label}</p>
          {loading ? (
            <Skeleton className='mt-2 h-8 w-16' />
          ) : (
            <p className='mt-1 text-2xl font-semibold tabular-nums'>
              {value ?? 0}
            </p>
          )}
        </div>
        <Icon className='size-5 text-muted-foreground' aria-hidden='true' />
      </CardContent>
    </Card>
  )
}
