import { describe, expect, it } from 'vitest'
import { render } from 'vitest-browser-react'
import { useAuthStore } from '@/stores/auth-store'
import type { AuthorizationPrincipal } from './authorization-types'
import { useAuthorization } from './use-authorization'

function principal(permissions: string[]): AuthorizationPrincipal {
  return {
    userId: 'u1',
    tenantId: 't1',
    username: 'u1',
    displayName: 'U1',
    roles: ['r'],
    permissions,
    dataScopes: {},
  }
}

function Harness() {
  const snapshot = useAuthorization()
  return <span data-testid='auth'>{snapshot?.userId ?? 'none'}</span>
}

describe('useAuthorization', () => {
  it('returns null when no principal is loaded', async () => {
    useAuthStore.getState().auth.reset()
    const screen = await render(<Harness />)
    await expect.element(screen.getByTestId('auth')).toHaveTextContent('none')
  })

  it('returns the principal once it is loaded', async () => {
    const expected = principal(['platform:user:read'])
    useAuthStore.getState().auth.setPrincipal(expected)
    useAuthStore.getState().auth.setAuthorizationLoaded(true)
    const screen = await render(<Harness />)
    await expect.element(screen.getByTestId('auth')).toHaveTextContent('u1')
    useAuthStore.getState().auth.reset()
  })
})
