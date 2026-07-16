import { createFileRoute } from '@tanstack/react-router'
import { requireAnyPermission } from '@/auth/permission'
import { DatasourcesPage } from '@/pages/datasources'

export const Route = createFileRoute('/_authenticated/datasources/')({
  beforeLoad: () => requireAnyPermission(['datasource:read']),
  component: DatasourcesPage,
})
