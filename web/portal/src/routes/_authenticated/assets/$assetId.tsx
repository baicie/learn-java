import { createFileRoute } from '@tanstack/react-router'
import { requireAnyPermission } from '@/auth/permission'
import { AssetDetailPage } from '@/pages/assets/detail'

export const Route = createFileRoute('/_authenticated/assets/$assetId')({
  beforeLoad: () => requireAnyPermission(['asset:read']),
  component: Component,
})

// eslint-disable-next-line react-refresh/only-export-components -- TanStack Router requires a route-local component.
function Component() {
  const { assetId } = Route.useParams()
  return <AssetDetailPage assetId={assetId} />
}
