import { z } from 'zod'
import { useForm, useWatch } from 'react-hook-form'
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
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select'
import { notify } from '@/components/feedback/app-toaster'

const schema = z
  .object({
    type: z.enum([
      'zabbix',
      'kubernetes',
      'opentelemetry',
      'rum',
      'github',
      'gitlab',
      'jenkins',
      'webhook',
    ]),
    name: z.string().trim().min(1, '请输入名称'),
    endpoint: z
      .string()
      .trim()
      .refine(
        (value) => !value || URL.canParse(value),
        '请输入有效的 Endpoint'
      ),
    username: z.string().trim(),
    password: z.string(),
    apiToken: z.string().trim(),
  })
  .refine(
    (value) =>
      !['zabbix', 'kubernetes'].includes(value.type) || Boolean(value.endpoint),
    {
      message: '请输入 Endpoint',
      path: ['endpoint'],
    }
  )
  .refine(
    (value) =>
      !['zabbix', 'kubernetes'].includes(value.type) ||
      (value.type === 'kubernetes'
        ? Boolean(value.apiToken)
        : Boolean(value.apiToken || (value.username && value.password))),
    {
      message: '请填写 API Token，或同时填写用户名和密码',
      path: ['apiToken'],
    }
  )

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
      type: 'zabbix',
      name: '',
      endpoint: '',
      username: '',
      password: '',
      apiToken: '',
    },
  })
  const type = useWatch({ control: form.control, name: 'type' })
  const submit = form.handleSubmit((value) =>
    create.mutate(
      {
        type: value.type,
        name: value.name,
        zabbix:
          value.type === 'zabbix'
            ? {
                endpoint: value.endpoint,
                username: value.username || undefined,
                password: value.password || undefined,
                apiToken: value.apiToken || undefined,
              }
            : undefined,
        kubernetes:
          value.type === 'kubernetes'
            ? {
                endpoint: value.endpoint,
                apiToken: value.apiToken,
              }
            : undefined,
        passive: !['zabbix', 'kubernetes'].includes(value.type)
          ? {
              endpoint: value.endpoint || undefined,
            }
          : undefined,
      },
      {
        onSuccess: () => {
          notify.success('数据源已添加')
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
          <DialogTitle>添加数据源</DialogTitle>
          <DialogDescription>
            配置只读数据源连接。敏感字段保存后不会回显。
          </DialogDescription>
        </DialogHeader>
        <Form {...form}>
          <form className='grid gap-4 py-2' onSubmit={submit}>
            <FormField
              control={form.control}
              name='type'
              render={({ field }) => (
                <FormItem>
                  <FormLabel>类型</FormLabel>
                  <Select value={field.value} onValueChange={field.onChange}>
                    <FormControl>
                      <SelectTrigger>
                        <SelectValue />
                      </SelectTrigger>
                    </FormControl>
                    <SelectContent>
                      <SelectItem value='zabbix'>Zabbix</SelectItem>
                      <SelectItem value='kubernetes'>Kubernetes</SelectItem>
                      <SelectItem value='opentelemetry'>
                        OpenTelemetry
                      </SelectItem>
                      <SelectItem value='rum'>RUM</SelectItem>
                      <SelectItem value='github'>GitHub Actions</SelectItem>
                      <SelectItem value='gitlab'>GitLab</SelectItem>
                      <SelectItem value='jenkins'>Jenkins</SelectItem>
                      <SelectItem value='webhook'>通用 Webhook</SelectItem>
                    </SelectContent>
                  </Select>
                  <FormMessage />
                </FormItem>
              )}
            />
            <TextField
              form={form}
              name='name'
              label='名称'
              placeholder={type === 'zabbix' ? '生产 Zabbix' : `生产 ${type}`}
            />
            <TextField
              form={form}
              name='endpoint'
              label='Endpoint'
              placeholder={
                type === 'zabbix'
                  ? 'https://zabbix.example/api_jsonrpc.php'
                  : type === 'kubernetes'
                    ? 'https://kubernetes.example:6443'
                    : '可选：上游系统地址'
              }
            />
            {type === 'zabbix' && (
              <div className='grid gap-4 sm:grid-cols-2'>
                <TextField form={form} name='username' label='用户名' />
                <TextField
                  form={form}
                  name='password'
                  label='密码'
                  type='password'
                />
              </div>
            )}
            {['zabbix', 'kubernetes'].includes(type) && (
              <TextField
                form={form}
                name='apiToken'
                label={
                  type === 'zabbix'
                    ? 'API Token（可替代用户名密码）'
                    : 'Service Account Token'
                }
                type='password'
              />
            )}
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
