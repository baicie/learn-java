import { ServerIcon, SirenIcon } from 'lucide-react'

import type { AlertEventRecord, AssetRecord } from '@/api/client'

import { SeverityBadge } from '@/components/console/status-badge'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { Empty, EmptyDescription, EmptyMedia, EmptyTitle } from '@/components/ui/empty'
import { Skeleton } from '@/components/ui/skeleton'

export function AssetsCard({
  items,
  isLoading,
}: {
  items: AssetRecord[] | undefined
  isLoading: boolean
}) {
  const top = items?.slice(0, 8) ?? []

  return (
    <Card>
      <CardHeader>
        <div className="flex items-center gap-2">
          <ServerIcon className="size-4 text-muted-foreground" />
          <CardTitle>Assets</CardTitle>
        </div>
        <CardDescription>Zabbix 同步后的资产列表</CardDescription>
      </CardHeader>
      <CardContent>
        {isLoading && (
          <div className="flex flex-col gap-2">
            {Array.from({ length: 4 }).map((_, idx) => (
              <Skeleton key={idx} className="h-10 w-full" />
            ))}
          </div>
        )}

        {top.length > 0 && (
          <ul className="divide-y rounded-lg border">
            {top.map((asset) => (
              <li key={asset.id} className="flex flex-col gap-0.5 px-3 py-2 text-sm">
                <span className="font-medium">{asset.displayName || asset.name}</span>
                <span className="text-xs text-muted-foreground">
                  {asset.assetType} · {asset.source} · {asset.status}
                </span>
              </li>
            ))}
          </ul>
        )}

        {!isLoading && top.length === 0 && (
          <Empty className="border">
            <EmptyMedia variant="icon">
              <ServerIcon />
            </EmptyMedia>
            <EmptyTitle>No assets synced</EmptyTitle>
            <EmptyDescription>先在数据源上执行一次 Sync 才会出现资产。</EmptyDescription>
          </Empty>
        )}
      </CardContent>
    </Card>
  )
}

export function AlertsCard({
  items,
  isLoading,
}: {
  items: AlertEventRecord[] | undefined
  isLoading: boolean
}) {
  const top = items?.slice(0, 8) ?? []

  return (
    <Card>
      <CardHeader>
        <div className="flex items-center gap-2">
          <SirenIcon className="size-4 text-muted-foreground" />
          <CardTitle>Alerts</CardTitle>
        </div>
        <CardDescription>最近同步的 AlertEvent</CardDescription>
      </CardHeader>
      <CardContent>
        {isLoading && (
          <div className="flex flex-col gap-2">
            {Array.from({ length: 4 }).map((_, idx) => (
              <Skeleton key={idx} className="h-10 w-full" />
            ))}
          </div>
        )}

        {top.length > 0 && (
          <ul className="divide-y rounded-lg border">
            {top.map((alert) => (
              <li key={alert.id} className="flex flex-col gap-1 px-3 py-2 text-sm">
                <div className="flex items-center justify-between gap-2">
                  <span className="truncate font-medium">{alert.title}</span>
                  <SeverityBadge severity={alert.severity} />
                </div>
                <span className="text-xs text-muted-foreground">
                  {alert.status} · {alert.startsAt}
                </span>
              </li>
            ))}
          </ul>
        )}

        {!isLoading && top.length === 0 && (
          <Empty className="border">
            <EmptyMedia variant="icon">
              <SirenIcon />
            </EmptyMedia>
            <EmptyTitle>No alerts synced</EmptyTitle>
            <EmptyDescription>Zabbix 触发 problem 后,这里会出现告警。</EmptyDescription>
          </Empty>
        )}
      </CardContent>
    </Card>
  )
}
