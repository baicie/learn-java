import '@/styles/theme.css'
import { clearCookies } from '@/test-utils/cookies'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { render } from 'vitest-browser-react'
import { ThemeProvider } from './theme-provider'

describe('ThemeProvider', () => {
  beforeEach(() => {
    clearCookies()
    document.documentElement.classList.remove('light', 'dark')

    const meta = document.createElement('meta')
    meta.name = 'theme-color'
    meta.content = 'outdated-theme-color'
    document.head.append(meta)
  })

  afterEach(() => {
    document.querySelector("meta[name='theme-color']")?.remove()
  })

  it('keeps the browser chrome color aligned with the resolved theme', async () => {
    await render(
      <ThemeProvider defaultTheme='dark'>
        <span>Theme content</span>
      </ThemeProvider>
    )

    await vi.waitFor(() =>
      expect(document.documentElement.classList.contains('dark')).toBe(true)
    )

    const expected = getComputedStyle(document.documentElement)
      .getPropertyValue('--background')
      .trim()

    expect(
      document.querySelector<HTMLMetaElement>("meta[name='theme-color']")
        ?.content
    ).toBe(expected)
  })
})
