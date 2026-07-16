import { useEffect } from 'react'
import { z } from 'zod'
import { useForm } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
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
import {
  Form,
  FormControl,
  FormField,
  FormItem,
  FormLabel,
  FormMessage,
} from '@/components/ui/form'
import { Input } from '@/components/ui/input'
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select'
import { notify } from '@/components/feedback/app-toaster'

const schema = z.object({
  assetType: z.enum(['host', 'service', 'application', 'database']),
  name: z.string().trim().min(1, '请输入资源名称').max(256),
  displayName: z.string().trim().max(256),
  environment: z.string().trim().max(128),
  ip: z.string().trim().max(256),
  site: z.string().trim().max(128),
  ownerTeam: z.string().trim().max(128),
  criticality: z.enum(['normal', 'tier-3', 'tier-2', 'tier-1']),
  machineId: z.string().trim().max(512),
})

type Values = z.infer<typeof schema>

function values(asset?: Asset): Values {
  return {
    assetType: (asset?.assetType as Values['assetType']) ?? 'host',
    name: asset?.name ?? '',
    displayName: asset?.displayName ?? '',
    environment: asset?.environment ?? 'production',
    ip: asset?.ip ?? '',
    site: asset?.site ?? '',
    ownerTeam: asset?.ownerTeam ?? '',
    criticality: (asset?.criticality as Values['criticality']) ?? 'normal',
    machineId: '',
  }
}

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
  const form = useForm<Values>({
    resolver: zodResolver(schema),
    defaultValues: values(asset),
  })

  useEffect(() => {
    if (open) form.reset(values(asset))
  }, [asset, form, open])

  const submit = form.handleSubmit((data) => {
    const input = {
      ...data,
      description: asset?.description ?? undefined,
      status: asset?.status,
      tags: asset?.tags,
      version: asset?.version,
      identities:
        !asset && data.machineId
          ? [
              {
                identityType: 'machine_id',
                scopeKey: 'global',
                identityValue: data.machineId,
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
    if (asset) update.mutate({ id: asset.id, input }, options)
    else create.mutate(input, options)
  })

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
        <Form {...form}>
          <form className='grid gap-5 py-2' onSubmit={submit}>
            <section className='grid gap-3'>
              <h3 className='text-sm font-medium'>基本信息</h3>
              <div className='grid gap-4 sm:grid-cols-2'>
                <SelectField
                  form={form}
                  name='assetType'
                  label='资源类型'
                  options={[
                    ['host', '主机'],
                    ['service', '服务'],
                    ['application', '应用'],
                    ['database', '数据库'],
                  ]}
                />
                <TextField form={form} name='name' label='名称' />
                <TextField form={form} name='displayName' label='显示名称' />
                <TextField form={form} name='ip' label='IP / 地址' />
              </div>
            </section>
            <section className='grid gap-3'>
              <h3 className='text-sm font-medium'>运行上下文</h3>
              <div className='grid gap-4 sm:grid-cols-2'>
                <TextField form={form} name='environment' label='环境' />
                <TextField form={form} name='site' label='站点' />
                <TextField form={form} name='ownerTeam' label='负责人团队' />
                <SelectField
                  form={form}
                  name='criticality'
                  label='关键等级'
                  options={[
                    ['normal', '普通'],
                    ['tier-3', 'Tier 3'],
                    ['tier-2', 'Tier 2'],
                    ['tier-1', 'Tier 1'],
                  ]}
                />
              </div>
            </section>
            {!asset ? (
              <TextField
                form={form}
                name='machineId'
                label='Machine ID（可选）'
              />
            ) : null}
            <DialogFooter>
              <Button
                type='button'
                variant='outline'
                onClick={() => onOpenChange(false)}
              >
                取消
              </Button>
              <Button
                type='submit'
                disabled={create.isPending || update.isPending}
              >
                {create.isPending || update.isPending
                  ? '保存中…'
                  : asset
                    ? '保存修改'
                    : '创建资源'}
              </Button>
            </DialogFooter>
          </form>
        </Form>
      </DialogContent>
    </Dialog>
  )
}

type FormApi = ReturnType<typeof useForm<Values>>
type FieldName = keyof Values

function TextField({
  form,
  name,
  label,
}: {
  form: FormApi
  name: FieldName
  label: string
}) {
  return (
    <FormField
      control={form.control}
      name={name}
      render={({ field }) => (
        <FormItem>
          <FormLabel>{label}</FormLabel>
          <FormControl>
            <Input {...field} />
          </FormControl>
          <FormMessage />
        </FormItem>
      )}
    />
  )
}

function SelectField({
  form,
  name,
  label,
  options,
}: {
  form: FormApi
  name: FieldName
  label: string
  options: ReadonlyArray<readonly [string, string]>
}) {
  return (
    <FormField
      control={form.control}
      name={name}
      render={({ field }) => (
        <FormItem>
          <FormLabel>{label}</FormLabel>
          <Select value={field.value} onValueChange={field.onChange}>
            <FormControl>
              <SelectTrigger>
                <SelectValue />
              </SelectTrigger>
            </FormControl>
            <SelectContent>
              {options.map(([value, text]) => (
                <SelectItem key={value} value={value}>
                  {text}
                </SelectItem>
              ))}
            </SelectContent>
          </Select>
          <FormMessage />
        </FormItem>
      )}
    />
  )
}
