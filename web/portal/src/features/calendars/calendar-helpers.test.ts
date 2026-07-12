import { describe, expect, it } from 'vitest'
import type { CalendarDay } from './api'
import {
  buildDayMap,
  formatDayKey,
  getDayClassName,
  lookupDay,
  pickDayKind,
} from './calendar-helpers'

const day = (overrides: Partial<CalendarDay>): CalendarDay => ({
  id: 'd',
  tenantId: 't',
  calendarId: 'c',
  calendarDate: '2026-04-01',
  dayOfWeek: 3,
  dayType: 'WORKDAY',
  workday: true,
  holidayCode: null,
  holidayName: null,
  sourceType: 'manual',
  remark: null,
  createdBy: null,
  createdAt: null,
  updatedAt: null,
  ...overrides,
})

describe('formatDayKey', () => {
  it('pads single-digit month and day', () => {
    expect(formatDayKey(new Date(2026, 0, 5))).toBe('2026-01-05')
    expect(formatDayKey(new Date(2026, 11, 31))).toBe('2026-12-31')
  })
})

describe('buildDayMap', () => {
  it('indexes days by calendarDate', () => {
    const map = buildDayMap([
      day({ calendarDate: '2026-04-01', id: 'a' }),
      day({ calendarDate: '2026-04-02', id: 'b' }),
    ])
    expect(map.size).toBe(2)
    expect(map.get('2026-04-02')?.id).toBe('b')
  })
})

describe('lookupDay', () => {
  it('returns matching record for given date', () => {
    const map = buildDayMap([day({ calendarDate: '2026-04-01', id: 'a' })])
    expect(lookupDay(map, new Date(2026, 3, 1))?.id).toBe('a')
    expect(lookupDay(map, new Date(2026, 3, 2))).toBeUndefined()
  })
})

describe('pickDayKind', () => {
  it('returns workday when missing or workday=true', () => {
    expect(pickDayKind(undefined).label).toBe('workday')
    expect(pickDayKind(day({ workday: true })).label).toBe('workday')
  })

  it('returns off when workday=false', () => {
    expect(pickDayKind(day({ workday: false })).label).toBe('off')
  })
})

describe('getDayClassName', () => {
  it('dims outside days', () => {
    expect(
      getDayClassName({ dayData: undefined, outside: true, today: false })
    ).toMatch(/opacity-50/)
  })

  it('colors holiday red', () => {
    const cls = getDayClassName({
      dayData: day({ dayType: 'HOLIDAY', workday: false }),
      outside: false,
      today: false,
    })
    expect(cls).toContain('bg-red-100')
  })

  it('colors adjusted workday green', () => {
    const cls = getDayClassName({
      dayData: day({ dayType: 'ADJUSTED_WORKDAY', workday: true }),
      outside: false,
      today: false,
    })
    expect(cls).toContain('bg-emerald-100')
  })

  it('marks non-workday as muted when no override present', () => {
    const cls = getDayClassName({
      dayData: day({ dayType: 'WEEKEND', workday: false }),
      outside: false,
      today: false,
    })
    expect(cls).toContain('text-muted-foreground')
  })

  it('returns empty class for ordinary workday', () => {
    expect(
      getDayClassName({
        dayData: day({ workday: true }),
        outside: false,
        today: false,
      })
    ).toBe('')
  })

  it('highlights today with primary ring when no extra info', () => {
    expect(
      getDayClassName({ dayData: undefined, outside: false, today: true })
    ).toMatch(/ring-primary/)
  })

  it('keeps the today ring even when day has color', () => {
    const cls = getDayClassName({
      dayData: day({ workday: true }),
      outside: false,
      today: true,
    })
    expect(cls).toMatch(/ring-primary/)
  })
})
