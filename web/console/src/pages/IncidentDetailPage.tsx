import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { useParams, useNavigate } from 'react-router-dom'

import {
  getIncidentBundle,
  collectIncidentEvidenceByCollector,
  analyzeIncidentRca,
  runIncidentAiDiagnosis,
  generateReport,
  type IncidentDetailBundle,
  type DiagnosisEvidenceRecord,
  type EvidenceCollectionTaskRecord,
  type RcaAnalysisResponse,
  type AiDiagnosisResponse,
  type IncidentReportRecord,
} from '@/api/client'
import { MarkdownPreview } from '@/components/console/MarkdownPreview'
import { Alert, AlertDescription } from '@/components/ui/alert'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Card, CardContent } from '@/components/ui/card'
import { Tabs, TabsList, TabsTrigger, TabsContent } from '@/components/ui/tabs'

const TABS = [
  { key: 'summary', label: 'Summary' },
  { key: 'alerts', label: 'Alerts' },
  { key: 'evidence', label: 'Evidence' },
  { key: 'rca', label: 'RCA' },
  { key: 'ai', label: 'AI Diagnosis' },
  { key: 'report', label: 'Report' },
] as const

function ListItems({ values, renderCode }: { values?: string[]; renderCode?: boolean }) {
  if (!values || values.length === 0) return null
  return (
    <ul className="ml-5 list-disc space-y-1">
      {values.map((v) => (
        <li key={v} className="text-sm">
          {renderCode ? <code className="text-xs">{v}</code> : v}
        </li>
      ))}
    </ul>
  )
}

function FieldGrid({ items }: { items: Array<[string, React.ReactNode]> }) {
  return (
    <div className="grid grid-cols-3 gap-3">
      {items.map(([label, value]) => (
        <div key={label} className="rounded-lg bg-muted/40 p-3">
          <p className="text-xs text-muted-foreground">{label}</p>
          <p className="mt-1 text-sm font-medium">{value || '-'}</p>
        </div>
      ))}
    </div>
  )
}

function SummaryTab({ bundle }: { bundle: IncidentDetailBundle }) {
  const inc = bundle.incident
  return (
    <div className="space-y-4">
      <FieldGrid
        items={[
          ['Incident ID', inc.id],
          [
            '状态',
            <Badge key="s" variant="outline">
              {inc.status}
            </Badge>,
          ],
          [
            '级别',
            <Badge key="l" variant={inc.severity === 'critical' ? 'destructive' : 'outline'}>
              {inc.severity}
            </Badge>,
          ],
          ['来源', inc.source],
          ['影响对象', inc.primaryAssetId],
          ['告警数', inc.alertCount ?? 0],
          [
            '聚合键',
            <code key="ak" className="text-xs">
              {inc.aggregationKey || '-'}
            </code>,
          ],
          ['开始时间', inc.startedAt],
          ['最后出现', inc.lastSeenAt],
          ['恢复时间', inc.resolvedAt],
        ]}
      />
    </div>
  )
}

function AlertsTab({ bundle }: { bundle: IncidentDetailBundle }) {
  if (bundle.alerts.length === 0) {
    return <p className="text-sm text-muted-foreground">暂无关联告警。</p>
  }
  return (
    <div className="space-y-2">
      {bundle.alerts.map((alert) => (
        <div key={alert.id} className="flex items-center gap-3 rounded-lg border p-3 text-sm">
          <Badge variant="outline">{alert.severity}</Badge>
          <span className="flex-1 truncate">{alert.title || alert.id}</span>
          <span className="text-xs text-muted-foreground">{alert.entityName || alert.assetId}</span>
          <span className="text-xs text-muted-foreground">{alert.startsAt}</span>
        </div>
      ))}
    </div>
  )
}

