import { beforeEach, describe, expect, it } from 'vitest'
import {
  DEFAULT_LANGUAGE,
  LANGUAGE_STORAGE_KEY,
  SUPPORTED_LANGUAGES,
  i18n,
  setLanguage,
  t,
} from './index'

describe('i18n', () => {
  beforeEach(() => {
    window.localStorage.clear()
    void i18n.changeLanguage(DEFAULT_LANGUAGE)
  })

  it('defaults to zh-CN with all required keys', () => {
    expect(i18n.language).toBe(DEFAULT_LANGUAGE)
    expect(t('workRecords.nav.root')).toBe('工作记录')
    expect(t('platform.nav.dictionaries')).toBe('字典管理')
    expect(t('common.save')).toBe('保存')
  })

  it('exposes both zh-CN and en-US in supported languages', () => {
    expect(SUPPORTED_LANGUAGES).toEqual(['zh-CN', 'en-US'])
    expect(i18n.hasResourceBundle('zh-CN', 'translation')).toBe(true)
    expect(i18n.hasResourceBundle('en-US', 'translation')).toBe(true)
  })

  it('switches to en-US via setLanguage and persists the choice', () => {
    setLanguage('en-US')
    expect(i18n.language).toBe('en-US')
    expect(t('workRecords.nav.root')).toBe('Work Records')
    expect(t('platform.nav.dictionaries')).toBe('Dictionaries')
    expect(window.localStorage.getItem(LANGUAGE_STORAGE_KEY)).toBe('en-US')
  })

  it('returns the requested key string when no translation exists in any language', () => {
    void i18n.changeLanguage('en-US')
    // 未知键：i18next 在两层回退都没命中时回吐原 key 本身。
    // 用 `as never` 绕过 MessageKey 字面量类型约束。
    const unknown = 'workRecords.nav.doesNotExist' as never
    expect(t(unknown)).toBe('workRecords.nav.doesNotExist')
  })
})
