import { z } from 'zod'
import { createFileRoute } from '@tanstack/react-router'
import { requireAnyPermission } from '@/auth/permission'
import { PlatformUsersPage } from '@/components/iam/platform-users-page'

const usersSearchSchema = z.object({
  page: z.coerce.number().int().min(1).catch(1),
  pageSize: z.coerce.number().int().min(1).max(200).catch(20),
  keyword: z.string().catch(''),
  status: z.enum(['active', 'disabled', 'locked', 'pending']).optional(),
  roleCodes: z.array(z.string()).catch([]),
  sortBy: z.string().catch(''),
  sortDir: z.enum(['asc', 'desc']).catch('desc'),
})

export const Route = createFileRoute('/_authenticated/platform/users/')({
  beforeLoad: () => requireAnyPermission(['platform:user:read']),
  validateSearch: usersSearchSchema,
  component: PlatformUsersPage,
})
