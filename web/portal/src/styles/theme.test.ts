import { afterAll, describe, expect, it } from 'vitest'
import './theme.css'

type Rgb = [number, number, number]

const initialClassName = document.documentElement.className

afterAll(() => {
  document.documentElement.className = initialClassName
})

describe('portal theme hierarchy and contrast', () => {
  it.each(['light', 'dark'] as const)(
    'keeps text and primary actions readable in %s mode',
    (mode) => {
      applyTheme(mode)

      expect(contrast('--foreground', '--background')).toBeGreaterThanOrEqual(7)
      expect(
        contrast('--muted-foreground', '--background')
      ).toBeGreaterThanOrEqual(4.5)
      expect(
        contrast('--primary-foreground', '--primary')
      ).toBeGreaterThanOrEqual(4.5)
    }
  )

  it('uses brighter surfaces over the light canvas', () => {
    applyTheme('light')

    const canvas = luminance(readToken('--background'))

    expect(luminance(readToken('--card'))).toBeGreaterThan(canvas + 0.015)
    expect(luminance(readToken('--sidebar'))).toBeGreaterThan(canvas + 0.015)
  })

  it('uses elevated cards and a recessed sidebar in dark mode', () => {
    applyTheme('dark')

    const canvas = luminance(readToken('--background'))

    expect(luminance(readToken('--card'))).toBeGreaterThan(canvas + 0.012)
    expect(luminance(readToken('--sidebar'))).toBeLessThan(canvas - 0.003)
  })
})

function applyTheme(mode: 'light' | 'dark') {
  document.documentElement.classList.toggle('dark', mode === 'dark')
  document.documentElement.classList.toggle('light', mode === 'light')
}

function contrast(foregroundToken: string, backgroundToken: string) {
  const foreground = luminance(readToken(foregroundToken))
  const background = luminance(readToken(backgroundToken))
  const lighter = Math.max(foreground, background)
  const darker = Math.min(foreground, background)

  return (lighter + 0.05) / (darker + 0.05)
}

function readToken(token: string): Rgb {
  const probe = document.createElement('span')
  probe.style.color = `var(${token})`
  document.body.append(probe)

  const canvas = document.createElement('canvas')
  canvas.width = 1
  canvas.height = 1
  const context = canvas.getContext('2d')

  if (!context) {
    throw new Error('Unable to create a canvas color context')
  }

  context.fillStyle = getComputedStyle(probe).color
  context.fillRect(0, 0, 1, 1)
  const [red, green, blue] = context.getImageData(0, 0, 1, 1).data
  probe.remove()

  return [red, green, blue]
}

function luminance([red, green, blue]: Rgb) {
  const [r, g, b] = [red, green, blue].map((channel) => {
    const value = channel / 255
    return value <= 0.04045
      ? value / 12.92
      : Math.pow((value + 0.055) / 1.055, 2.4)
  })

  return 0.2126 * r + 0.7152 * g + 0.0722 * b
}
