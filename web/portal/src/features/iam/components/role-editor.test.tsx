import { describe, expect, it } from 'vitest'
import { render } from 'vitest-browser-react'
import { act } from 'react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { ConfirmProvider } from '@/components/feedback/confirm-provider'
import { useRoleEditor } from './role-editor'
import type { PlatformRole } from '../schemas/platform-role'

const role: PlatformRole = {
  roleCode: 'ops-viewer',
  roleName: 'Ops Viewer',
  description: null,
  enabled: true,
  system: false,
  userCount: 0,
  permissions: ['platform:user:read'],
  dataScopes: {},
  rowVersion: 1,
}

interface HarnessProps {
  initialCode?: string
}

function Harness({ initialCode }: HarnessProps) {
  const editor = useRoleEditor(initialCode)
  return (
    <div>
      <span data-testid='dirty'>{String(editor.state.isDirty)}</span>
      <span data-testid='has-write'>
        {String(editor.state.selected.has('platform:user:write'))}
      </span>
      <span data-testid='dangerous'>
        {editor.state.dangerousCodes.join(',')}
      </span>
      <button
        type='button'
        data-testid='apply'
        onClick={() => editor.apply(role)}
      >
        apply
      </button>
      <button
        type='button'
        data-testid='change-write'
        onClick={() => editor.applyChange(['platform:user:write'], true)}
      >
        change-write
      </button>
      <button
        type='button'
        data-testid='change-dangerous'
        onClick={() => editor.applyChange(['platform:user:status'], true)}
      >
        change-dangerous
      </button>
      <button
        type='button'
        data-testid='reset'
        onClick={() => editor.reset()}
      >
        reset
      </button>
    </div>
  )
}

const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
const wrap = (ui: React.ReactNode) => (
  <QueryClientProvider client={client}>
    <ConfirmProvider>{ui}</ConfirmProvider>
  </QueryClientProvider>
)

describe('useRoleEditor', () => {
  it('starts clean', async () => {
    const screen = await render(wrap(<Harness initialCode='ops-viewer' />))
    await expect.element(screen.getByTestId('dirty')).toHaveTextContent('false')
  })

  it('marks dirty when permissions change', async () => {
    const screen = await render(wrap(<Harness initialCode='ops-viewer' />))
    await act(async () => {
      ;(screen.getByTestId('apply').element() as HTMLButtonElement).click()
      ;(screen.getByTestId('change-write').element() as HTMLButtonElement).click()
    })
    await expect.element(screen.getByTestId('dirty')).toHaveTextContent('true')
    await expect.element(screen.getByTestId('has-write')).toHaveTextContent('true')
  })

  it('flags dangerous added codes', async () => {
    const screen = await render(wrap(<Harness initialCode='ops-viewer' />))
    await act(async () => {
      ;(screen.getByTestId('apply').element() as HTMLButtonElement).click()
      ;(screen.getByTestId('change-dangerous').element() as HTMLButtonElement).click()
    })
    await expect.element(screen.getByTestId('dangerous')).toHaveTextContent(
      'platform:user:status'
    )
  })

  it('reset restores the initial set', async () => {
    const screen = await render(wrap(<Harness initialCode='ops-viewer' />))
    await act(async () => {
      ;(screen.getByTestId('apply').element() as HTMLButtonElement).click()
      ;(screen.getByTestId('change-write').element() as HTMLButtonElement).click()
    })
    await act(async () => {
      ;(screen.getByTestId('reset').element() as HTMLButtonElement).click()
    })
    await expect.element(screen.getByTestId('dirty')).toHaveTextContent('false')
    await expect.element(screen.getByTestId('has-write')).toHaveTextContent('false')
  })
})