import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { describe, expect, it, vi } from 'vitest'
import { render } from 'vitest-browser-react'
import { ConfirmProvider } from '@/components/feedback/confirm-provider'
import { PlatformRolesPage } from './platform-roles-page'

vi.mock('@/hooks/iam/use-platform-roles', () => ({
  usePlatformRoles: () => ({
    data: [
      {
        roleCode: 'ops-viewer',
        roleName: 'Ops Viewer',
        description: null,
        enabled: true,
        system: false,
        userCount: 0,
        permissions: [],
        dataScopes: {},
        rowVersion: 1,
      },
    ],
    isPending: false,
    isError: false,
  }),
  usePermissionTree: () => ({ data: [], isPending: false, isError: false }),
  useReplaceRolePermissions: () => ({ mutateAsync: vi.fn() }),
  useDeletePlatformRole: () => ({ isPending: false, mutateAsync: vi.fn() }),
}))

vi.mock('@/auth/permission-gate', () => ({
  PermissionGate: ({ children }: { children: React.ReactNode }) => children,
}))
vi.mock('./platform-role-create-dialog', () => ({
  PlatformRoleCreateDialog: () => null,
}))

describe('PlatformRolesPage', () => {
  it('selects the first role without entering an update loop', async () => {
    const consoleError = vi.spyOn(console, 'error').mockImplementation(() => {})
    const screen = await render(
      <QueryClientProvider client={new QueryClient()}>
        <ConfirmProvider>
          <PlatformRolesPage />
        </ConfirmProvider>
      </QueryClientProvider>
    )

    await expect
      .element(screen.getByRole('heading', { name: '角色权限' }))
      .toBeVisible()
    await expect
      .element(screen.getByRole('heading', { name: 'Ops Viewer' }))
      .toBeVisible()
    expect(
      consoleError.mock.calls.some(([message]) =>
        String(message).includes('Maximum update depth exceeded')
      )
    ).toBe(false)
    consoleError.mockRestore()
  })
})
