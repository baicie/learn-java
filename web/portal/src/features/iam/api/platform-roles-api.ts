import { apiClient } from '@/lib/api-client'
import {
  platformRoleSchema,
  createRoleInputSchema,
  replaceRoleDataScopesInputSchema,
  replaceRolePermissionsInputSchema,
  updateRoleInputSchema,
  permissionModuleSchema,
  type CreateRoleInput,
  type PermissionModule,
  type PlatformRole,
  type ReplaceRoleDataScopesInput,
  type ReplaceRolePermissionsInput,
  type UpdateRoleInput,
} from '../schemas/platform-role'

export async function fetchPlatformRoles(): Promise<PlatformRole[]> {
  const { data } = await apiClient.get('/api/platform/roles')
  return platformRoleSchema.array().parse(data)
}

export async function fetchPlatformRole(code: string): Promise<PlatformRole> {
  const { data } = await apiClient.get(`/api/platform/roles/${code}`)
  return platformRoleSchema.parse(data)
}

export async function createPlatformRole(
  input: CreateRoleInput
): Promise<PlatformRole> {
  const payload = createRoleInputSchema.parse(input)
  const { data } = await apiClient.post('/api/platform/roles', payload)
  return platformRoleSchema.parse(data)
}

export async function updatePlatformRole(
  code: string,
  input: UpdateRoleInput
): Promise<PlatformRole> {
  const payload = updateRoleInputSchema.parse(input)
  const { data } = await apiClient.put(`/api/platform/roles/${code}`, payload)
  return platformRoleSchema.parse(data)
}

export async function replaceRolePermissions(
  code: string,
  input: ReplaceRolePermissionsInput
): Promise<PlatformRole> {
  const payload = replaceRolePermissionsInputSchema.parse(input)
  const { data } = await apiClient.post(
    `/api/platform/roles/${code}/permissions`,
    payload
  )
  return platformRoleSchema.parse(data)
}

export async function replaceRoleDataScopes(
  code: string,
  input: ReplaceRoleDataScopesInput
): Promise<PlatformRole> {
  const payload = replaceRoleDataScopesInputSchema.parse(input)
  const { data } = await apiClient.post(
    `/api/platform/roles/${code}/data-scopes`,
    payload
  )
  return platformRoleSchema.parse(data)
}

export async function deletePlatformRole(code: string): Promise<void> {
  await apiClient.delete(`/api/platform/roles/${code}`)
}

export async function fetchPermissionTree(): Promise<PermissionModule[]> {
  const { data } = await apiClient.get('/api/platform/permissions')
  return permissionModuleSchema.array().parse(data)
}