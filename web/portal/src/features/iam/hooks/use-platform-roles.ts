import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import {
  createPlatformRole,
  deletePlatformRole,
  fetchPermissionTree,
  fetchPlatformRole,
  fetchPlatformRoles,
  replaceRoleDataScopes,
  replaceRolePermissions,
  updatePlatformRole,
} from '../api/platform-roles-api'
import { platformRoleKeys, platformUserKeys } from '../api/query-keys'
import type {
  CreateRoleInput,
  ReplaceRoleDataScopesInput,
  ReplaceRolePermissionsInput,
  UpdateRoleInput,
} from '../schemas/platform-role'

export function usePlatformRoles() {
  return useQuery({
    queryKey: platformRoleKeys.lists(),
    queryFn: fetchPlatformRoles,
    staleTime: 30_000,
  })
}

export function usePlatformRole(code: string | undefined) {
  return useQuery({
    queryKey: code ? platformRoleKeys.detail(code) : platformRoleKeys.detail('disabled'),
    queryFn: () => fetchPlatformRole(code as string),
    enabled: Boolean(code),
  })
}

export function usePermissionTree() {
  return useQuery({
    queryKey: platformRoleKeys.permissions,
    queryFn: fetchPermissionTree,
    staleTime: 5 * 60_000,
  })
}

export function useCreatePlatformRole() {
  const client = useQueryClient()
  return useMutation({
    mutationFn: (input: CreateRoleInput) => createPlatformRole(input),
    onSuccess: async () => {
      await client.invalidateQueries({ queryKey: platformRoleKeys.lists() })
    },
  })
}

export function useUpdatePlatformRole(code: string) {
  const client = useQueryClient()
  return useMutation({
    mutationFn: (input: UpdateRoleInput) => updatePlatformRole(code, input),
    onSuccess: async (role) => {
      client.setQueryData(platformRoleKeys.detail(code), role)
      await client.invalidateQueries({ queryKey: platformRoleKeys.lists() })
    },
  })
}

export function useReplaceRolePermissions(code: string) {
  const client = useQueryClient()
  return useMutation({
    mutationFn: (input: ReplaceRolePermissionsInput) =>
      replaceRolePermissions(code, input),
    onSuccess: async () => {
      await client.invalidateQueries({ queryKey: platformRoleKeys.detail(code) })
      // Roles drive permission checks; user detail also needs refresh
      await client.invalidateQueries({ queryKey: platformUserKeys.lists() })
    },
  })
}

export function useReplaceRoleDataScopes(code: string) {
  const client = useQueryClient()
  return useMutation({
    mutationFn: (input: ReplaceRoleDataScopesInput) =>
      replaceRoleDataScopes(code, input),
    onSuccess: async () => {
      await client.invalidateQueries({ queryKey: platformRoleKeys.detail(code) })
    },
  })
}

export function useDeletePlatformRole() {
  const client = useQueryClient()
  return useMutation({
    mutationFn: (code: string) => deletePlatformRole(code),
    onSuccess: async () => {
      await client.invalidateQueries({ queryKey: platformRoleKeys.lists() })
    },
  })
}