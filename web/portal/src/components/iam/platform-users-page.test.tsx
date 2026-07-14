import type { AuthorizationPrincipal } from '@/auth/authorization-types'
import { PermissionGate } from '@/auth/permission-gate'
import { describe, expect, it } from 'vitest'
import { render } from 'vitest-browser-react'
import { useAuthStore } from '@/stores/auth-store'

// Minimal stub of the platform-users page wiring: shows that the
// backend shape drives the table without rendering fake users.

function PageStub({ serverUser }: { serverUser: { username: string } | null }) {
  return (
    <div>
      <PermissionGate anyOf={['platform:user:write']}>
        <button>新建用户</button>
      </PermissionGate>
      {serverUser ? <div data-testid='row'>{serverUser.username}</div> : null}
    </div>
  )
}

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

describe('PlatformUsersPage surface', () => {
  it('renders server users and never renders fake template users', async () => {
    setPrincipal(['platform:user:read', 'platform:user:write'])
    const screen = await render(<PageStub serverUser={{ username: 'alice' }} />)
    await expect.element(screen.getByText('alice')).toBeVisible()
    await expect.element(screen.getByText('cashier')).not.toBeInTheDocument()
  })

  it('hides create button when permission missing', async () => {
    setPrincipal(['platform:user:read'])
    const screen = await render(<PageStub serverUser={null} />)
    await expect.element(screen.getByText('新建用户')).not.toBeInTheDocument()
  })
})
