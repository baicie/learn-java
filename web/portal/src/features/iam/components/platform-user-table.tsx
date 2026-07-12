import { useCallback } from 'react'
import { useTranslation } from 'react-i18next'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { PermissionGate } from '@/features/auth/permission-gate'
import { useConfirm } from '@/components/feedback/confirm-provider'
import {
  useChangeUserStatus,
  usePlatformUsers,
} from '../hooks/use-platform-users'
import { platformUserKeys } from '../api/query-keys'
import type { PlatformUser, PlatformUserPage } from '../schemas/platform-user'
import { useQueryClient } from '@tanstack/react-query'
import { toIamRequestError } from '../errors/iam-api-error'

const STATUS_LABEL: Record<PlatformUser['status'], string> = {
  active: '正常',
  disabled: '已禁用',
  locked: '已锁定',
  pending: '待激活',
}

const STATUS_VARIANT: Record<
  PlatformUser['status'],
  'default' | 'secondary' | 'destructive' | 'outline'
> = {
  active: 'default',
  disabled: 'outline',
  locked: 'destructive',
  pending: 'secondary',
}

export type PlatformUserTableProps = {
  page: PlatformUserPage
  onQueryChange: (next: Record<string, unknown>) => void
}

export function PlatformUserTable({ page, onQueryChange }: PlatformUserTableProps) {
  return (
    <div
      className='overflow-hidden rounded-md border'
      data-testid='platform-user-table'
    >
      <table className='w-full text-sm'>
        <thead className='bg-muted/40 text-xs uppercase text-muted-foreground'>
          <tr>
            <th className='px-4 py-2 text-left font-medium'>用户名</th>
            <th className='px-4 py-2 text-left font-medium'>显示名</th>
            <th className='px-4 py-2 text-left font-medium'>邮箱</th>
            <th className='px-4 py-2 text-left font-medium'>状态</th>
            <th className='px-4 py-2 text-left font-medium'>角色</th>
            <th className='px-4 py-2 text-left font-medium'>最近登录</th>
            <th className='px-4 py-2 text-right font-medium'>操作</th>
          </tr>
        </thead>
        <tbody>
          {page.items.map((user) => (
            <Row key={user.id} user={user} onQueryChange={onQueryChange} />
          ))}
        </tbody>
      </table>
      <Pagination page={page} onQueryChange={onQueryChange} />
    </div>
  )
}

function Row({
  user,
  onQueryChange,
}: {
  user: PlatformUser
  onQueryChange: (next: Record<string, unknown>) => void
}) {
  const confirm = useConfirm()
  const queryClient = useQueryClient()
  const { t } = useTranslation('platform')
  const changeStatus = useChangeUserStatus(user.id)

  const onDisable = useCallback(async () => {
    if (user.status === 'disabled') return
    const accepted = await confirm({
      title: t('platform.users.confirm.disable.title', {
        name: user.displayName,
      }),
      description: t('platform.users.confirm.disable.description'),
      confirmText: t('platform.users.confirm.disable.confirmLabel'),
      cancelText: '取消',
      variant: 'destructive',
      confirmationText: user.username,
    })
    if (!accepted) return
    try {
      await changeStatus.mutateAsync({
        status: 'disabled',
        reason: '管理员操作',
        rowVersion: user.rowVersion,
      })
      await queryClient.invalidateQueries({ queryKey: platformUserKeys.lists() })
    } catch (error) {
      const iamError = toIamRequestError(error)
      if (iamError.code === 'platform.user.version_conflict') {
        onQueryChange({})
        window.alert(t('platform.users.error.version_conflict'))
      }
    }
  }, [user, confirm, changeStatus, queryClient, onQueryChange, t])

  return (
    <tr
      className='border-t hover:bg-muted/30'
      data-testid={`user-row-${user.username}`}
    >
      <td className='px-4 py-2 font-medium'>{user.username}</td>
      <td className='px-4 py-2'>{user.displayName}</td>
      <td className='px-4 py-2 text-muted-foreground'>{user.email ?? '—'}</td>
      <td className='px-4 py-2'>
        <Badge variant={STATUS_VARIANT[user.status]}>
          {STATUS_LABEL[user.status]}
        </Badge>
      </td>
      <td className='px-4 py-2'>
        <div className='flex flex-wrap gap-1'>
          {user.roles.map((role) => (
            <Badge key={role.code} variant='outline'>
              {role.name}
            </Badge>
          ))}
        </div>
      </td>
      <td className='px-4 py-2 text-xs text-muted-foreground'>
        {user.lastLoginAt
          ? new Date(user.lastLoginAt).toLocaleString()
          : '—'}
      </td>
      <td className='px-4 py-2 text-right'>
        <PermissionGate anyOf={['platform:user:status']}>
          {user.status === 'active' ? (
            <Button
              variant='outline'
              size='sm'
              onClick={onDisable}
              data-testid={`user-disable-${user.username}`}
            >
              禁用
            </Button>
          ) : (
            <Button
              variant='outline'
              size='sm'
              disabled
              data-testid={`user-disabled-${user.username}`}
            >
              已{STATUS_LABEL[user.status]}
            </Button>
          )}
        </PermissionGate>
      </td>
    </tr>
  )
}

function Pagination({
  page,
  onQueryChange,
}: {
  page: PlatformUserPage
  onQueryChange: (next: Record<string, unknown>) => void
}) {
  const totalPages = Math.max(1, Math.ceil(page.total / page.pageSize))
  return (
    <div className='flex items-center justify-between border-t bg-muted/20 px-4 py-2 text-xs text-muted-foreground'>
      <span>
        第 {page.page} / {totalPages} 页 · 共 {page.total} 条
      </span>
      <div className='flex items-center gap-2'>
        <Button
          variant='outline'
          size='sm'
          disabled={page.page <= 1}
          onClick={() => onQueryChange({ page: page.page - 1 })}
        >
          上一页
        </Button>
        <Button
          variant='outline'
          size='sm'
          disabled={page.page >= totalPages}
          onClick={() => onQueryChange({ page: page.page + 1 })}
        >
          下一页
        </Button>
      </div>
    </div>
  )
}

// Use export so that consumers can re-render with their current search.
export function usePlatformUsersPage(query: Parameters<typeof usePlatformUsers>[0]) {
  return usePlatformUsers(query)
}