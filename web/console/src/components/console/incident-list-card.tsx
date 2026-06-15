import { ListChecksIcon } from 'lucide-react'

import type { IncidentRecord } from '@/api/client'

import { SeverityBadge, StatusBadge } from '@/components/console/status-badge'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { Empty, EmptyDescription, EmptyMedia, EmptyTitle } from '@/components/ui/empty'
import { Skeleton } from '@/components/ui/skeleton'
import { cn } from '@/lib/utils'

export function IncidentListCard({
  items,
  isLoading,
  selectedId,
  onSelect,
}: {
  items: IncidentRecord[] | undefined
  isLoading: boolean
  selectedId: string | null
  onSelect: (id: string) => void
}) {
  return (
    <Card>
      <CardHeader>
        <div className="flex items-center gap-2">
          <ListChecksIcon className="size-4 text-muted-foreground" />
          <CardTitle>Incidents</CardTitle>
        </div>
        <CardDescription>点击 Incident 加载右侧详情、RCA 与时间线。</CardDescription>
      </CardHeader>
      <CardContent>
        {isLoading && (
          <div className="flex flex-col gap-2">
            {Array.from({ length: 4 }).map((_, idx) => (
              <Skeleton key={idx} className="h-14 w-full" />
            ))}
          </div>
        )}

        {items && items.length > 0 && (
          <ul className="flex flex-col gap-2">
            {items.map((incident) => {
              const selected = selectedId === incident.id
              return (
                <li key={incident.id}>
                  <Button
                    type="button"
                    variant="ghost"
                    onClick={() => onSelect(incident.id)}
                    className={cn(
                      'h-auto w-full justify-between gap-3 rounded-lg border px-3 py-3 text-left whitespace-normal',
                      selected
                        ? 'border-primary/40 bg-primary/5 hover:bg-primary/10'
                        : 'border-border bg-card hover:bg-muted/50',
                    )}
                  >
                    <div className="flex min-w-0 flex-col gap-1">
                      <span className="truncate font-medium">{incident.title}</span>
                      <div className="flex flex-wrap items-center gap-2 text-xs text-muted-foreground">
                        <SeverityBadge severity={incident.severity} />
                        <span>alerts {incident.alertCount}</span>
                        <span>· {incident.startedAt}</span>
                      </div>
                    </div>
                    <StatusBadge status={incident.status} />
                  </Button>
                </li>
              )
            })}
          </ul>
        )}

        {!isLoading && items && items.length === 0 && (
          <Empty className="border">
            <EmptyMedia variant="icon">
              <ListChecksIcon />
            </EmptyMedia>
            <EmptyTitle>暂无 Incident</EmptyTitle>
            <EmptyDescription>
              同步告警后点击上方的 "Aggregate Incidents" 生成事故。
            </EmptyDescription>
          </Empty>
        )}
      </CardContent>
    </Card>
  )
}
