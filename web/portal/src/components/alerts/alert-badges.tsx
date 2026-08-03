import type { AlertEvent } from '@/lib/operations/operations'
import { Badge } from '@/components/ui/badge'

const severityLabel: Record<string, string> = {
  info: '信息',
  low: '低',
  warning: '警告',
  medium: '中',
  high: '高',
  critical: '严重',
  disaster: '灾难',
}

const statusLabel: Record<string, string> = {
  open: '进行中',
  resolved: '已恢复',
}

export function AlertSeverityBadge({ severity }: Pick<AlertEvent, 'severity'>) {
  return (
    <Badge
      variant={
        severity === 'critical' ||
        severity === 'disaster' ||
        severity === 'high'
          ? 'destructive'
          : severity === 'warning' || severity === 'medium'
            ? 'default'
            : 'secondary'
      }
    >
      {severityLabel[severity] ?? severity}
    </Badge>
  )
}

export function AlertStatusBadge({ status }: Pick<AlertEvent, 'status'>) {
  return (
    <Badge variant={status === 'open' ? 'default' : 'secondary'}>
      {statusLabel[status] ?? status}
    </Badge>
  )
}
