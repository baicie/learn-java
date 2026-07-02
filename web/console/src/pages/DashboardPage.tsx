import { useMutation, useQuery } from '@tanstack/react-query'
import { Wand2Icon } from 'lucide-react'
import { useState } from 'react'
import { Link } from 'react-router-dom'

import { aggregateIncidents, overview } from '@/api/client'
import { Alert, AlertDescription } from '@/components/ui/alert'
import { Button } from '@/components/ui/button'
import { Card, CardContent } from '@/components/ui/card'
import { Skeleton } from '@/components/ui/skeleton'

export function DashboardPage() {
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

  return (
    <div className="flex flex-col gap-6">
      {banner && (
        <Alert variant={banner.tone === 'warning' ? 'destructive' : 'default'}>
          <AlertDescription>{banner.text}</AlertDescription>
        </Alert>
      )}

      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-semibold">工作台</h1>
          <p className="text-sm text-muted-foreground">
            按故障处理链路完成一次 Zabbix AI Ops Demo。
          </p>
        </div>
        <Button
          size="sm"
          onClick={() => void aggregateMutation.mutate()}
          disabled={aggregateMutation.isPending}
        >
          <Wand2Icon data-icon="inline-start" />
          {aggregateMutation.isPending ? '聚合中...' : '聚合告警'}
        </Button>
      </div>

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

      <div className="grid grid-cols-1 gap-4 sm:grid-cols-3">
        {[
          {
            n: '1',
            title: '同步 Zabbix',
            desc: '进入数据源页面，触发 Zabbix 主机、Problem 与告警同步。',
            to: '/app/datasources',
          },
          {
            n: '2',
            title: '聚合 Incident',
            desc: '在告警列表触发聚合，将多条告警合并成一次故障。',
            to: '/app/alerts',
          },
          {
            n: '3',
            title: '查看 Incident',
            desc: '进入故障详情，查看告警、证据、RCA、AI 诊断与报告。',
            to: '/app/incidents',
          },
        ].map(({ n, title, desc, to }) => (
          <Card key={n} className="cursor-pointer transition-colors hover:border-[var(--primary)]">
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
    </div>
  )
}
