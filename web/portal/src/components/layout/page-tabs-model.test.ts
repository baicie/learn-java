import { describe, expect, it } from 'vitest'
import {
  closeTab,
  readStoredTabs,
  visitTab,
  type PageTab,
} from './page-tabs-model'

const dashboard: PageTab = { href: '/', title: 'Dashboard' }
const assets: PageTab = { href: '/assets', title: 'Assets' }
const users: PageTab = { href: '/platform/users', title: 'Users' }

describe('page tabs model', () => {
  it('adds a visited page once and refreshes its title', () => {
    expect(visitTab([], dashboard)).toEqual([dashboard])
    expect(
      visitTab([dashboard, assets], {
        href: '/assets',
        title: 'Asset list',
      })
    ).toEqual([dashboard, { href: '/assets', title: 'Asset list' }])
  })

  it('reuses the same tab when only search parameters change', () => {
    expect(
      visitTab([{ href: '/assets?page=1', title: 'Assets' }], {
        href: '/assets?page=2&status=active',
        title: 'Assets',
      })
    ).toEqual([{ href: '/assets?page=2&status=active', title: 'Assets' }])
  })

  it('closes an inactive tab without requesting navigation', () => {
    expect(closeTab([dashboard, assets, users], '/', '/assets')).toEqual({
      tabs: [assets, users],
    })
  })

  it('closes the active tab and prefers the tab on its right', () => {
    expect(closeTab([dashboard, assets, users], '/assets', '/assets')).toEqual({
      tabs: [dashboard, users],
      nextHref: '/platform/users',
    })
  })

  it('falls back to the tab on the left when closing the last active tab', () => {
    expect(
      closeTab([dashboard, assets, users], '/platform/users', '/platform/users')
    ).toEqual({
      tabs: [dashboard, assets],
      nextHref: '/assets',
    })
  })

  it('keeps the only open tab', () => {
    expect(closeTab([dashboard], '/', '/')).toEqual({ tabs: [dashboard] })
  })

  it('reads only valid tabs from storage and tolerates damaged data', () => {
    const validStorage = {
      getItem: () => JSON.stringify([dashboard, { href: 1, title: 'Bad' }]),
    }
    const damagedStorage = { getItem: () => '{not-json' }

    expect(readStoredTabs(validStorage)).toEqual([dashboard])
    expect(readStoredTabs(damagedStorage)).toEqual([])
  })
})
