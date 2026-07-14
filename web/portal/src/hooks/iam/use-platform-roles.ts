import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import {
  fetchPermissionTree,
  createPlatformRole,
  deletePlatformRole,
  fetchPlatformRoles,
  replaceRolePermissions,
} from '@/api/iam/platform-roles-api'
import { platformRoleKeys, platformUserKeys } from '@/api/iam/query-keys'
import type {
  CreatePlatformRoleInput,
  ReplaceRolePermissionsInput,
} from '@/lib/iam/platform-role'

export function usePlatformRoles() {
  return useQuery({
    queryKey: platformRoleKeys.lists(),
    queryFn: fetchPlatformRoles,
    staleTime: 30_000,
  })
}

export function usePermissionTree() {
  return useQuery({
    queryKey: platformRoleKeys.permissions,
    queryFn: fetchPermissionTree,
    staleTime: 5 * 60_000,
  })
}

export function useReplaceRolePermissions(code: string) {
  const client = useQueryClient()
  return useMutation({
    mutationFn: (input: ReplaceRolePermissionsInput) =>
      replaceRolePermissions(code, input),
    onSuccess: async () => {
      await client.invalidateQueries({
        queryKey: platformRoleKeys.detail(code),
      })
      // Roles drive permission checks; user detail also needs refresh
      await client.invalidateQueries({ queryKey: platformUserKeys.lists() })
    },
  })
}

export function useCreatePlatformRole() {
  const client = useQueryClient()
  return useMutation({
    mutationFn: (input: CreatePlatformRoleInput) => createPlatformRole(input),
    onSuccess: async () => {
      await client.invalidateQueries({ queryKey: platformRoleKeys.lists() })
    },
  })
}

export function useDeletePlatformRole() {
  const client = useQueryClient()
  return useMutation({
    mutationFn: deletePlatformRole,
    onSuccess: async () => {
      await client.invalidateQueries({ queryKey: platformRoleKeys.lists() })
    },
  })
}
