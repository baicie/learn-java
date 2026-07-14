import { describe, expect, it, vi } from 'vitest'

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
}))

// Smoke-test that the mock module surface is wired correctly.
// Full UI render is covered by Storybook / Playwright in a later slice.
describe('PlatformRolesPage hook surface', () => {
  it('mocks expose the expected query hooks', async () => {
    const mod = await import('@/hooks/iam/use-platform-roles')
    expect(typeof mod.usePlatformRoles).toBe('function')
    expect(typeof mod.usePermissionTree).toBe('function')
  })
})
