import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { CircleCheckIcon, InfoIcon, TriangleAlertIcon } from 'lucide-react'
import { useState } from 'react'

import type { AiDiagnosisResponse, RcaAnalysisResponse } from '@/api/client'

import {
  aggregateIncidents,
  analyzeIncidentRca,
  createZabbixDataSource,
  diagnoseIncidentAi,
  getIncident,
  resolveIncident,
  syncDataSource,
  testDataSource,
} from '@/api/client'
import { useAuth } from '@/auth/AuthContext'
import { AlertsCard, AssetsCard } from '@/components/console/asset-alert-section'
import { ConsoleHeader } from '@/components/console/console-header'
import {
  DatasourceFormCard,
  DatasourceListCard,
  type DatasourceForm,
} from '@/components/console/datasource-section'
import { IncidentDetailCard } from '@/components/console/incident-detail-card'
import { IncidentListCard } from '@/components/console/incident-list-card'
import { OverviewSection } from '@/components/console/overview-section'
import { Alert, AlertDescription, AlertTitle } from '@/components/ui/alert'
import { Spinner } from '@/components/ui/spinner'
import { usePhase1Queries } from '@/hooks/usePhase1Queries'

const initialForm: DatasourceForm = {
  name: 'Local Zabbix',
  endpoint: 'http://localhost:8081/api_jsonrpc.php',
  username: 'Admin',
  password: '',
  apiToken: '',
}

