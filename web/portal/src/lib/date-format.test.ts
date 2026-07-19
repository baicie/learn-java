import { describe, expect, it } from 'vitest'
import { formatDate, formatDateTime } from './date-format'

describe('date format', () => {
  it('formats a date with padded slash-separated parts', () => {
    expect(formatDate('2026-07-09')).toBe('2026/07/09')
  })

  it('formats a local date time with seconds', () => {
    expect(formatDateTime('2026-07-09T08:05:04')).toBe('2026/07/09 08:05:04')
  })

  it('preserves an invalid source value for diagnosis', () => {
    expect(formatDate('not-a-date')).toBe('not-a-date')
    expect(formatDateTime('not-a-date')).toBe('not-a-date')
  })
})
