import '@testing-library/jest-dom'
import { screen } from '@testing-library/react'
import { Route, Routes } from 'react-router-dom'
import { describe, expect, it, vi } from 'vitest'

import { AppLayout } from '../layout/AppLayout'
import { renderWithRouter } from '../test/test-utils'

vi.mock('../api/client', () => ({
  listPlatformMenus: async () => [
    {
      id: 'menu-workbench',
      moduleId: 'platform',
      path: '/app/workbench',
      title: '工作台',
      sortOrder: 10,
      enabled: true,
      createdAt: '2026-07-02T00:00:00Z',
    },
  ],
}))

describe('AppLayout', () => {
  it('renders platform menu and page content', async () => {
    renderWithRouter(
      <Routes>
        <Route
          path="/app/workbench"
          element={
            <AppLayout>
              <div>content</div>
            </AppLayout>
          }
        />
      </Routes>,
      ['/app/workbench'],
    )

    expect(await screen.findByText('工作台')).toBeInTheDocument()
    expect(screen.getByText('content')).toBeInTheDocument()
  })
})
