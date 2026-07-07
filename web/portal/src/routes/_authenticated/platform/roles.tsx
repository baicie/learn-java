import { createFileRoute } from '@tanstack/react-router'
import { Roles } from '@/features/roles'

export const Route = createFileRoute('/_authenticated/platform/roles')({
  component: Roles,
})
