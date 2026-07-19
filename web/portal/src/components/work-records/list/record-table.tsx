import { Link } from '@tanstack/react-router'
import { useTranslation } from 'react-i18next'
import { formatDate, formatDateTime } from '@/lib/date-format'
import { Button } from '@/components/ui/button'
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from '@/components/ui/table'
import { EmptyState } from '@/components/feedback/async-state'
import type { DictOptionMap, RecordListColumn, WorkRecord } from './types'

type Props = {
  records: WorkRecord[]
  columns: RecordListColumn[]
  dictOptions?: DictOptionMap
  sortBy: string
  sortDir: 'asc' | 'desc'
  onSort: (sortBy: string, sortDir: 'asc' | 'desc') => void
  templateNames?: Record<string, string>
  userNames?: Record<string, string>
}

export function RecordTable({
  records,
  columns,
  dictOptions = {},
  sortBy,
  sortDir,
  onSort,
  templateNames = {},
  userNames = {},
}: Props) {
  const { t } = useTranslation()

  if (!records.length) {
    return <EmptyState compact title={t('workRecords.list.noRecords')} />
  }

  return (
    <div className='overflow-auto rounded-lg border'>
      <Table className='min-w-[960px]'>
        <TableHeader className='bg-muted'>
          <TableRow>
            {columns.map((column) => (
              <TableHead key={column.key}>
                <Button
                  type='button'
                  variant='ghost'
                  size='sm'
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
                </Button>
              </TableHead>
            ))}
            <TableHead>{t('workRecords.list.actions')}</TableHead>
          </TableRow>
        </TableHeader>
        <TableBody>
          {records.map((record) => (
            <TableRow key={record.id}>
              {columns.map((column) => (
                <TableCell key={column.key}>
                  {renderCell(
                    record,
                    column,
                    dictOptions,
                    templateNames,
                    userNames,
                    t
                  )}
                </TableCell>
              ))}
              <TableCell>
                <Button asChild variant='link' size='sm'>
                  <Link
                    to='/work-records/$recordId'
                    params={{ recordId: record.id }}
                  >
                    {t('workRecords.list.view')}
                  </Link>
                </Button>
              </TableCell>
            </TableRow>
          ))}
        </TableBody>
      </Table>
    </div>
  )
}

type TFunction = (key: string) => string

function renderCell(
  record: WorkRecord,
  column: RecordListColumn,
  dictOptions: DictOptionMap,
  templateNames: Record<string, string>,
  userNames: Record<string, string>,
  t: TFunction
) {
  if (column.source === 'custom' && column.fieldCode) {
    const value = parseCustom(record.customDataJson)[column.fieldCode]
    return renderDynamicValue(value, column, dictOptions)
  }

  switch (column.key) {
    case 'id':
      return <span className='font-mono text-xs'>{record.id}</span>
    case 'title':
      return record.title
    case 'status':
      return statusLabel(record.status, t)
    case 'ownerId':
      return record.ownerId
        ? (userNames[record.ownerId] ?? record.ownerId)
        : '-'
    case 'creatorId':
      return userNames[record.creatorId] ?? record.creatorId
    case 'recordTime':
      return formatDateTime(record.recordTime)
    case 'createdAt':
      return formatDateTime(record.createdAt)
    case 'templateId':
      return templateNames[record.templateId] ?? record.templateId
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

  if (column.fieldType === 'date') {
    return formatDate(String(value))
  }

  if (column.fieldType === 'datetime') {
    return formatDateTime(String(value))
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

function statusLabel(value: string, t: TFunction) {
  const labels: Record<string, string> = {
    draft: t('workRecords.list.status.draft'),
    processing: t('workRecords.list.status.processing'),
    done: t('workRecords.list.status.done'),
    archived: t('workRecords.list.status.archived'),
  }
  return labels[value] ?? value
}
