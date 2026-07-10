import axios from 'axios'
import { useAuthStore } from '@/stores/auth-store'
import { fetchCurrentAuthorization } from './authorization-api'
import type { AuthorizationPrincipal } from './authorization-types'

let pending: Promise<AuthorizationPrincipal> | null = null

export async function ensureAuthorizationLoaded(
  force = false
): Promise<AuthorizationPrincipal> {
  const current = useAuthStore.getState().auth

  if (!current.accessToken) {
    throw new Error('AUTH_TOKEN_MISSING')
  }

  if (!force && current.authorizationLoaded && current.principal) {
    return current.principal
  }

  if (pending) {
    return pending
  }

  useAuthStore.getState().auth.setAuthorizationLoaded(false)

  pending = fetchCurrentAuthorization()
    .then((principal) => {
      const auth = useAuthStore.getState().auth

      auth.setPrincipal(principal)
      auth.setAuthorizationLoaded(true)

      return principal
    })
    .catch((error: unknown) => {
      const auth = useAuthStore.getState().auth

      auth.setAuthorizationLoaded(true)

      if (
        axios.isAxiosError(error) &&
        (error.response?.status === 401 || error.response?.status === 403)
      ) {
        auth.reset()
      }

      throw error
    })
    .finally(() => {
      pending = null
    })

  return pending
}
