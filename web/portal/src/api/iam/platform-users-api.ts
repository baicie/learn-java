import { apiClient } from '@/lib/api-client'
import {
  createPlatformUserSchema,
  changeUserStatusInputSchema,
  replaceUserRolesInputSchema,
  platformUserPageSchema,
  platformUserSchema,
  type ChangeUserStatusInput,
  type CreatePlatformUserInput,
  type PlatformUserPage,
  type PlatformUserQuery,
  type PlatformUser,
  type ReplaceUserRolesInput,
} from '@/lib/iam/platform-user'

function buildQueryString(query: PlatformUserQuery): Record<string, unknown> {
  const params: Record<string, unknown> = {
    page: query.page,
    pageSize: query.pageSize,
    sortBy: query.sortBy,
    sortDir: query.sortDir,
  }
  if (query.keyword) params.keyword = query.keyword
  if (query.status) params.status = query.status
  if (query.statuses && query.statuses.length > 0) {
    params.status = query.statuses.join(',')
  }
  if (query.roleCodes && query.roleCodes.length > 0) {
    params.role = query.roleCodes.join(',')
  }
  return params
}

export async function fetchPlatformUsers(
  query: PlatformUserQuery
): Promise<PlatformUserPage> {
  const { data } = await apiClient.get('/api/platform/users', {
    params: buildQueryString(query),
  })
  return platformUserPageSchema.parse(data)
}

export async function createPlatformUser(
  input: CreatePlatformUserInput
): Promise<PlatformUser> {
  const payload = createPlatformUserSchema.parse(input)
  const { data } = await apiClient.post('/api/platform/users', payload)
  return platformUserSchema.parse(data)
}

export async function changeUserStatus(
  id: string,
  input: ChangeUserStatusInput
): Promise<PlatformUser> {
  const payload = changeUserStatusInputSchema.parse(input)
  const { data } = await apiClient.post(
    `/api/platform/users/${id}/status`,
    payload
  )
  return platformUserSchema.parse(data)
}

export async function replacePlatformUserRoles(
  id: string,
  input: ReplaceUserRolesInput
): Promise<PlatformUser> {
  const payload = replaceUserRolesInputSchema.parse(input)
  const { data } = await apiClient.put(
    `/api/platform/users/${id}/roles`,
    payload
  )
  return platformUserSchema.parse(data)
}
