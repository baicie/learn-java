export type DictionaryTransferFormat = 'table' | 'csv'

export type DictionaryTransferRow = {
  itemLabel: string
  itemValue: string
  description: string
  sortOrder: number
  enabled: boolean
}

const HEADERS: Array<keyof DictionaryTransferRow> = [
  'itemLabel',
  'itemValue',
  'description',
  'sortOrder',
  'enabled',
]

export function serializeDictionaryRows(
  rows: DictionaryTransferRow[],
  format: DictionaryTransferFormat
) {
  const delimiter = format === 'csv' ? ',' : '\t'
  return [HEADERS, ...rows.map((row) => HEADERS.map((key) => row[key]))]
    .map((values) =>
      values
        .map((value) => escapeCell(String(value), delimiter))
        .join(delimiter)
    )
    .join('\r\n')
}

export function parseDictionaryRows(
  content: string,
  format: DictionaryTransferFormat
): DictionaryTransferRow[] {
  const delimiter = format === 'csv' ? ',' : '\t'
  const [header = [], ...records] = parseDelimited(
    content.replace(/^\uFEFF/, ''),
    delimiter
  )
  const indexes = Object.fromEntries(
    HEADERS.map((name) => [name, header.indexOf(name)])
  ) as Record<keyof DictionaryTransferRow, number>

  const missing = HEADERS.filter((name) => indexes[name] < 0)
  if (missing.length) throw new Error(`缺少必需列：${missing.join('、')}`)

  return records
    .filter((record) => record.some((cell) => cell.trim()))
    .map((record, index) => {
      const itemLabel = record[indexes.itemLabel]?.trim() ?? ''
      const itemValue = record[indexes.itemValue]?.trim() ?? ''
      if (!itemLabel || !itemValue) {
        throw new Error(`第 ${index + 2} 行的名称和值不能为空`)
      }
      const sortOrder = Number(record[indexes.sortOrder] || 0)
      if (!Number.isFinite(sortOrder)) {
        throw new Error(`第 ${index + 2} 行的排序必须是数字`)
      }
      return {
        itemLabel,
        itemValue,
        description: record[indexes.description]?.trim() ?? '',
        sortOrder,
        enabled: !['false', '0', '否', '禁用'].includes(
          (record[indexes.enabled] ?? '').trim().toLowerCase()
        ),
      }
    })
}

function escapeCell(value: string, delimiter: string) {
  return value.includes(delimiter) || /["\r\n]/.test(value)
    ? `"${value.replace(/"/g, '""')}"`
    : value
}

function parseDelimited(content: string, delimiter: string) {
  const rows: string[][] = []
  let row: string[] = []
  let cell = ''
  let quoted = false

  for (let index = 0; index < content.length; index += 1) {
    const char = content[index]
    if (char === '"') {
      if (quoted && content[index + 1] === '"') {
        cell += '"'
        index += 1
      } else {
        quoted = !quoted
      }
    } else if (char === delimiter && !quoted) {
      row.push(cell)
      cell = ''
    } else if ((char === '\n' || char === '\r') && !quoted) {
      if (char === '\r' && content[index + 1] === '\n') index += 1
      row.push(cell)
      rows.push(row)
      row = []
      cell = ''
    } else {
      cell += char
    }
  }
  if (cell || row.length) {
    row.push(cell)
    rows.push(row)
  }
  return rows
}
