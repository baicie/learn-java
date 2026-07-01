import '@testing-library/jest-dom'
import { screen } from '@testing-library/react'
import { Route, Routes } from 'react-router-dom'
import { describe, expect, it } from 'vitest'

import { RolePermissionPage } from '../pages/platform/RolePermissionPage'
import { renderWithRouter } from '../test/test-utils'

describe('RolePermissionPage', () => {
  it('renders placeholder content', () => {
    renderWithRouter(
      <Routes>
        <Route path="/platform/roles" element={<RolePermissionPage />} />
      </Routes>,
      ['/platform/roles'],
    )

    expect(screen.getByText('角色权限')).toBeInTheDocument()
    expect(screen.getByText('管理员')).toBeInTheDocument()
    expect(screen.getByText('运维人员')).toBeInTheDocument()
    expect(screen.getByText('只读用户')).toBeInTheDocument()
  })
})