export function DashboardPage() {
  const auth = useAuth()
  const queryClient = useQueryClient()
  const [banner, setBanner] = useState<{
    tone: 'success' | 'info' | 'warning'
    text: string
  } | null>(null)
  const [selectedIncidentId, setSelectedIncidentId] = useState<string | null>(null)
  const [rcaResult, setRcaResult] = useState<RcaAnalysisResponse | null>(null)
  const [aiDiagnosis, setAiDiagnosis] = useState<AiDiagnosisResponse | null>(null)
  const [form, setForm] = useState<DatasourceForm>(initialForm)

  const { overviewQuery, datasourceQuery, assetQuery, alertQuery, incidentQuery, invalidateAll } =
    usePhase1Queries()

  const incidentDetailQuery = useQuery({
    queryKey: ['incident', selectedIncidentId],
    queryFn: () => getIncident(selectedIncidentId!),
    enabled: Boolean(selectedIncidentId),
  })

  const createMutation = useMutation({
    mutationFn: createZabbixDataSource,
    onSuccess: async (created) => {
      setBanner({ tone: 'success', text: `Datasource created: ${created.name}` })
      await invalidateAll()
    },
    onError: (error) => setBanner({ tone: 'warning', text: String(error) }),
  })

  const testMutation = useMutation({
    mutationFn: testDataSource,
    onSuccess: (result) => {
      setBanner({
        tone: result.ok ? 'success' : 'warning',
        text: result.ok ? `Zabbix connected, version ${result.version}` : result.message,
      })
      invalidateAll()
    },
    onError: (error) => setBanner({ tone: 'warning', text: String(error) }),
  })

  const syncMutation = useMutation({
    mutationFn: syncDataSource,
    onSuccess: async (result) => {
      setBanner({
        tone: 'success',
        text: `Sync ${result.status}: +${result.hostsCreated} hosts, +${result.alertsCreated} alerts`,
      })
      await invalidateAll()
    },
    onError: (error) => setBanner({ tone: 'warning', text: String(error) }),
  })

  const aggregateMutation = useMutation({
    mutationFn: aggregateIncidents,
    onSuccess: async (result) => {
      setBanner({
        tone: 'success',
        text: `Aggregated ${result.scannedAlerts} alerts, created ${result.incidentsCreated}, updated ${result.incidentsUpdated}, linked ${result.alertsLinked}`,
      })
      await invalidateAll()
    },
    onError: (error) => setBanner({ tone: 'warning', text: String(error) }),
  })

  const resolveMutation = useMutation({
    mutationFn: resolveIncident,
    onSuccess: async () => {
      setBanner({ tone: 'success', text: 'Incident resolved' })
      await invalidateAll()
      await queryClient.invalidateQueries({ queryKey: ['incident', selectedIncidentId] })
    },
    onError: (error) => setBanner({ tone: 'warning', text: String(error) }),
  })

  const rcaMutation = useMutation({
    mutationFn: (incidentId: string) => analyzeIncidentRca(incidentId, true),
    onSuccess: async (result) => {
      setRcaResult(result)
      setBanner({ tone: 'info', text: `RCA completed: ${result.suspectedRootCause}` })
      await invalidateAll()
      await queryClient.invalidateQueries({ queryKey: ['incident', selectedIncidentId] })
    },
    onError: (error) => setBanner({ tone: 'warning', text: String(error) }),
  })

  const aiDiagnosisMutation = useMutation({
    mutationFn: (incidentId: string) => diagnoseIncidentAi(incidentId, true),
    onSuccess: async (result) => {
      setAiDiagnosis(result)
      setBanner({ tone: 'info', text: `AI diagnosis completed: ${result.summary}` })
      await queryClient.invalidateQueries({ queryKey: ['incident', selectedIncidentId] })
    },
    onError: (error) => setBanner({ tone: 'warning', text: String(error) }),
  })

  function selectIncident(id: string) {
    setSelectedIncidentId(id)
    setRcaResult(null)
    setAiDiagnosis(null)
  }

  return (
    <div className="flex min-h-screen flex-col bg-muted/30">
      <ConsoleHeader
        user={auth.user}
        onLogout={auth.logout}
        isAggregating={aggregateMutation.isPending}
        onAggregate={() => aggregateMutation.mutate()}
      />

      <main className="mx-auto flex w-full max-w-6xl flex-1 flex-col gap-6 px-6 py-8">
        {banner && (
          <Alert variant={banner.tone === 'warning' ? 'destructive' : 'default'}>
            {banner.tone === 'warning' ? (
              <TriangleAlertIcon data-icon="inline-start" />
            ) : banner.tone === 'success' ? (
              <CircleCheckIcon data-icon="inline-start" />
            ) : (
              <InfoIcon data-icon="inline-start" />
            )}
            <AlertTitle>
              {banner.tone === 'warning'
                ? '操作未完成'
                : banner.tone === 'success'
                  ? '操作成功'
                  : '已触发分析'}
            </AlertTitle>
            <AlertDescription>{banner.text}</AlertDescription>
          </Alert>
        )}

        <OverviewSection
          data={overviewQuery.data}
          isLoading={overviewQuery.isLoading}
          error={overviewQuery.error}
        />

        <section className="grid gap-6 lg:grid-cols-[420px_1fr]">
          <DatasourceFormCard
            form={form}
            onFormChange={setForm}
            onSubmit={(payload) => createMutation.mutate(payload)}
            isSubmitting={createMutation.isPending}
          />
          <DatasourceListCard
            items={datasourceQuery.data}
            isLoading={datasourceQuery.isLoading}
            onTest={(id) => testMutation.mutate(id)}
            onSync={(id) => syncMutation.mutate(id)}
            isTestPending={testMutation.isPending}
            isSyncPending={syncMutation.isPending}
          />
        </section>

        <section className="grid gap-6 lg:grid-cols-2">
          <AssetsCard items={assetQuery.data} isLoading={assetQuery.isLoading} />
          <AlertsCard items={alertQuery.data} isLoading={alertQuery.isLoading} />
        </section>

        <section className="grid gap-6 lg:grid-cols-[1fr_1.1fr]">
          <IncidentListCard
            items={incidentQuery.data}
            isLoading={incidentQuery.isLoading}
            selectedId={selectedIncidentId}
            onSelect={selectIncident}
          />
          <IncidentDetailCard
            detail={incidentDetailQuery.data}
            isLoading={incidentDetailQuery.isLoading}
            error={incidentDetailQuery.error}
            rcaResult={rcaResult}
            aiDiagnosis={aiDiagnosis}
            isResolving={resolveMutation.isPending}
            isAnalyzing={rcaMutation.isPending}
            isAiDiagnosing={aiDiagnosisMutation.isPending}
            onResolve={(id) => resolveMutation.mutate(id)}
            onAnalyze={(id) => rcaMutation.mutate(id)}
            onAiDiagnose={(id) => aiDiagnosisMutation.mutate(id)}
          />
        </section>
      </main>

      {aggregateMutation.isPending && (
        <div
          role="status"
          className="fixed bottom-4 right-4 inline-flex items-center gap-2 rounded-full border bg-card px-3 py-1.5 text-xs shadow-md"
        >
          <Spinner data-icon="inline-start" />
          正在聚合告警…
        </div>
      )}
    </div>
  )
}
