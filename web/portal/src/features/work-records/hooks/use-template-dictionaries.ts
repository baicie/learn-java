import { z } from 'zod'
import { useQueries, useQuery } from '@tanstack/react-query'
import { listDictItems } from '@/features/dictionaries/api'
import {
  dictItemSchema,
  type DictItem,
} from '@/features/dictionaries/data/schema'
import { listTemplates } from '../api/template-api'
import { type WorkRecordTemplate } from '../data/schema'

/**
 * Collect all dictCodes from a template's schema.
 * Scans schema.properties for fields with x-work-record.optionSource='dict'.
 */
function extractDictCodesFromSchema(schemaJson: string): string[] {
  try {
    const schema = JSON.parse(schemaJson)
    const codes = new Set<string>()

    function scan(node: unknown) {
      if (!node || typeof node !== 'object') return

      const obj = node as Record<string, unknown>
      const ext = obj['x-work-record'] as Record<string, unknown> | undefined

      if (ext?.optionSource === 'dict' && typeof ext.dictCode === 'string') {
        codes.add(ext.dictCode)
      }

      if (obj.properties && typeof obj.properties === 'object') {
        for (const value of Object.values(
          obj.properties as Record<string, unknown>
        )) {
          scan(value)
        }
      }
    }

    scan(schema)
    return Array.from(codes)
  } catch {
    return []
  }
}

/**
 * Get default template (first enabled template).
 * Falls back to first template if none marked as default.
 */
export function useDefaultTemplate() {
  return useQuery({
    queryKey: ['work-record-default-template'],
    queryFn: async (): Promise<WorkRecordTemplate | null> => {
      const templates = await listTemplates()
      if (templates.length === 0) return null
      return templates.find((t) => t.enabled) ?? templates[0] ?? null
    },
  })
}

/**
 * Hook to load all dictionary items needed by a template.
 * Returns a Record<dictCode, DictItem[]> for injectDictionaryOptions.
 */
export function useTemplateDictionaries(
  template: WorkRecordTemplate | null | undefined,
  includeDisabled = false
) {
  const dictCodes = template
    ? extractDictCodesFromSchema(template.schemaJson)
    : []

  const results = useQueries({
    queries: dictCodes.map((code) => ({
      queryKey: ['dict-items', code, includeDisabled],
      queryFn: () => listDictItems(code, includeDisabled),
      enabled: dictCodes.length > 0,
    })),
  })

  // zod v4 的 z.infer 在 nullable 字段推断上偶尔把字段标成可选，
  // 这里用明确的 z.array(dictItemSchema) 重新窄化
  const dictionaries: Record<string, DictItem[]> = {}
  for (let i = 0; i < dictCodes.length; i++) {
    const data = results[i].data
    if (data) {
      dictionaries[dictCodes[i]] = z.array(dictItemSchema).parse(data)
    }
  }

  const isLoading = results.some((r) => r.isLoading)
  const isError = results.some((r) => r.isError)

  return { dictionaries, isLoading, isError }
}
