import { useCallback, useState } from 'react'
import { useQueryClient } from '@tanstack/react-query'
import { PermissionGate } from '@/auth/permission-gate'
import { useTranslation } from 'react-i18next'
import { platformUserKeys } from '@/api/iam/query-keys'
import { formatDateTime } from '@/lib/date-format'
import { toIamRequestError } from '@/lib/iam/errors/iam-api-error'
import type { PlatformUser, PlatformUserPage } from '@/lib/iam/platform-user'
import { useChangeUserStatus } from '@/hooks/iam/use-platform-users'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from '@/components/ui/table'
import { useConfirm } from '@/components/feedback/confirm-provider'
import { PlatformUserManageDialog } from './platform-user-manage-dialog'

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

export function PlatformUserTable({
  page,
  onQueryChange,
}: PlatformUserTableProps) {
  return (
    <div
      className='overflow-hidden rounded-md border'
      data-testid='platform-user-table'
    >
      <Table>
        <TableHeader className='bg-muted/40 text-xs text-muted-foreground uppercase'>
          <TableRow>
            <TableHead>用户名</TableHead>
            <TableHead>显示名</TableHead>
            <TableHead>邮箱</TableHead>
            <TableHead>状态</TableHead>
            <TableHead>角色</TableHead>
            <TableHead>最近登录</TableHead>
            <TableHead className='text-right'>操作</TableHead>
          </TableRow>
        </TableHeader>
        <TableBody>
          {page.items.map((user) => (
            <Row key={user.id} user={user} onQueryChange={onQueryChange} />
          ))}
        </TableBody>
      </Table>
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
  const { t } = useTranslation()
  const changeStatus = useChangeUserStatus(user.id)
  const [manageOpen, setManageOpen] = useState(false)

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
      await queryClient.invalidateQueries({
        queryKey: platformUserKeys.lists(),
      })
    } catch (error) {
      const iamError = toIamRequestError(error)
      if (iamError.code === 'platform.user.version_conflict') {
        onQueryChange({})
        window.alert(t('platform.users.error.version_conflict'))
      }
    }
  }, [user, confirm, changeStatus, queryClient, onQueryChange, t])

  const onEnable = useCallback(async () => {
    await changeStatus.mutateAsync({
      status: 'active',
      reason: '管理员恢复账号',
      rowVersion: user.rowVersion,
    })
    await queryClient.invalidateQueries({ queryKey: platformUserKeys.lists() })
  }, [changeStatus, queryClient, user.rowVersion])

  return (
    <>
      <TableRow
        className='border-t hover:bg-muted/30'
        data-testid={`user-row-${user.username}`}
      >
        <TableCell className='font-medium'>{user.username}</TableCell>
        <TableCell>{user.displayName}</TableCell>
        <TableCell className='text-muted-foreground'>
          {user.email ?? '—'}
        </TableCell>
        <TableCell>
          <Badge variant={STATUS_VARIANT[user.status]}>
            {STATUS_LABEL[user.status]}
          </Badge>
        </TableCell>
        <TableCell>
          <div className='flex flex-wrap gap-1'>
            {user.roles.map((role) => (
              <Badge key={role.code} variant='outline'>
                {role.name}
              </Badge>
            ))}
          </div>
        </TableCell>
        <TableCell className='text-xs text-muted-foreground'>
          {user.lastLoginAt ? formatDateTime(user.lastLoginAt) : '—'}
        </TableCell>
        <TableCell className='text-right'>
          <div className='flex justify-end gap-2'>
            <PermissionGate
              anyOf={[
                'platform:user:write',
                'platform:user:assign-role',
                'platform:user:reset-password',
              ]}
            >
              <Button
                variant='outline'
                size='sm'
                onClick={() => setManageOpen(true)}
              >
                管理
              </Button>
            </PermissionGate>
            <PermissionGate anyOf={['platform:user:status']}>
              <Button
                variant='outline'
                size='sm'
                onClick={user.status === 'active' ? onDisable : onEnable}
                data-testid={`user-status-${user.username}`}
              >
                {user.status === 'active' ? '禁用' : '启用'}
              </Button>
            </PermissionGate>
          </div>
        </TableCell>
      </TableRow>
      {manageOpen ? (
        <PlatformUserManageDialog
          user={user}
          open
          onOpenChange={setManageOpen}
        />
      ) : null}
    </>
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
