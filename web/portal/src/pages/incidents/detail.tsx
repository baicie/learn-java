import { Link } from '@tanstack/react-router'
import {
  AlertTriangle,
  ArrowLeft,
  BrainCircuit,
  CheckCircle2,
  CircleDot,
  FileText,
  Fingerprint,
  GitBranch,
  ListChecks,
  RefreshCw,
  ShieldAlert,
  Siren,
  Timer,
} from 'lucide-react'
import { toast } from 'sonner'
import { formatDateTime } from '@/lib/date-format'
import type {
  AiDiagnosis,
  Incident,
  IncidentAlert,
  IncidentEvidence,
  IncidentReport,
  IncidentTimeline,
  RcaAnalysis,
} from '@/lib/operations/operations'
import {
  useAnalyzeIncidentRca,
  useCloseIncident,
  useDiagnoseIncident,
  useGenerateIncidentReport,
  useIncidentOperationsDetail,
  useResolveIncident,
} from '@/hooks/operations/use-operations'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import {
  Card,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle,
} from '@/components/ui/card'
import { Separator } from '@/components/ui/separator'
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/components/ui/tabs'
import {
  AlertSeverityBadge,
  AlertStatusBadge,
} from '@/components/alerts/alert-badges'
import {
  EmptyState,
  ErrorState,
  PageLoadingState,
  TableLoadingState,
} from '@/components/feedback/async-state'
import { useConfirm } from '@/components/feedback/confirm-provider'
import {
  IncidentSeverityBadge,
  IncidentStatusBadge,
} from '@/components/incidents/list/incident-state'
import { Header } from '@/components/layout/header'
import { Main } from '@/components/layout/main'
import { PermissionGate } from '@/components/permission-gate'
import { ProfileDropdown } from '@/components/profile-dropdown'
import { Search } from '@/components/search'
import { ThemeSwitch } from '@/components/theme-switch'

export function IncidentDetailPage({ incidentId }: { incidentId: string }) {
  const operations = useIncidentOperationsDetail(incidentId)
  const analyze = useAnalyzeIncidentRca(incidentId)
  const diagnose = useDiagnoseIncident(incidentId)
  const generateReport = useGenerateIncidentReport(incidentId)
  const resolve = useResolveIncident(incidentId)
  const close = useCloseIncident(incidentId)
  const confirm = useConfirm()

  if (operations.detail.isPending) return <PageLoadingState rows={10} />

  if (operations.detail.isError || !operations.detail.data) {
    return (
      <ErrorState
        error={operations.detail.error}
        onRetry={() => void operations.detail.refetch()}
      />
    )
  }

  const { incident, alerts, timeline } = operations.detail.data
  const canResolve = !['resolved', 'closed', 'ignored'].includes(
    incident.status
  )
  const canClose = incident.status === 'resolved'

  const resolveIncident = async () => {
    const accepted = await confirm({
      title: '确认解决 Incident',
      description: '解决后 Incident 会停止计入待处理统计，但仍可在详情中复盘。',
      confirmText: '确认解决',
      variant: 'warning',
    })
    if (!accepted) return
    resolve.mutate(undefined, {
      onSuccess: () => toast.success('Incident 已标记为已解决'),
    })
  }

  const closeIncident = async () => {
    const accepted = await confirm({
      title: '确认关闭 Incident',
      description: '关闭表示处置与复盘均已完成，后续只读保留历史记录。',
      confirmText: '确认关闭',
      variant: 'destructive',
    })
    if (!accepted) return
    close.mutate(undefined, {
      onSuccess: () => toast.success('Incident 已关闭'),
    })
  }

  return (
    <>
      <Header fixed>
        <div className='ml-auto flex items-center gap-2'>
          <Search />
          <ThemeSwitch />
          <ProfileDropdown />
        </div>
      </Header>
      <Main className='grid gap-6'>
        <IncidentHeader
          incident={incident}
          onAnalyze={() =>
            analyze.mutate(
              { force: true },
              { onSuccess: () => toast.success('RCA 分析已完成') }
            )
          }
          onDiagnose={() =>
            diagnose.mutate(
              { locale: 'zh-CN' },
              { onSuccess: () => toast.success('AI 诊断已完成') }
            )
          }
          onGenerateReport={() =>
            generateReport.mutate(
              {},
              { onSuccess: () => toast.success('复盘报告已生成') }
            )
          }
          onResolve={() => void resolveIncident()}
          onClose={() => void closeIncident()}
          canResolve={canResolve}
          canClose={canClose}
          pending={
            analyze.isPending ||
            diagnose.isPending ||
            generateReport.isPending ||
            resolve.isPending ||
            close.isPending
          }
        />

        <Tabs defaultValue='overview' className='min-w-0'>
          <TabsList
            aria-label='Incident 工作区视图'
            className='flex w-full justify-start overflow-x-auto'
          >
            <TabsTrigger value='overview'>概览</TabsTrigger>
            <TabsTrigger value='timeline'>时间线</TabsTrigger>
            <TabsTrigger value='diagnosis'>AI 诊断</TabsTrigger>
            <TabsTrigger value='runbook' disabled>
              Runbook
            </TabsTrigger>
            <TabsTrigger value='automation' disabled>
              自动化日志
            </TabsTrigger>
            <TabsTrigger value='postmortem'>复盘</TabsTrigger>
          </TabsList>

          <TabsContent value='overview' className='mt-4'>
            <div className='grid min-w-0 gap-4 xl:grid-cols-[minmax(220px,280px)_minmax(0,1fr)_minmax(280px,360px)]'>
              <TimelinePanel timeline={timeline} />

              <div className='grid min-w-0 content-start gap-4'>
                <IncidentOverview incident={incident} />
                <RelatedAlerts alerts={alerts} />
                <RcaPanel query={operations.rca} />
                <AiDiagnosisPanel query={operations.aiDiagnosis} />
              </div>

              <div className='grid content-start gap-4'>
                <EvidencePanel query={operations.evidence} />
                <ReportPanel query={operations.report} />
              </div>
            </div>
          </TabsContent>

          <TabsContent value='timeline' className='mt-4'>
            <TimelinePanel timeline={timeline} />
          </TabsContent>

          <TabsContent value='diagnosis' className='mt-4'>
            <div className='grid min-w-0 gap-4 xl:grid-cols-3'>
              <RcaPanel query={operations.rca} />
              <AiDiagnosisPanel query={operations.aiDiagnosis} />
              <EvidencePanel query={operations.evidence} />
            </div>
          </TabsContent>

          <TabsContent value='postmortem' className='mt-4'>
            <ReportPanel query={operations.report} />
          </TabsContent>
        </Tabs>
      </Main>
    </>
  )
}

