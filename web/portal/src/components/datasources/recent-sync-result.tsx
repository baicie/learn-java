import { useSyncRuns } from '@/hooks/datasources/use-datasources'
import { Badge } from '@/components/ui/badge'

export function RecentSyncResult({ datasourceId }: { datasourceId: string }) {
  const runs = useSyncRuns(datasourceId)
  const latest = runs.data?.[0]
  if (runs.isLoading)
    return <span className='text-muted-foreground'>读取中…</span>
  if (!latest) return <span className='text-muted-foreground'>—</span>
  return (
    <Badge
      variant={latest.status === 'failed' ? 'destructive' : 'secondary'}
      title={latest.message ?? undefined}
    >
      {latest.status === 'pending'
        ? '排队中'
        : latest.status === 'running'
          ? '同步中'
          : latest.status === 'success'
            ? '成功'
            : '失败'}
    </Badge>
  )
}
