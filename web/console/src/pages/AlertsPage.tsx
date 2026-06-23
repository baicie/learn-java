import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { Wand2Icon } from 'lucide-react'
import { useState } from 'react'

import { listAlerts, aggregateIncidents, type AlertEventRecord } from '@/api/client'
import { Alert, AlertDescription } from '@/components/ui/alert'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Card, CardContent } from '@/components/ui/card'
import { Skeleton } from '@/components/ui/skeleton'

export function AlertsPage() {
  const queryClient = useQueryClient()
  const [banner, setBanner] = useState<{ tone: 'success' | 'warning'; text: string } | null>(null)

  const query = useQuery({
    queryKey: ['alerts'],
    queryFn: listAlerts,
    refetchInterval: 30000,
  })

  const aggregateMut = useMutation({
    mutationFn: aggregateIncidents,
    onSuccess: (result) => {
      setBanner({
        tone: 'success',
        text: `聚合完成：扫描 ${result.alertsScanned} 条告警，创建 ${result.incidentsCreated} 个 Incident，关联 ${result.alertsLinked} 条。`,
      })
      void queryClient.invalidateQueries({ queryKey: ['alerts'] })
    },
    onError: (err) => setBanner({ tone: 'warning', text: String(err) }),
  })

  return (
    <div className="flex min-h-screen flex-col bg-muted/30">
      <header className="sticky top-0 z-40 border-b bg-background/80 backdrop-blur">
        <div className="mx-auto flex max-w-6xl items-center justify-between gap-4 px-6 py-3">
          <div>
            <h1 className="text-base font-semibold leading-none">Alerts</h1>
            <p className="mt-1 text-xs text-muted-foreground">
              查看 Zabbix 标准化告警，并触发 Incident 聚合。
            </p>
          </div>
          <div className="flex gap-2">
            <Button size="sm" onClick={() => void query.refetch()} disabled={query.isFetching}>
              {query.isFetching ? '刷新中...' : '刷新'}
            </Button>
            <Button
              size="sm"
              onClick={() => void aggregateMut.mutate()}
              disabled={aggregateMut.isPending}
            >
              <Wand2Icon data-icon="inline-start" className="size-3.5" />
              {aggregateMut.isPending ? '聚合中...' : 'Aggregate Incident'}
            </Button>
          </div>
        </div>
      </header>

      <main className="mx-auto flex w-full max-w-6xl flex-1 flex-col gap-4 px-6 py-6">
        {banner && (
          <Alert variant={banner.tone === 'warning' ? 'destructive' : 'default'}>
            <AlertDescription>{banner.text}</AlertDescription>
          </Alert>
        )}

        {query.isLoading && (
          <div className="space-y-3">
            {[1, 2, 3].map((i) => (
              <Skeleton key={i} className="h-14 rounded-xl" />
            ))}
          </div>
        )}

        {query.isError && (
          <Alert variant="destructive">
            <AlertDescription>加载失败: {String(query.error)}</AlertDescription>
          </Alert>
        )}

        {query.data && query.data.length === 0 && (
          <Card>
            <CardContent className="flex flex-col items-center justify-center py-12">
              <p className="text-muted-foreground">暂无告警</p>
              <p className="mt-1 text-sm text-muted-foreground">请先执行 Sync Zabbix。</p>
            </CardContent>
          </Card>
        )}

        {query.data && query.data.length > 0 && (
          <div className="space-y-2">
            {query.data.map((alert: AlertEventRecord) => (
              <div
                key={alert.id}
                className="flex items-center gap-3 rounded-lg border bg-card p-3 text-sm"
              >
                <Badge
                  variant={
                    alert.severity === 'critical'
                      ? 'destructive'
                      : alert.severity === 'high'
                        ? 'destructive'
                        : 'outline'
                  }
                >
                  {alert.severity}
                </Badge>
                <Badge variant="outline">{alert.status}</Badge>
                <span className="flex-1 truncate">{alert.title}</span>
                <span className="text-xs text-muted-foreground">
                  {alert.startsAt || alert.createdAt}
                </span>
              </div>
            ))}
          </div>
        )}
      </main>
    </div>
  )
}
