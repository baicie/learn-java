import {
  flexRender,
  getCoreRowModel,
  useReactTable,
} from '@tanstack/react-table'
import type { Datasource } from '@/lib/datasources/datasource'
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from '@/components/ui/table'
import { ResponsiveTable } from '@/components/layout/responsive-table'
import { datasourceColumns } from './datasource-columns'

export function DatasourcesTable({
  items,
  onTest,
  onSync,
  onEdit,
  onWebhook,
  pending,
}: {
  items: Datasource[]
  onTest: (id: string) => void
  onSync: (id: string) => void
  onEdit: (datasource: Datasource) => void
  onWebhook: (datasource: Datasource) => void
  pending: boolean
}) {
  // eslint-disable-next-line react-hooks/incompatible-library -- TanStack Table intentionally exposes a stateful table instance.
  const table = useReactTable({
    data: items,
    columns: datasourceColumns({
      onTest,
      onSync,
      onEdit,
      onWebhook,
      pending,
    }),
    getCoreRowModel: getCoreRowModel(),
  })
  return (
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
  )
}
