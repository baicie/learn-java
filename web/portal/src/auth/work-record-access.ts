import type { AuthorizationPrincipal } from './authorization-types'

const OPERATIONS_PERMISSIONS = [
  'work-record:analytics',
  'work-record:handover',
  'work-record:approval:act',
]

function hasPermission(
  principal: AuthorizationPrincipal | null | undefined,
  permission: string
) {
  return principal?.permissions.includes(permission) ?? false
}

function hasTenantWideRead(
  principal: AuthorizationPrincipal | null | undefined
) {
  return (
    hasPermission(principal, 'work-record:read:all') &&
    principal?.dataScopes['work-record'] === 'ALL'
  )
}

export function canGenerateWorkRecordPeriodReports(
  principal: AuthorizationPrincipal | null | undefined
) {
  return (
    hasPermission(principal, 'work-record:ai:generate') &&
    hasTenantWideRead(principal)
  )
}

export function canReadWorkRecordPeriodReports(
  principal: AuthorizationPrincipal | null | undefined
) {
  return (
    hasTenantWideRead(principal) &&
    (hasPermission(principal, 'work-record:ai:generate') ||
      hasPermission(principal, 'work-record:ai:review'))
  )
}

export function canAccessWorkRecordOperations(
  principal: AuthorizationPrincipal | null | undefined
) {
  return (
    OPERATIONS_PERMISSIONS.some((permission) =>
      hasPermission(principal, permission)
    ) || canReadWorkRecordPeriodReports(principal)
  )
}
