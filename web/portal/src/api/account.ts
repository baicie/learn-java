import { z } from 'zod'
import { apiClient } from '@/lib/api-client'
import { apiResponseSchema } from '@/lib/api-response'

const profileSchema = z.object({
  id: z.string(),
  username: z.string(),
  displayName: z.string(),
  email: z.string().nullable(),
})

export type AccountProfile = z.infer<typeof profileSchema>

export async function getAccountProfile() {
  const { data } = await apiClient.get('/api/auth/profile')
  return apiResponseSchema(profileSchema).parse(data).data
}

export async function updateAccountProfile(input: {
  displayName: string
  email: string
}) {
  const { data } = await apiClient.put('/api/auth/profile', input)
  return apiResponseSchema(profileSchema).parse(data).data
}

export async function changeAccountPassword(input: {
  currentPassword: string
  newPassword: string
}) {
  await apiClient.post('/api/auth/change-password', input)
}
