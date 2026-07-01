import {
  ActivityIcon,
  BrainCircuitIcon,
  CheckCircle2Icon,
  ShieldAlertIcon,
  SparklesIcon,
} from 'lucide-react'

import type {
  AiDiagnosisResponse,
  IncidentAlertRecord,
  IncidentDetailRecord,
  IncidentTimelineRecord,
  RcaAnalysisResponse,
} from '@/api/client'

import { SeverityBadge, StatusBadge } from '@/components/console/status-badge'
import { Alert, AlertDescription, AlertTitle } from '@/components/ui/alert'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { Empty, EmptyDescription, EmptyMedia, EmptyTitle } from '@/components/ui/empty'
import { Separator } from '@/components/ui/separator'
import { Skeleton } from '@/components/ui/skeleton'
import { Spinner } from '@/components/ui/spinner'

export function IncidentDetailCard({
  detail,
  isLoading,
  error,
  rcaResult,
  aiDiagnosis,
  isResolving,
  isAnalyzing,
  isAiDiagnosing,
  onResolve,
  onAnalyze,
  onAiDiagnose,
}: {
  detail: IncidentDetailRecord | undefined
  isLoading: boolean
  error: Error | null
  rcaResult: RcaAnalysisResponse | null
  aiDiagnosis: AiDiagnosisResponse | null
  isResolving: boolean
  isAnalyzing: boolean
  isAiDiagnosing: boolean
  onResolve: (id: string) => void
  onAnalyze: (id: string) => void
  onAiDiagnose: (id: string) => void
}) {
  return (
    <Card>
      <CardHeader>
        <div className="flex items-center gap-2">
          <ShieldAlertIcon className="size-4 text-muted-foreground" />
          <CardTitle>Incident Detail</CardTitle>
        </div>
        <CardDescription>查看告警、证据链、时间线并触发 RCA 分析。</CardDescription>
      </CardHeader>
      <CardContent className="flex flex-col gap-6">
        {!detail && !isLoading && !error && (
          <Empty className="border">
            <EmptyMedia variant="icon">
              <ShieldAlertIcon />
            </EmptyMedia>
            <EmptyTitle>选择左侧 Incident</EmptyTitle>
            <EmptyDescription>选中事故后会加载告警、证据链与时间线。</EmptyDescription>
          </Empty>
        )}

        {isLoading && (
          <div className="flex flex-col gap-3">
            <Skeleton className="h-6 w-2/3" />
            <Skeleton className="h-4 w-1/3" />
            <Skeleton className="h-20 w-full" />
          </div>
        )}

        {error && (
          <Alert variant="destructive">
            <ShieldAlertIcon data-icon="inline-start" />
            <AlertTitle>无法加载 Incident</AlertTitle>
            <AlertDescription>{error?.message ?? 'Unknown error'}</AlertDescription>
          </Alert>
        )}

        {detail && (
          <>
            <section className="flex flex-col gap-3">
              <div className="flex flex-wrap items-center justify-between gap-2">
                <h3 className="font-heading text-base leading-snug font-semibold">
                  {detail.incident.title}
                </h3>
                <div className="flex items-center gap-2">
                  <SeverityBadge severity={detail.incident.severity} />
                  <StatusBadge status={detail.incident.status} />
                </div>
              </div>
              {detail.incident.summary && (
                <p className="text-sm text-muted-foreground">{detail.incident.summary}</p>
              )}
              <div className="flex flex-wrap gap-2">
                <Button
                  size="sm"
                  disabled={isResolving}
                  onClick={() => onResolve(detail.incident.id)}
                >
                  {isResolving ? (
                    <>
                      <Spinner data-icon="inline-start" />
                      Resolving
                    </>
                  ) : (
                    <>
                      <CheckCircle2Icon data-icon="inline-start" />
                      Resolve
                    </>
                  )}
                </Button>
                <Button
                  size="sm"
                  variant="outline"
                  disabled={isAnalyzing}
                  onClick={() => onAnalyze(detail.incident.id)}
                >
                  {isAnalyzing ? (
                    <>
                      <Spinner data-icon="inline-start" />
                      Analyzing
                    </>
                  ) : (
                    <>
                      <BrainCircuitIcon data-icon="inline-start" />
                      Analyze RCA
                    </>
                  )}
                </Button>
                <Button
                  size="sm"
                  variant="outline"
                  disabled={isAiDiagnosing}
                  onClick={() => onAiDiagnose(detail.incident.id)}
                >
                  {isAiDiagnosing ? (
                    <>
                      <Spinner data-icon="inline-start" />
                      Diagnosing
                    </>
                  ) : (
                    <>
                      <SparklesIcon data-icon="inline-start" />
                      AI Diagnose
                    </>
                  )}
                </Button>
              </div>
            </section>

            {rcaResult && (
              <section className="flex flex-col gap-3 rounded-lg border border-primary/30 bg-primary/5 p-3">
                <div className="flex items-center gap-2 text-sm font-semibold">
                  <SparklesIcon className="size-4 text-primary" />
                  RCA Result
                </div>
                <div>
                  <p className="text-sm font-medium">{rcaResult.suspectedRootCause}</p>
                  <p className="mt-0.5 text-xs text-muted-foreground">
                    confidence {rcaResult.confidence} · model {rcaResult.modelVersion}
                  </p>
                </div>
                <p className="text-sm text-foreground/80">{rcaResult.summary}</p>
                <Separator />
                <ul className="flex flex-col gap-2">
                  {rcaResult.evidence.map((item, index) => (
                    <li
                      key={`${item.ruleId}-${index}`}
                      className="rounded-md border bg-card p-2.5 text-xs"
                    >
                      <p className="font-medium">{item.title}</p>
                      <p className="text-muted-foreground">
                        {item.ruleId} · score {item.score} · confidence {item.confidence}
                      </p>
                      <p className="mt-1 text-foreground/80">{item.description}</p>
                    </li>
                  ))}
                </ul>
              </section>
            )}

            {aiDiagnosis && (
              <section className="flex flex-col gap-3 rounded-lg border border-purple-200 bg-purple-50 p-3">
                <div className="flex items-center gap-2 text-sm font-semibold text-purple-700">
                  <SparklesIcon className="size-4" />
                  AI Diagnosis Agent
                </div>
                <p className="text-sm text-foreground/90">{aiDiagnosis.summary}</p>
                <div>
                  <p className="text-xs font-semibold text-muted-foreground">Root cause</p>
                  <p className="text-sm">{aiDiagnosis.rootCause}</p>
                </div>
                <div>
                  <p className="text-xs font-semibold text-muted-foreground">Impact</p>
                  <p className="text-sm">{aiDiagnosis.impact}</p>
                </div>
                <p className="text-xs text-muted-foreground">
                  {aiDiagnosis.provider} · {aiDiagnosis.model} · {aiDiagnosis.agentName} ·{' '}
                  {aiDiagnosis.createdAt}
                </p>
                <Separator />
                <div className="flex flex-col gap-2">
                  <h4 className="text-xs font-semibold text-muted-foreground">Next Steps</h4>
                  {aiDiagnosis.nextSteps.map((step, index) => (
                    <div
                      className="rounded-md border bg-card p-2.5 text-xs"
                      key={`ai-step-${index}`}
                    >
                      {index + 1}. {step}
                    </div>
                  ))}
                </div>
                {aiDiagnosis.runbookSuggestions.length > 0 && (
                  <div className="flex flex-col gap-2">
                    <h4 className="text-xs font-semibold text-muted-foreground">
                      Runbook Suggestions
                    </h4>
                    {aiDiagnosis.runbookSuggestions.map((item, index) => (
                      <div
                        className="rounded-md border bg-card p-2.5 text-xs"
                        key={`ai-runbook-${index}`}
                      >
                        {item}
                      </div>
                    ))}
                  </div>
                )}
                {aiDiagnosis.risks.length > 0 && (
                  <div className="flex flex-col gap-2">
                    <h4 className="text-xs font-semibold text-amber-600">Risks</h4>
                    {aiDiagnosis.risks.map((item, index) => (
                      <div
                        className="rounded-md border border-amber-200 bg-amber-50 p-2.5 text-xs"
                        key={`ai-risk-${index}`}
                      >
                        {item}
                      </div>
                    ))}
                  </div>
                )}
              </section>
            )}

            <LinkedAlertsList alerts={detail.alerts} />
            <TimelineList timeline={detail.timeline} />
          </>
        )}
      </CardContent>
    </Card>
  )
}

