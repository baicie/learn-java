export type DataScope = 'SELF' | 'ALL'

export type AuthorizationPrincipal = {
  userId: string
  tenantId: string
  username: string
  displayName: string
  roles: string[]
  permissions: string[]
  dataScopes: Record<string, DataScope>
}

export type CurrentAuthorizationResponse = {
  success: boolean
  data: AuthorizationPrincipal
  errorCode: string | null
  message: string | null
  timestamp: string
}
