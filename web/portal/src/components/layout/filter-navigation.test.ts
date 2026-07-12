import { describe, expect, it } from 'vitest'
import { filterNavigation } from './filter-navigation'
import { navigation } from './navigation'
import type { AuthorizationPrincipal } from '@/features/auth/authorization-types'

function principal(
  permissions: string[],
  roles: string[] = ['normal_user']
): AuthorizationPrincipal {
  return {
    userId: 'u1',
    tenantId: 't1',
    username: 'u1',
    displayName: 'U1',
    roles,
    permissions,
    dataScopes: {},
  }
}

describe('filterNavigation', () => {
  it('removes platform items without platform permissions', () => {
    const result = filterNavigation(
      navigation,
      principal(['work-record:read:self'])
    )

    const serialized = JSON.stringify(result)
    expect(serialized).toContain('/work-records')
    expect(serialized).not.toContain('/platform/users')
    expect(serialized).not.toContain('/platform/roles')
    expect(serialized).not.toContain('/platform/dictionaries')
    expect(serialized).not.toContain('/platform/calendars')
    expect(serialized).not.toContain('/platform/audit-logs')
  })

  it('keeps role management for authorized admin', () => {
    const result = filterNavigation(
      navigation,
      principal(['platform:role:read'], ['system_admin'])
    )

    expect(JSON.stringify(result)).toContain('/platform/roles')
  })

  it('keeps dashboard when principal is missing', () => {
    const result = filterNavigation(navigation, null)

    expect(result.map((item) => item.to)).toContain('/')
  })

  it('keeps every visible child when the group itself is unpermissioned but children are', () => {
    const result = filterNavigation(
      navigation,
      principal([
        'work-record:read:self',
        'work-record:template:read',
        'work-record:import',
      ])
    )

    const serialized = JSON.stringify(result)
    expect(serialized).toContain('/work-records')
    expect(serialized).toContain('/work-records/designer')
    expect(serialized).toContain('/work-records/tasks')
  })
})