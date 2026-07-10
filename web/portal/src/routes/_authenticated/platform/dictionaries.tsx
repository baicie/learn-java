import { createFileRoute } from '@tanstack/react-router'
import { requireAnyPermission } from '@/features/auth/permission'
import { DictionariesPage } from '@/features/dictionaries'

export const Route = createFileRoute('/_authenticated/platform/dictionaries')({
  beforeLoad: () => requireAnyPermission(['platform:dict:read']),
  component: DictionariesPage,
})
