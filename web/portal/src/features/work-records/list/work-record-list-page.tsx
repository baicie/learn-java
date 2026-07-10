import { useNavigate } from '@tanstack/react-router'
import { ColumnControl } from './column-control'
import { DynamicFilterPanel } from './dynamic-filter-panel'
import { ListToolbar } from './list-toolbar'
import { QuickViewTabs } from './quick-view-tabs'
import { RecordTable } from './record-table'
import type { ListQueryState } from './types'
import { useWorkRecordList } from './use-work-record-list'

type Props = {
  query: ListQueryState
  onQueryChange: (next: ListQueryState) => void
}

export function WorkRecordListPage({ query, onQueryChange }: Props) {
  const navigate = useNavigate()
  const list = useWorkRecordList(query, onQueryChange)

  if (list.loading && !list.meta) {
    return <main className='p-6 text-sm text-muted-foreground'>加载中...</main>
  }

  if (list.error) {
    return (
      <main className='p-6'>
        <div className='rounded-lg border border-red-200 bg-red-50 p-4 text-sm text-red-700'>
          加载失败：{(list.error as Error).message}
        </div>
      </main>
    )
  }

  return (
    <main className='grid gap-4 p-6'>
      <div className='flex items-center justify-between gap-3'>
        <div>
          <h1 className='text-2xl font-semibold'>工作记录</h1>
          <p className='text-sm text-muted-foreground'>
            企业级查询列表 / 动态列 / 动态字段筛选 / 分页
          </p>
        </div>

        <button
          type='button'
          className='rounded-md bg-primary px-3 py-2 text-sm text-primary-foreground'
          onClick={() => navigate({ to: '/work-records/new' })}
        >
          新建记录
        </button>
      </div>

      <QuickViewTabs
        value={list.query.quickView}
        onChange={(quickView) => list.patchQuery({ quickView })}
      />

      {list.query.quickView === 'recent_workdays' ? (
        <label className='flex items-center gap-2 text-sm'>
          最近
          <input
            className='w-20 rounded-md border px-2 py-1'
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

      <ListToolbar
        meta={list.meta}
        query={list.query}
        onChange={list.patchQuery}
      />

      <DynamicFilterPanel
        fields={list.meta?.filterFields ?? []}
        filters={list.query.dynamicFilters}
        onChange={(dynamicFilters) => list.patchQuery({ dynamicFilters })}
      />

      <ColumnControl
        columns={list.meta?.columns ?? []}
        visible={list.query.visibleColumns}
        onChange={(visibleColumns) => list.patchQuery({ visibleColumns })}
      />

      <RecordTable
        records={list.records}
        columns={list.effectiveColumns}
        dictOptions={list.dictOptions}
        sortBy={list.query.sortBy}
        sortDir={list.query.sortDir}
        onSort={(sortBy, sortDir) => list.patchQuery({ sortBy, sortDir })}
      />

      <div className='flex items-center justify-between text-sm'>
        <div>共 {list.total} 条</div>
        <div className='flex items-center gap-2'>
          <select
            className='rounded-md border px-2 py-1'
            value={list.query.pageSize}
            onChange={(event) =>
              list.patchQuery({ pageSize: Number(event.target.value) })
            }
          >
            <option value={20}>20 条/页</option>
            <option value={50}>50 条/页</option>
            <option value={100}>100 条/页</option>
          </select>

          <button
            type='button'
            className='rounded-md border px-3 py-1.5'
            disabled={list.query.page <= 1}
            onClick={() => list.patchQuery({ page: list.query.page - 1 })}
          >
            上一页
          </button>
          <span>
            第 {list.query.page} 页 / 每页 {list.query.pageSize} 条
          </span>
          <button
            type='button'
            className='rounded-md border px-3 py-1.5'
            disabled={list.query.page * list.query.pageSize >= list.total}
            onClick={() => list.patchQuery({ page: list.query.page + 1 })}
          >
            下一页
          </button>
        </div>
      </div>
    </main>
  )
}
