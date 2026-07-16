import { useState } from 'react'
import { useNavigate } from '@tanstack/react-router'
import { PermissionGate } from '@/auth/permission-gate'
import { useTranslation } from 'react-i18next'
import { useWorkRecordList } from '@/hooks/work-records/use-work-record-list'
import { Button } from '@/components/ui/button'
import { Card, CardContent } from '@/components/ui/card'
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select'
import {
  EmptyState,
  ErrorState,
  PageLoadingState,
  TableLoadingState,
} from '@/components/feedback/async-state'
import { ResponsiveTable } from '@/components/layout/responsive-table'
import { TableToolbar } from '@/components/layout/table-toolbar'
import { ColumnControl } from './column-control'
import { DynamicFilterPanel } from './dynamic-filter-panel'
import { ListToolbar } from './list-toolbar'
import { QuickViewTabs } from './quick-view-tabs'
import { RecordTable } from './record-table'
import { buildEmptyListQuery, type ListQueryState } from './types'
import { WorkRecordExportDialog } from './work-record-export-dialog'
import { WorkRecordImportDialog } from './work-record-import-dialog'
import { WorkdaySummaryCard } from './workday-summary-card'

export function WorkRecordListPage() {
  const navigate = useNavigate()
  const { t } = useTranslation()
  const [query, setQuery] = useState<ListQueryState>(() =>
    buildEmptyListQuery()
  )
  const [importOpen, setImportOpen] = useState(false)
  const [exportOpen, setExportOpen] = useState(false)
  const view = useWorkRecordList(query, setQuery)

  const onSort = (sortBy: string, sortDir: 'asc' | 'desc') => {
    setQuery({ ...query, sortBy, sortDir, page: 1 })
  }

  if (view.initialLoading) {
    return <PageLoadingState />
  }

  if (view.pageError) {
    return (
      <main className='p-4 md:p-6'>
        <ErrorState
          error={view.pageError as Error}
          onRetry={() => view.retryPage()}
        />
      </main>
    )
  }

  return (
    <main className='grid gap-4 p-4 md:p-6'>
      <header className='flex flex-col gap-1'>
        <h1 className='text-2xl font-semibold'>
          {t('workRecords.list.title')}
        </h1>
        <p className='text-sm text-muted-foreground'>
          {t('workRecords.list.description')}
        </p>
      </header>

      <QuickViewTabs
        value={query.quickView}
        available={
          view.meta?.quickViews ?? [
            'mine',
            'all',
            'today',
            'this_week',
            'this_month',
            'this_work_month',
            'recent_workdays',
          ]
        }
        onChange={(next) => setQuery({ ...query, quickView: next, page: 1 })}
      />

      <TableToolbar
        title={t('workRecords.list.title')}
        description={t('workRecords.list.description')}
        actions={
          <div className='flex flex-wrap gap-2'>
            <PermissionGate anyOf={['work-record:import']}>
              <Button
                type='button'
                variant='outline'
                onClick={() => setImportOpen(true)}
              >
                {t('workRecords.list.import')}
              </Button>
            </PermissionGate>
            <PermissionGate anyOf={['work-record:export']}>
              <Button
                type='button'
                variant='outline'
                onClick={() => setExportOpen(true)}
              >
                {t('workRecords.list.export')}
              </Button>
            </PermissionGate>
            <PermissionGate anyOf={['work-record:write']}>
              <Button
                type='button'
                onClick={() =>
                  navigate({
                    to: '/work-records/new',
                  })
                }
              >
                {t('workRecords.list.create')}
              </Button>
            </PermissionGate>
          </div>
        }
      >
        <ListToolbar
          meta={view.meta}
          query={query}
          userOptions={view.userOptions}
          onChange={(patch) => setQuery({ ...query, ...patch, page: 1 })}
        />
      </TableToolbar>

      <ColumnControl
        columns={view.meta?.columns ?? []}
        visible={query.visibleColumns}
        onChange={(visible) => setQuery({ ...query, visibleColumns: visible })}
      />

      <DynamicFilterPanel
        fields={(view.meta?.columns ?? []).filter(
          (column) => column.source === 'custom' && column.filterable
        )}
        filters={query.dynamicFilters}
        onChange={(filters) =>
          setQuery({ ...query, dynamicFilters: filters, page: 1 })
        }
      />

      {view.workdaySummary ? (
        <WorkdaySummaryCard
          summary={view.workdaySummary}
          loading={view.workdaySummaryLoading}
        />
      ) : null}

      <Card>
        <CardContent className='grid gap-3 p-4'>
          {view.tableRefreshError ? (
            <div className='rounded-md border border-destructive/30 bg-destructive/5 px-3 py-2 text-sm text-destructive'>
              {t('workRecords.list.refreshFailedHint')}
            </div>
          ) : null}

          {view.tableLoading ? (
            <TableLoadingState columns={view.effectiveColumns.length || 4} />
          ) : view.tableError ? (
            <ErrorState
              compact
              error={view.tableError as Error}
              onRetry={() => view.retryTable()}
            />
          ) : view.records.length === 0 ? (
            <EmptyState
              title={t('workRecords.list.emptyTitle')}
              description={t('workRecords.list.emptyDescription')}
            />
          ) : (
            <ResponsiveTable>
              <RecordTable
                records={view.records}
                columns={view.effectiveColumns}
                dictOptions={view.dictOptions}
                sortBy={query.sortBy}
                sortDir={query.sortDir}
                onSort={onSort}
                templateNames={Object.fromEntries(
                  (view.meta?.templates ?? []).map((template) => [
                    template.id,
                    template.name,
                  ])
                )}
                userNames={view.userNames}
              />
            </ResponsiveTable>
          )}

          <div className='flex items-center justify-between text-xs text-muted-foreground'>
            <span>
              {t('workRecords.list.total', {
                count: view.total,
              })}
            </span>
            <div className='flex items-center gap-2'>
              <Select
                value={String(query.pageSize)}
                onValueChange={(value) =>
                  setQuery({ ...query, page: 1, pageSize: Number(value) })
                }
              >
                <SelectTrigger
                  className='w-28'
                  aria-label={t('workRecords.list.pageSize')}
                >
                  <SelectValue />
                </SelectTrigger>
                <SelectContent>
                  {[30, 50, 100].map((size) => (
                    <SelectItem key={size} value={String(size)}>
                      {t('workRecords.list.pageSizeOption', { count: size })}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
              <Button
                type='button'
                disabled={query.page <= 1}
                size='sm'
                variant='outline'
                onClick={() => setQuery({ ...query, page: query.page - 1 })}
              >
                {t('workRecords.list.previousPage')}
              </Button>
              <span>
                {t('workRecords.list.currentPage', { page: query.page })}
              </span>
              <Button
                type='button'
                size='sm'
                variant='outline'
                disabled={
                  view.records.length === 0 ||
                  view.records.length < query.pageSize
                }
                onClick={() => setQuery({ ...query, page: query.page + 1 })}
              >
                {t('workRecords.list.nextPage')}
              </Button>
            </div>
          </div>
        </CardContent>
      </Card>

      <WorkRecordImportDialog
        open={importOpen}
        onOpenChange={setImportOpen}
        meta={view.meta}
      />
      <WorkRecordExportDialog
        open={exportOpen}
        onOpenChange={setExportOpen}
        query={query}
        meta={view.meta}
        currentColumns={view.effectiveColumns}
        total={view.total}
      />
    </main>
  )
}
