import { describe, expect, it } from 'vitest'
import { toOffsetDateTime } from './api'

describe('runtime api helpers', () => {
  it('rejects invalid recordTime before submit', () => {
    expect(() => toOffsetDateTime('bad-time')).toThrow('invalid recordTime')
  })
})