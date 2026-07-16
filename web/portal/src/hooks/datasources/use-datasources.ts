import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import {
  createDatasource,
  listDatasources,
  listSyncRuns,
  syncDatasource,
  testDatasource,
  type CreateDatasourceInput,
} from '@/api/datasources/datasources-api'
import { datasourceKeys } from '@/api/datasources/query-keys'

export function useDatasources() {
  return useQuery({
    queryKey: datasourceKeys.lists(),
    queryFn: listDatasources,
  })
}

export function useSyncRuns(id: string) {
  return useQuery({
    queryKey: datasourceKeys.syncRuns(id),
    queryFn: () => listSyncRuns(id),
    refetchInterval: (query) =>
      query.state.data?.some((run) => run.status === 'pending')
        ? 2_000
        : 15_000,
  })
}

export function useCreateDatasource() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (input: CreateDatasourceInput) => createDatasource(input),
    onSuccess: () =>
      queryClient.invalidateQueries({ queryKey: datasourceKeys.all }),
  })
}

export function useTestDatasource() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (id: string) => testDatasource(id),
    onSuccess: () =>
      queryClient.invalidateQueries({ queryKey: datasourceKeys.all }),
  })
}

export function useSyncDatasource() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (id: string) => syncDatasource(id),
    onSuccess: () =>
      queryClient.invalidateQueries({ queryKey: datasourceKeys.all }),
  })
}
