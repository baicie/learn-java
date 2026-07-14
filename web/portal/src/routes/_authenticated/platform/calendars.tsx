import { createFileRoute } from '@tanstack/react-router'
import { requireAnyPermission } from '@/auth/permission'
import { CalendarsPage } from '@/pages/calendars'

export const Route = createFileRoute('/_authenticated/platform/calendars')({
  beforeLoad: () => requireAnyPermission(['platform:calendar:read']),
  component: CalendarsPage,
})
