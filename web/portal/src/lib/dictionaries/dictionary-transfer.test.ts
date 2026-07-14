import { describe, expect, it } from 'vitest'
import {
  parseDictionaryRows,
  serializeDictionaryRows,
} from './dictionary-transfer'

const rows = [
  {
    itemLabel: '高,优先级',
    itemValue: 'high',
    description: '需要"立即"处理',
    sortOrder: 10,
    enabled: true,
  },
]

describe('dictionary transfer', () => {
  it('round trips CSV values containing commas and quotes', () => {
    const content = serializeDictionaryRows(rows, 'csv')

    expect(parseDictionaryRows(content, 'csv')).toEqual(rows)
  })

  it('round trips spreadsheet-friendly TSV', () => {
    const content = serializeDictionaryRows(rows, 'table')

    expect(content).toContain('\t')
    expect(parseDictionaryRows(content, 'table')).toEqual(rows)
  })

  it('rejects files with missing required columns', () => {
    expect(() =>
      parseDictionaryRows('itemLabel,itemValue\n名称,code', 'csv')
    ).toThrow('缺少必需列')
  })
})