function EvidenceTab({
  evidence,
  tasks,
}: {
  evidence: DiagnosisEvidenceRecord[]
  tasks: EvidenceCollectionTaskRecord[]
}) {
  return (
    <div className="space-y-4">
      <div>
        <h4 className="mb-2 text-sm font-semibold">采集任务</h4>
        {tasks.length === 0 ? (
          <p className="text-sm text-muted-foreground">暂无采集任务。</p>
        ) : (
          <div className="space-y-2">
            {tasks.map((task) => (
              <div key={task.id} className="rounded-lg border p-3 text-sm">
                <div className="flex items-center justify-between gap-3">
                  <div>
                    <p className="font-medium">{task.collectorKey}</p>
                    <p className="text-xs text-muted-foreground">{task.createdAt}</p>
                  </div>
                  <Badge
                    variant={
                      task.status === 'completed'
                        ? 'default'
                        : task.status === 'failed'
                          ? 'destructive'
                          : 'outline'
                    }
                  >
                    {task.status}
                  </Badge>
                </div>

                {task.errorMessage && (
                  <p className="mt-2 text-xs text-destructive">{task.errorMessage}</p>
                )}
              </div>
            ))}
          </div>
        )}
      </div>

      <div>
        <h4 className="mb-2 text-sm font-semibold">证据列表</h4>
        {evidence.length === 0 ? (
          <p className="text-sm text-muted-foreground">暂无 Evidence。</p>
        ) : (
          <div className="space-y-3">
            {evidence.map((item) => (
              <div key={item.id} className="rounded-lg border p-4">
                <div className="flex items-start justify-between gap-2">
                  <h4 className="font-medium">{item.title || item.evidenceType}</h4>
                  <Badge variant="outline" className="shrink-0">
                    {item.evidenceType}
                  </Badge>
                </div>
                <p className="mt-1 text-sm text-muted-foreground">{item.summary || '-'}</p>
                <code className="mt-1 block text-xs text-muted-foreground">{item.evidenceKey}</code>
              </div>
            ))}
          </div>
        )}
      </div>
    </div>
  )
}

function RcaTab({ rca }: { rca?: RcaAnalysisResponse | null }) {
  if (!rca) return <p className="text-sm text-muted-foreground">暂无 RCA。</p>
  return (
    <div className="space-y-4">
      <FieldGrid
        items={[
          [
            '置信度',
            rca.confidence != null ? `${(Number(rca.confidence) * 100).toFixed(0)}%` : '-',
          ],
          ['模型版本', rca.modelVersion],
          ['创建时间', rca.createdAt],
        ]}
      />
      <div>
        <h4 className="text-sm font-semibold">根因判断</h4>
        <p className="mt-1 text-sm">{rca.suspectedRootCause || '-'}</p>
      </div>
      <div>
        <h4 className="text-sm font-semibold">摘要</h4>
        <p className="mt-1 text-sm">{rca.summary || '-'}</p>
      </div>
      <div>
        <h4 className="text-sm font-semibold">命中规则</h4>
        <ListItems values={rca.matchedRules} renderCode />
        {(!rca.matchedRules || rca.matchedRules.length === 0) && (
          <p className="mt-1 text-sm text-muted-foreground">-</p>
        )}
      </div>
      <div>
        <h4 className="text-sm font-semibold">证据引用</h4>
        <ListItems values={rca.evidenceRefs} renderCode />
        {(!rca.evidenceRefs || rca.evidenceRefs.length === 0) && (
          <p className="mt-1 text-sm text-muted-foreground">-</p>
        )}
      </div>
      <div>
        <h4 className="text-sm font-semibold">证据链</h4>
        {rca.evidence.length > 0 ? (
          <div className="mt-2 space-y-2">
            {rca.evidence.map((item, idx) => (
              <div key={idx} className="rounded-lg border p-3">
                <div className="flex items-center gap-2">
                  <code className="text-xs font-medium">{item.ruleId}</code>
                  <span className="text-xs text-muted-foreground">
                    Score: {(Number(item.score) * 100).toFixed(0)}%
                  </span>
                </div>
                <p className="mt-1 text-sm">{item.title}</p>
                <p className="mt-1 text-xs text-muted-foreground">{item.description}</p>
              </div>
            ))}
          </div>
        ) : (
          <p className="mt-1 text-sm text-muted-foreground">-</p>
        )}
      </div>
    </div>
  )
}

