import { describe, expect, it } from 'vitest'
import {
  formatDate,
  formatDateTime,
  startOfIsoWeek,
  toCalendarDate,
  toLocalOffsetDateTime,
} from './date-format'

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

  it.each([
    ['2026-07-27', '2026-07-27'],
    ['2026-08-02', '2026-07-27'],
    ['2026-08-03T12:30:00+08:00', '2026-08-03'],
  ])('finds the ISO week Monday for %s', (value, expected) => {
    expect(startOfIsoWeek(value)).toBe(expected)
  })

  it('keeps explicit-offset dates and restores local dates from UTC instants', () => {
    expect(toCalendarDate('2026-08-03T00:00:00+08:00')).toBe('2026-08-03')
    const localMidnight = new Date(2026, 7, 3)
    expect(toCalendarDate(localMidnight.toISOString())).toBe('2026-08-03')
  })

  it('serializes a local date time without losing its calendar date', () => {
    const local = new Date(2026, 6, 9, 8, 5, 4, 321)
    const serialized = toLocalOffsetDateTime(local)

    expect(serialized.slice(0, 23)).toBe('2026-07-09T08:05:04.321')
    expect(new Date(serialized).getTime()).toBe(local.getTime())
  })

  it.each(['2026-02-30', '2026-13-01', '2026-00-10'])(
    'rejects the invalid calendar date %s',
    (value) => {
      expect(() => toCalendarDate(value)).toThrow(RangeError)
      expect(() => startOfIsoWeek(value)).toThrow(RangeError)
    }
  )
})
