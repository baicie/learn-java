import { FormEvent, useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import {
  aggregateIncidents,
  createZabbixDataSource,
  getIncident,
  resolveIncident,
  syncDataSource,
  testDataSource
} from '../api/client'
import { useAuth } from '../auth/AuthContext'
import { usePhase1Queries } from '../hooks/usePhase1Queries'

const cards = [
  ['tenants', 'Tenants'],
  ['users', 'Users'],
  ['assets', 'Assets'],
  ['alerts', 'Alerts'],
  ['incidents', 'Incidents']
]

export function DashboardPage() {
  const auth = useAuth()
  const queryClient = useQueryClient()
  const [message, setMessage] = useState('')
  const [selectedIncidentId, setSelectedIncidentId] = useState<string | null>(null)
  const [form, setForm] = useState({
    name: 'Local Zabbix',
    endpoint: 'http://localhost:8081/api_jsonrpc.php',
    username: 'Admin',
    password: '',
    apiToken: ''
  })

  const {
    overviewQuery,
    datasourceQuery,
    assetQuery,
    alertQuery,
    incidentQuery,
    invalidateAll
  } = usePhase1Queries()

  const incidentDetailQuery = useQuery({
    queryKey: ['incident', selectedIncidentId],
    queryFn: () => getIncident(selectedIncidentId!),
    enabled: Boolean(selectedIncidentId)
  })

  const createMutation = useMutation({
    mutationFn: createZabbixDataSource,
    onSuccess: async (created) => {
      setMessage(`Datasource created: ${created.name}`)
      await invalidateAll()
    },
    onError: (error) => setMessage(String(error))
  })

  const testMutation = useMutation({
    mutationFn: testDataSource,
    onSuccess: (result) => {
      setMessage(result.ok ? `Zabbix connected, version ${result.version}` : result.message)
      invalidateAll()
    },
    onError: (error) => setMessage(String(error))
  })

  const syncMutation = useMutation({
    mutationFn: syncDataSource,
    onSuccess: async (result) => {
      setMessage(`Sync ${result.status}: +${result.hostsCreated} hosts, +${result.alertsCreated} alerts`)
      await invalidateAll()
    },
    onError: (error) => setMessage(String(error))
  })

  const aggregateMutation = useMutation({
    mutationFn: aggregateIncidents,
    onSuccess: async (result) => {
      setMessage(
        `Aggregated ${result.scannedAlerts} alerts, created ${result.incidentsCreated}, updated ${result.incidentsUpdated}, linked ${result.alertsLinked}`
      )
      await invalidateAll()
    },
    onError: (error) => setMessage(String(error))
  })

  const resolveMutation = useMutation({
    mutationFn: resolveIncident,
    onSuccess: async () => {
      setMessage('Incident resolved')
      await invalidateAll()
      await queryClient.invalidateQueries({ queryKey: ['incident', selectedIncidentId] })
    },
    onError: (error) => setMessage(String(error))
  })

  function submit(event: FormEvent) {
    event.preventDefault()
    createMutation.mutate({
      type: 'zabbix',
      name: form.name,
      zabbix: {
        endpoint: form.endpoint,
        username: form.username || undefined,
        password: form.password || undefined,
        apiToken: form.apiToken || undefined,
        connectTimeoutSeconds: 5,
        readTimeoutSeconds: 20
      }
    })
  }

  return (
    <main className="min-h-screen bg-slate-100">
      <header className="border-b bg-white">
        <div className="mx-auto flex max-w-6xl items-center justify-between px-6 py-4">
          <div>
            <h1 className="text-xl font-bold">AegisOps Console</h1>
            <p className="text-sm text-slate-500">Phase2 Incident aggregation center</p>
          </div>
          <div className="flex items-center gap-4">
            <span className="text-sm text-slate-600">{auth.user?.displayName || 'Admin'}</span>
            <button className="rounded-lg border px-3 py-1.5 text-sm" onClick={auth.logout}>Logout</button>
          </div>
        </div>
      </header>

      <section className="mx-auto max-w-6xl px-6 py-8">
        <div className="mb-6 rounded-2xl bg-white p-6 shadow-sm">
          <div className="flex flex-wrap items-start justify-between gap-4">
            <div>
              <h2 className="text-lg font-semibold">System Overview</h2>
              <p className="mt-1 text-sm text-slate-500">
                Phase2 已支持 Zabbix 告警同步、AlertEvent 聚合、Incident 详情与时间线。
              </p>
            </div>
            <button
              className="rounded-lg bg-indigo-600 px-4 py-2 text-sm font-medium text-white disabled:opacity-60"
              disabled={aggregateMutation.isPending}
              onClick={() => aggregateMutation.mutate()}
            >
              {aggregateMutation.isPending ? 'Aggregating...' : 'Aggregate Incidents'}
            </button>
          </div>

          {message && <div className="mt-4 rounded-lg bg-slate-100 px-4 py-3 text-sm text-slate-700">{message}</div>}
        </div>

        {overviewQuery.isLoading && <div className="rounded-xl bg-white p-6">Loading...</div>}
        {overviewQuery.error && <div className="rounded-xl bg-red-50 p-6 text-red-700">{String(overviewQuery.error)}</div>}
        {overviewQuery.data && (
          <div className="grid gap-4 md:grid-cols-5">
            {cards.map(([key, label]) => (
              <div className="rounded-2xl bg-white p-5 shadow-sm" key={key}>
                <div className="text-sm text-slate-500">{label}</div>
                <div className="mt-3 text-3xl font-bold">{String(overviewQuery.data[key] ?? 0)}</div>
              </div>
            ))}
          </div>
        )}

        <div className="mt-6 grid gap-6 lg:grid-cols-[420px_1fr]">
          <section className="rounded-2xl bg-white p-6 shadow-sm">
            <h2 className="text-lg font-semibold">Add Zabbix Datasource</h2>
            <form className="mt-4 space-y-3" onSubmit={submit}>
              <Field label="Name" value={form.name} onChange={(name) => setForm({ ...form, name })} />
              <Field label="API Endpoint" value={form.endpoint} onChange={(endpoint) => setForm({ ...form, endpoint })} />
              <Field label="Username" value={form.username} onChange={(username) => setForm({ ...form, username })} />
              <Field label="Password" type="password" value={form.password} onChange={(password) => setForm({ ...form, password })} />
              <Field label="API Token" type="password" value={form.apiToken} onChange={(apiToken) => setForm({ ...form, apiToken })} />
              <button
                className="w-full rounded-lg bg-slate-900 px-4 py-2 text-sm font-medium text-white disabled:opacity-60"
                disabled={createMutation.isPending}
              >
                {createMutation.isPending ? 'Creating...' : 'Create Datasource'}
              </button>
            </form>
          </section>

          <section className="rounded-2xl bg-white p-6 shadow-sm">
            <h2 className="text-lg font-semibold">Datasources</h2>
            <div className="mt-4 space-y-3">
              {datasourceQuery.data?.map((ds) => (
                <div className="rounded-xl border border-slate-200 p-4" key={ds.id}>
                  <div className="flex items-center justify-between gap-4">
                    <div>
                      <div className="flex items-center gap-2 font-medium">
                        <span>{ds.name}</span>
                        <StatusChip status={ds.status} />
                      </div>
                      <div className="text-sm text-slate-500">{ds.type} · last sync {ds.lastSyncAt || '-'}</div>
                    </div>
                    <div className="flex gap-2">
                      <button className="rounded-lg border px-3 py-1.5 text-sm" onClick={() => testMutation.mutate(ds.id)}>Test</button>
                      <button className="rounded-lg bg-slate-900 px-3 py-1.5 text-sm text-white" onClick={() => syncMutation.mutate(ds.id)}>Sync</button>
                    </div>
                  </div>
                </div>
              ))}
              {!datasourceQuery.data?.length && <div className="text-sm text-slate-500">No datasource yet.</div>}
            </div>
          </section>
        </div>

        <div className="mt-6 grid gap-6 lg:grid-cols-2">
          <section className="rounded-2xl bg-white p-6 shadow-sm">
            <h2 className="text-lg font-semibold">Assets</h2>
            <div className="mt-4 overflow-hidden rounded-xl border border-slate-200">
              {assetQuery.data?.slice(0, 8).map((asset) => (
                <div className="border-b border-slate-100 px-4 py-3 text-sm last:border-0" key={asset.id}>
                  <div className="font-medium">{asset.displayName || asset.name}</div>
                  <div className="text-slate-500">{asset.assetType} · {asset.source} · {asset.status}</div>
                </div>
              ))}
              {!assetQuery.data?.length && <div className="px-4 py-6 text-sm text-slate-500">No assets synced.</div>}
            </div>
          </section>

          <section className="rounded-2xl bg-white p-6 shadow-sm">
            <h2 className="text-lg font-semibold">Alerts</h2>
            <div className="mt-4 overflow-hidden rounded-xl border border-slate-200">
              {alertQuery.data?.slice(0, 8).map((alert) => (
                <div className="border-b border-slate-100 px-4 py-3 text-sm last:border-0" key={alert.id}>
                  <div className="font-medium">{alert.title}</div>
                  <div className="text-slate-500">{alert.severity} · {alert.status} · {alert.startsAt}</div>
                </div>
              ))}
              {!alertQuery.data?.length && <div className="px-4 py-6 text-sm text-slate-500">No alerts synced.</div>}
            </div>
          </section>
        </div>

        <div className="mt-6 grid gap-6 lg:grid-cols-[1fr_420px]">
          <section className="rounded-2xl bg-white p-6 shadow-sm">
            <h2 className="text-lg font-semibold">Incidents</h2>
            <div className="mt-4 overflow-hidden rounded-xl border border-slate-200">
              {incidentQuery.data?.map((incident) => (
                <button
                  className={`block w-full border-b border-slate-100 px-4 py-3 text-left text-sm last:border-0 ${
                    selectedIncidentId === incident.id ? 'bg-indigo-50' : 'bg-white hover:bg-slate-50'
                  }`}
                  key={incident.id}
                  onClick={() => setSelectedIncidentId(incident.id)}
                >
                  <div className="flex items-center justify-between gap-3">
                    <div className="font-medium">{incident.title}</div>
                    <StatusChip status={incident.status} />
                  </div>
                  <div className="mt-1 text-slate-500">
                    {incident.severity} · alerts {incident.alertCount} · {incident.startedAt}
                  </div>
                </button>
              ))}
              {!incidentQuery.data?.length && <div className="px-4 py-6 text-sm text-slate-500">No incidents yet. Click Aggregate Incidents after syncing alerts.</div>}
            </div>
          </section>

          <section className="rounded-2xl bg-white p-6 shadow-sm">
            <h2 className="text-lg font-semibold">Incident Detail</h2>
            {!selectedIncidentId && <div className="mt-4 text-sm text-slate-500">Select an incident.</div>}

            {incidentDetailQuery.isLoading && <div className="mt-4 text-sm text-slate-500">Loading incident...</div>}
            {incidentDetailQuery.error && <div className="mt-4 text-sm text-red-600">{String(incidentDetailQuery.error)}</div>}

            {incidentDetailQuery.data && (
              <div className="mt-4 space-y-5">
                <div>
                  <div className="text-base font-semibold">{incidentDetailQuery.data.incident.title}</div>
                  <div className="mt-1 text-sm text-slate-500">
                    {incidentDetailQuery.data.incident.severity} · {incidentDetailQuery.data.incident.status}
                  </div>
                  <p className="mt-3 text-sm text-slate-600">{incidentDetailQuery.data.incident.summary}</p>
                  <button
                    className="mt-3 rounded-lg bg-emerald-600 px-3 py-1.5 text-sm font-medium text-white disabled:opacity-60"
                    disabled={resolveMutation.isPending}
                    onClick={() => resolveMutation.mutate(incidentDetailQuery.data!.incident.id)}
                  >
                    Resolve
                  </button>
                </div>

                <div>
                  <h3 className="text-sm font-semibold">Linked Alerts</h3>
                  <div className="mt-2 space-y-2">
                    {incidentDetailQuery.data.alerts.map((alert) => (
                      <div className="rounded-lg border border-slate-200 p-3 text-sm" key={alert.id}>
                        <div className="font-medium">{alert.title}</div>
                        <div className="text-slate-500">{alert.relationType} · {alert.severity} · {alert.startsAt}</div>
                      </div>
                    ))}
                  </div>
                </div>

                <div>
                  <h3 className="text-sm font-semibold">Timeline</h3>
                  <div className="mt-2 space-y-2">
                    {incidentDetailQuery.data.timeline.map((item) => (
                      <div className="rounded-lg border border-slate-200 p-3 text-sm" key={item.id}>
                        <div className="font-medium">{item.title}</div>
                        <div className="text-slate-500">{item.eventType} · {item.eventTime}</div>
                        {item.description && <div className="mt-1 text-slate-600">{item.description}</div>}
                      </div>
                    ))}
                  </div>
                </div>
              </div>
            )}
          </section>
        </div>
      </section>
    </main>
  )
}

function Field({
  label,
  value,
  onChange,
  type = 'text'
}: {
  label: string
  value: string
  onChange: (value: string) => void
  type?: string
}) {
  return (
    <label className="block">
      <span className="text-sm text-slate-600">{label}</span>
      <input
        className="mt-1 w-full rounded-lg border border-slate-300 px-3 py-2 text-sm outline-none focus:border-slate-500"
        type={type}
        value={value}
        onChange={(event) => onChange(event.target.value)}
      />
    </label>
  )
}

const STATUS_STYLES: Record<string, string> = {
  active: 'bg-emerald-100 text-emerald-800 border-emerald-200',
  error: 'bg-rose-100 text-rose-800 border-rose-200',
  inactive: 'bg-slate-100 text-slate-700 border-slate-200',
  open: 'bg-rose-100 text-rose-800 border-rose-200',
  investigating: 'bg-amber-100 text-amber-800 border-amber-200',
  mitigating: 'bg-blue-100 text-blue-800 border-blue-200',
  resolved: 'bg-emerald-100 text-emerald-800 border-emerald-200',
  closed: 'bg-slate-100 text-slate-700 border-slate-200',
  ignored: 'bg-slate-100 text-slate-700 border-slate-200'
}

function StatusChip({ status }: { status: string }) {
  const tone = STATUS_STYLES[status] ?? STATUS_STYLES.inactive
  return (
    <span className={`inline-flex items-center rounded-full border px-2 py-0.5 text-xs font-medium ${tone}`}>
      {status}
    </span>
  )
}
