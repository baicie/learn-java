/**
 * Codec helpers used by the Formily runtime to round-trip values between
 * React (Formily expects primitives, arrays, plain objects) and the backend
 * (everything serialised as JSON strings).
 */

export function decodeFormValue(
  raw: unknown,
  type: 'string' | 'number' | 'boolean' | 'date' | 'datetime' | 'json'
): unknown {
  if (raw == null) return undefined
  switch (type) {
    case 'string':
      return typeof raw === 'string' ? raw : String(raw)
    case 'number':
      return typeof raw === 'number' ? raw : Number(raw)
    case 'boolean':
      return Boolean(raw)
    case 'date':
      return typeof raw === 'string' ? raw.slice(0, 10) : raw
    case 'datetime':
      return typeof raw === 'string' ? raw.slice(0, 16) : raw
    case 'json':
      if (typeof raw === 'string') {
        try {
          return JSON.parse(raw)
        } catch {
          return raw
        }
      }
      return raw
  }
}

export function encodeFormValue(
  value: unknown,
  type: 'string' | 'number' | 'boolean' | 'date' | 'datetime' | 'json'
): unknown {
  switch (type) {
    case 'string':
    case 'date':
    case 'datetime':
      return value == null ? value : String(value)
    case 'number':
      return typeof value === 'number'
        ? value
        : value == null
          ? value
          : Number(value)
    case 'boolean':
      return Boolean(value)
    case 'json':
      return value == null ? value : JSON.stringify(value)
  }
}
