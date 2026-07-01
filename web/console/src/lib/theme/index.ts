import { readStoredTheme, writeStoredTheme, resolveSystemTheme, applyTheme } from './apply'

export const THEME_STORAGE_KEY = 'aegisops.theme'

export { readStoredTheme, writeStoredTheme, resolveSystemTheme, applyTheme }
export type { ThemeMode } from './types'
