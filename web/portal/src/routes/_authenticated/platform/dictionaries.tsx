import { createFileRoute } from '@tanstack/react-router'
import { requireAnyPermission } from '@/auth/permission'
import { DictionariesPage } from '@/pages/dictionaries'

export const Route = createFileRoute('/_authenticated/platform/dictionaries')({
  beforeLoad: () => requireAnyPermission(['platform:dict:read']),
  component: DictionariesPage,
})
