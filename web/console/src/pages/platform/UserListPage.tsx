import { useQuery } from '@tanstack/react-query'

import { Badge } from '@/components/ui/badge'
import { Card, CardContent } from '@/components/ui/card'
import { Skeleton } from '@/components/ui/skeleton'

interface PlatformUserSummary {
  id: string
  username: string
  displayName: string
  status: string
  roles: string[]
}

export function UserListPage() {
  const query = useQuery<PlatformUserSummary[]>({
    queryKey: ['platform-users'],
    queryFn: () =>
      fetch('/api/platform/users')
        .then((r) => r.json())
        .then((body) => body.data ?? []),
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
