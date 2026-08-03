import type { ColumnDef } from '@tanstack/react-table'
import { formatDateTime } from '@/lib/date-format'
import type { AlertEvent } from '@/lib/operations/operations'
import { AlertSeverityBadge, AlertStatusBadge } from './alert-badges'

export const alertColumns: ColumnDef<AlertEvent>[] = [
  {
    accessorKey: 'title',
    header: '告警',
    cell: ({ row }) => (
      <div className='grid min-w-56 gap-1'>
        <span className='font-medium'>{row.original.title}</span>
        <span className='text-xs text-muted-foreground'>{row.original.id}</span>
      </div>
    ),
  },
  {
    accessorKey: 'severity',
    header: '严重度',
    cell: ({ row }) => <AlertSeverityBadge severity={row.original.severity} />,
  },
  {
    accessorKey: 'status',
    header: '状态',
    cell: ({ row }) => <AlertStatusBadge status={row.original.status} />,
  },
  {
    accessorKey: 'source',
    header: '来源',
    cell: ({ row }) => <span>{row.original.source}</span>,
  },
  {
    accessorKey: 'startsAt',
    header: '开始时间',
    cell: ({ row }) => formatDateTime(row.original.startsAt),
  },
  {
    accessorKey: 'createdAt',
    header: '接入时间',
    cell: ({ row }) => formatDateTime(row.original.createdAt),
  },
]
