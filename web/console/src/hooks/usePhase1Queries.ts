import { useQuery, useQueryClient } from '@tanstack/react-query'
import { useCallback } from 'react'

import {
  type AlertEventRecord,
  type AssetRecord,
  type DataSourceRecord,
  type IncidentRecord,
  listAlerts,
  listAssets,
  listDataSources,
  listIncidents,
  overview,
} from '../api/client'

type Overview = Record<string, number | string>

const CONSOLE_QUERY_KEYS = ['overview', 'datasources', 'assets', 'alerts', 'incidents'] as const

export function usePhase1Queries() {
  const queryClient = useQueryClient()

  const overviewQuery = useQuery<Overview>({
    queryKey: ['overview'],
    queryFn: overview,
  })

  const datasourceQuery = useQuery<DataSourceRecord[]>({
    queryKey: ['datasources'],
    queryFn: listDataSources,
  })

  const assetQuery = useQuery<AssetRecord[]>({
    queryKey: ['assets'],
    queryFn: listAssets,
  })

  const alertQuery = useQuery<AlertEventRecord[]>({
    queryKey: ['alerts'],
    queryFn: listAlerts,
  })

  const incidentQuery = useQuery<IncidentRecord[]>({
    queryKey: ['incidents'],
    queryFn: listIncidents,
  })

  const invalidateAll = useCallback(async () => {
    await Promise.all(
      CONSOLE_QUERY_KEYS.map((key) => queryClient.invalidateQueries({ queryKey: [key] })),
    )
  }, [queryClient])

  return {
    overviewQuery,
    datasourceQuery,
    assetQuery,
    alertQuery,
    incidentQuery,
    invalidateAll,
  } as const
}
