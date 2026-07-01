import { useQuery } from '@tanstack/react-query'
import { Link, useNavigate } from 'react-router-dom'

import { listIncidents } from '@/api/client'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Card, CardContent } from '@/components/ui/card'

export function IncidentsPage() {
  const navigate = useNavigate()
  const query = useQuery({
    queryKey: ['incidents'],
    queryFn: listIncidents,
    refetchInterval: 30000,
  })

  return (
    <div className="flex min-h-screen flex-col bg-muted/30">
      <header className="sticky top-0 z-40 border-b bg-background/80 backdrop-blur">
        <div className="mx-auto flex max-w-6xl items-center justify-between gap-4 px-6 py-3">
          <div>
            <h1 className="text-base leading-none font-semibold">Incidents</h1>
            <p className="mt-1 text-xs text-muted-foreground">
              故障列表。点击进入详情完成证据、RCA、AI 诊断和报告。
            </p>
          </div>
          <Button size="sm" onClick={() => query.refetch()} disabled={query.isFetching}>
            {query.isFetching ? '刷新中...' : '刷新'}
          </Button>
        </div>
      </header>

      <main className="mx-auto flex w-full max-w-6xl flex-1 flex-col gap-4 px-6 py-6">
        {query.isError && (
          <div className="rounded-lg border border-destructive/50 bg-destructive/10 px-4 py-3 text-sm text-destructive">
            加载失败: {String(query.error)}
          </div>
        )}

        {query.isLoading && (
          <div className="space-y-3">
            {[1, 2, 3].map((i) => (
              <div key={i} className="h-20 animate-pulse rounded-xl bg-muted" />
            ))}
          </div>
        )}

        {query.data && query.data.length === 0 && (
          <Card>
            <CardContent className="flex flex-col items-center justify-center py-12">
              <p className="text-muted-foreground">暂无 Incident</p>
              <p className="mt-1 text-sm text-muted-foreground">
                请先在 Alerts 页面执行 Aggregate Incident。
              </p>
              <Link
                to="/alerts"
                className="mt-4 inline-flex h-7 items-center justify-center gap-1 rounded-[min(var(--radius-md),12px)] border border-border bg-background px-2.5 text-[0.8rem] font-medium hover:bg-muted hover:text-foreground"
              >
                打开 Alerts
              </Link>
            </CardContent>
          </Card>
        )}

        {query.data && query.data.length > 0 && (
          <div className="space-y-3">
            {query.data.map((incident) => (
              <Card
                key={incident.id}
                className="cursor-pointer transition-colors hover:border-foreground"
                onClick={() => navigate(`/incidents/${incident.id}`)}
              >
                <CardContent className="flex items-center justify-between gap-4 p-4">
                  <div className="min-w-0 flex-1">
                    <div className="flex items-center gap-2">
                      <h3 className="truncate font-medium">{incident.title || incident.id}</h3>
                      <Badge
                        variant={
                          incident.severity === 'critical' || incident.severity === 'high'
                            ? 'destructive'
                            : incident.severity === 'warning' || incident.severity === 'medium'
                              ? 'outline'
                              : 'secondary'
                        }
                      >
                        {incident.severity}
                      </Badge>
                      <Badge variant="outline">{incident.status}</Badge>
                    </div>
                    <p className="mt-1 truncate text-sm text-muted-foreground">
                      {incident.summary || incident.aggregationKey || '暂无摘要'}
                    </p>
                  </div>
                  <div className="flex items-center gap-2 text-sm text-muted-foreground">
                    <span>{incident.alertCount ?? 0} alerts</span>
                  </div>
                </CardContent>
              </Card>
            ))}
          </div>
        )}
      </main>
    </div>
  )
}
