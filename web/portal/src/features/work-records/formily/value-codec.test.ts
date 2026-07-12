import { describe, expect, it } from 'vitest'
import { decodeFormValue, encodeFormValue } from './value-codec'

describe('value-codec', () => {
  it('decodeFormValue strips the time part of a datetime string', () => {
    expect(decodeFormValue('2026-07-12T10:11:12Z', 'date')).toBe('2026-07-12')
    expect(decodeFormValue('2026-07-12T10:11', 'datetime')).toBe('2026-07-12T10:11')
  })

  it('decodeFormValue parses JSON safely', () => {
    expect(decodeFormValue('{"k":1}', 'json')).toEqual({ k: 1 })
    expect(decodeFormValue('not-json', 'json')).toBe('not-json')
  })

  it('decodeFormValue returns undefined for nullish input', () => {
    expect(decodeFormValue(null, 'string')).toBeUndefined()
    expect(decodeFormValue(undefined, 'number')).toBeUndefined()
  })

  it('encodeFormValue stringifies non-string values', () => {
    expect(encodeFormValue('a', 'string')).toBe('a')
    expect(encodeFormValue(42, 'number')).toBe(42)
    expect(encodeFormValue('42', 'number')).toBe(42)
    expect(encodeFormValue(true, 'boolean')).toBe(true)
    expect(encodeFormValue({ k: 1 }, 'json')).toBe('{"k":1}')
  })

  it('encodeFormValue passes through nullish for nullable types', () => {
    expect(encodeFormValue(null, 'string')).toBeNull()
    expect(encodeFormValue(undefined, 'number')).toBeUndefined()
  })
})