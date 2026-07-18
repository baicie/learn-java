import { useEffect } from 'react'
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
  const queryClient = useQueryClient()
  const query = useQuery({
    queryKey: datasourceKeys.syncRuns(id),
    queryFn: () => listSyncRuns(id),
    refetchInterval: (query) =>
      query.state.data?.some((run) =>
        ['pending', 'running'].includes(run.status)
      )
        ? 2_000
        : 15_000,
  })

  const latestStatus = query.data?.[0]?.status
  useEffect(() => {
    if (latestStatus === 'success' || latestStatus === 'failed') {
      void queryClient.invalidateQueries({ queryKey: datasourceKeys.lists() })
    }
  }, [latestStatus, queryClient])

  return query
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
