import {
  flexRender,
  getCoreRowModel,
  useReactTable,
} from '@tanstack/react-table'
import type { Asset } from '@/lib/assets/asset'
import { Button } from '@/components/ui/button'
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from '@/components/ui/table'
import { ResponsiveTable } from '@/components/layout/responsive-table'
import { assetColumns } from './asset-columns'

export function AssetsTable({
  items,
  page,
  pageSize,
  total,
  onPageChange,
}: {
  items: Asset[]
  page: number
  pageSize: number
  total: number
  onPageChange: (page: number) => void
}) {
  // eslint-disable-next-line react-hooks/incompatible-library -- TanStack Table intentionally exposes a stateful table instance.
  const table = useReactTable({
    data: items,
    columns: assetColumns,
    getCoreRowModel: getCoreRowModel(),
    manualPagination: true,
    rowCount: total,
  })
  const totalPages = Math.max(1, Math.ceil(total / pageSize))
  return (
    <div className='grid gap-3'>
      <ResponsiveTable>
        <Table>
          <TableHeader>
            {table.getHeaderGroups().map((group) => (
              <TableRow key={group.id}>
                {group.headers.map((header) => (
                  <TableHead key={header.id}>
                    {flexRender(
                      header.column.columnDef.header,
                      header.getContext()
                    )}
                  </TableHead>
                ))}
              </TableRow>
            ))}
          </TableHeader>
          <TableBody>
            {table.getRowModel().rows.map((row) => (
              <TableRow key={row.id}>
                {row.getVisibleCells().map((cell) => (
                  <TableCell key={cell.id}>
                    {flexRender(cell.column.columnDef.cell, cell.getContext())}
                  </TableCell>
                ))}
              </TableRow>
            ))}
          </TableBody>
        </Table>
      </ResponsiveTable>
      <div className='flex items-center justify-end gap-2'>
        <Button
          variant='outline'
          disabled={page <= 1}
          onClick={() => onPageChange(page - 1)}
        >
          上一页
        </Button>
        <span className='text-sm text-muted-foreground'>
          第 {page} / {totalPages} 页
        </span>
        <Button
          variant='outline'
          disabled={page >= totalPages}
          onClick={() => onPageChange(page + 1)}
        >
          下一页
        </Button>
      </div>
    </div>
  )
}
