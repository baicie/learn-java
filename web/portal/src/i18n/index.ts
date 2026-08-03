import i18n from 'i18next'
import { initReactI18next } from 'react-i18next'
import { enUS, type Translation } from './locales/en-US'
import { zhCN } from './locales/zh-CN'

export const SUPPORTED_LANGUAGES = ['zh-CN', 'en-US'] as const
export type SupportedLanguage = (typeof SUPPORTED_LANGUAGES)[number]

export const DEFAULT_LANGUAGE: SupportedLanguage = 'zh-CN'

export const LANGUAGE_STORAGE_KEY = 'aegisops.language'

export type { Translation }

const STORED_LANGUAGE =
  typeof window !== 'undefined'
    ? window.localStorage.getItem(LANGUAGE_STORAGE_KEY)
    : null

const initialLanguage: SupportedLanguage =
  STORED_LANGUAGE &&
  (SUPPORTED_LANGUAGES as readonly string[]).includes(STORED_LANGUAGE)
    ? (STORED_LANGUAGE as SupportedLanguage)
    : DEFAULT_LANGUAGE

void i18n.use(initReactI18next).init({
  resources: {
    'zh-CN': { translation: zhCN },
    'en-US': { translation: enUS },
  },
  lng: initialLanguage,
  fallbackLng: DEFAULT_LANGUAGE,
  defaultNS: 'translation',
  ns: ['translation'],
  interpolation: {
    escapeValue: false,
  },
  returnNull: false,
  // 语言已通过 resources 预加载，关闭 i18next 内部 suspend。
  react: {
    useSuspense: false,
  },
})

export function setLanguage(language: SupportedLanguage): void {
  void i18n.changeLanguage(language)
  if (typeof window !== 'undefined') {
    window.localStorage.setItem(LANGUAGE_STORAGE_KEY, language)
  }
}

/**
 * 非组件场景（如模块顶层常量、纯函数）的翻译入口。
 * 组件内部请使用 react-i18next 的 `useTranslation` hook，以获得响应式更新。
 */
export function t(key: keyof Translation): string {
  return i18n.t(key as string)
}

export { i18n }
