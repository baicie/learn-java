import type { Incident } from '@/lib/operations/operations'
import { Badge } from '@/components/ui/badge'

type BadgeVariant = 'default' | 'secondary' | 'destructive' | 'outline'

const severityLabels: Record<string, string> = {
  disaster: '灾难',
  critical: '严重',
  high: '高',
  medium: '中',
  warning: '警告',
  low: '低',
  info: '信息',
}

const statusLabels: Record<string, string> = {
  open: '待处理',
  investigating: '调查中',
  mitigating: '处置中',
  resolved: '已解决',
  closed: '已关闭',
  ignored: '已忽略',
}

function severityVariant(severity: string): BadgeVariant {
  if (severity === 'disaster' || severity === 'critical') return 'destructive'
  if (severity === 'high') return 'default'
  if (severity === 'medium' || severity === 'warning') return 'secondary'
  return 'outline'
}

function statusVariant(status: string): BadgeVariant {
  if (status === 'open' || status === 'investigating') return 'default'
  if (status === 'mitigating') return 'secondary'
  return 'outline'
}

function incidentSeverityLabel(severity: string) {
  return severityLabels[severity] ?? severity
}

function incidentStatusLabel(status: string) {
  return statusLabels[status] ?? status
}

export function IncidentSeverityBadge({
  severity,
}: Pick<Incident, 'severity'>) {
  return (
    <Badge variant={severityVariant(severity)}>
      {incidentSeverityLabel(severity)}
    </Badge>
  )
}

export function IncidentStatusBadge({ status }: Pick<Incident, 'status'>) {
  return (
    <Badge variant={statusVariant(status)}>{incidentStatusLabel(status)}</Badge>
  )
}
