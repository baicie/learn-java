import {
  flexRender,
  getCoreRowModel,
  useReactTable,
} from '@tanstack/react-table'
import type { Incident } from '@/lib/operations/operations'
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
import { incidentColumns } from './incident-columns'

export function IncidentListTable({
  items,
  page,
  pageCount,
  onPageChange,
}: {
  items: Incident[]
  page: number
  pageCount: number
  onPageChange: (page: number) => void
}) {
  // eslint-disable-next-line react-hooks/incompatible-library -- TanStack Table intentionally exposes a stateful table instance.
  const table = useReactTable({
    data: items,
    columns: incidentColumns,
    getCoreRowModel: getCoreRowModel(),
  })

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
          第 {page} / {pageCount} 页
        </span>
        <Button
          variant='outline'
          disabled={page >= pageCount}
          onClick={() => onPageChange(page + 1)}
        >
          下一页
        </Button>
      </div>
    </div>
  )
}
