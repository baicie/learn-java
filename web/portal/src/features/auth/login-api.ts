import { z } from 'zod'
import { apiClient } from '@/lib/api-client'
import { apiResponseSchema } from '@/lib/api-response'

const loginDataSchema = z.object({
  token: z.string().min(1),
  user: z.object({
    id: z.string(),
    tenantId: z.string(),
    username: z.string(),
    displayName: z.string(),
    roles: z.array(z.string()),
  }),
})

export type LoginResponse = z.infer<typeof loginDataSchema>

export async function login(
  username: string,
  password: string
): Promise<LoginResponse> {
  const { data } = await apiClient.post('/api/auth/login', {
    username,
    password,
  })

  return apiResponseSchema(loginDataSchema).parse(data).data
}
