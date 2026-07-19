import { useMemo, useState } from 'react'
import { getRouteApi } from '@tanstack/react-router'
import { PermissionGate } from '@/auth/permission-gate'
import { Plus } from 'lucide-react'
import { useTranslation } from 'react-i18next'
import type { PlatformUserQuery } from '@/lib/iam/platform-user'
import { usePlatformUsers } from '@/hooks/iam/use-platform-users'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import {
  EmptyState,
  ErrorState,
  QueryStateBoundary,
  TableLoadingState,
} from '@/components/feedback/async-state'
import { PlatformUserCreateDialog } from './platform-user-create-dialog'
import { PlatformUserTable } from './platform-user-table'
import { PlatformUserToolbar } from './platform-user-toolbar'

const routeApi = getRouteApi('/_authenticated/platform/users/')

export function PlatformUsersPage() {
  const { t } = useTranslation()
  const search = routeApi.useSearch()
  const navigate = routeApi.useNavigate()
  const [createOpen, setCreateOpen] = useState(false)

  const query = useMemo<PlatformUserQuery>(
    () => ({
      page: search.page ?? 1,
      pageSize: search.pageSize ?? 20,
      keyword: search.keyword,
      status: search.status,
      roleCodes: search.roleCodes,
      sortBy: search.sortBy,
      sortDir: search.sortDir,
    }),
    [search]
  )

  const usersQuery = usePlatformUsers(query)

  return (
    <>
      <main className='flex flex-1 flex-col gap-4 p-4 md:gap-6 md:p-6'>
        <header className='flex flex-col gap-4 sm:flex-row sm:items-start sm:justify-between'>
          <div>
            <h1 className='text-xl font-semibold md:text-2xl'>
              {t('platform.users.title')}
            </h1>
            <p className='text-sm text-muted-foreground'>
              {t('platform.users.description')}
            </p>
          </div>
          <PermissionGate anyOf={['platform:user:write']}>
            <Button onClick={() => setCreateOpen(true)}>
              <Plus className='mr-2 size-4' />
              {t('platform.users.create')}
            </Button>
          </PermissionGate>
        </header>

        <PlatformUserToolbar
          value={query}
          onChange={(next) =>
            navigate({
              search: (previous) => ({
                ...previous,
                ...next,
                page: 1,
              }),
            })
          }
        />

        <QueryStateBoundary
          loading={usersQuery.isPending}
          error={usersQuery.isError ? usersQuery.error : null}
          empty={usersQuery.data?.total === 0}
          loadingFallback={<TableLoadingState rows={6} columns={5} />}
          errorFallback={
            <ErrorState
              compact
              error={usersQuery.error}
              onRetry={() => void usersQuery.refetch()}
            />
          }
          emptyFallback={
            <EmptyState
              compact
              title={t('platform.users.empty.title')}
              description={t('platform.users.empty.description')}
            />
          }
        >
          {usersQuery.data && usersQuery.data.total > 0 ? (
            <PlatformUserTable
              page={usersQuery.data}
              onQueryChange={(next) =>
                navigate({ search: (previous) => ({ ...previous, ...next }) })
              }
            />
          ) : null}
        </QueryStateBoundary>

        <span className='sr-only' data-testid='user-status-pill'>
          <Badge variant='outline'>platform-user-page</Badge>
        </span>
      </main>

      <PlatformUserCreateDialog
        open={createOpen}
        onOpenChange={setCreateOpen}
      />
    </>
  )
}
