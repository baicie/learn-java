import { describe, expect, it, vi } from 'vitest'
import { render } from 'vitest-browser-react'
import { AlertFilterBar } from './alert-filter-bar'

describe('AlertFilterBar', () => {
  it('offers disaster as a severity filter', async () => {
    const screen = await render(
      <AlertFilterBar
        keyword=''
        onKeywordChange={vi.fn()}
        columnFilters={[]}
        onColumnFiltersChange={vi.fn()}
      />
    )

    await expect.element(screen.getByText('灾难')).toBeVisible()
  })
})
