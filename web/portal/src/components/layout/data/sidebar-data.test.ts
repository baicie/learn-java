import { describe, expect, it } from 'vitest'
import { getNavGroups } from './sidebar-data'

describe('getNavGroups', () => {
  it('resolves navigation labels with the current translator', () => {
    const zh = getNavGroups((key) => `zh:${key}`)
    const en = getNavGroups((key) => `en:${key}`)

    expect(zh[0]?.title).toBe('zh:nav.dashboard')
    expect(en[0]?.title).toBe('en:nav.dashboard')
  })
})
