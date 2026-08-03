import { describe, expect, it } from 'vitest'
import { render } from 'vitest-browser-react'
import { AlertSeverityBadge } from './alert-badges'

describe('AlertSeverityBadge', () => {
  it('renders disaster severity with the destructive treatment', async () => {
    const screen = await render(<AlertSeverityBadge severity='disaster' />)

    await expect.element(screen.getByText('灾难')).toHaveClass('bg-destructive')
  })
})
