import { describe, expect, it } from 'vitest'
import {
  normalizeListSearch,
  parseListSearch,
  stringifyListSearch,
  toRouteSearch,
} from './search'

describe('work record list route search', () => {
  it('restores full query state from route search', () => {
    const state = normalizeListSearch({
      page: 2,
      pageSize: 50,
      quickView: 'recent_workdays',
      workdayCount: 10,
      templateId: 'tpl1',
      statuses: ['done'],
      dynamicFilters: [
        {
          fieldCode: 'priority',
          operator: 'eq',
          value: 'P1',
        },
      ],
      visibleColumns: ['title', 'custom.priority'],
    })

    expect(state.page).toBe(2)
    expect(state.workdayCount).toBe(10)
    expect(state.dynamicFilters).toHaveLength(1)
    expect(state.visibleColumns).toContain('custom.priority')

    expect(toRouteSearch(state)).toEqual(state)
  })

  it('clamps invalid pagination values', () => {
    const state = normalizeListSearch({
      page: -1,
      pageSize: 999,
      workdayCount: 100,
    })

    expect(state.page).toBe(1)
    expect(state.pageSize).toBe(100)
    expect(state.workdayCount).toBe(60)
  })

  it('roundtrips through URL params', () => {
    const state = normalizeListSearch({
      page: 2,
      pageSize: 50,
      quickView: 'mine',
      workdayCount: 5,
      templateId: 'tpl1',
      statuses: ['done', 'draft'],
      keyword: '日报',
      dynamicFilters: [{ fieldCode: 'priority', operator: 'eq', value: 'P1' }],
      visibleColumns: ['title', 'custom.priority'],
    })

    const url = stringifyListSearch(state)
    const parsed = parseListSearch(url)

    expect(parsed.page).toBe(2)
    expect(parsed.pageSize).toBe(50)
    expect(parsed.quickView).toBe('mine')
    expect(parsed.statuses).toEqual(['done', 'draft'])
    expect(parsed.dynamicFilters).toHaveLength(1)
    expect(parsed.visibleColumns).toEqual(['title', 'custom.priority'])
  })

  it('ignores invalid dynamic filter json', () => {
    const state = parseListSearch(new URLSearchParams('dynamicFilters=bad'))
    expect(state.dynamicFilters).toEqual([])
  })
})
