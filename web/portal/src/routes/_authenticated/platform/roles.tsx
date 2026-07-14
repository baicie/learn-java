import { createFileRoute } from '@tanstack/react-router'
import { requireAnyPermission } from '@/auth/permission'
import { PlatformRolesPage } from '@/components/iam/platform-roles-page'

export const Route = createFileRoute('/_authenticated/platform/roles')({
  beforeLoad: () => requireAnyPermission(['platform:role:read']),
  component: PlatformRolesPage,
})
