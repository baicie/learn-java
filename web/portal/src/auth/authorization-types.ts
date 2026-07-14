type DataScope = 'SELF' | 'ALL'

export type AuthorizationPrincipal = {
  userId: string
  tenantId: string
  username: string
  displayName: string
  roles: string[]
  permissions: string[]
  dataScopes: Record<string, DataScope>
}
