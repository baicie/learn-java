import { describe, expect, it } from 'vitest'
import { isReservedFieldCode } from './reserved-field-codes'

describe('reserved field codes', () => {
  it('rejects built-in columns', () => {
    expect(isReservedFieldCode('title')).toBe(true)
    expect(isReservedFieldCode(' custom_data_json ')).toBe(true)
  })

  it('allows custom field codes', () => {
    expect(isReservedFieldCode('process_result')).toBe(false)
  })
})
