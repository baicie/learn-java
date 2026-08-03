import { useEffect } from 'react'
import { BellRing } from 'lucide-react'
import { useAlerts } from '@/hooks/operations/use-operations'
import { useTableUrlState } from '@/hooks/use-table-url-state'
import { AlertFilterBar } from '@/components/alerts/alert-filter-bar'
import { AlertsTable } from '@/components/alerts/alerts-table'
import {
  EmptyState,
  ErrorState,
  TableLoadingState,
} from '@/components/feedback/async-state'
import { Header } from '@/components/layout/header'
import { Main } from '@/components/layout/main'
import { ProfileDropdown } from '@/components/profile-dropdown'
import { Search } from '@/components/search'
import { ThemeSwitch } from '@/components/theme-switch'
import {
  alertFilterValues,
  filterAlerts,
  paginateAlerts,
  type AlertFilter,
} from './alert-filter'

export type AlertsSearch = AlertFilter & {
  page: number
  pageSize: number
}

export function AlertsPage({
  search,
  onSearch,
}: {
  search: AlertsSearch
  onSearch: (next: Partial<AlertsSearch>, replace?: boolean) => void
}) {
  const alerts = useAlerts()
  const tableUrlState = useTableUrlState({
    search,
    navigate: ({ search: nextSearch, replace }) => {
      if (nextSearch === true) return
      const next =
        typeof nextSearch === 'function' ? nextSearch(search) : nextSearch
      onSearch(next as Partial<AlertsSearch>, replace)
    },
    pagination: { defaultPage: 1, defaultPageSize: 20 },
    globalFilter: { key: 'keyword' },
    columnFilters: [
      { columnId: 'status', searchKey: 'statuses', type: 'array' },
      { columnId: 'severity', searchKey: 'severities', type: 'array' },
    ],
  })
  const filteredAlerts = filterAlerts(alerts.data ?? [], {
    keyword: tableUrlState.globalFilter,
    statuses: alertFilterValues(tableUrlState.columnFilters, 'status'),
    severities: alertFilterValues(tableUrlState.columnFilters, 'severity'),
  })
  const pagedAlerts = paginateAlerts(filteredAlerts, {
    page: tableUrlState.pagination.pageIndex + 1,
    pageSize: tableUrlState.pagination.pageSize,
  })

  useEffect(() => {
    if (!alerts.isLoading && !alerts.isError) {
      tableUrlState.ensurePageInRange(pagedAlerts.pageCount)
    }
  }, [alerts.isError, alerts.isLoading, pagedAlerts.pageCount, tableUrlState])

  return (
    <>
      <Header fixed>
        <div className='ml-auto flex items-center gap-2'>
          <Search />
          <ThemeSwitch />
          <ProfileDropdown />
        </div>
      </Header>
      <Main className='grid gap-6'>
        <div className='grid gap-1'>
          <h1 className='text-2xl font-bold'>告警中心</h1>
          <p className='text-sm text-muted-foreground'>
            查看已归一化的监控告警，并按状态和严重度快速定位异常。
          </p>
        </div>
        <AlertFilterBar
          keyword={tableUrlState.globalFilter ?? ''}
          onKeywordChange={(value) =>
            tableUrlState.onGlobalFilterChange?.(value)
          }
          columnFilters={tableUrlState.columnFilters}
          onColumnFiltersChange={tableUrlState.onColumnFiltersChange}
        />
        {alerts.isLoading ? (
          <TableLoadingState rows={8} columns={6} />
        ) : alerts.isError ? (
          <ErrorState
            error={alerts.error}
            onRetry={() => void alerts.refetch()}
          />
        ) : !alerts.data?.length ? (
          <EmptyState
            title='还没有告警'
            description='数据源同步到告警后，会在这里展示归一化事件。'
            icon={<BellRing className='size-6' />}
          />
        ) : !filteredAlerts.length ? (
          <EmptyState
            title='没有匹配的告警'
            description='调整关键字、状态或严重度筛选条件后重试。'
            icon={<BellRing className='size-6' />}
          />
        ) : (
          <AlertsTable
            items={pagedAlerts.items}
            page={tableUrlState.pagination.pageIndex + 1}
            pageCount={pagedAlerts.pageCount}
            total={pagedAlerts.total}
            onPageChange={(page) =>
              tableUrlState.onPaginationChange((current) => ({
                ...current,
                pageIndex: page - 1,
              }))
            }
          />
        )}
      </Main>
    </>
  )
}
