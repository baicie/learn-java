import type { PropsWithChildren, ReactNode } from 'react'
import { useAuthorization } from './use-authorization'

type Props = PropsWithChildren<{
  anyOf?: string[]
  allOf?: string[]
  fallback?: ReactNode
}>

export function PermissionGate({
  anyOf = [],
  allOf = [],
  fallback = null,
  children,
}: Props) {
  const authorization = useAuthorization()
  const owned = new Set(authorization?.permissions ?? [])

  const anyAllowed =
    anyOf.length === 0 || anyOf.some((code) => owned.has(code))

  const allAllowed = allOf.every((code) => owned.has(code))

  return anyAllowed && allAllowed ? children : fallback
}