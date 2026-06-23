import '@testing-library/jest-dom/vitest'
import { screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'

import { MarkdownPreview } from '../components/console/MarkdownPreview'
import { renderWithRouter } from '../test/test-utils'

describe('MarkdownPreview', () => {
  it('renders markdown heading and list', () => {
    renderWithRouter(<MarkdownPreview markdown={'# 故障报告\n\n- CPU 使用率持续高位'} />)

    expect(screen.getByRole('heading', { name: '故障报告' })).toBeInTheDocument()
    expect(screen.getByText('CPU 使用率持续高位')).toBeInTheDocument()
  })

  it('renders empty state when markdown is undefined', () => {
    renderWithRouter(<MarkdownPreview />)
    expect(screen.getByText('暂无 Markdown 报告。')).toBeInTheDocument()
  })

  it('renders empty state when markdown is empty string', () => {
    renderWithRouter(<MarkdownPreview markdown="" />)
    expect(screen.getByText('暂无 Markdown 报告。')).toBeInTheDocument()
  })
})