function AiTab({ diagnosis }: { diagnosis?: AiDiagnosisResponse | null }) {
  if (!diagnosis) return <p className="text-sm text-muted-foreground">暂无 AI Diagnosis。</p>
  return (
    <div className="space-y-4">
      <FieldGrid
        items={[
          ['Provider', diagnosis.provider],
          ['Model', diagnosis.model],
          ['Agent', diagnosis.agentName],
          ['状态', diagnosis.status],
          ['创建时间', diagnosis.createdAt],
        ]}
      />
      <div>
        <h4 className="text-sm font-semibold">AI 结论</h4>
        <p className="mt-1 text-sm">{diagnosis.summary || '-'}</p>
      </div>
      <div>
        <h4 className="text-sm font-semibold">疑似根因</h4>
        <p className="mt-1 text-sm">{diagnosis.rootCause || '-'}</p>
      </div>
      <div>
        <h4 className="text-sm font-semibold">影响范围</h4>
        <p className="mt-1 text-sm">{diagnosis.impact || '-'}</p>
      </div>
      <div>
        <h4 className="text-sm font-semibold">建议动作</h4>
        <ListItems values={diagnosis.nextSteps} />
      </div>
      <div>
        <h4 className="text-sm font-semibold">引用证据</h4>
        <ListItems values={diagnosis.evidenceRefs} renderCode />
      </div>
    </div>
  )
}

function ReportTab({ report }: { report?: IncidentReportRecord | null }) {
  if (!report) return <p className="text-sm text-muted-foreground">暂无报告。</p>
  return (
    <div className="space-y-4">
      <FieldGrid
        items={[
          ['报告 ID', report.id],
          ['版本', report.versionNo],
          ['格式', report.format],
          ['创建人', report.createdBy],
          ['创建时间', report.createdAt],
        ]}
      />
      <MarkdownPreview markdown={report.markdownContent} />
    </div>
  )
}

