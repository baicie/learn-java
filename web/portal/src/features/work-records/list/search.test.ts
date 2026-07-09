import { describe, expect, it } from 'vitest'
import { parseListSearch, stringifyListSearch } from './search'

describe('work record list search', () => {
  it('parses and stringifies url search', () => {
    const state = parseListSearch(
      new URLSearchParams(
        'page=2&pageSize=50&quickView=mine&status=done&status=draft&keyword=日报&column=title&column=custom.priority',
      ),
    )

    expect(state.page).toBe(2)
    expect(state.statuses).toEqual(['done', 'draft'])
    expect(state.visibleColumns).toEqual(['title', 'custom.priority'])

    const next = stringifyListSearch(state)
    expect(next.get('quickView')).toBe('mine')
    expect(next.getAll('status')).toEqual(['done', 'draft'])
  })

  it('ignores invalid dynamic filter json', () => {
    const state = parseListSearch(new URLSearchParams('dynamicFilters=bad'))
    expect(state.dynamicFilters).toEqual([])
  })
})