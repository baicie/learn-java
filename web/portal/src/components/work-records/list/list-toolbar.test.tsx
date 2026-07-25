import { i18n } from '@/i18n'
import { I18nextProvider } from 'react-i18next'
import { describe, expect, it, vi } from 'vitest'
import { render } from 'vitest-browser-react'
import { userEvent } from 'vitest/browser'
import { ListToolbar } from './list-toolbar'
import { buildEmptyListQuery } from './types'

async function renderToolbar(onChange = vi.fn()) {
  const screen = await render(
    <I18nextProvider i18n={i18n} defaultNS='translation'>
      <ListToolbar
        query={buildEmptyListQuery()}
        userOptions={[]}
        onChange={onChange}
      />
    </I18nextProvider>
  )
  return { screen, onChange }
}

describe('ListToolbar date filters', () => {
  it('labels the two date inputs as record time range', async () => {
    const { screen } = await renderToolbar()

    await expect.element(screen.getByText('记录时间（开始）')).toBeVisible()
    await expect.element(screen.getByText('记录时间（结束）')).toBeVisible()
  })

  it('shows the placeholder in yyyy/mm/dd style when empty', async () => {
    const { screen } = await renderToolbar()

    const from = screen.getByRole('button', { name: '记录时间（开始）' })
    const to = screen.getByRole('button', { name: '记录时间（结束）' })

    await expect.element(from).toHaveTextContent('yyyy/mm/dd')
    await expect.element(to).toHaveTextContent('yyyy/mm/dd')
  })

  it('displays existing values as yyyy/mm/dd', async () => {
    const screen = await render(
      <I18nextProvider i18n={i18n} defaultNS='translation'>
        <ListToolbar
          query={{
            ...buildEmptyListQuery(),
            recordTimeFrom: '2026-07-01',
            recordTimeTo: '2026-07-22',
          }}
          userOptions={[]}
          onChange={vi.fn()}
        />
      </I18nextProvider>
    )

    await expect
      .element(screen.getByRole('button', { name: '记录时间（开始）' }))
      .toHaveTextContent('2026/07/01')
    await expect
      .element(screen.getByRole('button', { name: '记录时间（结束）' }))
      .toHaveTextContent('2026/07/22')
  })

  it('picks a date from the calendar and applies it on search', async () => {
    const { screen, onChange } = await renderToolbar()

    await screen.getByRole('button', { name: '记录时间（开始）' }).click()
    await screen.getByRole('button', { name: /15日/ }).click()

    await screen.getByRole('button', { name: '查询' }).click()
    expect(onChange).toHaveBeenCalledWith(
      expect.objectContaining({
        recordTimeFrom: expect.stringMatching(/^\d{4}-\d{2}-15$/),
      })
    )
  })

  it('lets outside clicks pass through while closing the calendar', async () => {
    const { screen, onChange } = await renderToolbar()

    await screen.getByRole('button', { name: '记录时间（开始）' }).click()
    await expect
      .element(screen.getByRole('button', { name: /15日/ }))
      .toBeVisible()

    // Clicking 查询 while the calendar is open must not be swallowed:
    // the calendar closes and the search fires in a single click.
    await screen.getByRole('button', { name: '查询' }).click()
    expect(onChange).toHaveBeenCalled()
    await expect
      .element(screen.getByRole('button', { name: /15日/ }))
      .not.toBeInTheDocument()
  })
})

describe('ListToolbar labeled layout', () => {
  it('shows a label for every filter field', async () => {
    const { screen } = await renderToolbar()

    await expect
      .element(screen.getByText('关键词', { exact: true }))
      .toBeVisible()
    await expect
      .element(screen.getByText('工作类型', { exact: true }).first())
      .toBeVisible()
    await expect
      .element(screen.getByText('负责人', { exact: true }).first())
      .toBeVisible()
    await expect
      .element(screen.getByText('创建人', { exact: true }).first())
      .toBeVisible()
    await expect
      .element(screen.getByText('状态', { exact: true }).first())
      .toBeVisible()
  })

  it('renders reset and search actions', async () => {
    const { screen } = await renderToolbar()

    await expect
      .element(screen.getByRole('button', { name: '重置筛选' }))
      .toBeVisible()
    await expect
      .element(screen.getByRole('button', { name: '查询' }))
      .toBeVisible()
  })
})

describe('ListToolbar draft filters', () => {
  it('does not apply filters until the search button is clicked', async () => {
    const { screen, onChange } = await renderToolbar()

    await userEvent.fill(screen.getByPlaceholder('搜索记录标题…'), '日报')
    expect(onChange).not.toHaveBeenCalled()

    await screen.getByRole('button', { name: '查询' }).click()
    expect(onChange).toHaveBeenCalledWith(
      expect.objectContaining({ keyword: '日报' })
    )
  })

  it('applies filters when pressing enter in the keyword input', async () => {
    const { screen, onChange } = await renderToolbar()

    const keyword = screen.getByPlaceholder('搜索记录标题…')
    await userEvent.fill(keyword, '周报')
    await userEvent.keyboard('{Enter}')

    expect(onChange).toHaveBeenCalledWith(
      expect.objectContaining({ keyword: '周报' })
    )
  })

  it('resets filters immediately with the reset button', async () => {
    const onChange = vi.fn()
    const screen = await render(
      <I18nextProvider i18n={i18n} defaultNS='translation'>
        <ListToolbar
          query={{ ...buildEmptyListQuery(), keyword: '日报' }}
          userOptions={[]}
          onChange={onChange}
        />
      </I18nextProvider>
    )

    await expect
      .element(screen.getByPlaceholder('搜索记录标题…'))
      .toHaveValue('日报')

    await screen.getByRole('button', { name: '重置筛选' }).click()
    expect(onChange).toHaveBeenCalledWith(
      expect.objectContaining({
        keyword: '',
        templateId: '',
        ownerId: '',
        creatorId: '',
        statuses: [],
        recordTimeFrom: '',
        recordTimeTo: '',
      })
    )
  })
})
