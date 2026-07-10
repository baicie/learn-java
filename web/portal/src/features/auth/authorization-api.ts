import { z } from 'zod'
import { apiClient } from '@/lib/api-client'
import type { AuthorizationPrincipal } from './authorization-types'

const principalSchema = z.object({
  userId: z.string(),
  tenantId: z.string(),
  username: z.string(),
  displayName: z.string(),
  roles: z.array(z.string()),
  permissions: z.array(z.string()),
  dataScopes: z.record(z.string(), z.enum(['SELF', 'ALL'])),
})

const responseSchema = z.object({
  success: z.literal(true),
  data: principalSchema,
  errorCode: z.null(),
  message: z.null(),
  timestamp: z.string(),
})

export async function fetchCurrentAuthorization(): Promise<AuthorizationPrincipal> {
  const { data } = await apiClient.get('/api/auth/me')

  return responseSchema.parse(data).data
}
