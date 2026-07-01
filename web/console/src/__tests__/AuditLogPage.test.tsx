import '@testing-library/jest-dom'
import { screen } from '@testing-library/react'
import { Route, Routes } from 'react-router-dom'
import { describe, expect, it } from 'vitest'

import { AuditLogPage } from '../pages/audit/AuditLogPage'
import { renderWithRouter } from '../test/test-utils'

describe('AuditLogPage', () => {
  it('renders placeholder content', () => {
    renderWithRouter(
      <Routes>
        <Route path="/audit" element={<AuditLogPage />} />
      </Routes>,
      ['/audit'],
    )

    expect(screen.getByText('审计日志')).toBeInTheDocument()
    expect(screen.getByText('Phase 1 建立入口，后续展示关键操作记录。')).toBeInTheDocument()
  })
})
