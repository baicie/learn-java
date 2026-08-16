import type { AuthorizationPrincipal } from '@/auth/authorization-types'
import { describe, expect, it } from 'vitest'
import { filterNavigation } from './filter-navigation'
import { navigation } from './navigation'

function principal(
  permissions: string[],
  roles: string[] = ['normal_user'],
  workRecordDataScope?: 'SELF' | 'ALL'
): AuthorizationPrincipal {
  return {
    userId: 'u1',
    tenantId: 't1',
    username: 'u1',
    displayName: 'U1',
    roles,
    permissions,
    dataScopes: workRecordDataScope
      ? { 'work-record': workRecordDataScope }
      : {},
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

  it('shows each incident center route only with its read permission', () => {
    const alertsOnly = filterNavigation(navigation, principal(['alert:read']))
    const incidentsOnly = filterNavigation(
      navigation,
      principal(['incident:read'])
    )

    expect(JSON.stringify(alertsOnly)).toContain('/alerts')
    expect(JSON.stringify(alertsOnly)).not.toContain('/incidents')
    expect(JSON.stringify(incidentsOnly)).toContain('/incidents')
    expect(JSON.stringify(incidentsOnly)).not.toContain('/alerts')
  })

  it('keeps operations for its independent operational permissions', () => {
    for (const permission of [
      'work-record:analytics',
      'work-record:handover',
      'work-record:approval:act',
    ]) {
      const result = filterNavigation(navigation, principal([permission]))

      expect(JSON.stringify(result)).toContain('/work-records/operations')
    }
  })

  it('requires tenant-wide read permission and scope for AI-only operations access', () => {
    for (const permission of [
      'work-record:ai:generate',
      'work-record:ai:review',
    ]) {
      const tenantWide = filterNavigation(
        navigation,
        principal([permission, 'work-record:read:all'], ['normal_user'], 'ALL')
      )
      const selfOnly = filterNavigation(
        navigation,
        principal([permission, 'work-record:read:all'], ['normal_user'], 'SELF')
      )

      expect(JSON.stringify(tenantWide)).toContain('/work-records/operations')
      expect(JSON.stringify(selfOnly)).not.toContain('/work-records/operations')
    }
  })

  it('does not expose the unimplemented audit log route', () => {
    const result = filterNavigation(
      navigation,
      principal(['audit:read'], ['system_admin'])
    )

    expect(JSON.stringify(result)).not.toContain('/platform/audit-logs')
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
    expect(serialized).toContain('/work-records/templates')
    expect(serialized).not.toContain('/work-records/designer')
    expect(serialized).not.toContain('/work-records/tasks')
  })
})
