import { createFileRoute } from '@tanstack/react-router'
import { requireAnyPermission } from '@/features/auth/permission'
import { PlatformRolesPage } from '@/features/iam/components/platform-roles-page'

export const Route = createFileRoute('/_authenticated/platform/roles')({
  beforeLoad: () => requireAnyPermission(['platform:role:read']),
  component: PlatformRolesPage,
})