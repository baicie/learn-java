import { z } from 'zod'
import { useForm } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { useCreateDatasource } from '@/hooks/datasources/use-datasources'
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
import { notify } from '@/components/feedback/app-toaster'

const schema = z
  .object({
    name: z.string().trim().min(1, '请输入名称'),
    endpoint: z.url('请输入有效的 Zabbix Endpoint'),
    username: z.string().trim(),
    password: z.string(),
    apiToken: z.string().trim(),
  })
  .refine((value) => value.apiToken || (value.username && value.password), {
    message: '请填写 API Token，或同时填写用户名和密码',
    path: ['apiToken'],
  })

type Values = z.infer<typeof schema>

export function DatasourceFormDialog({
  open,
  onOpenChange,
}: {
  open: boolean
  onOpenChange: (open: boolean) => void
}) {
  const create = useCreateDatasource()
  const form = useForm<Values>({
    resolver: zodResolver(schema),
    defaultValues: {
      name: '',
      endpoint: '',
      username: '',
      password: '',
      apiToken: '',
    },
  })
  const submit = form.handleSubmit((value) =>
    create.mutate(
      {
        type: 'zabbix',
        name: value.name,
        zabbix: {
          endpoint: value.endpoint,
          username: value.username || undefined,
          password: value.password || undefined,
          apiToken: value.apiToken || undefined,
        },
      },
      {
        onSuccess: () => {
          notify.success('Zabbix 数据源已添加')
          form.reset()
          onOpenChange(false)
        },
        onError: (error) => notify.error(error, '添加数据源失败'),
      }
    )
  )

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className='max-h-[90vh] overflow-y-auto sm:max-w-xl'>
        <DialogHeader>
          <DialogTitle>添加 Zabbix 数据源</DialogTitle>
          <DialogDescription>
            配置 Zabbix JSON-RPC 地址和认证信息。敏感字段保存后不会回显。
          </DialogDescription>
        </DialogHeader>
        <Form {...form}>
          <form className='grid gap-4 py-2' onSubmit={submit}>
            <TextField
              form={form}
              name='name'
              label='名称'
              placeholder='生产 Zabbix'
            />
            <TextField
              form={form}
              name='endpoint'
              label='Endpoint'
              placeholder='https://zabbix.example/api_jsonrpc.php'
            />
            <div className='grid gap-4 sm:grid-cols-2'>
              <TextField form={form} name='username' label='用户名' />
              <TextField
                form={form}
                name='password'
                label='密码'
                type='password'
              />
            </div>
            <TextField
              form={form}
              name='apiToken'
              label='API Token（可替代用户名密码）'
              type='password'
            />
            <DialogFooter>
              <Button
                type='button'
                variant='outline'
                onClick={() => onOpenChange(false)}
              >
                取消
              </Button>
              <Button type='submit' disabled={create.isPending}>
                {create.isPending ? '保存中…' : '保存数据源'}
              </Button>
            </DialogFooter>
          </form>
        </Form>
      </DialogContent>
    </Dialog>
  )
}

type FormApi = ReturnType<typeof useForm<Values>>
function TextField({
  form,
  name,
  label,
  placeholder,
  type = 'text',
}: {
  form: FormApi
  name: keyof Values
  label: string
  placeholder?: string
  type?: string
}) {
  return (
    <FormField
      control={form.control}
      name={name}
      render={({ field }) => (
        <FormItem>
          <FormLabel>{label}</FormLabel>
          <FormControl>
            <Input {...field} type={type} placeholder={placeholder} />
          </FormControl>
          <FormMessage />
        </FormItem>
      )}
    />
  )
}
