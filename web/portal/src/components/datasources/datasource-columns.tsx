import type { ColumnDef } from '@tanstack/react-table'
import { Pencil, RefreshCw, TestTube2, Webhook } from 'lucide-react'
import type { Datasource } from '@/lib/datasources/datasource'
import { formatDateTime } from '@/lib/date-format'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { PermissionGate } from '@/components/permission-gate'
import { RecentSyncResult } from './recent-sync-result'

const typeLabels: Record<Datasource['type'], string> = {
  zabbix: 'Zabbix',
  kubernetes: 'Kubernetes',
  opentelemetry: 'OpenTelemetry',
  rum: 'RUM',
  github: 'GitHub Actions',
  gitlab: 'GitLab',
  jenkins: 'Jenkins',
  webhook: 'Webhook',
}

export function datasourceColumns({
  onTest,
  onSync,
  onEdit,
  onWebhook,
  pending,
}: {
  onTest: (id: string) => void
  onSync: (id: string) => void
  onEdit: (datasource: Datasource) => void
  onWebhook: (datasource: Datasource) => void
  pending: boolean
}): ColumnDef<Datasource>[] {
  return [
    {
      accessorKey: 'name',
      header: '名称',
      cell: ({ row }) => (
        <span className='font-medium'>{row.original.name}</span>
      ),
    },
    {
      accessorKey: 'type',
      header: '类型',
      cell: ({ row }) => typeLabels[row.original.type],
    },
    {
      accessorKey: 'endpoint',
      header: 'Endpoint',
      cell: ({ row }) => (
        <span className='font-mono text-xs'>
          {row.original.endpoint ?? '—'}
        </span>
      ),
    },
    {
      accessorKey: 'status',
      header: '连接状态',
      cell: ({ row }) => (
        <Badge
          variant={row.original.status === 'error' ? 'destructive' : 'outline'}
        >
          {row.original.status}
        </Badge>
      ),
    },
    {
      accessorKey: 'lastSyncAt',
      header: '最近同步',
      cell: ({ row }) =>
        row.original.lastSyncAt
          ? formatDateTime(row.original.lastSyncAt)
          : '尚未同步',
    },
    {
      id: 'latestRun',
      header: '最近结果',
      cell: ({ row }) => <RecentSyncResult datasourceId={row.original.id} />,
    },
    {
      id: 'actions',
      header: '操作',
      cell: ({ row }) => (
        <PermissionGate any={['datasource:write']}>
          <div className='flex justify-end gap-1'>
            <Button
              size='sm'
              variant='ghost'
              disabled={pending}
              onClick={() => onEdit(row.original)}
            >
              <Pencil data-icon='inline-start' />
              编辑
            </Button>
            {['zabbix', 'kubernetes'].includes(row.original.type) && (
              <Button
                size='sm'
                variant='ghost'
                disabled={pending}
                onClick={() => onTest(row.original.id)}
              >
                <TestTube2 />
                测试
              </Button>
            )}
            {row.original.type === 'zabbix' && (
              <Button
                size='sm'
                variant='ghost'
                disabled={pending || row.original.status !== 'active'}
                onClick={() => onWebhook(row.original)}
              >
                <Webhook />
                Webhook
              </Button>
            )}
            <Button
              size='sm'
              variant='ghost'
              disabled={pending || row.original.status !== 'active'}
              onClick={() => onSync(row.original.id)}
            >
              <RefreshCw />
              同步
            </Button>
          </div>
        </PermissionGate>
      ),
    },
  ]
}
