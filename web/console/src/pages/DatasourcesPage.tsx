import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { Wand2Icon } from 'lucide-react'
import { useState } from 'react'

import { listDataSources, syncDataSource, type DataSourceRecord } from '@/api/client'
import { Alert, AlertDescription } from '@/components/ui/alert'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Card, CardContent } from '@/components/ui/card'
import { Skeleton } from '@/components/ui/skeleton'

export function DatasourcesPage() {
  const queryClient = useQueryClient()
  const [banner, setBanner] = useState<{ tone: 'success' | 'warning'; text: string } | null>(null)

  const query = useQuery({
    queryKey: ['datasources'],
    queryFn: listDataSources,
    refetchInterval: 30000,
  })

  const syncMut = useMutation({
    mutationFn: syncDataSource,
    onSuccess: (result) => {
      setBanner({
        tone: 'success',
        text: `同步完成：+${result.hostsCreated} 主机，+${result.alertsCreated} 告警`,
      })
      void queryClient.invalidateQueries({ queryKey: ['datasources'] })
    },
    onError: (err) => setBanner({ tone: 'warning', text: String(err) }),
  })

  return (
    <div className="flex min-h-screen flex-col bg-muted/30">
      <header className="sticky top-0 z-40 border-b bg-background/80 backdrop-blur">
        <div className="mx-auto flex max-w-6xl items-center justify-between gap-4 px-6 py-3">
          <div>
            <h1 className="text-base leading-none font-semibold">Datasources</h1>
            <p className="mt-1 text-xs text-muted-foreground">
              管理 Zabbix 数据源，并触发 Demo 同步。
            </p>
          </div>
          <Button size="sm" onClick={() => void query.refetch()} disabled={query.isFetching}>
            {query.isFetching ? '刷新中...' : '刷新'}
          </Button>
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
            {[1, 2].map((i) => (
              <Skeleton key={i} className="h-16 rounded-xl" />
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
              <p className="text-muted-foreground">暂无数据源</p>
              <p className="mt-1 text-sm text-muted-foreground">请先创建 Zabbix datasource。</p>
            </CardContent>
          </Card>
        )}

        {query.data && query.data.length > 0 && (
          <div className="space-y-3">
            {query.data.map((ds: DataSourceRecord) => (
              <Card key={ds.id}>
                <CardContent className="flex items-center justify-between gap-4 p-4">
                  <div className="flex items-center gap-3">
                    <div>
                      <h3 className="font-medium">{ds.name || ds.id}</h3>
                      <p className="mt-0.5 text-xs text-muted-foreground">
                        {ds.type} · {ds.status}
                      </p>
                    </div>
                  </div>
                  <div className="flex items-center gap-2">
                    <Badge variant="outline">{ds.type}</Badge>
                    <Badge variant={ds.status === 'connected' ? 'default' : 'outline'}>
                      {ds.status}
                    </Badge>
                    <Button
                      size="sm"
                      variant="outline"
                      onClick={() => void syncMut.mutate(ds.id)}
                      disabled={syncMut.isPending || ds.type !== 'zabbix'}
                    >
                      <Wand2Icon data-icon="inline-start" className="size-3.5" />
                      {syncMut.isPending && syncMut.variables === ds.id
                        ? '同步中...'
                        : 'Sync Zabbix'}
                    </Button>
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
