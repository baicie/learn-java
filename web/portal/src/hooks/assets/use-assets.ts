import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import {
  archiveAsset,
  createAsset,
  getAsset,
  getAssetSummary,
  listAssetIdentities,
  listAssetRelations,
  listAssets,
  listAssetSources,
  type AssetInput,
  type AssetSearch,
  updateAsset,
} from '@/api/assets/assets-api'
import { assetKeys } from '@/api/assets/query-keys'

export function useAssets(search: AssetSearch) {
  return useQuery({
    queryKey: assetKeys.list(search),
    queryFn: () => listAssets(search),
  })
}

export function useAssetSummary() {
  return useQuery({
    queryKey: assetKeys.summary(),
    queryFn: getAssetSummary,
  })
}

export function useAssetDetail(id: string) {
  return useQuery({
    queryKey: assetKeys.detail(id),
    queryFn: async () => {
      const [asset, sources, identities, relations] = await Promise.all([
        getAsset(id),
        listAssetSources(id),
        listAssetIdentities(id),
        listAssetRelations(id),
      ])
      return { asset, sources, identities, relations }
    },
  })
}

export function useCreateAsset() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (input: AssetInput) => createAsset(input),
    onSuccess: () =>
      queryClient.invalidateQueries({ queryKey: assetKeys.lists() }),
  })
}

export function useUpdateAsset() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: ({ id, input }: { id: string; input: AssetInput }) =>
      updateAsset(id, input),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: assetKeys.all }),
  })
}

export function useArchiveAsset() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: ({ id, version }: { id: string; version: number }) =>
      archiveAsset(id, version),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: assetKeys.all }),
  })
}
