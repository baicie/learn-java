import { useState } from 'react'
import { useNavigate } from '@tanstack/react-router'
import { Download, Plus } from 'lucide-react'
import { Button } from '@/components/ui/button'
import {
  EmptyState,
  ErrorState,
  PageLoadingState,
  QueryStateBoundary,
  TableLoadingState,
} from '@/components/feedback/async-state'
import { ResponsiveTable } from '@/components/layout/responsive-table'
import { TableToolbar } from '@/components/layout/table-toolbar'
import { PermissionGate } from '@/components/permission-gate'
import { ColumnControl } from './column-control'
import { DynamicFilterPanel } from './dynamic-filter-panel'
import { ListToolbar } from './list-toolbar'
import { QuickViewTabs } from './quick-view-tabs'
import { RecordTable } from './record-table'
import type { ListQueryState } from './types'
import { useWorkRecordList } from './use-work-record-list'
import { WorkRecordExportDialog } from './work-record-export-dialog'
import { WorkdaySummaryCard } from './workday-summary-card'

type Props = {
  query: ListQueryState
  onQueryChange: (next: ListQueryState) => void
}

export function WorkRecordListPage({ query, onQueryChange }: Props) {
  const navigate = useNavigate()
  const list = useWorkRecordList(query, onQueryChange)
  const [exportOpen, setExportOpen] = useState(false)

  if (list.initialLoading) {
    return <PageLoadingState />
  }

  if (list.pageError) {
    return (
      <main className='p-4 md:p-6'>
        <ErrorState
          error={list.pageError}
          onRetry={() => {
            void list.retryPage()
          }}
        />
      </main>
    )
  }

  const createButton = (
    <PermissionGate any={['work-record:write']}>
      <Button
        type='button'
        onClick={() =>
          navigate({
            to: '/work-records/new',
          })
        }
      >
        <Plus className='mr-2 size-4' />
        新建记录
      </Button>
    </PermissionGate>
  )

  const activeFilterCount =
    list.query.dynamicFilters.length +
    list.query.statuses.length +
    (list.query.templateId ? 1 : 0) +
    (list.query.ownerId ? 1 : 0) +
    (list.query.creatorId ? 1 : 0)

  return (
    <>
      <main className='grid gap-4 p-4 md:gap-6 md:p-6'>
        <header className='flex flex-col gap-4 sm:flex-row sm:items-start sm:justify-between'>
          <div>
            <h1 className='text-xl font-semibold md:text-2xl'>工作记录</h1>
            <p className='text-sm text-muted-foreground'>
              企业级查询列表 / 动态列 / 动态字段筛选 / 分页
            </p>
          </div>

          <div className='flex flex-wrap gap-2'>
            <PermissionGate any={['work-record:export']}>
              <Button
                type='button'
                variant='outline'
                onClick={() => setExportOpen(true)}
              >
                <Download className='mr-2 size-4' />
                导出
              </Button>
            </PermissionGate>

            {createButton}
          </div>
        </header>

        <QuickViewTabs
          value={list.query.quickView}
          available={list.meta?.quickViews ?? []}
          onChange={(quickView) => list.patchQuery({ quickView })}
        />

        {list.query.quickView === 'this_work_month' ? (
          <WorkdaySummaryCard
            summary={list.workdaySummary}
            loading={list.workdaySummaryLoading}
            error={list.workdaySummaryError}
          />
        ) : null}

        {list.query.quickView === 'recent_workdays' ? (
          <label className='flex flex-wrap items-center gap-2 text-sm'>
            最近
            <input
              className='h-9 w-24 rounded-md border bg-background px-2'
              type='number'
              min={1}
              max={60}
              value={list.query.workdayCount}
              onChange={(event) =>
                list.patchQuery({
                  workdayCount: Number(event.target.value),
                })
              }
            />
            个工作日
          </label>
        ) : null}

        <TableToolbar
          search={
            <ListToolbar
              meta={list.meta}
              query={list.query}
              onChange={list.patchQuery}
            />
          }
          filters={
            <>
              <DynamicFilterPanel
                fields={list.meta?.filterFields ?? []}
                filters={list.query.dynamicFilters}
                onChange={(dynamicFilters) =>
                  list.patchQuery({ dynamicFilters })
                }
              />

              <ColumnControl
                columns={list.meta?.columns ?? []}
                visible={list.query.visibleColumns}
                onChange={(visibleColumns) =>
                  list.patchQuery({ visibleColumns })
                }
              />
            </>
          }
          activeFilterCount={activeFilterCount}
          onReset={() =>
            list.patchQuery({
              dynamicFilters: [],
              visibleColumns: [],
              statuses: [],
              templateId: '',
              ownerId: '',
              creatorId: '',
              page: 1,
            })
          }
        />

        <div className='relative'>
          {list.tableRefreshing ? (
            <div className='absolute top-3 right-3 z-10 rounded-md border bg-background/90 px-3 py-1 text-xs text-muted-foreground shadow-sm'>
              正在刷新...
            </div>
          ) : null}

          <QueryStateBoundary
            loading={list.tableLoading}
            error={list.tableError}
            empty={list.records.length === 0}
            loadingFallback={
              <TableLoadingState
                columns={Math.max(3, list.effectiveColumns.length)}
              />
            }
            errorFallback={
              <ErrorState
                compact
                error={list.tableError}
                onRetry={() => {
                  void list.retryTable()
                }}
              />
            }
            emptyFallback={
              <EmptyState
                title='暂无工作记录'
                description='当前筛选条件下没有符合条件的记录。'
                action={createButton}
              />
            }
          >
            <ResponsiveTable>
              <RecordTable
                records={list.records}
                columns={list.effectiveColumns}
                dictOptions={list.dictOptions}
                sortBy={list.query.sortBy}
                sortDir={list.query.sortDir}
                onSort={(sortBy, sortDir) =>
                  list.patchQuery({ sortBy, sortDir })
                }
              />
            </ResponsiveTable>
          </QueryStateBoundary>
        </div>

        <div className='flex flex-col gap-3 text-sm sm:flex-row sm:items-center sm:justify-between'>
          <div>共 {list.total} 条</div>

          <div className='flex flex-wrap items-center gap-2'>
            <select
              className='h-9 rounded-md border bg-background px-2'
              value={list.query.pageSize}
              onChange={(event) =>
                list.patchQuery({
                  pageSize: Number(event.target.value),
                })
              }
            >
              <option value={20}>20 条/页</option>
              <option value={50}>50 条/页</option>
              <option value={100}>100 条/页</option>
            </select>

            <Button
              type='button'
              size='sm'
              variant='outline'
              disabled={list.query.page <= 1}
              onClick={() => list.patchQuery({ page: list.query.page - 1 })}
            >
              上一页
            </Button>

            <span className='whitespace-nowrap'>第 {list.query.page} 页</span>

            <Button
              type='button'
              size='sm'
              variant='outline'
              disabled={list.query.page * list.query.pageSize >= list.total}
              onClick={() => list.patchQuery({ page: list.query.page + 1 })}
            >
              下一页
            </Button>
          </div>
        </div>
      </main>

      <WorkRecordExportDialog
        open={exportOpen}
        onOpenChange={setExportOpen}
        query={list.query}
        meta={list.meta}
        currentColumns={list.effectiveColumns}
        total={list.total}
      />
    </>
  )
}
