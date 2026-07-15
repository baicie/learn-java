import { describe, expect, it, vi } from 'vitest'
import {
  fetchPlatformUsers,
  createPlatformUser,
  changeUserStatus,
  replacePlatformUserRoles,
  resetPlatformUserPassword,
  updatePlatformUser,
} from '@/api/iam/platform-users-api'

vi.mock('@/lib/api-client', () => ({
  apiClient: {
    get: vi.fn(async (url: string) => {
      if (url === '/api/platform/users') {
        return {
          data: {
            success: true,
            data: {
              items: [
                {
                  id: 'u1',
                  tenantId: 't1',
                  username: 'alice',
                  displayName: 'Alice',
                  status: 'active',
                  roles: [],
                  dataScopes: {},
                  lastLoginAt: null,
                  createdAt: '2026-07-12T00:00:00Z',
                  updatedAt: '2026-07-12T00:00:00Z',
                  rowVersion: 1,
                },
              ],
              page: 1,
              pageSize: 20,
              total: 1,
            },
          },
        }
      }
      return { data: null }
    }),
    post: vi.fn(async () => ({
      data: {
        success: true,
        data: {
          id: 'u2',
          tenantId: 't1',
          username: 'bob',
          displayName: 'Bob',
          email: null,
          status: 'disabled',
          roles: [],
          dataScopes: {},
          lastLoginAt: null,
          createdAt: '2026-07-12T00:00:00Z',
          updatedAt: '2026-07-12T00:00:00Z',
          rowVersion: 2,
        },
      },
    })),
    put: vi.fn(async () => ({
      data: {
        id: 'u1',
        tenantId: 't1',
        username: 'alice',
        displayName: 'Alice',
        email: null,
        status: 'active',
        roles: [{ code: 'ops-viewer', name: 'Ops Viewer' }],
        dataScopes: {},
        lastLoginAt: null,
        createdAt: '2026-07-12T00:00:00Z',
        updatedAt: '2026-07-12T00:00:00Z',
        rowVersion: 2,
      },
    })),
  },
}))

describe('platform user API smoke', () => {
  it('fetchPlatformUsers validates the response shape', async () => {
    const page = await fetchPlatformUsers({ page: 1, pageSize: 20 })
    expect(page.items[0].username).toBe('alice')
    expect(page.items[0].email).toBeNull()
    expect(page.total).toBe(1)
  })

  it('createPlatformUser accepts a typed payload and returns a typed user', async () => {
    const user = await createPlatformUser({
      username: 'bob',
      displayName: 'Bob',
      initialPassword: 'changeMe-9!',
      status: 'active',
      roleCodes: [],
    })
    expect(user.username).toBe('bob')
  })

  it('changeUserStatus posts the expected body and returns a typed user', async () => {
    const user = await changeUserStatus('u1', {
      status: 'disabled',
      reason: 'admin op',
      rowVersion: 1,
    })
    expect(user.status).toBe('disabled')
  })

  it('replacePlatformUserRoles posts the expected body', async () => {
    const user = await replacePlatformUserRoles('u1', {
      roleCodes: ['ops-viewer'],
      reason: 'admin op',
      rowVersion: 1,
    })
    expect(user.roles).toHaveLength(1)
    expect(user.rowVersion).toBe(2)
  })

  it('updates user profile and resets password through typed contracts', async () => {
    const updated = await updatePlatformUser('u1', {
      displayName: 'Alice 2',
      email: 'alice@example.com',
    })
    const reset = await resetPlatformUserPassword('u1', {
      newPassword: 'changeMe-10!',
    })

    expect(updated.username).toBe('alice')
    expect(reset.username).toBe('bob')
  })
})
