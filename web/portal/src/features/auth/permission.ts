import { redirect } from '@tanstack/react-router'
import { useAuthStore } from '@/stores/auth-store'

export function hasPermission(permission: string) {
  const principal = useAuthStore.getState().auth.principal

  return Boolean(principal?.permissions.includes(permission))
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

export function requireAnyPermission(permissions: string[]) {
  const state = useAuthStore.getState().auth

  if (!state.accessToken) {
    throw redirect({
      to: '/sign-in',
    })
  }

  if (!hasAnyPermission(permissions)) {
    throw redirect({
      to: '/403',
    })
  }
}
