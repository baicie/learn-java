type DictItem = {
  itemLabel: string
  itemValue: string
}

type DictMap = Record<string, DictItem[]>
type JsonObject = Record<string, unknown>

function isObject(value: unknown): value is JsonObject {
  return typeof value === 'object' && value !== null && !Array.isArray(value)
}

export function injectDictionaryOptions<T extends JsonObject>(
  schema: T,
  dicts: DictMap
): T {
  const cloned = structuredClone(schema)

  function visit(node: unknown) {
    if (!isObject(node)) return
    const extension = node['x-work-record']
    if (isObject(extension) && extension.optionSource === 'dict') {
      const dictCode = String(extension.dictCode)
      node.enum = (dicts[dictCode] ?? []).map((item) => ({
        label: item.itemLabel,
        value: item.itemValue,
      }))
    }
    for (const value of Object.values(node)) {
      if (isObject(value) || Array.isArray(value)) visit(value)
    }
  }

  visit(cloned)
  return cloned
}
