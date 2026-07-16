import { Link } from '@tanstack/react-router'
import type { ColumnDef } from '@tanstack/react-table'
import { assetTypeLabels, type Asset } from '@/lib/assets/asset'
import { Badge } from '@/components/ui/badge'

export const assetColumns: ColumnDef<Asset>[] = [
  {
    accessorKey: 'name',
    header: '名称',
    cell: ({ row }) => (
      <>
        <Link
          className='font-medium hover:underline'
          to='/assets/$assetId'
          params={{ assetId: row.original.id }}
        >
          {row.original.displayName || row.original.name}
        </Link>
        <div className='text-xs text-muted-foreground'>{row.original.name}</div>
      </>
    ),
  },
  {
    accessorKey: 'assetType',
    header: '类型',
    cell: ({ row }) =>
      assetTypeLabels[row.original.assetType] ?? row.original.assetType,
  },
  {
    accessorKey: 'environment',
    header: '环境',
    cell: ({ row }) => row.original.environment || '—',
  },
  {
    accessorKey: 'ip',
    header: '地址/标识',
    cell: ({ row }) => (
      <span className='font-mono text-xs'>
        {row.original.ip || row.original.id}
      </span>
    ),
  },
  {
    accessorKey: 'sourceCount',
    header: '来源',
    cell: ({ row }) => (
      <Badge variant='outline'>
        {row.original.sourceCount > 1
          ? `${row.original.sourceCount} 个来源`
          : '单一来源'}
      </Badge>
    ),
  },
  {
    accessorKey: 'ownerTeam',
    header: '负责人',
    cell: ({ row }) => row.original.ownerTeam || '—',
  },
  { accessorKey: 'criticality', header: '关键等级' },
  {
    accessorKey: 'status',
    header: '状态',
    cell: ({ row }) => (
      <Badge
        variant={row.original.status === 'active' ? 'default' : 'secondary'}
      >
        {row.original.status}
      </Badge>
    ),
  },
  {
    accessorKey: 'updatedAt',
    header: '最近更新',
    cell: ({ row }) => new Date(row.original.updatedAt).toLocaleString(),
  },
]
