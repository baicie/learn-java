import { useState } from 'react'
import { useNavigate } from '@tanstack/react-router'
import { useTranslation } from 'react-i18next'
import { Card, CardContent } from '@/components/ui/card'
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
import { useWorkRecordList } from './use-work-record-list'
import { WorkdaySummaryCard } from './workday-summary-card'

export function WorkRecordListPage() {
  const navigate = useNavigate()
  const { t } = useTranslation()
  const [query, setQuery] = useState<ListQueryState>(() =>
    buildEmptyListQuery()
  )
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
          {t('workRecords.list.subtitle')}
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
        description={t('workRecords.list.subtitle')}
        actions={
          <button
            type='button'
            className='rounded-md bg-primary px-3 py-1.5 text-sm text-primary-foreground'
            onClick={() =>
              navigate({
                to: '/work-records/new',
              })
            }
          >
            {t('workRecords.list.create')}
          </button>
        }
      >
        <ListToolbar
          meta={view.meta}
          query={query}
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
              <button
                type='button'
                disabled={query.page <= 1}
                className='rounded-md border px-2 py-1 text-xs disabled:opacity-50'
                onClick={() => setQuery({ ...query, page: query.page - 1 })}
              >
                {t('workRecords.list.previousPage')}
              </button>
              <span>
                {t('workRecords.list.currentPage', { page: query.page })}
              </span>
              <button
                type='button'
                className='rounded-md border px-2 py-1 text-xs disabled:opacity-50'
                disabled={
                  view.records.length === 0 ||
                  view.records.length < query.pageSize
                }
                onClick={() => setQuery({ ...query, page: query.page + 1 })}
              >
                {t('workRecords.list.nextPage')}
              </button>
            </div>
          </div>
        </CardContent>
      </Card>
    </main>
  )
}
