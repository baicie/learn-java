import '@testing-library/jest-dom'
import { screen } from '@testing-library/react'
import { Route, Routes } from 'react-router-dom'
import { afterEach, describe, expect, it, vi } from 'vitest'

import { ModuleListPage } from '../pages/modules/ModuleListPage'
import { renderWithRouter } from '../test/test-utils'

const mockModules = [
  {
    id: 'mod-1',
    moduleId: 'platform',
    name: '平台底座',
    version: '1.0.0',
    enabled: true,
    healthStatus: 'HEALTHY',
    configJson: '{}',
    createdAt: '2026-01-01T00:00:00Z',
  },
]

const mockFetch = vi.fn()

vi.stubGlobal('fetch', mockFetch)

afterEach(() => {
  mockFetch.mockClear()
})

describe('ModuleListPage', () => {
  it('renders module cards when loaded', async () => {
    mockFetch.mockResolvedValueOnce(
      new Response(JSON.stringify({ data: mockModules, success: true }), {
        status: 200,
        headers: { 'content-type': 'application/json' },
      }),
    )

    renderWithRouter(
      <Routes>
        <Route path="/modules" element={<ModuleListPage />} />
      </Routes>,
      ['/modules'],
    )

    expect(await screen.findByText('平台底座')).toBeInTheDocument()
    expect(screen.getByText('HEALTHY')).toBeInTheDocument()
    expect(screen.getByText('1.0.0')).toBeInTheDocument()
  })

  it('renders skeleton while loading', () => {
    mockFetch.mockReturnValueOnce(
      new Promise(() => {}), // never resolves
    )

    renderWithRouter(
      <Routes>
        <Route path="/modules" element={<ModuleListPage />} />
      </Routes>,
      ['/modules'],
    )

    // skeleton class should be present
    expect(document.querySelector('[class*="animate-pulse"]')).toBeInTheDocument()
  })

  it('renders empty state when no modules', async () => {
    mockFetch.mockResolvedValueOnce(
      new Response(JSON.stringify({ data: [], success: true }), {
        status: 200,
        headers: { 'content-type': 'application/json' },
      }),
    )

    renderWithRouter(
      <Routes>
        <Route path="/modules" element={<ModuleListPage />} />
      </Routes>,
      ['/modules'],
    )

    expect(await screen.findByText('暂无模块')).toBeInTheDocument()
  })
})
