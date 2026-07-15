import { describe, expect, it } from 'vitest'
import { useAuthStore } from '@/stores/auth-store'
import { getNavGroups } from './sidebar-data'

describe('getNavGroups', () => {
  it('resolves navigation labels with the current translator', () => {
    const zh = getNavGroups((key) => `zh:${key}`)
    const en = getNavGroups((key) => `en:${key}`)

    expect(zh[0]?.title).toBe('zh:nav.dashboard')
    expect(en[0]?.title).toBe('en:nav.dashboard')
  })

  it('places form designer under platform management', () => {
    useAuthStore.getState().auth.setPrincipal({
      userId: 'admin',
      tenantId: 'tenant-1',
      username: 'admin',
      displayName: 'Admin',
      roles: ['admin'],
      permissions: [
        'work-record:read:all',
        'work-record:template:read',
        'work-record:analytics',
        'platform:user:read',
        'platform:role:read',
        'platform:dict:read',
        'platform:calendar:read',
        'platform:audit:read',
      ],
      dataScopes: {},
    })
    const groups = getNavGroups((key) => key)
    const workRecords = groups.find(
      (group) => group.title === 'nav.workRecords.group'
    )
    const platform = groups.find(
      (group) => group.title === 'nav.platform.group'
    )

    expect(workRecords?.items.map((item) => item.url)).not.toContain(
      '/work-records/templates'
    )
    expect(platform?.items.map((item) => item.url)).toContain(
      '/work-records/templates'
    )
  })
})
