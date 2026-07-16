import { useState } from 'react'
import type { Asset } from '@/lib/assets/asset'
import { useCreateAsset, useUpdateAsset } from '@/hooks/assets/use-assets'
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
import { Label } from '@/components/ui/label'
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select'
import { notify } from '@/components/feedback/app-toaster'

export function AssetFormDialog({
  open,
  onOpenChange,
  asset,
}: {
  open: boolean
  onOpenChange: (open: boolean) => void
  asset?: Asset
}) {
  const create = useCreateAsset()
  const update = useUpdateAsset()
  const [assetType, setAssetType] = useState(asset?.assetType ?? 'host')
  const [name, setName] = useState(asset?.name ?? '')
  const [displayName, setDisplayName] = useState(asset?.displayName ?? '')
  const [environment, setEnvironment] = useState(
    asset?.environment ?? 'production'
  )
  const [ip, setIp] = useState(asset?.ip ?? '')
  const [site, setSite] = useState(asset?.site ?? '')
  const [ownerTeam, setOwnerTeam] = useState(asset?.ownerTeam ?? '')
  const [criticality, setCriticality] = useState(asset?.criticality ?? 'normal')
  const [machineId, setMachineId] = useState('')

  const submit = () => {
    if (!name.trim()) return notify.error('请输入资源名称')
    const input = {
      assetType,
      name: name.trim(),
      displayName: displayName.trim(),
      description: asset?.description ?? undefined,
      environment,
      ip: ip.trim(),
      site: site.trim(),
      ownerTeam: ownerTeam.trim(),
      criticality,
      status: asset?.status,
      tags: asset?.tags,
      version: asset?.version,
      identities:
        !asset && machineId.trim()
          ? [
              {
                identityType: 'machine_id',
                scopeKey: 'global',
                identityValue: machineId.trim(),
                verified: true,
              },
            ]
          : [],
    }
    const options = {
      onSuccess: () => {
        notify.success(asset ? '资源已更新' : '资源已创建')
        onOpenChange(false)
      },
      onError: (error: Error) =>
        notify.error(error, asset ? '更新资源失败' : '创建资源失败'),
    }
    if (asset) {
      update.mutate({ id: asset.id, input }, options)
    } else {
      create.mutate(input, options)
    }
  }

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className='max-h-[90vh] overflow-y-auto sm:max-w-2xl'>
        <DialogHeader>
          <DialogTitle>{asset ? '编辑资源' : '新增资源'}</DialogTitle>
          <DialogDescription>
            {asset
              ? '更新规范资源字段；外部来源标识仍保持只读。'
              : '手工创建规范资源。来源固定为 manual，外部来源标识不可伪造。'}
          </DialogDescription>
        </DialogHeader>
        <div className='grid gap-5 py-2'>
          <section className='grid gap-3'>
            <h3 className='text-sm font-medium'>基本信息</h3>
            <div className='grid gap-4 sm:grid-cols-2'>
              <div className='grid gap-2'>
                <Label>资源类型</Label>
                <Select value={assetType} onValueChange={setAssetType}>
                  <SelectTrigger>
                    <SelectValue />
                  </SelectTrigger>
                  <SelectContent>
                    <SelectItem value='host'>主机</SelectItem>
                    <SelectItem value='service'>服务</SelectItem>
                    <SelectItem value='application'>应用</SelectItem>
                    <SelectItem value='database'>数据库</SelectItem>
                  </SelectContent>
                </Select>
              </div>
              <Field label='名称' value={name} onChange={setName} />
              <Field
                label='显示名称'
                value={displayName}
                onChange={setDisplayName}
              />
              <Field label='IP / 地址' value={ip} onChange={setIp} />
            </div>
          </section>
          <section className='grid gap-3'>
            <h3 className='text-sm font-medium'>运行上下文</h3>
            <div className='grid gap-4 sm:grid-cols-2'>
              <Field
                label='环境'
                value={environment}
                onChange={setEnvironment}
              />
              <Field label='站点' value={site} onChange={setSite} />
              <Field
                label='负责人团队'
                value={ownerTeam}
                onChange={setOwnerTeam}
              />
              <div className='grid gap-2'>
                <Label>关键等级</Label>
                <Select value={criticality} onValueChange={setCriticality}>
                  <SelectTrigger>
                    <SelectValue />
                  </SelectTrigger>
                  <SelectContent>
                    <SelectItem value='normal'>普通</SelectItem>
                    <SelectItem value='tier-3'>Tier 3</SelectItem>
                    <SelectItem value='tier-2'>Tier 2</SelectItem>
                    <SelectItem value='tier-1'>Tier 1</SelectItem>
                  </SelectContent>
                </Select>
              </div>
            </div>
          </section>
          {!asset ? (
            <section className='grid gap-3'>
              <h3 className='text-sm font-medium'>身份信息</h3>
              <Field
                label='Machine ID（可选）'
                value={machineId}
                onChange={setMachineId}
              />
            </section>
          ) : null}
        </div>
        <DialogFooter>
          <Button variant='outline' onClick={() => onOpenChange(false)}>
            取消
          </Button>
          <Button
            disabled={create.isPending || update.isPending}
            onClick={submit}
          >
            {create.isPending || update.isPending
              ? '保存中…'
              : asset
                ? '保存修改'
                : '创建资源'}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}

function Field({
  label,
  value,
  onChange,
}: {
  label: string
  value: string
  onChange: (value: string) => void
}) {
  return (
    <div className='grid gap-2'>
      <Label>{label}</Label>
      <Input value={value} onChange={(event) => onChange(event.target.value)} />
    </div>
  )
}
