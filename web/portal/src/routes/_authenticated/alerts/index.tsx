import { z } from 'zod'
import { createFileRoute } from '@tanstack/react-router'
import { requireAnyPermission } from '@/auth/permission'
import { AlertsPage } from '@/pages/alerts'

const alertsSearchSchema = z.object({
  page: z.coerce.number().int().min(1).catch(1),
  pageSize: z.coerce.number().int().min(1).max(100).catch(20),
  keyword: z.string().catch(''),
  statuses: z.array(z.string()).catch([]),
  severities: z.array(z.string()).catch([]),
})

export const Route = createFileRoute('/_authenticated/alerts/')({
  beforeLoad: () => requireAnyPermission(['alert:read']),
  validateSearch: alertsSearchSchema,
  component: Component,
})

// eslint-disable-next-line react-refresh/only-export-components -- TanStack Router requires a route-local component.
function Component() {
  const search = Route.useSearch()
  const navigate = Route.useNavigate()

  return (
    <AlertsPage
      search={search}
      onSearch={(next, replace) =>
        void navigate({
          replace,
          search: (current) => ({ ...current, ...next }),
        })
      }
    />
  )
}
