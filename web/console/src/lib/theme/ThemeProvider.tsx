import { useEffect, useState } from 'react'

import { type ThemeMode, readStoredTheme, writeStoredTheme, applyTheme } from './apply'

export { type ThemeMode }

export function useTheme() {
  const [theme, setTheme] = useState<ThemeMode>(() => readStoredTheme())

  useEffect(() => {
    applyTheme(theme)
    writeStoredTheme(theme)
  }, [theme])

  useEffect(() => {
    const mediaQuery = window.matchMedia('(prefers-color-scheme: dark)')
    const handler = () => {
      if (readStoredTheme() === 'system') {
        applyTheme('system')
      }
    }
    mediaQuery.addEventListener('change', handler)
    return () => mediaQuery.removeEventListener('change', handler)
  }, [])

  return { theme, setTheme }
}

export function ThemeInit() {
  useEffect(() => {
    applyTheme(readStoredTheme())
  }, [])
  return null
}
