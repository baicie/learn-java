export type { ThemeMode } from './types'
import type { ThemeMode } from './types'

export function readStoredTheme(): ThemeMode {
  if (typeof window === 'undefined') {
    return 'system'
  }

  const saved = window.localStorage.getItem('aegisops.theme')

  if (saved === 'system' || saved === 'light' || saved === 'dark') {
    return saved
  }

  return 'system'
}

export function writeStoredTheme(theme: ThemeMode): void {
  if (typeof window === 'undefined') {
    return
  }

  window.localStorage.setItem('aegisops.theme', theme)
}

export function resolveSystemTheme(): 'light' | 'dark' {
  if (typeof window === 'undefined') {
    return 'light'
  }

  return window.matchMedia('(prefers-color-scheme: dark)').matches ? 'dark' : 'light'
}

export function applyTheme(theme: ThemeMode): void {
  if (typeof document === 'undefined') {
    return
  }

  const resolvedTheme = theme === 'system' ? resolveSystemTheme() : theme
  document.documentElement.classList.toggle('dark', resolvedTheme === 'dark')
  document.documentElement.dataset.theme = theme
}