function LinkedAlertsList({ alerts }: { alerts: IncidentAlertRecord[] }) {
  return (
    <section className="flex flex-col gap-2">
      <h4 className="flex items-center gap-2 text-sm font-semibold">
        <ShieldAlertIcon className="size-3.5 text-muted-foreground" />
        Linked Alerts
      </h4>
      {alerts.length === 0 ? (
        <Empty className="border">
          <EmptyMedia variant="icon">
            <ShieldAlertIcon />
          </EmptyMedia>
          <EmptyTitle>暂无关联告警</EmptyTitle>
        </Empty>
      ) : (
        <ul className="flex flex-col gap-2">
          {alerts.map((alert) => (
            <li key={alert.id} className="rounded-md border p-2.5 text-xs">
              <div className="flex items-center justify-between gap-2">
                <span className="font-medium">{alert.title}</span>
                <SeverityBadge severity={alert.severity} />
              </div>
              <p className="text-muted-foreground">
                {alert.relationType} · {alert.status} · {alert.startsAt}
              </p>
            </li>
          ))}
        </ul>
      )}
    </section>
  )
}

function TimelineList({ timeline }: { timeline: IncidentTimelineRecord[] }) {
  return (
    <section className="flex flex-col gap-2">
      <h4 className="flex items-center gap-2 text-sm font-semibold">
        <ActivityIcon className="size-3.5 text-muted-foreground" />
        Timeline
      </h4>
      {timeline.length === 0 ? (
        <Empty className="border">
          <EmptyMedia variant="icon">
            <ActivityIcon />
          </EmptyMedia>
          <EmptyTitle>暂无时间线</EmptyTitle>
        </Empty>
      ) : (
        <ol className="flex flex-col gap-2">
          {timeline.map((item) => (
            <li key={item.id} className="rounded-md border p-2.5 text-xs">
              <div className="flex items-center justify-between gap-2">
                <span className="font-medium">{item.title}</span>
                <span className="text-muted-foreground">
                  {item.eventType} · {item.eventTime}
                </span>
              </div>
              {item.description && <p className="mt-1 text-foreground/80">{item.description}</p>}
            </li>
          ))}
        </ol>
      )}
    </section>
  )
}
