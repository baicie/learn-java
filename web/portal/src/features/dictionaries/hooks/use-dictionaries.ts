import { useQuery } from '@tanstack/react-query'
import {
  listDictItems,
  listDictTypes,
  type DictType,
  type DictItem,
} from '../api'

export function useDictTypes() {
  return useQuery<DictType[], Error>({
    queryKey: ['dict-types'],
    queryFn: () => listDictTypes(true),
  })
}

export function useDictItems(
  dictCode: string | undefined,
  includeDisabled = false
) {
  return useQuery<DictItem[], Error>({
    queryKey: ['dict-items', dictCode, includeDisabled],
    queryFn: () => listDictItems(dictCode ?? '', includeDisabled),
    enabled: Boolean(dictCode),
  })
}
