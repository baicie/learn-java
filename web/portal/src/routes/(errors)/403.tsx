import { createFileRoute } from '@tanstack/react-router'
import { ForbiddenPage } from '@/features/errors/forbidden-page'

export const Route = createFileRoute('/(errors)/403')({
  component: ForbiddenPage,
})