function IncidentHeader({
  incident,
  onAnalyze,
  onDiagnose,
  onGenerateReport,
  onResolve,
  onClose,
  canResolve,
  canClose,
  pending,
}: {
  incident: Incident
  onAnalyze: () => void
  onDiagnose: () => void
  onGenerateReport: () => void
  onResolve: () => void
  onClose: () => void
  canResolve: boolean
  canClose: boolean
  pending: boolean
}) {
  return (
    <header className='grid gap-4 rounded-lg border bg-card p-4 sm:p-5'>
      <div className='flex flex-col gap-4 lg:flex-row lg:items-start lg:justify-between'>
        <div className='min-w-0'>
          <Button variant='ghost' size='sm' asChild className='mb-2 -ml-3'>
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
              <ArrowLeft />
              返回 Incident 列表
            </Link>
          </Button>
          <div className='flex flex-wrap items-center gap-2'>
            <h1 className='text-xl font-semibold break-words md:text-2xl'>
              {incident.title}
            </h1>
            <IncidentSeverityBadge severity={incident.severity} />
            <IncidentStatusBadge status={incident.status} />
          </div>
          <p className='mt-1 text-sm [overflow-wrap:anywhere] text-muted-foreground'>
            {incident.summary || '暂无摘要'} · {incident.id}
          </p>
        </div>

        <div className='flex shrink-0 flex-wrap gap-2'>
          <PermissionGate any={['incident:diagnose']}>
            <Button
              variant='outline'
              size='sm'
              disabled={pending}
              onClick={onAnalyze}
            >
              <GitBranch />
              RCA 分析
            </Button>
            <Button
              variant='outline'
              size='sm'
              disabled={pending}
              onClick={onDiagnose}
            >
              <BrainCircuit />
              AI 诊断
            </Button>
          </PermissionGate>
          <PermissionGate any={['incident:write']}>
            <Button
              variant='outline'
              size='sm'
              disabled={pending}
              onClick={onGenerateReport}
            >
              <FileText />
              生成报告
            </Button>
            {canResolve ? (
              <Button
                variant='outline'
                size='sm'
                disabled={pending}
                onClick={onResolve}
              >
                <CheckCircle2 />
                解决
              </Button>
            ) : null}
            {canClose ? (
              <Button
                variant='destructive'
                size='sm'
                disabled={pending}
                onClick={onClose}
              >
                <ShieldAlert />
                关闭
              </Button>
            ) : null}
          </PermissionGate>
        </div>
      </div>

      <div className='grid gap-3 text-sm sm:grid-cols-2 xl:grid-cols-4'>
        <HeaderValue icon={Siren} label='来源' value={incident.source} />
        <HeaderValue
          icon={ListChecks}
          label='关联告警'
          value={`${incident.alertCount} 条`}
        />
        <HeaderValue
          icon={Timer}
          label='开始时间'
          value={formatDateTime(incident.startedAt)}
        />
        <HeaderValue
          icon={RefreshCw}
          label='最近更新'
          value={formatDateTime(incident.updatedAt)}
        />
      </div>
    </header>
  )
}

