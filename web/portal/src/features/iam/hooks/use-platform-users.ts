import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import {
  changeUserStatus,
  createPlatformUser,
  fetchPlatformUser,
  fetchPlatformUsers,
  replacePlatformUserRoles,
  resetPlatformUserPassword,
  updatePlatformUser,
} from '../api/platform-users-api'
import { platformUserKeys } from '../api/query-keys'
import type {
  ChangeUserStatusInput,
  CreatePlatformUserInput,
  PlatformUserQuery,
  ReplaceUserRolesInput,
  UpdatePlatformUserInput,
} from '../schemas/platform-user'

export function usePlatformUsers(query: PlatformUserQuery) {
  return useQuery({
    queryKey: platformUserKeys.list(query),
    queryFn: () => fetchPlatformUsers(query),
    placeholderData: (previous) => previous,
  })
}

export function usePlatformUser(id: string | undefined) {
  return useQuery({
    queryKey: id ? platformUserKeys.detail(id) : platformUserKeys.detail('disabled'),
    queryFn: () => fetchPlatformUser(id as string),
    enabled: Boolean(id),
  })
}

export function useCreatePlatformUser() {
  const client = useQueryClient()
  return useMutation({
    mutationFn: (input: CreatePlatformUserInput) => createPlatformUser(input),
    onSuccess: async () => {
      await client.invalidateQueries({ queryKey: platformUserKeys.lists() })
    },
  })
}

export function useUpdatePlatformUser(id: string) {
  const client = useQueryClient()
  return useMutation({
    mutationFn: (input: UpdatePlatformUserInput) => updatePlatformUser(id, input),
    onSuccess: async (user) => {
      client.setQueryData(platformUserKeys.detail(id), user)
      await client.invalidateQueries({ queryKey: platformUserKeys.lists() })
    },
  })
}

export function useChangeUserStatus(id: string) {
  const client = useQueryClient()
  return useMutation({
    mutationFn: (input: ChangeUserStatusInput) => changeUserStatus(id, input),
    onSuccess: async (user) => {
      client.setQueryData(platformUserKeys.detail(id), user)
      await client.invalidateQueries({ queryKey: platformUserKeys.lists() })
    },
  })
}

export function useResetPlatformUserPassword(id: string) {
  const client = useQueryClient()
  return useMutation({
    mutationFn: (newPassword: string) => resetPlatformUserPassword(id, newPassword),
    onSuccess: async () => {
      await client.invalidateQueries({ queryKey: platformUserKeys.detail(id) })
    },
  })
}

export function useReplacePlatformUserRoles(id: string) {
  const client = useQueryClient()
  return useMutation({
    mutationFn: (input: ReplaceUserRolesInput) =>
      replacePlatformUserRoles(id, input),
    onSuccess: async (user) => {
      client.setQueryData(platformUserKeys.detail(id), user)
      await client.invalidateQueries({ queryKey: platformUserKeys.lists() })
    },
  })
}