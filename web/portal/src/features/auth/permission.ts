import axios from 'axios'
import { redirect } from '@tanstack/react-router'
import { useAuthStore } from '@/stores/auth-store'
import { ensureAuthorizationLoaded } from './authorization-session'

export function hasPermission(permission: string) {
  return Boolean(
    useAuthStore.getState().auth.principal?.permissions.includes(permission)
  )
}

export function hasAnyPermission(permissions: string[]) {
  if (!permissions.length) {
    return true
  }

  const principal = useAuthStore.getState().auth.principal

  return permissions.some((permission) =>
    principal?.permissions.includes(permission)
  )
}

export function hasAllPermissions(permissions: string[]) {
  const principal = useAuthStore.getState().auth.principal

  return permissions.every((permission) =>
    principal?.permissions.includes(permission)
  )
}

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