function HeaderValue({
  icon: Icon,
  label,
  value,
}: {
  icon: typeof Siren
  label: string
  value: string
}) {
  return (
    <div className='flex min-w-0 items-center gap-2 rounded-md border p-3'>
      <Icon className='size-4 shrink-0 text-muted-foreground' />
      <div className='min-w-0'>
        <p className='text-xs text-muted-foreground'>{label}</p>
        <p className='truncate font-medium'>{value}</p>
      </div>
    </div>
  )
}

function TimelinePanel({ timeline }: { timeline: IncidentTimeline[] }) {
  return (
    <Card>
      <CardHeader>
        <CardTitle className='flex items-center gap-2'>
          <CircleDot className='size-4' />
          时间线
        </CardTitle>
        <CardDescription>按时间排序的 Incident 证据事件。</CardDescription>
      </CardHeader>
      <CardContent>
        {!timeline.length ? (
          <EmptyState compact title='暂无时间线事件' />
        ) : (
          <ol className='grid gap-4'>
            {timeline.map((event) => (
              <li key={event.id} className='relative grid gap-1 border-l pl-4'>
                <span className='absolute top-1 -left-[5px] size-2 rounded-full bg-primary' />
                <span className='text-xs text-muted-foreground'>
                  {formatDateTime(event.eventTime)}
                </span>
                <span className='font-medium'>{event.title}</span>
                {event.description ? (
                  <span className='text-sm text-muted-foreground'>
                    {event.description}
                  </span>
                ) : null}
                <Badge variant='outline' className='mt-1'>
                  {event.eventType}
                </Badge>
              </li>
            ))}
          </ol>
        )}
      </CardContent>
    </Card>
  )
}

function IncidentOverview({ incident }: { incident: Incident }) {
  return (
    <Card>
      <CardHeader>
        <CardTitle>Incident 概览</CardTitle>
        <CardDescription>核心聚合信息与租户边界。</CardDescription>
      </CardHeader>
      <CardContent className='grid gap-3 sm:grid-cols-2'>
        <Value label='租户' value={incident.tenantId} />
        <Value label='主资产' value={incident.primaryAssetId || '未关联'} />
        <Value
          label='聚合键'
          value={incident.aggregationKey || '未生成'}
          mono
        />
        <Value label='检测时间' value={formatDateTime(incident.detectedAt)} />
        <Value label='最后发现' value={formatDateTime(incident.lastSeenAt)} />
        <Value label='解决时间' value={formatDateTime(incident.resolvedAt)} />
      </CardContent>
    </Card>
  )
}

