import { createFileRoute } from '@tanstack/react-router'
import { CalendarsPage } from '@/features/calendars'

export const Route = createFileRoute('/_authenticated/platform/calendars')({
  component: CalendarsPage,
})
