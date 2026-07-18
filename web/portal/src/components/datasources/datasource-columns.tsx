import type { ColumnDef } from '@tanstack/react-table'
import { RefreshCw, TestTube2 } from 'lucide-react'
import type { Datasource } from '@/lib/datasources/datasource'
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
  pending,
}: {
  onTest: (id: string) => void
  onSync: (id: string) => void
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
          ? new Date(row.original.lastSyncAt).toLocaleString()
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
            <Button
              size='sm'
              variant='ghost'
              disabled={pending}
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