function RelatedAlerts({ alerts }: { alerts: IncidentAlert[] }) {
  return (
    <Card>
      <CardHeader>
        <CardTitle className='flex items-center gap-2'>
          <AlertTriangle className='size-4' />
          关联告警
        </CardTitle>
        <CardDescription>已归一化并参与聚合的告警事件。</CardDescription>
      </CardHeader>
      <CardContent>
        {!alerts.length ? (
          <EmptyState compact title='暂无关联告警' />
        ) : (
          <div className='grid gap-3'>
            {alerts.map((alert) => (
              <div key={alert.id} className='grid gap-2 rounded-md border p-3'>
                <div className='flex flex-wrap items-start justify-between gap-2'>
                  <span className='font-medium'>{alert.title}</span>
                  <div className='flex shrink-0 gap-2'>
                    <AlertSeverityBadge severity={alert.severity} />
                    <AlertStatusBadge status={alert.status} />
                  </div>
                </div>
                <div className='flex flex-wrap gap-x-3 gap-y-1 text-xs text-muted-foreground'>
                  <span>{alert.source}</span>
                  <span>
                    {alert.entityName || alert.assetId || '未关联资产'}
                  </span>
                  <span>{formatDateTime(alert.startsAt)}</span>
                </div>
                <div className='flex min-w-0 items-center gap-2 overflow-hidden text-xs text-muted-foreground'>
                  <Fingerprint className='size-3 shrink-0' />
                  <span className='truncate font-mono'>
                    {alert.fingerprint}
                  </span>
                </div>
              </div>
            ))}
          </div>
        )}
      </CardContent>
    </Card>
  )
}

function RcaPanel({ query }: { query: QueryState<RcaAnalysis | null> }) {
  return (
    <Card>
      <CardHeader>
        <CardTitle className='flex items-center gap-2'>
          <GitBranch className='size-4' />
          RCA 证据分析
        </CardTitle>
        <CardDescription>规则命中、根因假设与置信度。</CardDescription>
      </CardHeader>
      <CardContent>
        {query.isPending ? (
          <TableLoadingState rows={3} columns={1} />
        ) : query.isError ? (
          <ErrorState
            compact
            error={query.error}
            onRetry={() => void query.refetch()}
          />
        ) : !query.data ? (
          <EmptyState
            compact
            title='暂无 RCA 结果'
            description='运行 RCA 分析后会在这里展示证据。'
          />
        ) : (
          <RcaContent rca={query.data} />
        )}
      </CardContent>
    </Card>
  )
}

function RcaContent({ rca }: { rca: RcaAnalysis }) {
  return (
    <div className='grid gap-3'>
      <div className='flex flex-wrap items-center gap-2'>
        <Badge>{rca.status}</Badge>
        <Badge variant='outline'>
          置信度 {formatConfidence(rca.confidence)}
        </Badge>
      </div>
      <p className='font-medium'>{rca.suspectedRootCause}</p>
      {rca.summary ? (
        <p className='text-sm text-muted-foreground'>{rca.summary}</p>
      ) : null}
      {rca.matchedRules.length ? (
        <div className='flex flex-wrap gap-2'>
          {rca.matchedRules.map((rule) => (
            <Badge key={rule} variant='secondary'>
              {rule}
            </Badge>
          ))}
        </div>
      ) : null}
      <Value label='模型版本' value={rca.modelVersion} mono />
    </div>
  )
}

function AiDiagnosisPanel({
  query,
}: {
  query: QueryState<AiDiagnosis | null>
}) {
  return (
    <Card>
      <CardHeader>
        <CardTitle className='flex items-center gap-2'>
          <BrainCircuit className='size-4' />
          AI Diagnosis
        </CardTitle>
        <CardDescription>基于实际证据生成的诊断摘要。</CardDescription>
      </CardHeader>
      <CardContent>
        {query.isPending ? (
          <TableLoadingState rows={4} columns={1} />
        ) : query.isError ? (
          <ErrorState
            compact
            error={query.error}
            onRetry={() => void query.refetch()}
          />
        ) : !query.data ? (
          <EmptyState
            compact
            title='暂无 AI 诊断'
            description='触发诊断后会在这里展示结构化结果。'
          />
        ) : (
          <AiContent diagnosis={query.data} />
        )}
      </CardContent>
    </Card>
  )
}

function AiContent({ diagnosis }: { diagnosis: AiDiagnosis }) {
  return (
    <div className='grid gap-3'>
      <div className='flex flex-wrap gap-2'>
        <Badge>{diagnosis.status}</Badge>
        <Badge variant='outline'>{diagnosis.provider}</Badge>
        <Badge variant='outline'>{diagnosis.model}</Badge>
      </div>
      <p className='font-medium'>{diagnosis.summary}</p>
      <Value label='疑似根因' value={diagnosis.rootCause} />
      <Value label='影响' value={diagnosis.impact} />
      <ListBlock title='下一步' items={diagnosis.nextSteps} />
      <ListBlock title='Runbook 建议' items={diagnosis.runbookSuggestions} />
      <ListBlock title='风险提示' items={diagnosis.risks} />
    </div>
  )
}

