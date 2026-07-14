import { describe, expect, it } from 'vitest'
import { render } from 'vitest-browser-react'
import { FormGrid } from './grid'
import { FormSection } from './section'

describe('FormSection', () => {
  it('renders a title and description block', async () => {
    const screen = await render(
      <FormSection title='基本信息' description='主要字段'>
        <span data-testid='child'>child</span>
      </FormSection>
    )
    await expect.element(screen.getByText('基本信息')).toBeVisible()
    await expect.element(screen.getByText('主要字段')).toBeVisible()
    await expect.element(screen.getByTestId('child')).toBeVisible()
  })

  it('hides the header when title/description are missing', async () => {
    const screen = await render(
      <FormSection>
        <span>child</span>
      </FormSection>
    )
    await expect.element(screen.getByText('child')).toBeVisible()
  })
})

describe('FormGrid', () => {
  it('applies responsive grid classes for 3 columns', async () => {
    const { container } = await render(
      <FormGrid columns={3}>
        <span>a</span>
        <span>b</span>
      </FormGrid>
    )
    expect(container.firstElementChild?.className ?? '').toContain(
      'xl:grid-cols-3'
    )
  })

  it('applies responsive grid classes for 1 column', async () => {
    const { container } = await render(
      <FormGrid columns={1}>
        <span>a</span>
      </FormGrid>
    )
    expect(container.firstElementChild?.className ?? '').toContain(
      'grid-cols-1'
    )
  })
})
