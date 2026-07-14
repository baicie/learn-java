import { describe, expect, it, vi } from 'vitest'
import { render } from 'vitest-browser-react'
import { PermissionModule } from './permission-matrix'

const module = {
  moduleCode: 'work-record',
  moduleName: 'Work Record',
  children: [
    {
      code: 'work-record:read:self',
      moduleCode: 'work-record',
      moduleName: 'Work Record',
      name: 'Read self records',
      description: null,
      riskLevel: 'normal' as const,
      dependencies: [],
    },
    {
      code: 'work-record:write',
      moduleCode: 'work-record',
      moduleName: 'Work Record',
      name: 'Write records',
      description: null,
      riskLevel: 'high' as const,
      dependencies: [],
    },
  ],
}

describe('PermissionMatrix', () => {
  it('supports indeterminate module state', async () => {
    const onChange = vi.fn()
    const screen = await render(
      <PermissionModule
        moduleCode={module.moduleCode}
        moduleName={module.moduleName}
        children={module.children}
        selected={new Set(['work-record:read:self'])}
        onChange={onChange}
      />
    )
    const checkbox = screen.getByRole('checkbox').elements()[0] as HTMLElement
    expect(checkbox.getAttribute('data-state')).toBe('indeterminate')
  })

  it('fires onChange with the entire module when header is toggled', async () => {
    const onChange = vi.fn()
    const screen = await render(
      <PermissionModule
        moduleCode={module.moduleCode}
        moduleName={module.moduleName}
        children={module.children}
        selected={new Set()}
        onChange={onChange}
      />
    )
    const checkbox = screen.getByRole('checkbox').elements()[0] as HTMLElement
    checkbox.click()
    expect(onChange).toHaveBeenCalledWith(
      ['work-record:read:self', 'work-record:write'],
      true
    )
  })

  it('marks dangerous permissions with destructive badge', async () => {
    const screen = await render(
      <PermissionModule
        moduleCode={module.moduleCode}
        moduleName={module.moduleName}
        children={module.children}
        selected={new Set(['work-record:write'])}
        onChange={() => {}}
      />
    )
    await expect.element(screen.getByText('高风险')).toBeVisible()
  })
})
