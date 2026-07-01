import { useQuery, useMutation } from '@tanstack/react-query'
import { Wand2Icon } from 'lucide-react'
import { useState } from 'react'
import { Link } from 'react-router-dom'

import { aggregateIncidents, overview } from '@/api/client'
import { useAuth } from '@/auth/AuthContext'
import { Alert, AlertDescription } from '@/components/ui/alert'
import { Button } from '@/components/ui/button'
import { Card, CardContent } from '@/components/ui/card'
import { Skeleton } from '@/components/ui/skeleton'
import { useTheme } from '@/lib/theme/ThemeProvider'

export function DashboardPage() {
  const auth = useAuth()
  const { theme, setTheme } = useTheme()
  const [banner, setBanner] = useState<{ tone: 'success' | 'warning'; text: string } | null>(null)

  const overviewQuery = useQuery({
    queryKey: ['overview'],
    queryFn: overview,
  })

  const aggregateMutation = useMutation({
    mutationFn: aggregateIncidents,
    onSuccess: (result) => {
      setBanner({
        tone: 'success',
        text: `聚合完成：扫描 ${result.alertsScanned} 条告警，创建 ${result.incidentsCreated} 个 Incident，关联 ${result.alertsLinked} 条。`,
      })
    },
    onError: (err) => setBanner({ tone: 'warning', text: String(err) }),
  })

  const displayName = auth.user?.displayName || 'Admin'

  return (
    <div className="flex min-h-screen flex-col">
      {/* Top bar */}
      <header className="glass-header sticky top-0 z-40 flex h-14 items-center justify-between gap-3 px-6">
        <div className="flex min-w-0 shrink-0 items-center gap-2">
          <div className="flex size-8 items-center justify-center rounded-md bg-blue-500 text-white">
            <Wand2Icon className="size-4" />
          </div>
          <span className="text-xl font-semibold">AegisOps</span>
        </div>

        <nav
          className="hidden min-w-0 flex-1 justify-center md:flex"
          aria-label="Primary navigation"
        >
          <div className="flex max-w-full items-center gap-1 overflow-hidden rounded-2xl bg-muted p-1">
            {[
              { label: 'Dashboard', to: '/' },
              { label: 'Datasources', to: '/datasources' },
              { label: 'Alerts', to: '/alerts' },
              { label: 'Incidents', to: '/incidents' },
              { label: 'Reports', to: '/reports' },
            ].map(({ label, to }) => (
              <Link
                key={to}
                to={to}
                className="truncate rounded-xl px-4 py-2 text-sm font-medium text-muted-foreground transition-colors hover:bg-background hover:text-foreground"
              >
                {label}
              </Link>
            ))}
          </div>
        </nav>

        <div className="flex min-w-0 shrink-0 items-center gap-2">
          <span className="hidden text-sm text-muted-foreground md:inline">{displayName}</span>
          <Button
            size="sm"
            variant="ghost"
            onClick={() => setTheme(theme === 'dark' ? 'light' : 'dark')}
          >
            {theme === 'dark' ? 'Light' : 'Dark'}
          </Button>
          <Button
            size="sm"
            onClick={() => void aggregateMutation.mutate()}
            disabled={aggregateMutation.isPending}
          >
            <Wand2Icon data-icon="inline-start" />
            {aggregateMutation.isPending ? '聚合中...' : 'Aggregate'}
          </Button>
        </div>
      </header>

      <main className="mx-auto flex w-full max-w-6xl flex-1 flex-col gap-6 px-6 py-8">
        {banner && (
          <Alert variant={banner.tone === 'warning' ? 'destructive' : 'default'}>
            <AlertDescription>{banner.text}</AlertDescription>
          </Alert>
        )}

        {/* Stats row */}
        <div className="grid grid-cols-2 gap-4 sm:grid-cols-4">
          {overviewQuery.isLoading
            ? [1, 2, 3, 4].map((i) => <Skeleton key={i} className="h-20 rounded-lg" />)
            : [
                { label: '数据源', key: 'datasources' },
                { label: '告警', key: 'alerts' },
                { label: '资产', key: 'assets' },
                { label: 'Incident', key: 'incidents' },
              ].map(({ label, key }) => (
                <Card key={key}>
                  <CardContent className="p-4">
                    <p className="text-sm text-muted-foreground">{label}</p>
                    <p className="mt-1 text-2xl font-semibold">{overviewQuery.data?.[key] ?? 0}</p>
                  </CardContent>
                </Card>
              ))}
        </div>

        {/* Demo Guide */}
        <div>
          <h2 className="mb-3 text-sm font-semibold">Demo Guide</h2>
          <p className="mb-4 text-xs text-muted-foreground">
            按故障处理链路完成一次 Zabbix AI Ops Demo。
          </p>
          <div className="grid grid-cols-1 gap-4 sm:grid-cols-3">
            {[
              {
                n: '1',
                title: '同步 Zabbix',
                desc: '进入数据源页面，触发 Zabbix 主机、Problem 与告警同步。',
                to: '/datasources',
              },
              {
                n: '2',
                title: '聚合 Incident',
                desc: '在 Alerts 页面执行聚合，将多条告警合并成一次故障。',
                to: '/alerts',
              },
              {
                n: '3',
                title: '查看 Incident',
                desc: '进入 Incident Detail，查看告警、证据、RCA、AI 诊断与报告。',
                to: '/incidents',
              },
            ].map(({ n, title, desc, to }) => (
              <Card
                key={n}
                className="cursor-pointer transition-colors hover:border-[var(--primary)]"
              >
                <CardContent className="p-4">
                  <div className="mb-2 flex size-7 items-center justify-center rounded-full bg-blue-500 text-xs text-white">
                    {n}
                  </div>
                  <h3 className="font-semibold">{title}</h3>
                  <p className="mt-1 text-xs text-muted-foreground">{desc}</p>
                  <Link
                    to={to}
                    className="mt-3 inline-flex h-7 items-center gap-1 rounded-md border border-[var(--border)] bg-background px-3 text-xs hover:bg-gray-100 dark:hover:bg-gray-800"
                  >
                    打开
                  </Link>
                </CardContent>
              </Card>
            ))}
          </div>
        </div>

        {/*链路说明 */}
        <Card>
          <CardContent className="p-4">
            <h3 className="mb-2 text-sm font-semibold">最小演示链路</h3>
            <pre className="text-xs text-muted-foreground">
              {`Zabbix Alert
  -> Alert Event
  -> Incident
  -> Evidence
  -> RCA
  -> AI Diagnosis
  -> Markdown Report`}
            </pre>
          </CardContent>
        </Card>
      </main>
    </div>
  )
}
