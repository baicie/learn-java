import { createFileRoute } from '@tanstack/react-router'
import { requireAuthenticated } from '@/auth/permission'
import { AuthenticatedLayout } from '@/components/layout/authenticated-layout'

export const Route = createFileRoute('/_authenticated')({
  beforeLoad: () => requireAuthenticated(),
  component: AuthenticatedLayout,
})
