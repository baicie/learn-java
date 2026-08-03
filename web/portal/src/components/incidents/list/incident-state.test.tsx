import { describe, expect, it } from 'vitest'
import { render } from 'vitest-browser-react'
import { IncidentSeverityBadge } from './incident-state'

describe('IncidentSeverityBadge', () => {
  it('renders disaster severity with the destructive treatment', async () => {
    const screen = await render(<IncidentSeverityBadge severity='disaster' />)

    await expect.element(screen.getByText('灾难')).toHaveClass('bg-destructive')
  })
})
