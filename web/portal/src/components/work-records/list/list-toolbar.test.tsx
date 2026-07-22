import { i18n } from '@/i18n'
import { I18nextProvider } from 'react-i18next'
import { describe, expect, it, vi } from 'vitest'
import { render } from 'vitest-browser-react'
import { ListToolbar } from './list-toolbar'
import { buildEmptyListQuery } from './types'

function renderToolbar() {
  return render(
    <I18nextProvider i18n={i18n} defaultNS='translation'>
      <ListToolbar
        query={buildEmptyListQuery()}
        userOptions={[]}
        onChange={vi.fn()}
      />
    </I18nextProvider>
  )
}

describe('ListToolbar date filters', () => {
  it('labels the two date inputs as record time range', async () => {
    const screen = await renderToolbar()

    await expect.element(screen.getByText('记录时间（开始）')).toBeVisible()
    await expect.element(screen.getByText('记录时间（结束）')).toBeVisible()
  })

  it('uses date-only inputs instead of datetime inputs', async () => {
    const screen = await renderToolbar()

    const from = screen.getByLabelText('记录时间（开始）')
    const to = screen.getByLabelText('记录时间（结束）')

    await expect.element(from).toHaveAttribute('type', 'date')
    await expect.element(to).toHaveAttribute('type', 'date')
  })
})
