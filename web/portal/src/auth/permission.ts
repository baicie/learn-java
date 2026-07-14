import axios from 'axios'
import { redirect } from '@tanstack/react-router'
import { useAuthStore } from '@/stores/auth-store'
import { ensureAuthorizationLoaded } from './authorization-session'

export async function requireAuthenticated() {
  const auth = useAuthStore.getState().auth

  if (!auth.accessToken) {
    throw redirect({
      to: '/sign-in',
    })
  }

  try {
    return await ensureAuthorizationLoaded()
  } catch (error) {
    if (
      axios.isAxiosError(error) &&
      error.response?.status !== 401 &&
      error.response?.status !== 403
    ) {
      throw error
    }

    throw redirect({
      to: '/sign-in',
    })
  }
}

export async function requireAnyPermission(permissions: string[]) {
  const principal = await requireAuthenticated()

  const allowed =
    permissions.length === 0 ||
    permissions.some((permission) => principal.permissions.includes(permission))

  if (!allowed) {
    throw redirect({
      to: '/403',
    })
  }

  return principal
}
