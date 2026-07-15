import { useCallback, useMemo, useState } from 'react'
import { PermissionGate } from '@/auth/permission-gate'
import type {
  PermissionModule as PermissionModuleType,
  PlatformRole,
} from '@/lib/iam/platform-role'
import {
  useDeletePlatformRole,
  useReplaceRolePermissions,
} from '@/hooks/iam/use-platform-roles'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Separator } from '@/components/ui/separator'
import { useConfirm } from '@/components/feedback/confirm-provider'
import { PermissionModule } from './permission-matrix'

type RoleEditorSnapshot = {
  role: PlatformRole
  selectedPermissions: Set<string>
  initialPermissions: Set<string>
}

type RoleEditorState = {
  role: PlatformRole | null
  selected: Set<string>
  initial: Set<string>
  isDirty: boolean
  dangerousCodes: ReadonlyArray<string>
}

export type RoleEditorApi = {
  state: RoleEditorState
  apply: (role: PlatformRole) => void
  applyChange: (codes: ReadonlyArray<string>, checked: boolean) => void
  save: (reason: string, dangerousAcknowledged: boolean) => Promise<void>
  confirmLeaveIfDirty: () => Promise<boolean>
  reset: () => void
}

// eslint-disable-next-line react-refresh/only-export-components -- the editor hook and component share the same cohesive state model
export function useRoleEditor(initialRole?: PlatformRole): RoleEditorApi {
  const [snapshot, setSnapshot] = useState<RoleEditorSnapshot | null>(null)
  const initialSnapshot = useMemo(
    () => (initialRole ? snapshotOf(initialRole) : null),
    [initialRole]
  )
  const currentSnapshot = snapshot ?? initialSnapshot
  const replaceMutation = useReplaceRolePermissions(
    currentSnapshot?.role.roleCode ?? '__none__'
  )
  const confirm = useConfirm()

  const apply = useCallback((role: PlatformRole) => {
    setSnapshot(snapshotOf(role))
  }, [])

  const applyChange = useCallback(
    (codes: ReadonlyArray<string>, checked: boolean) => {
      setSnapshot((current) => {
        const source = current ?? initialSnapshot
        if (!source) return current
        const next = new Set(source.selectedPermissions)
        for (const code of codes) {
          if (checked) next.add(code)
          else next.delete(code)
        }
        return {
          role: source.role,
          selectedPermissions: next,
          initialPermissions: source.initialPermissions,
        }
      })
    },
    [initialSnapshot]
  )

  const reset = useCallback(() => {
    if (!currentSnapshot) return
    setSnapshot({
      role: currentSnapshot.role,
      selectedPermissions: new Set(currentSnapshot.initialPermissions),
      initialPermissions: currentSnapshot.initialPermissions,
    })
  }, [currentSnapshot])

  const isDirty = useMemo(() => {
    if (!currentSnapshot) return false
    if (
      currentSnapshot.selectedPermissions.size !==
      currentSnapshot.initialPermissions.size
    ) {
      return true
    }
    for (const code of currentSnapshot.selectedPermissions) {
      if (!currentSnapshot.initialPermissions.has(code)) return true
    }
    return false
  }, [currentSnapshot])

  const dangerousCodes = useMemo(() => {
    if (!currentSnapshot) return []
    const added: string[] = []
    for (const code of currentSnapshot.selectedPermissions) {
      if (!currentSnapshot.initialPermissions.has(code)) added.push(code)
    }
    return added
  }, [currentSnapshot])

  const save = useCallback(
    async (reason: string, dangerousAcknowledged: boolean) => {
      if (!currentSnapshot) return
      await replaceMutation.mutateAsync({
        permissionCodes: [...currentSnapshot.selectedPermissions],
        confirmation: { reason, dangerousAcknowledged },
      })
      const next = new Set(currentSnapshot.selectedPermissions)
      setSnapshot({
        role: currentSnapshot.role,
        selectedPermissions: next,
        initialPermissions: next,
      })
    },
    [currentSnapshot, replaceMutation]
  )

  return {
    state: {
      role: currentSnapshot?.role ?? null,
      selected: currentSnapshot?.selectedPermissions ?? new Set(),
      initial: currentSnapshot?.initialPermissions ?? new Set(),
      isDirty,
      dangerousCodes,
    },
    applyChange,
    save,
    confirmLeaveIfDirty: async () => {
      if (!isDirty) return true
      return confirm({
        title: '切换角色',
        description: '当前角色的修改尚未保存，切换会丢失这些修改。',
        confirmText: '切换并丢弃',
        cancelText: '取消',
        variant: 'warning',
      })
    },
    reset,
    apply,
  }
}

