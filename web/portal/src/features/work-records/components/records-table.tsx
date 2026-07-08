import { useEffect, useMemo, useState } from 'react'
import { Link } from '@tanstack/react-router'
import {
  flexRender,
  getCoreRowModel,
  getFacetedRowModel,
  getFacetedUniqueValues,
  getFilteredRowModel,
  getPaginationRowModel,
  getSortedRowModel,
  useReactTable,
  type SortingState,
  type VisibilityState,
} from '@tanstack/react-table'
import { Plus } from 'lucide-react'
import { useTranslation } from 'react-i18next'
import { cn } from '@/lib/utils'
import { type NavigateFn, useTableUrlState } from '@/hooks/use-table-url-state'
import { Button } from '@/components/ui/button'
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from '@/components/ui/table'
import { DataTablePagination, DataTableToolbar } from '@/components/data-table'
import {
  type WorkRecord,
  type RecordListMetadata,
  type DynamicFilter,
  recordStatusValues,
} from '../data/schema'
import { DynamicFilterSheet } from './dynamic-filter-sheet'
import { ExportRecordsDialog } from './export-records-dialog'
import { createRecordsColumns } from './records-columns'

type RecordsTableProps = {
  data: WorkRecord[]
  metadata?: RecordListMetadata
  total: number
  search: Record<string, unknown>
  navigate: NavigateFn
  onQueryChange: (query: Record<string, unknown>) => void
}

export function RecordsTable({
  data,
  metadata,
  total,
  search,
  navigate,
  onQueryChange,
}: RecordsTableProps) {
  const { t } = useTranslation()
  const statusOptions = useMemo(
    () =>
      recordStatusValues.map((value) => ({
        value,
        label: t(`workRecords.status.${value}`),
      })),
    [t]
  )

  // Dynamic filters state - synced with URL search
  const filters = (search.filters as DynamicFilter[]) ?? []

  const [rowSelection, setRowSelection] = useState({})
  const [columnVisibility, setColumnVisibility] = useState<VisibilityState>({})
  const [sorting, setSorting] = useState<SortingState>([])

  const {
    columnFilters,
    onColumnFiltersChange,
    pagination,
    onPaginationChange,
    ensurePageInRange,
  } = useTableUrlState({
    search,
    navigate,
    pagination: { defaultPage: 1, defaultPageSize: 20 },
    globalFilter: { enabled: false },
    columnFilters: [
      { columnId: 'title', searchKey: 'keyword', type: 'string' },
      { columnId: 'status', searchKey: 'status', type: 'array' },
    ],
  })

  const columns = useMemo(() => createRecordsColumns(), [])

  // eslint-disable-next-line react-hooks/incompatible-library
  const table = useReactTable({
    data,
    columns,
    state: {
      sorting,
      pagination,
      rowSelection,
      columnFilters,
      columnVisibility,
    },
    enableRowSelection: true,
    onPaginationChange,
    onColumnFiltersChange,
    onRowSelectionChange: setRowSelection,
    onSortingChange: setSorting,
    onColumnVisibilityChange: setColumnVisibility,
    getPaginationRowModel: getPaginationRowModel(),
    getCoreRowModel: getCoreRowModel(),
    getFilteredRowModel: getFilteredRowModel(),
    getSortedRowModel: getSortedRowModel(),
    getFacetedRowModel: getFacetedRowModel(),
    getFacetedUniqueValues: getFacetedUniqueValues(),
  })

  useEffect(() => {
    ensurePageInRange(table.getPageCount())
  }, [table, ensurePageInRange])

  const handleFiltersChange = (newFilters: DynamicFilter[]) => {
    onQueryChange({ ...search, filters: newFilters, page: 1 })
  }

  const filterFields = metadata?.filterFields ?? []
  const templates = metadata?.templates ?? []
  const selectedTemplateId = search.templateId as string | undefined

  return (
    <div className={cn('flex flex-1 flex-col gap-4')}>
      {/* Toolbar row */}
      <div className='flex flex-wrap items-center gap-2'>
        {/* Template selector */}
        {templates.length > 1 && (
          <select
            className='h-8 rounded-md border border-input bg-background px-3 text-sm'
            value={selectedTemplateId ?? ''}
            onChange={(e) =>
              onQueryChange({
                ...search,
                templateId: e.target.value || undefined,
                page: 1,
              })
            }
          >
            <option value=''>{t('workRecords.list.allTemplates')}</option>
            {templates.map((tpl) => (
              <option key={tpl.id} value={tpl.id}>
                {tpl.name}
              </option>
            ))}
          </select>
        )}

        {/* Search input */}
        <DataTableToolbar
          table={table}
          searchPlaceholder={t('workRecords.list.searchPlaceholder')}
          searchKey='title'
          filters={[
            {
              columnId: 'status',
              title: t('workRecords.field.status'),
              options: statusOptions,
            },
          ]}
        />

        {/* Dynamic filter sheet */}
        <DynamicFilterSheet
          filterFields={filterFields}
          filters={filters}
          onFiltersChange={handleFiltersChange}
        />

        <div className='ml-auto flex gap-2'>
          {/* Export button */}
          <ExportRecordsDialog
            templateId={selectedTemplateId}
            status={search.status as string[] | undefined}
            keyword={search.keyword as string | undefined}
            filters={filters}
            total={total}
            maxRows={metadata?.maxExportRows ?? 5000}
            columns={metadata?.columns}
          />

          {/* New record button */}
          <Button asChild size='sm' className='gap-2'>
            <Link to='/work-records/new'>
              <Plus className='h-4 w-4' />
              {t('workRecords.list.create')}
            </Link>
          </Button>
        </div>
      </div>

      {/* Table */}
      <div className='overflow-hidden rounded-md border'>
        <Table>
          <TableHeader>
            {table.getHeaderGroups().map((headerGroup) => (
              <TableRow key={headerGroup.id} className='group/row'>
                {headerGroup.headers.map((header) => (
                  <TableHead
                    key={header.id}
                    colSpan={header.colSpan}
                    className={cn(
                      'bg-background group-hover/row:bg-muted',
                      header.column.columnDef.meta?.className,
                      header.column.columnDef.meta?.thClassName
                    )}
                  >
                    {header.isPlaceholder
                      ? null
                      : flexRender(
                          header.column.columnDef.header,
                          header.getContext()
                        )}
                  </TableHead>
                ))}
              </TableRow>
            ))}
          </TableHeader>
          <TableBody>
            {table.getRowModel().rows.length ? (
              table.getRowModel().rows.map((row) => (
                <TableRow
                  key={row.id}
                  data-state={row.getIsSelected() && 'selected'}
                  className='group/row'
                >
                  {row.getVisibleCells().map((cell) => (
                    <TableCell
                      key={cell.id}
                      className={cn(
                        'bg-background group-hover/row:bg-muted',
                        cell.column.columnDef.meta?.className,
                        cell.column.columnDef.meta?.tdClassName
                      )}
                    >
                      {flexRender(
                        cell.column.columnDef.cell,
                        cell.getContext()
                      )}
                    </TableCell>
                  ))}
                </TableRow>
              ))
            ) : (
              <TableRow>
                <TableCell
                  colSpan={columns.length}
                  className='h-24 text-center'
                >
                  {t('workRecords.list.empty')}
                </TableCell>
              </TableRow>
            )}
          </TableBody>
        </Table>
      </div>
      <DataTablePagination table={table} className='mt-auto' />
    </div>
  )
}
