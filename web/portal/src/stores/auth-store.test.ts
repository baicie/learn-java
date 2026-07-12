import { clearCookies } from '@/test-utils/cookies'
import { beforeEach, describe, expect, it, vi } from 'vitest'

async function importAuthStore() {
  const { useAuthStore } = await import('./auth-store')
  return useAuthStore
}

const samplePrincipal = {
  userId: 'u-1',
  tenantId: 't-1',
  username: 'admin',
  displayName: 'Admin',
  roles: ['system_admin'],
  permissions: [],
  dataScopes: {},
}

describe('useAuthStore', () => {
  beforeEach(() => {
    clearCookies()
    vi.resetModules()
  })

  it('starts with an empty access token when nothing is persisted', async () => {
    const useAuthStore = await importAuthStore()

    expect(useAuthStore.getState().auth.accessToken).toBe('')
    expect(useAuthStore.getState().auth.principal).toBeNull()
  })

  it('persists access token so a new store instance reads it back', async () => {
    const useAuthStore = await importAuthStore()
    useAuthStore.getState().auth.setAccessToken('session-token')

    vi.resetModules()
    const useAuthStoreAfterReload = await importAuthStore()

    expect(useAuthStoreAfterReload.getState().auth.accessToken).toBe(
      'session-token'
    )
  })

  it('clears persisted access token when resetAccessToken is used', async () => {
    const useAuthStore = await importAuthStore()
    useAuthStore.getState().auth.setAccessToken('to-clear')
    useAuthStore.getState().auth.resetAccessToken()

    vi.resetModules()
    const useAuthStoreAfterReload = await importAuthStore()

    expect(useAuthStoreAfterReload.getState().auth.accessToken).toBe('')
  })

  it('updates the principal via setPrincipal', async () => {
    const useAuthStore = await importAuthStore()

    useAuthStore.getState().auth.setPrincipal({ ...samplePrincipal })

    expect(useAuthStore.getState().auth.principal).toEqual(samplePrincipal)
  })

  it('reset clears principal and access token and drops persistence', async () => {
    const useAuthStore = await importAuthStore()
    useAuthStore.getState().auth.setAccessToken('will-be-cleared')
    useAuthStore.getState().auth.setPrincipal({ ...samplePrincipal })

    useAuthStore.getState().auth.reset()

    expect(useAuthStore.getState().auth.principal).toBeNull()
    expect(useAuthStore.getState().auth.accessToken).toBe('')

    vi.resetModules()
    const useAuthStoreAfterReload = await importAuthStore()

    expect(useAuthStoreAfterReload.getState().auth.principal).toBeNull()
    expect(useAuthStoreAfterReload.getState().auth.accessToken).toBe('')
  })
})