function EvidencePanel({ query }: { query: QueryState<IncidentEvidence[]> }) {
  return (
    <Card>
      <CardHeader>
        <CardTitle className='flex items-center gap-2'>
          <ShieldAlert className='size-4' />
          Evidence
        </CardTitle>
        <CardDescription>采集到的指标、日志与外部事件证据。</CardDescription>
      </CardHeader>
      <CardContent>
        {query.isPending ? (
          <TableLoadingState rows={4} columns={1} />
        ) : query.isError ? (
          <ErrorState
            compact
            error={query.error}
            onRetry={() => void query.refetch()}
          />
        ) : !query.data?.length ? (
          <EmptyState compact title='暂无 Evidence' />
        ) : (
          <div className='grid gap-3'>
            {query.data.map((item) => (
              <EvidenceItem key={item.id} evidence={item} />
            ))}
          </div>
        )}
      </CardContent>
    </Card>
  )
}

function EvidenceItem({ evidence }: { evidence: IncidentEvidence }) {
  return (
    <div className='grid gap-2 rounded-md border p-3'>
      <div className='flex items-start justify-between gap-2'>
        <span className='font-medium'>{evidence.title}</span>
        <Badge variant='outline'>{formatConfidence(evidence.confidence)}</Badge>
      </div>
      <p className='text-sm text-muted-foreground'>{evidence.summary}</p>
      <div className='flex flex-wrap gap-x-3 gap-y-1 text-xs text-muted-foreground'>
        <span>{evidence.source}</span>
        <span>{evidence.evidenceType}</span>
      </div>
      <span className='truncate font-mono text-xs text-muted-foreground'>
        {evidence.evidenceKey}
      </span>
    </div>
  )
}

function ReportPanel({ query }: { query: QueryState<IncidentReport | null> }) {
  return (
    <Card>
      <CardHeader>
        <CardTitle className='flex items-center gap-2'>
          <FileText className='size-4' />
          Report
        </CardTitle>
        <CardDescription>Incident 复盘报告的最新版本。</CardDescription>
      </CardHeader>
      <CardContent>
        {query.isPending ? (
          <TableLoadingState rows={5} columns={1} />
        ) : query.isError ? (
          <ErrorState
            compact
            error={query.error}
            onRetry={() => void query.refetch()}
          />
        ) : !query.data ? (
          <EmptyState compact title='暂无 Report' />
        ) : (
          <ReportContent report={query.data} />
        )}
      </CardContent>
    </Card>
  )
}

function ReportContent({ report }: { report: IncidentReport }) {
  return (
    <div className='grid gap-3'>
      <div className='flex flex-wrap items-center gap-2'>
        <Badge variant='outline'>v{report.versionNo}</Badge>
        <span className='text-xs text-muted-foreground'>
          {formatDateTime(report.updatedAt)}
        </span>
      </div>
      <h3 className='font-medium'>{report.title}</h3>
      <Separator />
      <pre className='max-h-96 overflow-auto text-sm leading-6 break-words whitespace-pre-wrap'>
        {report.markdownContent}
      </pre>
    </div>
  )
}

function ListBlock({ title, items }: { title: string; items: string[] }) {
  if (!items.length) return null
  return (
    <div className='grid gap-1'>
      <p className='text-sm font-medium'>{title}</p>
      <ul className='grid gap-1 text-sm text-muted-foreground'>
        {items.map((item) => (
          <li key={item} className='flex gap-2'>
            <span aria-hidden='true'>•</span>
            <span>{item}</span>
          </li>
        ))}
      </ul>
    </div>
  )
}

function Value({
  label,
  value,
  mono = false,
}: {
  label: string
  value: string
  mono?: boolean
}) {
  return (
    <div className='min-w-0'>
      <p className='text-xs text-muted-foreground'>{label}</p>
      <p
        className={
          mono
            ? 'font-mono text-sm [overflow-wrap:anywhere]'
            : 'text-sm break-words'
        }
      >
        {value || '—'}
      </p>
    </div>
  )
}

type QueryState<T> = {
  data: T | undefined
  error: unknown
  isError: boolean
  isPending: boolean
  refetch: () => unknown
}

function formatConfidence(value: number) {
  return `${Math.round(value * 100)}%`
}
