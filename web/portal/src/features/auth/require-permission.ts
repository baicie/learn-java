import { redirect } from '@tanstack/react-router'
import { useAuthStore } from '@/stores/auth-store'
import type { AuthorizationSnapshot } from './use-authorization'

export function requireAnyPermission(required: string[]): AuthorizationSnapshot {
  const snapshot = useAuthStore.getState().auth.principal

  if (!snapshot) {
    throw redirect({
      to: '/sign-in',
      search: { redirect: window.location.pathname },
    })
  }

  const owned = new Set(snapshot.permissions)

  if (!required.some((code) => owned.has(code))) {
    throw redirect({ to: '/403' })
  }

  return snapshot
}