import { createFileRoute, Outlet } from '@tanstack/react-router'
import { requireAnyPermission } from '@/auth/permission'

export const Route = createFileRoute('/_authenticated/work-records/templates')({
  beforeLoad: () => requireAnyPermission(['work-record:template:read']),
  component: Outlet,
})
