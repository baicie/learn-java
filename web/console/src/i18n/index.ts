import i18n from 'i18next'
import { initReactI18next } from 'react-i18next'

import enUS from './locales/en-US.json'
import zhCN from './locales/zh-CN.json'

export const SUPPORTED_LANGUAGES = ['zh-CN', 'en-US'] as const
export type SupportedLanguage = (typeof SUPPORTED_LANGUAGES)[number]

export const DEFAULT_LANGUAGE: SupportedLanguage = 'zh-CN'

export const LANGUAGE_STORAGE_KEY = 'aegisops.language'

const STORED_LANGUAGE =
  typeof window !== 'undefined' ? window.localStorage.getItem(LANGUAGE_STORAGE_KEY) : null

const initialLanguage: SupportedLanguage =
  STORED_LANGUAGE && (SUPPORTED_LANGUAGES as readonly string[]).includes(STORED_LANGUAGE)
    ? (STORED_LANGUAGE as SupportedLanguage)
    : DEFAULT_LANGUAGE

void i18n.use(initReactI18next).init({
  resources: {
    'zh-CN': { translation: zhCN },
    'en-US': { translation: enUS },
  },
  lng: initialLanguage,
  fallbackLng: DEFAULT_LANGUAGE,
  interpolation: {
    escapeValue: false,
  },
  returnNull: false,
})

export function setLanguage(language: SupportedLanguage): void {
  void i18n.changeLanguage(language)
  if (typeof window !== 'undefined') {
    window.localStorage.setItem(LANGUAGE_STORAGE_KEY, language)
  }
}

export { i18n }
