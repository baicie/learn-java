import { beforeEach, describe, expect, it } from 'vitest'
import { loadProfileAvatar, saveProfileAvatar } from './profile-avatar'

describe('profile avatar', () => {
  beforeEach(() => localStorage.clear())

  it('stores a validated image data URL for the current browser', () => {
    saveProfileAvatar('data:image/png;base64,YQ==')
    expect(loadProfileAvatar()).toBe('data:image/png;base64,YQ==')
  })

  it('rejects non-image and oversized data', () => {
    expect(() => saveProfileAvatar('data:text/plain;base64,YQ==')).toThrow()
    expect(() =>
      saveProfileAvatar(`data:image/png;base64,${'a'.repeat(600_000)}`)
    ).toThrow()
  })
})
