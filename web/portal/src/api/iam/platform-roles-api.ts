import { apiClient } from '@/lib/api-client'
import {
  platformRoleSchema,
  replaceRolePermissionsInputSchema,
  permissionModuleSchema,
  type PermissionModule,
  type PlatformRole,
  type ReplaceRolePermissionsInput,
} from '@/lib/iam/platform-role'

export async function fetchPlatformRoles(): Promise<PlatformRole[]> {
  const { data } = await apiClient.get('/api/platform/roles')
  return platformRoleSchema.array().parse(data)
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

export async function fetchPermissionTree(): Promise<PermissionModule[]> {
  const { data } = await apiClient.get('/api/platform/permissions')
  return permissionModuleSchema.array().parse(data)
}
