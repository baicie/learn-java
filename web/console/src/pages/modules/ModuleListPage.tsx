import { useQuery } from '@tanstack/react-query'

import { listPlatformModules } from '@/api/client'
import { Badge } from '@/components/ui/badge'
import { Card, CardContent } from '@/components/ui/card'
import { Skeleton } from '@/components/ui/skeleton'

export function ModuleListPage() {
  const query = useQuery({
    queryKey: ['platform-modules'],
    queryFn: listPlatformModules,
  })

  return (
    <div className="flex flex-col gap-6">
      <header>
        <h1 className="text-2xl font-semibold">模块管理</h1>
        <p className="text-sm text-muted-foreground">Phase 1 建立入口，后续补充模块安装与配置。</p>
      </header>

      {query.isLoading && (
        <div className="grid gap-3">
          {[1, 2].map((i) => (
            <Skeleton key={i} className="h-16 w-full rounded-lg" />
          ))}
        </div>
      )}

      {query.data?.length === 0 && (
        <Card>
          <CardContent className="py-8 text-center text-muted-foreground">暂无模块</CardContent>
        </Card>
      )}

      <div className="grid gap-3">
        {query.data?.map((m) => (
          <Card key={m.moduleId}>
            <CardContent className="flex items-center justify-between p-4">
              <div className="flex items-center gap-4">
                <div>
                  <p className="font-medium">{m.name}</p>
                  <p className="text-xs text-muted-foreground">{m.moduleId}</p>
                </div>
              </div>
              <div className="flex items-center gap-2">
                <Badge variant="outline">{m.version}</Badge>
                <Badge
                  variant={
                    m.healthStatus === 'HEALTHY'
                      ? 'default'
                      : m.healthStatus === 'UNHEALTHY'
                        ? 'destructive'
                        : 'secondary'
                  }
                >
                  {m.healthStatus}
                </Badge>
                <Badge variant={m.enabled ? 'default' : 'secondary'}>
                  {m.enabled ? '启用' : '禁用'}
                </Badge>
              </div>
            </CardContent>
          </Card>
        ))}
      </div>
    </div>
  )
}
