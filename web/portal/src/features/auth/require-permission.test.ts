import { describe, expect, it } from 'vitest'
import { isRedirect } from '@tanstack/react-router'
import { useAuthStore } from '@/stores/auth-store'
import { requireAnyPermission } from './require-permission'

describe('requireAnyPermission', () => {
  it('redirects to sign-in when principal is missing', () => {
    useAuthStore.getState().auth.reset()

    let caught: unknown = null
    try {
      requireAnyPermission(['platform:user:read'])
    } catch (error) {
      caught = error
    }

    expect(isRedirect(caught)).toBe(true)
  })

  it('redirects to /403 when principal lacks every permission', () => {
    useAuthStore.getState().auth.setPrincipal({
      userId: 'u1',
      tenantId: 't1',
      username: 'u1',
      displayName: 'U1',
      roles: [],
      permissions: ['work-record:read:self'],
      dataScopes: {},
    })

    let caught: unknown = null
    try {
      requireAnyPermission(['platform:role:write'])
    } catch (error) {
      caught = error
    }

    expect(isRedirect(caught)).toBe(true)
  })

  it('returns the snapshot when principal has at least one matching permission', () => {
    useAuthStore.getState().auth.setPrincipal({
      userId: 'u1',
      tenantId: 't1',
      username: 'u1',
      displayName: 'U1',
      roles: [],
      permissions: ['platform:user:read'],
      dataScopes: {},
    })
    useAuthStore.getState().auth.setAuthorizationLoaded(true)

    const snapshot = requireAnyPermission([
      'platform:user:write',
      'platform:user:read',
    ])

    expect(snapshot.userId).toBe('u1')
    expect(snapshot.permissions).toContain('platform:user:read')

    useAuthStore.getState().auth.reset()
  })
})