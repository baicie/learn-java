import { createFileRoute } from '@tanstack/react-router'
import { AuthenticatedLayout } from '@/components/layout/authenticated-layout'
import { requireAuthenticated } from '@/features/auth/permission'

export const Route = createFileRoute('/_authenticated')({
  beforeLoad: () => requireAuthenticated(),
  component: AuthenticatedLayout,
})
