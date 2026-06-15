import { render, screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'

import { StatusBadge, SeverityBadge } from './status-badge'

describe('StatusBadge', () => {
  it('renders the status text verbatim', () => {
    render(<StatusBadge status="open" />)
    expect(screen.getByText('open')).toBeInTheDocument()
  })

  it('falls back to neutral tone for unknown status', () => {
    const { container } = render(<StatusBadge status="not-a-real-status" />)
    const span = container.querySelector('[data-slot="status-badge"]')
    expect(span).toHaveAttribute('data-tone', 'neutral')
  })

  it('maps known statuses to semantic tones', () => {
    const cases: Array<[string, string]> = [
      ['open', 'danger'],
      ['investigating', 'warning'],
      ['mitigating', 'info'],
      ['resolved', 'success'],
      ['closed', 'muted'],
    ]
    for (const [status, tone] of cases) {
      const { container } = render(<StatusBadge status={status} />)
      const span = container.querySelector('[data-slot="status-badge"]')
      expect(span, `status=${status}`).toHaveAttribute('data-tone', tone)
    }
  })
})

describe('SeverityBadge', () => {
  it('uppercases the severity label', () => {
    render(<SeverityBadge severity="critical" />)
    expect(screen.getByText('critical')).toHaveClass('uppercase')
  })

  it('uses danger tone for high-severity values', () => {
    const { container } = render(<SeverityBadge severity="high" />)
    expect(container.querySelector('[data-slot="severity-badge"]')).toHaveAttribute(
      'data-tone',
      'danger',
    )
  })
})
