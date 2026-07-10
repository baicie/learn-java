// 同一文件同时导出组件（I18nProvider）和 hook（useI18n），
// 遵循 Phase 16 设计稿中的国际化模块导出约定。
/* eslint-disable react-refresh/only-export-components */
import {
  createContext,
  type ReactNode,
  useContext,
  useMemo,
  useState,
} from 'react'
import { enUS, type MessageKey, zhCN } from './messages'

export type Locale = 'zh-CN' | 'en-US'

type I18nContextValue = {
  locale: Locale
  setLocale: (locale: Locale) => void
  t: (key: MessageKey, params?: Record<string, string | number>) => string
}

const STORAGE_KEY = 'aegisops.locale'

const I18nContext = createContext<I18nContextValue | null>(null)

const dictionaries = {
  'zh-CN': zhCN,
  'en-US': enUS,
} satisfies Record<Locale, Record<MessageKey, string>>

export function I18nProvider({ children }: { children: ReactNode }) {
  const [locale, setLocaleState] = useState<Locale>(() => {
    if (typeof window === 'undefined') return 'zh-CN'
    const stored = window.localStorage.getItem(STORAGE_KEY)
    return stored === 'en-US' ? 'en-US' : 'zh-CN'
  })

  const setLocale = (next: Locale) => {
    if (typeof window !== 'undefined') {
      window.localStorage.setItem(STORAGE_KEY, next)
    }
    setLocaleState(next)
  }

  const value = useMemo<I18nContextValue>(
    () => ({
      locale,
      setLocale,
      t(key, params) {
        let result = dictionaries[locale][key]

        for (const [name, val] of Object.entries(params ?? {})) {
          result = result.split(`{${name}}`).join(String(val))
        }

        return result
      },
    }),
    [locale]
  )

  return <I18nContext.Provider value={value}>{children}</I18nContext.Provider>
}

export function useI18n() {
  const context = useContext(I18nContext)
  if (!context) {
    throw new Error('useI18n must be used inside I18nProvider')
  }
  return context
}
