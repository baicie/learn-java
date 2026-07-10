import { describe, expect, it, vi } from 'vitest'
import { resolveBlockedNavigation } from './use-unsaved-changes-guard'

describe('resolveBlockedNavigation', () => {
  it('proceeds when user confirms', async () => {
    const proceed = vi.fn()
    const reset = vi.fn()

    await resolveBlockedNavigation({ proceed, reset }, async () => true)

    expect(proceed).toHaveBeenCalledOnce()
    expect(reset).not.toHaveBeenCalled()
  })

  it('resets when user cancels', async () => {
    const proceed = vi.fn()
    const reset = vi.fn()

    await resolveBlockedNavigation({ proceed, reset }, async () => false)

    expect(reset).toHaveBeenCalledOnce()
    expect(proceed).not.toHaveBeenCalled()
  })
})
