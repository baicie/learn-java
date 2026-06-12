import { useQuery } from '@tanstack/react-query'
import { overview } from '../api/client'
import { useAuth } from '../auth/AuthContext'

const cards = [
  ['tenants', 'Tenants'],
  ['users', 'Users'],
  ['assets', 'Assets'],
  ['alerts', 'Alerts'],
  ['incidents', 'Incidents']
]

export function DashboardPage() {
  const auth = useAuth()
  const query = useQuery({ queryKey: ['overview'], queryFn: overview })

  return (
    <main className="min-h-screen bg-slate-100">
      <header className="border-b bg-white">
        <div className="mx-auto flex max-w-6xl items-center justify-between px-6 py-4">
          <div>
            <h1 className="text-xl font-bold">AegisOps Console</h1>
            <p className="text-sm text-slate-500">Phase0 engineering foundation</p>
          </div>
          <div className="flex items-center gap-4">
            <span className="text-sm text-slate-600">{auth.user?.displayName || 'Admin'}</span>
            <button className="rounded-lg border px-3 py-1.5 text-sm" onClick={auth.logout}>Logout</button>
          </div>
        </div>
      </header>
      <section className="mx-auto max-w-6xl px-6 py-8">
        <div className="mb-6 rounded-2xl bg-white p-6 shadow-sm">
          <h2 className="text-lg font-semibold">System Overview</h2>
          <p className="mt-1 text-sm text-slate-500">后续 Phase 会在这里接入 Zabbix、Incident、RCA 与自动化执行。</p>
        </div>
        {query.isLoading && <div className="rounded-xl bg-white p-6">Loading...</div>}
        {query.error && <div className="rounded-xl bg-red-50 p-6 text-red-700">{String(query.error)}</div>}
        {query.data && (
          <div className="grid gap-4 md:grid-cols-5">
            {cards.map(([key, label]) => (
              <div className="rounded-2xl bg-white p-5 shadow-sm" key={key}>
                <div className="text-sm text-slate-500">{label}</div>
                <div className="mt-3 text-3xl font-bold">{String(query.data[key] ?? 0)}</div>
              </div>
            ))}
          </div>
        )}
        <div className="mt-6 grid gap-4 md:grid-cols-3">
          <Panel title="Phase1" text="Zabbix 数据源接入、资产同步、告警同步。" />
          <Panel title="Phase2" text="AlertEvent 去重聚合，生成 Incident 与时间线。" />
          <Panel title="Phase3" text="查询指标上下文，生成 RCA 证据链。" />
        </div>
      </section>
    </main>
  )
}

function Panel({ title, text }: { title: string; text: string }) {
  return (
    <div className="rounded-2xl border border-slate-200 bg-white p-5">
      <h3 className="font-semibold">{title}</h3>
      <p className="mt-2 text-sm text-slate-500">{text}</p>
    </div>
  )
}