function snapshotOf(role: PlatformRole): RoleEditorSnapshot {
  return {
    role,
    selectedPermissions: new Set(role.permissions),
    initialPermissions: new Set(role.permissions),
  }
}

export function RoleEditor({
  editor,
  permissionTree,
  onDeleted,
}: {
  editor: RoleEditorApi
  permissionTree: ReadonlyArray<PermissionModuleType>
  onDeleted?: () => void
}) {
  if (!editor.state.role) {
    return (
      <div className='flex h-full items-center justify-center p-12 text-sm text-muted-foreground'>
        请选择左侧角色开始编辑。
      </div>
    )
  }
  const role = editor.state.role
  return (
    <div className='flex h-full flex-col overflow-hidden'>
      <header className='flex flex-wrap items-center gap-3 border-b px-6 py-4'>
        <div className='flex-1'>
          <h2 className='flex items-center gap-2 text-lg font-semibold'>
            {role.roleName}
            {role.system ? <Badge variant='secondary'>系统</Badge> : null}
            {!role.enabled ? <Badge variant='outline'>已停用</Badge> : null}
          </h2>
          <p className='font-mono text-xs text-muted-foreground'>
            {role.roleCode} · {role.userCount} 个用户
          </p>
        </div>
        <div className='flex items-center gap-2'>
          <PermissionGate anyOf={['platform:role:write']}>
            {!role.system ? (
              <DeleteButton role={role} onDeleted={onDeleted} />
            ) : null}
            <Button
              type='button'
              variant='outline'
              disabled={!editor.state.isDirty}
              onClick={() => editor.reset()}
            >
              重置
            </Button>
            <SaveButton editor={editor} />
          </PermissionGate>
        </div>
      </header>

      <Separator />

      <div className='flex-1 overflow-y-auto px-6 py-4'>
        <div className='grid gap-4'>
          {permissionTree.map((module) => (
            <PermissionModule
              key={module.moduleCode}
              moduleCode={module.moduleCode}
              moduleName={module.moduleName}
              children={module.children}
              selected={editor.state.selected}
              onChange={editor.applyChange}
            />
          ))}
        </div>
      </div>
    </div>
  )
}

function DeleteButton({
  role,
  onDeleted,
}: {
  role: PlatformRole
  onDeleted?: () => void
}) {
  const remove = useDeletePlatformRole()
  const confirm = useConfirm()
  return (
    <Button
      type='button'
      variant='destructive'
      disabled={remove.isPending || role.userCount > 0}
      onClick={async () => {
        const accepted = await confirm({
          title: `删除角色 ${role.roleName}`,
          description: '删除后不可继续分配；已有用户时禁止删除。',
          confirmText: '确认删除',
          cancelText: '取消',
          variant: 'destructive',
          confirmationText: role.roleCode,
        })
        if (!accepted) return
        await remove.mutateAsync(role.roleCode)
        onDeleted?.()
      }}
    >
      删除
    </Button>
  )
}

function SaveButton({ editor }: { editor: RoleEditorApi }) {
  const confirm = useConfirm()
  const handleSave = useCallback(async () => {
    if (!editor.state.isDirty) return
    const dangerous = editor.state.dangerousCodes.length > 0
    const reason = window.prompt('请输入变更原因') ?? ''
    if (reason.trim().length === 0) return
    if (dangerous) {
      const accepted = await confirm({
        title: '危险权限确认',
        description: '本次修改包含 critical 权限，必须明确确认后才可保存。',
        confirmText: '确认保存',
        cancelText: '取消',
        variant: 'destructive',
      })
      if (!accepted) return
    }
    await editor.save(reason, dangerous)
  }, [editor, confirm])

  return (
    <Button type='button' disabled={!editor.state.isDirty} onClick={handleSave}>
      保存
    </Button>
  )
}
