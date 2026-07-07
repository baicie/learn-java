import { describe, expect, it } from 'vitest'
import { t } from './index'

describe('i18n', () => {
  it('returns zh-CN text by key', () => {
    expect(t('workRecords.nav.root')).toBe('工作记录')
  })

  it('keeps platform keys in the same typed dictionary', () => {
    expect(t('platform.nav.dictionaries')).toBe('字典管理')
  })
})
