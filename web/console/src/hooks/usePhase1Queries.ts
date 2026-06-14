import { useCallback } from 'react'
import { useQuery, useQueryClient } from '@tanstack/react-query'
import {
  AlertEventRecord,
  AssetRecord,
  DataSourceRecord,
  listAlerts,
  listAssets,
  listDataSources,
  overview
} from '../api/client'

type Overview = Record<string, number | string>

const PHASE1_QUERY_KEYS = ['overview', 'datasources', 'assets', 'alerts'] as const

export function usePhase1Queries() {
  const queryClient = useQueryClient()

  const overviewQuery = useQuery<Overview>({
    queryKey: ['overview'],
    queryFn: overview
  })

  const datasourceQuery = useQuery<DataSourceRecord[]>({
    queryKey: ['datasources'],
    queryFn: listDataSources
  })

  const assetQuery = useQuery<AssetRecord[]>({
    queryKey: ['assets'],
    queryFn: listAssets
  })

  const alertQuery = useQuery<AlertEventRecord[]>({
    queryKey: ['alerts'],
    queryFn: listAlerts
  })

  const invalidateAll = useCallback(
    async () => {
      await Promise.all(
        PHASE1_QUERY_KEYS.map((key) =>
          queryClient.invalidateQueries({ queryKey: [key] })
        )
      )
    },
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
