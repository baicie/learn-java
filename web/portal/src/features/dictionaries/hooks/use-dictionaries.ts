import { useQuery } from '@tanstack/react-query'
import { listDictItems, listDictTypes } from '../api'

export function useDictTypes() {
  return useQuery({
    queryKey: ['dict-types'],
    queryFn: listDictTypes,
  })
}

export function useDictItems(
  dictCode: string | undefined,
  includeDisabled = false
) {
  return useQuery({
    queryKey: ['dict-items', dictCode, includeDisabled],
    queryFn: () => listDictItems(dictCode ?? '', includeDisabled),
    enabled: Boolean(dictCode),
  })
}
