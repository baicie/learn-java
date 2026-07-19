import { Alert, AlertDescription } from '@/components/ui/alert'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Skeleton } from '@/components/ui/skeleton'
import { EmptyState } from '@/components/feedback/async-state'
import type { AuditChange, AuditEvent } from './types'

type Props = {
  events: AuditEvent[]
  loading?: boolean
  error?: Error | null
}

export function RecordHistoryCard({
  events,
  loading = false,
  error = null,
}: Props) {
  if (loading) {
    return (
      <Card>
        <CardHeader>
          <CardTitle>变更历史</CardTitle>
        </CardHeader>
        <CardContent
          className='grid gap-2 text-sm text-muted-foreground'
          aria-label='变更历史加载中'
          aria-busy='true'
        >
          <div>正在加载变更历史...</div>
          <Skeleton className='h-4 w-full' />
          <Skeleton className='h-4 w-4/5' />
          <Skeleton className='h-4 w-3/5' />
        </CardContent>
      </Card>
    )
  }

  if (error) {
    return (
      <Card>
        <CardHeader>
          <CardTitle>变更历史</CardTitle>
        </CardHeader>
        <CardContent>
          <Alert variant='destructive'>
            <AlertDescription>加载失败：{error.message}</AlertDescription>
          </Alert>
        </CardContent>
      </Card>
    )
  }

  return (
    <Card>
      <CardHeader>
        <CardTitle>变更历史</CardTitle>
      </CardHeader>
      <CardContent>
        {events.length === 0 ? (
          <EmptyState compact title='暂无变更记录' />
        ) : (
          <ol className='relative border-s'>
            {events.map((event) => (
              <HistoryItem key={event.id} event={event} />
            ))}
          </ol>
        )}
      </CardContent>
    </Card>
  )
}

function HistoryItem({ event }: { event: AuditEvent }) {
  const detail = parseObject(event.detailJson)
  const changes: AuditChange[] = Array.isArray(detail.changes)
    ? (detail.changes as AuditChange[])
    : []
  const truncated = detail.changesTruncated === true

  return (
    <li className='ms-5 mb-6 last:mb-0'>
      <span className='absolute -start-1.5 mt-1.5 size-3 rounded-full border bg-background' />
      <div className='flex flex-wrap items-center justify-between gap-2'>
        <div className='font-medium'>{actionLabel(event.action)}</div>
        <time
          className='text-xs text-muted-foreground'
          dateTime={event.createdAt}
        >
          {formatDateTime(event.createdAt)}
        </time>
      </div>
      <div className='mt-1 text-xs text-muted-foreground'>
        操作人：{event.actorId}
      </div>

      {changes.length > 0 ? (
        <div className='mt-3 grid gap-2'>
          {changes.map((change, index) => (
            <div
              key={`${change.path}-${index}`}
              className='rounded-md border p-2 text-xs'
            >
              <div className='font-medium'>{pathLabel(change.path)}</div>
              <div className='mt-1 grid gap-1 md:grid-cols-2'>
                <Value label='变更前' value={change.beforeValue} />
                <Value label='变更后' value={change.afterValue} />
              </div>
            </div>
          ))}
        </div>
      ) : null}

      {truncated ? (
        <div className='mt-2 text-xs text-amber-600'>
          变更项过多，仅展示前 200 项。
        </div>
      ) : null}
    </li>
  )
}

function Value({ label, value }: { label: string; value: unknown }) {
  return (
    <div>
      <div className='text-muted-foreground'>{label}</div>
      <div className='mt-0.5 break-all'>{formatValue(value)}</div>
    </div>
  )
}

function parseObject(value: string): Record<string, unknown> {
  try {
    const parsed = JSON.parse(value)
    return parsed && typeof parsed === 'object' && !Array.isArray(parsed)
      ? (parsed as Record<string, unknown>)
      : {}
  } catch {
    return {}
  }
}

const ACTION_LABELS: Record<string, string> = {
  'work_record.record.create': '创建记录',
  'work_record.record.update': '编辑记录',
  'work_record.record.delete': '删除记录',
}

function actionLabel(action: string) {
  return ACTION_LABELS[action] ?? action
}

const PATH_LABELS: Record<string, string> = {
  '/title': '标题',
  '/status': '状态',
  '/ownerId': '负责人',
  '/recordTime': '记录时间',
}

function pathLabel(path: string) {
  return (
    PATH_LABELS[path] ??
    path
      .replace('/customData/', '动态字段 / ')
      .replace('/builtinData/', '基础字段 / ')
  )
}

function formatValue(value: unknown) {
  if (value === null || value === undefined) return '-'
  if (typeof value === 'string') return value || '-'
  return JSON.stringify(value)
}

function formatDateTime(value: string) {
  const date = new Date(value)
  return Number.isNaN(date.getTime()) ? value : date.toLocaleString()
}
