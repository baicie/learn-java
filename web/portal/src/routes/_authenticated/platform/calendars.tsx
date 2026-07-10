import { createFileRoute } from '@tanstack/react-router'
import { requireAnyPermission } from '@/features/auth/permission'
import { CalendarsPage } from '@/features/calendars'

export const Route = createFileRoute('/_authenticated/platform/calendars')({
  beforeLoad: () => requireAnyPermission(['platform:calendar:read']),
  component: CalendarsPage,
})
