import { createFileRoute } from '@tanstack/react-router'
import { requireAnyPermission } from '@/auth/permission'
import { AiModelsPage } from '@/pages/ai-models'

export const Route = createFileRoute('/_authenticated/platform/ai-models')({
  beforeLoad: () => requireAnyPermission(['admin:manage']),
  component: AiModelsPage,
})
