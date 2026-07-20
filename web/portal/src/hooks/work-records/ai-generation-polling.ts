import type { AiGeneration } from '@/api/work-records/extensions'

const AI_GENERATION_POLL_INTERVAL_MS = 2_000
const ACTIVE_AI_GENERATION_STATUSES = new Set(['queued', 'running'])

export function getAiGenerationRefetchInterval(
  generations: readonly Pick<AiGeneration, 'status'>[] | undefined
) {
  return generations?.some(({ status }) =>
    ACTIVE_AI_GENERATION_STATUSES.has(status)
  )
    ? AI_GENERATION_POLL_INTERVAL_MS
    : false
}
