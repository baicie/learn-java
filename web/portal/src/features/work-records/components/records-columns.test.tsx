import { describe, expect, it } from 'vitest'
import { createRecordsColumns } from './records-columns'

describe('createRecordsColumns', () => {
  it('returns the expected column set', () => {
    const columns = createRecordsColumns()
    const ids = columns.map(
      (c) => c.id ?? (c as { accessorKey?: string }).accessorKey
    )
    expect(ids).toContain('select')
    expect(ids).toContain('title')
    expect(ids).toContain('status')
    expect(ids).toContain('ownerId')
    expect(ids).toContain('recordTime')
    expect(ids).toContain('actions')
  })

  it('has 6 columns', () => {
    const columns = createRecordsColumns()
    expect(columns).toHaveLength(6)
  })
})
