import { useMemo, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { Plus } from 'lucide-react'
import { getRouteApi } from '@tanstack/react-router'
import { Header } from '@/components/layout/header'
import { Main } from '@/components/layout/main'
import { Button } from '@/components/ui/button'
import { Badge } from '@/components/ui/badge'
import { PermissionGate } from '@/features/auth/permission-gate'
import { ProfileDropdown } from '@/components/profile-dropdown'
import { Search } from '@/components/search'
import { ThemeSwitch } from '@/components/theme-switch'
import { usePlatformUsers } from '../hooks/use-platform-users'
import type { PlatformUserQuery } from '../schemas/platform-user'
import { PlatformUserTable } from './platform-user-table'
import { PlatformUserToolbar } from './platform-user-toolbar'
import { PlatformUserCreateDialog } from './platform-user-create-dialog'

const routeApi = getRouteApi('/_authenticated/platform/users/')

export function PlatformUsersPage() {
  const { t } = useTranslation('platform')
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
      <Header fixed>
        <Search className='me-auto' />
        <ThemeSwitch />
        <ProfileDropdown />
      </Header>

      <Main className='flex flex-1 flex-col gap-4'>
        <div className='flex items-center justify-between'>
          <div>
            <h1 className='text-xl font-semibold'>
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
        </div>

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

        {usersQuery.isPending ? (
          <div className='rounded-md border bg-card/50 p-8 text-center text-sm text-muted-foreground'>
            加载中…
          </div>
        ) : null}

        {usersQuery.isError ? (
          <div className='rounded-md border border-destructive/40 bg-destructive/10 p-4 text-sm text-destructive'>
            加载失败，请稍后重试。
          </div>
        ) : null}

        {usersQuery.data && usersQuery.data.total === 0 ? (
          <div className='rounded-md border bg-card/40 p-10 text-center'>
            <h3 className='text-base font-semibold'>
              {t('platform.users.empty.title')}
            </h3>
            <p className='mt-2 text-sm text-muted-foreground'>
              {t('platform.users.empty.description')}
            </p>
          </div>
        ) : null}

        {usersQuery.data && usersQuery.data.total > 0 ? (
          <PlatformUserTable
            page={usersQuery.data}
            onQueryChange={(next) =>
              navigate({ search: (previous) => ({ ...previous, ...next }) })
            }
          />
        ) : null}

        <span className='sr-only' data-testid='user-status-pill'>
          <Badge variant='outline'>platform-user-page</Badge>
        </span>
      </Main>

      <PlatformUserCreateDialog
        open={createOpen}
        onOpenChange={setCreateOpen}
      />
    </>
  )
}