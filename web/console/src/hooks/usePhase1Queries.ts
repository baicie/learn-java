import { useCallback } from 'react'
import { useQuery, useQueryClient, UseQueryResult } from '@tanstack/react-query'
import {
  DataSourceRecord,
  listAlerts,
  listAssets,
  listDataSources,
  overview,
  AlertEventRecord,
  AssetRecord
} from '../api/client'

type Overview = Record<string, number | string>

const PHASE1_QUERY_KEYS = ['overview', 'datasources', 'assets', 'alerts'] as const

/**
 * Centralises all Phase 1 dashboard queries so the page can be wired up by
 * destructuring a single hook and the invalidation predicate lives in one
 * place. Adding a new Phase 1 query only requires adding it here + extending
 * {@link PHASE1_QUERY_KEYS}.
 */
export function usePhase1Queries() {
  const queryClient = useQueryClient()

  const overviewQuery = useQuery<Overview>({ queryKey: ['overview'], queryFn: overview })
  const datasourceQuery = useQuery<DataSourceRecord[]>({ queryKey: ['datasources'], queryFn: listDataSources })
  const assetQuery = useQuery<AssetRecord[]>({ queryKey: ['assets'], queryFn: listAssets })
  const alertQuery = useQuery<AlertEventRecord[]>({ queryKey: ['alerts'], queryFn: listAlerts })

  const invalidateAll = useCallback(
    () => queryClient.invalidateQueries({ queryKey: PHASE1_QUERY_KEYS }),
    [queryClient]
  )

  return {
    overviewQuery,
    datasourceQuery,
    assetQuery,
    alertQuery,
    invalidateAll
  } as const
}
