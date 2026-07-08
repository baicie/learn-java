import { i18n } from '@/i18n'
import { I18nextProvider } from 'react-i18next'
import { describe, expect, it, vi } from 'vitest'
import { render } from 'vitest-browser-react'
import { ExportRecordsDialog } from './export-records-dialog'

vi.mock('../api/work-record-api', () => ({
  exportRecords: vi.fn(() => Promise.resolve(new Blob(['test']))),
}))

describe('ExportRecordsDialog', () => {
  it('renders trigger button', async () => {
    const { getByRole } = await render(
      <I18nextProvider i18n={i18n}>
        <ExportRecordsDialog total={10} />
      </I18nextProvider>
    )
    const button = getByRole('button')
    await expect.element(button).toBeInTheDocument()
  })

  it('opens dialog when trigger clicked', async () => {
    const { getByRole } = await render(
      <I18nextProvider i18n={i18n}>
        <ExportRecordsDialog total={42} />
      </I18nextProvider>
    )

    const trigger = getByRole('button')
    await trigger.click()

    await expect.element(getByRole('dialog')).toBeInTheDocument()
  })
})
