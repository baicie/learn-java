import { describe, expect, it } from 'vitest'
import { render } from 'vitest-browser-react'
import { PermissionGate } from './permission-gate'
import { useAuthStore } from '@/stores/auth-store'
import type { AuthorizationPrincipal } from './authorization-types'

function setPrincipal(permissions: string[]) {
  const principal: AuthorizationPrincipal = {
    userId: 'u1',
    tenantId: 't1',
    username: 'u1',
    displayName: 'U1',
    roles: [],
    permissions,
    dataScopes: {},
  }
  useAuthStore.getState().auth.setPrincipal(principal)
  useAuthStore.getState().auth.setAuthorizationLoaded(true)
}

describe('PermissionGate', () => {
  it('renders children when anyOf matches', async () => {
    setPrincipal(['platform:user:read'])
    const screen = await render(
      <PermissionGate anyOf={['platform:user:write', 'platform:user:read']}>
        <span>allowed</span>
      </PermissionGate>
    )
    await expect.element(screen.getByText('allowed')).toBeVisible()
  })

  it('renders fallback when anyOf does not match', async () => {
    setPrincipal(['platform:user:read'])
    const screen = await render(
      <PermissionGate
        anyOf={['platform:role:write']}
        fallback={<span>denied</span>}
      >
        <span>allowed</span>
      </PermissionGate>
    )
    await expect.element(screen.getByText('denied')).toBeVisible()
  })

  it('requires every entry of allOf', async () => {
    setPrincipal(['platform:user:read'])
    const screen = await render(
      <>
        <PermissionGate allOf={['platform:user:read', 'platform:user:write']}>
          <span>first</span>
        </PermissionGate>
        <PermissionGate allOf={['platform:user:read']}>
          <span>second</span>
        </PermissionGate>
      </>
    )
    await expect.element(screen.getByText('second')).toBeVisible()

    setPrincipal(['platform:user:read', 'platform:user:write'])
    const next = await render(
      <PermissionGate allOf={['platform:user:read', 'platform:user:write']}>
        <span>after</span>
      </PermissionGate>
    )
    await expect.element(next.getByText('after')).toBeVisible()
  })
})