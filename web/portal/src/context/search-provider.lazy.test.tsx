import { expect, it, vi } from 'vitest'
import { render } from 'vitest-browser-react'
import { userEvent } from 'vitest/browser'
import { SearchProvider } from './search-provider'

const commandMenuModule = vi.hoisted(() => ({
  loads: 0,
}))

vi.mock('@/components/command-menu', () => {
  commandMenuModule.loads += 1

  return {
    CommandMenu: () => <div data-testid='lazy-command-menu' />,
  }
})

it('loads the command menu only after the search shortcut opens it', async () => {
  const screen = await render(<SearchProvider>{null}</SearchProvider>)

  expect(commandMenuModule.loads).toBe(0)

  await userEvent.keyboard('{Control>}k{/Control}')

  await vi.waitFor(() => expect(commandMenuModule.loads).toBe(1))
  await expect
    .element(screen.getByTestId('lazy-command-menu'))
    .toBeInTheDocument()
})
