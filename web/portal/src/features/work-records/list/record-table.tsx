import { Link } from '@tanstack/react-router'
import type { DictOptionMap, RecordListColumn, WorkRecord } from './types'

type Props = {
  records: WorkRecord[]
  columns: RecordListColumn[]
  dictOptions?: DictOptionMap
  sortBy: string
  sortDir: 'asc' | 'desc'
  onSort: (sortBy: string, sortDir: 'asc' | 'desc') => void
}

export function RecordTable({
  records,
  columns,
  dictOptions = {},
  sortBy,
  sortDir,
  onSort,
}: Props) {
  if (!records.length) {
    return (
      <div className='rounded-lg border border-dashed p-10 text-center text-sm text-muted-foreground'>
        暂无记录
      </div>
    )
  }

  return (
    <div className='overflow-auto rounded-lg border'>
      <table className='w-full min-w-[960px] text-sm'>
        <thead className='bg-muted'>
          <tr>
            {columns.map((column) => (
              <th key={column.key} className='px-3 py-2 text-left font-medium'>
                <button
                  type='button'
                  disabled={!column.sortable}
                  onClick={() =>
                    onSort(
                      column.key,
                      sortBy === column.key && sortDir === 'desc'
                        ? 'asc'
                        : 'desc'
                    )
                  }
                >
                  {column.title}
                  {sortBy === column.key
                    ? ` ${sortDir === 'asc' ? '↑' : '↓'}`
                    : ''}
                </button>
              </th>
            ))}
            <th className='px-3 py-2 text-left font-medium'>操作</th>
          </tr>
        </thead>
        <tbody>
          {records.map((record) => (
            <tr key={record.id} className='border-t'>
              {columns.map((column) => (
                <td key={column.key} className='px-3 py-2'>
                  {renderCell(record, column, dictOptions)}
                </td>
              ))}
              <td className='px-3 py-2'>
                <Link
                  className='text-primary underline'
                  to='/work-records/$recordId'
                  params={{ recordId: record.id }}
                >
                  查看
                </Link>
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  )
}

function renderCell(
  record: WorkRecord,
  column: RecordListColumn,
  dictOptions: DictOptionMap
) {
  if (column.source === 'custom' && column.fieldCode) {
    const value = parseCustom(record.customDataJson)[column.fieldCode]
    return renderDynamicValue(value, column, dictOptions)
  }

  switch (column.key) {
    case 'title':
      return record.title
    case 'status':
      return statusLabel(record.status)
    case 'ownerId':
      return record.ownerId ?? '-'
    case 'creatorId':
      return record.creatorId
    case 'recordTime':
      return formatDate(record.recordTime)
    case 'createdAt':
      return formatDate(record.createdAt)
    case 'templateId':
      return record.templateId
    default:
      return '-'
  }
}

function renderDynamicValue(
  value: unknown,
  column: RecordListColumn,
  dictOptions: DictOptionMap
) {
  if (value === null || value === undefined || value === '') {
    return '-'
  }

  const labels = column.dictCode
    ? new Map(
        (dictOptions[column.dictCode] ?? []).map((item) => [
          item.value,
          item.enabled ? item.label : `${item.label}（已禁用）`,
        ])
      )
    : new Map<string, string>()

  const label = (item: unknown) => labels.get(String(item)) ?? String(item)

  if (Array.isArray(value)) {
    return value.map(label).join('、')
  }

  if (column.fieldType === 'boolean') {
    return value === true ? '是' : '否'
  }

  return label(value)
}

function parseCustom(json: string) {
  try {
    const parsed = JSON.parse(json || '{}')
    return parsed && typeof parsed === 'object' ? parsed : {}
  } catch {
    return {}
  }
}

function formatDate(value: string) {
  const date = new Date(value)
  return Number.isNaN(date.getTime()) ? value : date.toLocaleString()
}

function statusLabel(value: string) {
  const labels: Record<string, string> = {
    draft: '草稿',
    processing: '处理中',
    done: '已完成',
    archived: '已归档',
  }
  return labels[value] ?? value
}
