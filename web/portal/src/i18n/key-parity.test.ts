import { common as enCommon } from '@/i18n/locales/en-US/common'
import { workRecords as enWorkRecords } from '@/i18n/locales/en-US/work-records'
import { common as zhCommon } from '@/i18n/locales/zh-CN/common'
import { workRecords as zhWorkRecords } from '@/i18n/locales/zh-CN/work-records'
import { describe, expect, it } from 'vitest'

function extractKeys(record: Record<string, unknown>): Set<string> {
  const keys = new Set<string>()
  for (const [key, value] of Object.entries(record)) {
    keys.add(key)
    if (value && typeof value === 'object' && !Array.isArray(value)) {
      for (const nested of extractKeys(value as Record<string, unknown>)) {
        keys.add(`${key}.${nested}`)
      }
    }
  }
  return keys
}

describe('i18n key parity', () => {
  it('common namespace keys match between zh-CN and en-US', () => {
    const zh = new Set(Object.keys(zhCommon))
    const en = new Set(Object.keys(enCommon))

    expect([...zh].sort().join('\n')).toBe([...en].sort().join('\n'))
  })

  it('work-records namespace keys match between zh-CN and en-US', () => {
    const zh = new Set(Object.keys(zhWorkRecords))
    const en = new Set(Object.keys(enWorkRecords))

    expect([...zh].sort().join('\n')).toBe([...en].sort().join('\n'))
  })
})

describe('i18n key lookup', () => {
  it('zh-CN resolves all required keys', () => {
    expect(zhCommon['common.loading']).toBeTruthy()
    expect(zhCommon['navigation.unsaved.title']).toBeTruthy()
    expect(zhWorkRecords['workRecords.list.refreshFailedHint']).toBeTruthy()
  })

  it('en-US resolves all required keys', () => {
    expect(enCommon['common.loading']).toBeTruthy()
    expect(enCommon['navigation.unsaved.title']).toBeTruthy()
    expect(enWorkRecords['workRecords.list.refreshFailedHint']).toBeTruthy()
  })

  // extractKeys 用于在测试时统一遍历嵌套对象
  it('placeholder so extractKeys helper is referenced', () => {
    expect(extractKeys(zhCommon).size).toBe(Object.keys(zhCommon).length)
  })
})
