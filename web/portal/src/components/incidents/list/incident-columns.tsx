import { Link } from '@tanstack/react-router'
import type { ColumnDef } from '@tanstack/react-table'
import { formatDateTime } from '@/lib/date-format'
import type { Incident } from '@/lib/operations/operations'
import { IncidentSeverityBadge, IncidentStatusBadge } from './incident-state'

export const incidentColumns: ColumnDef<Incident>[] = [
  {
    accessorKey: 'title',
    header: 'Incident',
    cell: ({ row }) => (
      <div className='grid min-w-64 gap-1'>
        <Link
          className='font-medium hover:underline'
          to='/incidents/$incidentId'
          params={{ incidentId: row.original.id }}
        >
          {row.original.title}
        </Link>
        {row.original.summary ? (
          <span className='line-clamp-1 text-xs text-muted-foreground'>
            {row.original.summary}
          </span>
        ) : null}
      </div>
    ),
  },
  {
    accessorKey: 'severity',
    header: '严重度',
    cell: ({ row }) => (
      <IncidentSeverityBadge severity={row.original.severity} />
    ),
  },
  {
    accessorKey: 'status',
    header: '状态',
    cell: ({ row }) => <IncidentStatusBadge status={row.original.status} />,
  },
  {
    accessorKey: 'source',
    header: '来源',
    cell: ({ row }) => row.original.source,
  },
  {
    accessorKey: 'alertCount',
    header: '关联告警',
    cell: ({ row }) => `${row.original.alertCount} 条告警`,
  },
  {
    accessorKey: 'startedAt',
    header: '开始时间',
    cell: ({ row }) => formatDateTime(row.original.startedAt),
  },
  {
    accessorKey: 'updatedAt',
    header: '最近更新',
    cell: ({ row }) => formatDateTime(row.original.updatedAt),
  },
]