export function IncidentDetailPage() {
  const { incidentId } = useParams<{ incidentId: string }>()
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const [activeTab, setActiveTab] = useState<string>('summary')
  const [banner, setBanner] = useState<{ tone: 'success' | 'warning'; text: string } | null>(null)

  const query = useQuery({
    queryKey: ['incident-bundle', incidentId],
    queryFn: () => getIncidentBundle(incidentId!),
    enabled: Boolean(incidentId),
  })

  const collectMut = useMutation({
    mutationFn: () => collectIncidentEvidenceByCollector(incidentId!),
    onSuccess: (result) => {
      setBanner({ tone: 'success', text: result.message || 'Evidence 采集完成。' })
      void queryClient.invalidateQueries({ queryKey: ['incident-bundle', incidentId] })
    },
    onError: (err) => setBanner({ tone: 'warning', text: String(err) }),
  })

  const rcaMut = useMutation({
    mutationFn: () => analyzeIncidentRca(incidentId!, true),
    onSuccess: () => {
      setBanner({ tone: 'success', text: 'RCA 诊断完成。' })
      void queryClient.invalidateQueries({ queryKey: ['incident-bundle', incidentId] })
    },
    onError: (err) => setBanner({ tone: 'warning', text: String(err) }),
  })

  const aiMut = useMutation({
    mutationFn: () => runIncidentAiDiagnosis(incidentId!),
    onSuccess: () => {
      setBanner({ tone: 'success', text: 'AI Diagnosis 完成。' })
      void queryClient.invalidateQueries({ queryKey: ['incident-bundle', incidentId] })
    },
    onError: (err) => setBanner({ tone: 'warning', text: String(err) }),
  })

  const reportMut = useMutation({
    mutationFn: () => generateReport(incidentId!),
    onSuccess: () => {
      setBanner({ tone: 'success', text: 'Markdown 报告已生成。' })
      void queryClient.invalidateQueries({ queryKey: ['incident-bundle', incidentId] })
    },
    onError: (err) => setBanner({ tone: 'warning', text: String(err) }),
  })

  const isBusy = collectMut.isPending || rcaMut.isPending || aiMut.isPending || reportMut.isPending

  if (!incidentId) {
    return (
      <div className="flex min-h-screen items-center justify-center">
        <p>Incident 不存在。</p>
      </div>
    )
  }

  return (
    <div className="flex min-h-screen flex-col bg-muted/30">
      <header className="sticky top-0 z-40 border-b bg-background/80 backdrop-blur">
        <div className="mx-auto flex max-w-6xl items-center justify-between gap-4 px-6 py-3">
          <div>
            <button
              onClick={() => navigate('/incidents')}
              className="mb-1 text-xs text-muted-foreground hover:underline"
            >
              ← Incidents
            </button>
            <h1 className="text-base leading-none font-semibold">
              {query.data?.incident.title || 'Loading...'}
            </h1>
          </div>
          <Button size="sm" onClick={() => void query.refetch()} disabled={query.isFetching}>
            {query.isFetching ? '刷新中...' : '刷新'}
          </Button>
        </div>
      </header>

      <main className="mx-auto flex w-full max-w-6xl flex-1 flex-col gap-4 px-6 py-4">
        {banner && (
          <Alert variant={banner.tone === 'warning' ? 'destructive' : 'default'}>
            <AlertDescription>{banner.text}</AlertDescription>
          </Alert>
        )}

        {query.isError && (
          <Alert variant="destructive">
            <AlertDescription>加载失败: {String(query.error)}</AlertDescription>
          </Alert>
        )}

        {query.isLoading && (
          <Card>
            <CardContent className="flex h-40 items-center justify-center">
              <p className="text-sm text-muted-foreground">加载中...</p>
            </CardContent>
          </Card>
        )}

        {query.data && (
          <div className="space-y-4">
            {/* Action bar */}
            <Card>
              <CardContent className="flex flex-wrap items-center gap-2 pt-4">
                <Button size="sm" onClick={() => void collectMut.mutate()} disabled={isBusy}>
                  {collectMut.isPending ? '采集中...' : 'Collect Evidence'}
                </Button>

                <Button size="sm" onClick={() => void rcaMut.mutate()} disabled={isBusy}>
                  {rcaMut.isPending ? 'RCA 中...' : 'Run RCA'}
                </Button>

                <Button size="sm" onClick={() => void aiMut.mutate()} disabled={isBusy}>
                  {aiMut.isPending ? '诊断中...' : 'Run AI Diagnosis'}
                </Button>
                <Button size="sm" onClick={() => void reportMut.mutate()} disabled={isBusy}>
                  {reportMut.isPending ? '生成中...' : 'Generate Report'}
                </Button>
              </CardContent>
            </Card>

            {/* Incident header */}
            <Card>
              <CardContent className="pt-4 pb-3">
                <h2 className="text-lg font-semibold">
                  {query.data.incident.title || query.data.incident.id}
                </h2>
                <p className="mt-1 text-sm text-muted-foreground">
                  {query.data.incident.summary || query.data.incident.aggregationKey}
                </p>
              </CardContent>
              <CardContent className="pt-0">
                <div className="flex gap-2">
                  <Badge
                    variant={
                      query.data.incident.severity === 'critical' ? 'destructive' : 'outline'
                    }
                  >
                    {query.data.incident.severity}
                  </Badge>
                  <Badge variant="outline">{query.data.incident.status}</Badge>
                  <span className="flex items-center text-sm text-muted-foreground">
                    {query.data.incident.alertCount ?? 0} alerts
                  </span>
                </div>
              </CardContent>
            </Card>

            {/* Tabs */}
            <Tabs value={activeTab} onValueChange={setActiveTab}>
              <TabsList>
                {TABS.map((tab) => (
                  <TabsTrigger key={tab.key} value={tab.key}>
                    {tab.label}
                  </TabsTrigger>
                ))}
              </TabsList>

              <TabsContent value="summary">
                <Card>
                  <CardContent className="pt-4">
                    <SummaryTab bundle={query.data} />
                  </CardContent>
                </Card>
              </TabsContent>

              <TabsContent value="alerts">
                <Card>
                  <CardContent className="pt-4">
                    <AlertsTab bundle={query.data} />
                  </CardContent>
                </Card>
              </TabsContent>

              <TabsContent value="evidence">
                <Card>
                  <CardContent className="pt-4">
                    <EvidenceTab
                      evidence={query.data.evidence}
                      tasks={query.data.evidenceTasks ?? []}
                    />
                  </CardContent>
                </Card>
              </TabsContent>

              <TabsContent value="rca">
                <Card>
                  <CardContent className="pt-4">
                    <RcaTab rca={query.data.rca} />
                  </CardContent>
                </Card>
              </TabsContent>

              <TabsContent value="ai">
                <Card>
                  <CardContent className="pt-4">
                    <AiTab diagnosis={query.data.aiDiagnosis} />
                  </CardContent>
                </Card>
              </TabsContent>

              <TabsContent value="report">
                <Card>
                  <CardContent className="pt-4">
                    <ReportTab report={query.data.report} />
                  </CardContent>
                </Card>
              </TabsContent>
            </Tabs>
          </div>
        )}
      </main>
    </div>
  )
}
