import { TriangleAlertIcon, InboxIcon } from 'lucide-react'

import { Alert, AlertDescription, AlertTitle } from '@/components/ui/alert'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Empty, EmptyDescription, EmptyMedia, EmptyTitle } from '@/components/ui/empty'
import { Skeleton } from '@/components/ui/skeleton'

type Overview = Record<string, number | string>

const cards: Array<{ key: string; label: string }> = [
  { key: 'tenants', label: 'Tenants' },
  { key: 'users', label: 'Users' },
  { key: 'assets', label: 'Assets' },
  { key: 'alerts', label: 'Alerts' },
  { key: 'incidents', label: 'Incidents' },
]

export function OverviewSection({
  data,
  isLoading,
  error,
}: {
  data?: Overview
  isLoading: boolean
  error: Error | null
}) {
  return (
    <div data-slot="overview-section" className="flex flex-col gap-4">
      <div className="flex items-center justify-between">
        <div>
          <h2 className="text-lg font-semibold">System Overview</h2>
          <p className="mt-1 text-sm text-muted-foreground">
            Phase 2 已支持 Zabbix 告警同步、AlertEvent 聚合、Incident 详情与时间线。
          </p>
        </div>
      </div>

      {error && (
        <Alert variant="destructive">
          <TriangleAlertIcon data-icon="inline-start" />
          <AlertTitle>无法加载概览</AlertTitle>
          <AlertDescription>{error?.message ?? 'Unknown error'}</AlertDescription>
        </Alert>
      )}

      {isLoading && (
        <div className="grid gap-4 md:grid-cols-5">
          {cards.map((card) => (
            <Card key={card.key}>
              <CardHeader>
                <CardTitle className="text-muted-foreground">{card.label}</CardTitle>
              </CardHeader>
              <CardContent>
                <Skeleton className="h-7 w-16" />
              </CardContent>
            </Card>
          ))}
        </div>
      )}

      {data && !isLoading && (
        <div className="grid gap-4 md:grid-cols-5">
          {cards.map((card) => (
            <Card key={card.key}>
              <CardHeader>
                <CardTitle className="text-xs font-normal text-muted-foreground">
                  {card.label}
                </CardTitle>
              </CardHeader>
              <CardContent>
                <div className="font-heading text-3xl font-semibold tracking-tight">
                  {String(data[card.key] ?? 0)}
                </div>
              </CardContent>
            </Card>
          ))}
        </div>
      )}

      {!data && !isLoading && !error && (
        <Empty className="border">
          <EmptyMedia variant="icon">
            <InboxIcon />
          </EmptyMedia>
          <EmptyTitle>暂无数据</EmptyTitle>
          <EmptyDescription>配置数据源并同步后,这里会展示关键指标。</EmptyDescription>
        </Empty>
      )}
    </div>
  )
}
