import { useQuery } from '@tanstack/react-query'

import type { PlatformUserRecord } from '@/api/client'

import { listPlatformUsers } from '@/api/client'
import { Badge } from '@/components/ui/badge'
import { Card, CardContent } from '@/components/ui/card'
import { Skeleton } from '@/components/ui/skeleton'

export function UserListPage() {
  const query = useQuery<PlatformUserRecord[]>({
    queryKey: ['platform-users'],
    queryFn: listPlatformUsers,
  })

  return (
    <div className="flex flex-col gap-6">
      <header className="flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-semibold">用户管理</h1>
          <p className="text-sm text-muted-foreground">Phase 1 建立入口，后续补充用户 CRUD。</p>
        </div>
      </header>

      {query.isLoading && (
        <div className="grid gap-3">
          {[1, 2, 3].map((i) => (
            <Skeleton key={i} className="h-16 w-full rounded-lg" />
          ))}
        </div>
      )}

      {query.isError && (
        <Card>
          <CardContent className="py-8 text-center text-destructive">
            加载失败：{String(query.error)}
          </CardContent>
        </Card>
      )}

      {query.data?.length === 0 && (
        <Card>
          <CardContent className="py-8 text-center text-muted-foreground">暂无用户</CardContent>
        </Card>
      )}

      <div className="grid gap-3">
        {query.data?.map((user) => (
          <Card key={user.id}>
            <CardContent className="flex items-center justify-between p-4">
              <div>
                <p className="font-medium">{user.displayName}</p>
                <p className="text-xs text-muted-foreground">{user.username}</p>
              </div>
              <div className="flex items-center gap-2">
                {user.roles.map((r) => (
                  <Badge key={r} variant="outline">
                    {r}
                  </Badge>
                ))}
                <Badge variant={user.status === 'active' ? 'default' : 'secondary'}>
                  {user.status}
                </Badge>
              </div>
            </CardContent>
          </Card>
        ))}
      </div>
    </div>
  )
}
