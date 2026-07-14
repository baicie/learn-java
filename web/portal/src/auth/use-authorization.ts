import { useEffect, useState } from 'react'
import { useAuthStore } from '@/stores/auth-store'
import type { AuthorizationPrincipal } from './authorization-types'

export type AuthorizationSnapshot = AuthorizationPrincipal

export function useAuthorization(): AuthorizationSnapshot | null {
  const [principal, setPrincipal] = useState<AuthorizationPrincipal | null>(
    () => useAuthStore.getState().auth.principal
  )

  useEffect(() => {
    const apply = () => setPrincipal(useAuthStore.getState().auth.principal)

    apply()

    return useAuthStore.subscribe(() => {
      apply()
    })
  }, [])

  return principal
}
