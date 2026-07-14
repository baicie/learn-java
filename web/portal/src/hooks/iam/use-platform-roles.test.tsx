import { describe, expect, it, vi } from 'vitest'
import {
  fetchPlatformRoles,
  fetchPermissionTree,
  replaceRolePermissions,
} from '@/api/iam/platform-roles-api'

vi.mock('@/lib/api-client', () => ({
  apiClient: {
    get: vi.fn(async (url: string) => {
      if (url === '/api/platform/roles') {
        return {
          data: [
            {
              roleCode: 'ops-viewer',
              roleName: 'Ops Viewer',
              description: null,
              enabled: true,
              system: false,
              userCount: 0,
              permissions: ['platform:user:read'],
              dataScopes: {},
              rowVersion: 1,
            },
          ],
        }
      }
      return { data: [] }
    }),
    post: vi.fn(async () => ({
      data: {
        roleCode: 'ops-viewer',
        roleName: 'Ops Viewer',
        description: null,
        enabled: true,
        system: false,
        userCount: 0,
        permissions: ['platform:user:write'],
        dataScopes: {},
        rowVersion: 2,
      },
    })),
  },
}))

describe('platform role API smoke', () => {
  it('fetchPlatformRoles validates the response shape', async () => {
    const roles = await fetchPlatformRoles()
    expect(roles).toHaveLength(1)
    expect(roles[0].roleCode).toBe('ops-viewer')
  })

  it('replaceRolePermissions returns the updated role', async () => {
    const role = await replaceRolePermissions('ops-viewer', {
      permissionCodes: ['platform:user:write'],
      confirmation: { reason: 'manual edit', dangerousAcknowledged: false },
    })
    expect(role.permissions).toContain('platform:user:write')
    expect(role.rowVersion).toBe(2)
  })

  it('fetchPermissionTree returns an array', async () => {
    const tree = await fetchPermissionTree()
    expect(Array.isArray(tree)).toBe(true)
  })
})
