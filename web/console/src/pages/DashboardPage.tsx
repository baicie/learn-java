import { useMutation, useQuery } from '@tanstack/react-query'
import { Wand2Icon } from 'lucide-react'
import { useState } from 'react'
import { Link } from 'react-router-dom'

import { aggregateIncidents, overview } from '@/api/client'
import { useAuth } from '@/auth/AuthContext'
import { Alert, AlertDescription } from '@/components/ui/alert'
import { Button } from '@/components/ui/button'
import { Card, CardContent } from '@/components/ui/card'
import { Skeleton } from '@/components/ui/skeleton'

export function DashboardPage() {
  const auth = useAuth()
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
    <div className="flex min-h-screen flex-col bg-muted/30">
      <header className="sticky top-0 z-40 border-b bg-background/80 backdrop-blur">
        <div className="mx-auto flex max-w-6xl items-center justify-between gap-4 px-6 py-3">
          <div className="flex items-center gap-3">
            <div className="flex size-8 items-center justify-center rounded-md bg-primary text-primary-foreground">
              <Wand2Icon className="size-4" />
            </div>
            <div>
              <h1 className="text-base leading-none font-semibold">AegisOps</h1>
              <p className="mt-1 text-xs text-muted-foreground">AI Ops Demo</p>
            </div>
          </div>
          <div className="flex items-center gap-3">
            <span className="text-sm text-muted-foreground">{displayName}</span>
            <Button
              size="sm"
              onClick={() => void aggregateMutation.mutate()}
              disabled={aggregateMutation.isPending}
            >
              <Wand2Icon data-icon="inline-start" />
              {aggregateMutation.isPending ? '聚合中...' : 'Aggregate Incidents'}
            </Button>
          </div>
        </div>
      </header>

      <main className="mx-auto flex w-full max-w-6xl flex-1 flex-col gap-6 px-6 py-8">
        {banner && (
          <Alert variant={banner.tone === 'warning' ? 'destructive' : 'default'}>
            <AlertDescription>{banner.text}</AlertDescription>
          </Alert>
        )}

        {/* Stats row */}
        <div className="grid grid-cols-4 gap-4">
          {overviewQuery.isLoading
            ? [1, 2, 3, 4].map((i) => <Skeleton key={i} className="h-20 rounded-xl" />)
            : [
                { label: '数据源', key: 'datasources' },
                { label: '告警', key: 'alerts' },
                { label: '资产', key: 'assets' },
                { label: 'Incident', key: 'incidents' },
              ].map(({ label, key }) => (
                <Card key={key}>
                  <CardContent className="pt-4">
                    <p className="text-xs text-muted-foreground">{label}</p>
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
          <div className="grid grid-cols-3 gap-4">
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
              <Card key={n} className="cursor-pointer transition-colors hover:border-foreground">
                <CardContent className="pt-4">
                  <div className="mb-2 flex size-7 items-center justify-center rounded-full bg-foreground text-xs text-background">
                    {n}
                  </div>
                  <h3 className="font-semibold">{title}</h3>
                  <p className="mt-1 text-xs text-muted-foreground">{desc}</p>
                  <Link
                    to={to}
                    className="mt-3 inline-flex h-7 items-center gap-1 rounded-[min(var(--radius-md),12px)] border-border bg-background px-2.5 text-[0.8rem] hover:bg-muted hover:text-foreground"
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
          <CardContent className="pt-4">
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
