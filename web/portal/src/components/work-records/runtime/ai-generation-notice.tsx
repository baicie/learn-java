import { TriangleAlert } from 'lucide-react'
import type { AiGeneration } from '@/api/work-records/extensions'
import { Alert, AlertDescription, AlertTitle } from '@/components/ui/alert'

function warningsFromJson(value?: string | null): string[] {
  if (!value) return []
  try {
    const parsed: unknown = JSON.parse(value)
    return Array.isArray(parsed)
      ? parsed.filter((item): item is string => typeof item === 'string')
      : []
  } catch {
    return []
  }
}

export function AiGenerationNotice({
  generation,
}: {
  generation: AiGeneration
}) {
  const warnings = warningsFromJson(generation.warningsJson)
  if (!generation.fallbackReason && warnings.length === 0) return null

  return (
    <Alert variant='destructive'>
      <TriangleAlert aria-hidden='true' />
      <AlertTitle>AI 生成已降级</AlertTitle>
      <AlertDescription className='space-y-1'>
        {warnings.map((warning) => (
          <p key={warning}>{warning}</p>
        ))}
        {generation.fallbackReason && (
          <p>降级原因：{generation.fallbackReason}</p>
        )}
      </AlertDescription>
    </Alert>
  )
}
