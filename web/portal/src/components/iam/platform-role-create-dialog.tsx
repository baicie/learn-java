import { useState } from 'react'
import { useCreatePlatformRole } from '@/hooks/iam/use-platform-roles'
import { Button } from '@/components/ui/button'
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog'
import { Input } from '@/components/ui/input'

export function PlatformRoleCreateDialog({
  open,
  onOpenChange,
}: {
  open: boolean
  onOpenChange: (open: boolean) => void
}) {
  const create = useCreatePlatformRole()
  const [code, setCode] = useState('')
  const [name, setName] = useState('')
  const [description, setDescription] = useState('')

  const close = () => {
    setCode('')
    setName('')
    setDescription('')
    onOpenChange(false)
  }

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>新建角色</DialogTitle>
          <DialogDescription>
            创建租户角色后，再在权限矩阵中配置权限。
          </DialogDescription>
        </DialogHeader>
        <div className='grid gap-3'>
          <Input
            aria-label='角色编码'
            value={code}
            onChange={(event) => setCode(event.target.value)}
          />
          <Input
            aria-label='角色名称'
            value={name}
            onChange={(event) => setName(event.target.value)}
          />
          <Input
            aria-label='角色描述'
            value={description}
            onChange={(event) => setDescription(event.target.value)}
          />
        </div>
        <DialogFooter>
          <Button variant='outline' onClick={close}>
            取消
          </Button>
          <Button
            disabled={create.isPending || !code.trim() || !name.trim()}
            onClick={() =>
              create.mutate(
                {
                  code: code.trim(),
                  name: name.trim(),
                  description: description.trim() || null,
                  system: false,
                  enabled: true,
                  permissionCodes: [],
                },
                { onSuccess: close }
              )
            }
          >
            创建
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}
