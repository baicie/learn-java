import { useQueries, useQuery } from '@tanstack/react-query'
import { listDictItems, listDictTypes, type DictItem } from '@/api/dictionaries'

type DictionaryOption = {
  value: string
  label: string
  enabled: boolean
}

export type DictionaryOptionMap = Record<string, DictionaryOption[]>

type DictionaryItemsMap = Record<string, DictItem[]>

const DICT_STALE_TIME = 30 * 60 * 1000
const DICT_GC_TIME = 24 * 60 * 60 * 1000

export const dictionaryKeys = {
  all: ['platform-dictionaries'] as const,

  types(includeDisabled: boolean) {
    return [...dictionaryKeys.all, 'types', includeDisabled] as const
  },

  items(dictCode: string, includeDisabled: boolean) {
    return [...dictionaryKeys.all, 'items', dictCode, includeDisabled] as const
  },
}

function dictionaryTypesQueryOptions(includeDisabled = true) {
  return {
    queryKey: dictionaryKeys.types(includeDisabled),
    queryFn: () => listDictTypes(includeDisabled),
    staleTime: DICT_STALE_TIME,
    gcTime: DICT_GC_TIME,
    refetchOnWindowFocus: false,
  }
}

export function dictionaryItemsQueryOptions(
  dictCode: string,
  includeDisabled = true
) {
  return {
    queryKey: dictionaryKeys.items(dictCode, includeDisabled),
    queryFn: () => listDictItems(dictCode, includeDisabled),
    staleTime: DICT_STALE_TIME,
    gcTime: DICT_GC_TIME,
    refetchOnWindowFocus: false,
  }
}

function normalizeCodes(dictCodes: string[]) {
  return Array.from(
    new Set(dictCodes.map((code) => code.trim()).filter(Boolean))
  ).sort()
}

function useRawDictionaryQueries(
  dictCodes: string[],
  includeDisabled: boolean
) {
  const codes = normalizeCodes(dictCodes)

  const queries = useQueries({
    queries: codes.map((dictCode) =>
      dictionaryItemsQueryOptions(dictCode, includeDisabled)
    ),
  })

  const typesQuery = useQuery({
    ...dictionaryTypesQueryOptions(true),
    enabled: codes.length > 0,
  })

  return { codes, queries, typesQuery }
}

export function useDictionaryOptions(
  dictCodes: string[],
  includeDisabled = true
) {
  const { codes, queries, typesQuery } = useRawDictionaryQueries(
    dictCodes,
    includeDisabled
  )
  const typeEnabled = new Map(
    typesQuery.data?.map((type) => [type.dictCode, type.enabled]) ?? []
  )

  const options: DictionaryOptionMap = {}

  codes.forEach((dictCode, index) => {
    options[dictCode] =
      queries[index]?.data?.map((item) => ({
        value: item.itemValue,
        label: item.itemLabel,
        enabled: item.enabled && typeEnabled.get(dictCode) === true,
      })) ?? []
  })

  return {
    options,
    loading: typesQuery.isLoading || queries.some((query) => query.isLoading),
    fetching:
      typesQuery.isFetching || queries.some((query) => query.isFetching),
    error:
      typesQuery.error ?? queries.find((query) => query.error)?.error ?? null,
    refetch: () =>
      Promise.all([
        typesQuery.refetch(),
        ...queries.map((query) => query.refetch()),
      ]),
  }
}

export function useDictionaryItemsMap(
  dictCodes: string[],
  includeDisabled = true
) {
  const { codes, queries, typesQuery } = useRawDictionaryQueries(
    dictCodes,
    includeDisabled
  )
  const typeEnabled = new Map(
    typesQuery.data?.map((type) => [type.dictCode, type.enabled]) ?? []
  )

  const items: DictionaryItemsMap = {}

  codes.forEach((dictCode, index) => {
    items[dictCode] =
      queries[index]?.data?.map((item) => ({
        ...item,
        enabled: item.enabled && typeEnabled.get(dictCode) === true,
      })) ?? []
  })

  return {
    items,
    loading: typesQuery.isLoading || queries.some((query) => query.isLoading),
    fetching:
      typesQuery.isFetching || queries.some((query) => query.isFetching),
    error:
      typesQuery.error ?? queries.find((query) => query.error)?.error ?? null,
    refetch: () =>
      Promise.all([
        typesQuery.refetch(),
        ...queries.map((query) => query.refetch()),
      ]),
  }
}

export function dictionaryLabel(
  optionMap: DictionaryOptionMap,
  dictCode: string,
  value: string
) {
  return (
    optionMap[dictCode]?.find((item) => item.value === value)?.label ?? value
  )
}
