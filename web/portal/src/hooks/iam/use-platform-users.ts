import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import {
  changeUserStatus,
  createPlatformUser,
  fetchPlatformUsers,
} from '@/api/iam/platform-users-api'
import { platformUserKeys } from '@/api/iam/query-keys'
import type {
  ChangeUserStatusInput,
  CreatePlatformUserInput,
  PlatformUserQuery,
} from '@/lib/iam/platform-user'

export function usePlatformUsers(query: PlatformUserQuery) {
  return useQuery({
    queryKey: platformUserKeys.list(query),
    queryFn: () => fetchPlatformUsers(query),
    placeholderData: (previous) => previous,
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
