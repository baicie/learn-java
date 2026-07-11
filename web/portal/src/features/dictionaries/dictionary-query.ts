import { useQueries } from '@tanstack/react-query'
import { listDictItems, type DictItem } from './api'

export type DictionaryOption = {
  value: string
  label: string
  enabled: boolean
}

export type DictionaryOptionMap = Record<string, DictionaryOption[]>

export type DictionaryItemsMap = Record<string, DictItem[]>

const DICT_STALE_TIME = 30 * 60 * 1000
const DICT_GC_TIME = 24 * 60 * 60 * 1000

export const dictionaryKeys = {
  all: ['platform-dictionaries'] as const,

  items(dictCode: string, includeDisabled: boolean) {
    return [...dictionaryKeys.all, 'items', dictCode, includeDisabled] as const
  },
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

  return { codes, queries }
}

export function useDictionaryOptions(
  dictCodes: string[],
  includeDisabled = true
) {
  const { codes, queries } = useRawDictionaryQueries(dictCodes, includeDisabled)

  const options: DictionaryOptionMap = {}

  codes.forEach((dictCode, index) => {
    options[dictCode] =
      queries[index]?.data?.map((item) => ({
        value: item.itemValue,
        label: item.itemLabel,
        enabled: item.enabled,
      })) ?? []
  })

  return {
    options,
    loading: queries.some((query) => query.isLoading),
    fetching: queries.some((query) => query.isFetching),
    error: queries.find((query) => query.error)?.error ?? null,
    refetch: () => Promise.all(queries.map((query) => query.refetch())),
  }
}

export function useDictionaryItemsMap(
  dictCodes: string[],
  includeDisabled = true
) {
  const { codes, queries } = useRawDictionaryQueries(dictCodes, includeDisabled)

  const items: DictionaryItemsMap = {}

  codes.forEach((dictCode, index) => {
    items[dictCode] = queries[index]?.data ?? []
  })

  return {
    items,
    loading: queries.some((query) => query.isLoading),
    fetching: queries.some((query) => query.isFetching),
    error: queries.find((query) => query.error)?.error ?? null,
    refetch: () => Promise.all(queries.map((query) => query.refetch())),
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
