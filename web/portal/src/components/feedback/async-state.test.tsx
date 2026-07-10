import { describe, expect, it } from 'vitest'
import { render } from 'vitest-browser-react'
import { EmptyState, ErrorState, QueryStateBoundary } from './async-state'

describe('QueryStateBoundary', () => {
  it('renders empty state', async () => {
    const screen = await render(
      <QueryStateBoundary
        loading={false}
        error={null}
        empty
        loadingFallback={<div>loading</div>}
        errorFallback={<div>error</div>}
        emptyFallback={<EmptyState title='暂无记录' />}
      >
        <div>content</div>
      </QueryStateBoundary>
    )

    await expect.element(screen.getByText('暂无记录')).toBeVisible()
  })

  it('renders loading when loading', async () => {
    const screen = await render(
      <QueryStateBoundary
        loading
        error={null}
        empty={false}
        loadingFallback={<div>loading…</div>}
        errorFallback={<div>error</div>}
        emptyFallback={<div>empty</div>}
      >
        <div>content</div>
      </QueryStateBoundary>
    )

    await expect.element(screen.getByText('loading…')).toBeVisible()
  })
})

describe('ErrorState', () => {
  it('renders retry button and triggers callback', async () => {
    let retried = false

    const screen = await render(
      <ErrorState
        error={new Error('network error')}
        onRetry={() => {
          retried = true
        }}
      />
    )

    await screen.getByRole('button', { name: '重新加载' }).click()

    expect(retried).toBe(true)
  })
})
