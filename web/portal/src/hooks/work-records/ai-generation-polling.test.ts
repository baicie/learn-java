import { describe, expect, it } from 'vitest'
import { getAiGenerationRefetchInterval } from './ai-generation-polling'

describe('getAiGenerationRefetchInterval', () => {
  it.each(['queued', 'running'])(
    'polls quickly while a generation is %s',
    (status) => {
      expect(getAiGenerationRefetchInterval([{ status }])).toBe(2_000)
    }
  )

  it.each(['success', 'failed', 'cancelled'])(
    'stops polling after status %s',
    (status) => {
      expect(getAiGenerationRefetchInterval([{ status }])).toBe(false)
    }
  )

  it('stops polling when no generation exists', () => {
    expect(getAiGenerationRefetchInterval([])).toBe(false)
  })
})
