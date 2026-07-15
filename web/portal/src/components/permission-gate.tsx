import type { ReactNode } from 'react'
import { useAuthStore } from '@/stores/auth-store'

type Props = {
  any?: string[]
  all?: string[]
  fallback?: ReactNode
  children: ReactNode
}

const EMPTY_PERMISSIONS: string[] = []

export function PermissionGate({
  any = [],
  all = [],
  fallback = null,
  children,
}: Props) {
  const permissions = useAuthStore(
    (state) => state.auth.principal?.permissions ?? EMPTY_PERMISSIONS
  )

  const anyAllowed =
    any.length === 0 ||
    any.some((permission) => permissions.includes(permission))

  const allAllowed = all.every((permission) => permissions.includes(permission))

  if (!anyAllowed || !allAllowed) {
    return <>{fallback}</>
  }

  return <>{children}</>
}
