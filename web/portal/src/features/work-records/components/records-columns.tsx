import { Link } from '@tanstack/react-router'
import { type ColumnDef } from '@tanstack/react-table'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Checkbox } from '@/components/ui/checkbox'
import { DataTableColumnHeader } from '@/components/data-table'
import { LongText } from '@/components/long-text'
import { i18n } from '@/i18n'
import { type WorkRecord } from '../data/schema'

export const recordStatusValues = ['draft', 'processing', 'done', 'archived'] as const
export type RecordStatusValue = (typeof recordStatusValues)[number]

export const recordsColumns: ColumnDef<WorkRecord>[] = [
  {
    id: 'select',
    header: ({ table }) => (
      <Checkbox
        checked={
          table.getIsAllPageRowsSelected() ||
          (table.getIsSomePageRowsSelected() && 'indeterminate')
        }
        onCheckedChange={(value) => table.toggleAllPageRowsSelected(!!value)}
        aria-label='Select all'
        className='translate-y-0.5'
      />
    ),
    cell: ({ row }) => (
      <Checkbox
        checked={row.getIsSelected()}
        onCheckedChange={(value) => row.toggleSelected(!!value)}
        aria-label='Select row'
        className='translate-y-0.5'
      />
    ),
    enableSorting: false,
    enableHiding: false,
  },
  {
    accessorKey: 'title',
    header: ({ column }) => (
      <DataTableColumnHeader
        column={column}
        title={i18n.t('workRecords.field.title')}
      />
    ),
    cell: ({ row }) => (
      <LongText className='max-w-64 font-medium'>
        {row.getValue('title')}
      </LongText>
    ),
    enableHiding: false,
  },
  {
    accessorKey: 'status',
    header: ({ column }) => (
      <DataTableColumnHeader
        column={column}
        title={i18n.t('workRecords.field.status')}
      />
    ),
    cell: ({ row }) => {
      const status = row.getValue<string>('status')
      return <Badge variant='outline'>{i18n.t(`workRecords.status.${status}`)}</Badge>
    },
    filterFn: (row, id, value) => value.includes(row.getValue(id)),
    enableSorting: false,
    enableHiding: false,
  },
  {
    accessorKey: 'ownerId',
    header: ({ column }) => (
      <DataTableColumnHeader
        column={column}
        title={i18n.t('workRecords.field.owner')}
      />
    ),
    cell: ({ row }) => (
      <span className='text-muted-foreground'>
        {row.getValue('ownerId') || '-'}
      </span>
    ),
    enableSorting: false,
  },
  {
    accessorKey: 'recordTime',
    header: ({ column }) => (
      <DataTableColumnHeader
        column={column}
        title={i18n.t('workRecords.field.recordTime')}
      />
    ),
    cell: ({ row }) => (
      <span className='text-sm text-nowrap'>
        {new Date(row.getValue('recordTime')).toLocaleString()}
      </span>
    ),
  },
  {
    id: 'actions',
    cell: ({ row }) => (
      <div className='flex justify-end gap-2'>
        <Button asChild variant='ghost' size='sm'>
          <Link
            to='/work-records/$recordId'
            params={{ recordId: row.original.id }}
          >
            {i18n.t('workRecords.list.detail')}
          </Link>
        </Button>
        <Button asChild variant='outline' size='sm'>
          <Link
            to='/work-records/$recordId/edit'
            params={{ recordId: row.original.id }}
          >
            {i18n.t('workRecords.list.edit')}
          </Link>
        </Button>
      </div>
    ),
    enableSorting: false,
    enableHiding: false,
  },
]
