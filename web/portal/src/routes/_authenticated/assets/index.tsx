import { z } from 'zod'
import { createFileRoute } from '@tanstack/react-router'
import { requireAnyPermission } from '@/auth/permission'
import { AssetsPage } from '@/pages/assets'

const searchSchema = z.object({
  page: z.coerce.number().int().min(1).catch(1),
  pageSize: z.coerce.number().int().min(1).max(100).catch(20),
  keyword: z.string().catch(''),
  assetType: z.string().catch(''),
  sourceType: z.string().catch(''),
  status: z.string().catch(''),
})

export const Route = createFileRoute('/_authenticated/assets/')({
  beforeLoad: () => requireAnyPermission(['asset:read']),
  validateSearch: searchSchema,
  component: Component,
})

// eslint-disable-next-line react-refresh/only-export-components -- TanStack Router requires a route-local component.
function Component() {
  const search = Route.useSearch()
  const navigate = Route.useNavigate()
  return (
    <AssetsPage
      search={search}
      onSearch={(next) =>
        void navigate({ search: (current) => ({ ...current, ...next }) })
      }
    />
  )
}
