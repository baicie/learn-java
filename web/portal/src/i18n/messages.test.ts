import { describe, expect, it } from 'vitest'
import { enUS, zhCN } from './messages'

describe('i18n messages', () => {
  it('keeps locale keys aligned', () => {
    expect(Object.keys(enUS).sort()).toEqual(Object.keys(zhCN).sort())
  })

  it('does not contain empty translations', () => {
    for (const value of [...Object.values(zhCN), ...Object.values(enUS)]) {
      expect(value.trim()).not.toBe('')
    }
  })
})
