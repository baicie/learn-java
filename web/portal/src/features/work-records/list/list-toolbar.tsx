import type { ListQueryState, RecordListMeta } from './types'

type Props = {
  meta?: RecordListMeta
  query: ListQueryState
  onChange: (patch: Partial<ListQueryState>) => void
}

export function ListToolbar({ meta, query, onChange }: Props) {
  return (
    <div className='grid gap-3 rounded-lg border p-4'>
      <div className='grid gap-3 md:grid-cols-4'>
        <input
          className='rounded-md border bg-background px-3 py-2 text-sm'
          placeholder='搜索标题'
          value={query.keyword}
          onChange={(event) => onChange({ keyword: event.target.value })}
        />

        <select
          className='rounded-md border bg-background px-3 py-2 text-sm'
          value={query.templateId}
          onChange={(event) => onChange({ templateId: event.target.value })}
        >
          <option value=''>全部模板</option>
          {(meta?.templates ?? []).map((template) => (
            <option key={template.id} value={template.id}>
              {template.name}
            </option>
          ))}
        </select>

        <input
          className='rounded-md border bg-background px-3 py-2 text-sm'
          placeholder='负责人 ID'
          value={query.ownerId}
          onChange={(event) => onChange({ ownerId: event.target.value })}
        />

        <input
          className='rounded-md border bg-background px-3 py-2 text-sm'
          placeholder='创建人 ID'
          value={query.creatorId}
          onChange={(event) => onChange({ creatorId: event.target.value })}
        />
      </div>

      <div className='grid gap-3 md:grid-cols-4'>
        <select
          className='rounded-md border bg-background px-3 py-2 text-sm'
          value={query.statuses[0] ?? ''}
          onChange={(event) =>
            onChange({ statuses: event.target.value ? [event.target.value] : [] })
          }
        >
          <option value=''>全部状态</option>
          <option value='draft'>草稿</option>
          <option value='processing'>处理中</option>
          <option value='done'>已完成</option>
          <option value='archived'>已归档</option>
        </select>

        <input
          className='rounded-md border bg-background px-3 py-2 text-sm'
          type='datetime-local'
          value={query.recordTimeFrom}
          onChange={(event) => onChange({ recordTimeFrom: event.target.value })}
        />

        <input
          className='rounded-md border bg-background px-3 py-2 text-sm'
          type='datetime-local'
          value={query.recordTimeTo}
          onChange={(event) => onChange({ recordTimeTo: event.target.value })}
        />

        <button
          type='button'
          className='rounded-md border px-3 py-2 text-sm'
          onClick={() =>
            onChange({
              keyword: '',
              templateId: '',
              statuses: [],
              ownerId: '',
              creatorId: '',
              recordTimeFrom: '',
              recordTimeTo: '',
              dynamicFilters: [],
            })
          }
        >
          重置筛选
        </button>
      </div>
    </div>
  )
}