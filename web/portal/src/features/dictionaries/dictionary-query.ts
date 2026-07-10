import { useQueries } from '@tanstack/react-query'
import { listDictItems } from './api'

export type DictionaryOption = {
  value: string
  label: string
  enabled: boolean
}

export type DictionaryOptionMap = Record<string, DictionaryOption[]>

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

export function useDictionaryOptions(
  dictCodes: string[],
  includeDisabled = true
) {
  const codes = Array.from(
    new Set(dictCodes.map((code) => code.trim()).filter(Boolean))
  ).sort()

  const queries = useQueries({
    queries: codes.map((dictCode) =>
      dictionaryItemsQueryOptions(dictCode, includeDisabled)
    ),
  })

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
