import { useEffect } from 'react'
import { useForm } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { KeyRound } from 'lucide-react'
import type { AiModel } from '@/lib/ai-models/ai-model'
import {
  createAiModelFormSchema,
  updateAiModelFormSchema,
  type AiModelFormValues,
} from '@/lib/ai-models/ai-model-form-schema'
import {
  useCreateAiModel,
  useUpdateAiModel,
} from '@/hooks/ai-models/use-ai-models'
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
  FormDescription,
  FormField,
  FormItem,
  FormLabel,
  FormMessage,
} from '@/components/ui/form'
import { Input } from '@/components/ui/input'
import { Switch } from '@/components/ui/switch'
import { notify } from '@/components/feedback/app-toaster'

const defaults: AiModelFormValues = {
  provider: 'deepseek',
  name: '',
  modelName: 'deepseek-chat',
  baseUrl: 'https://api.deepseek.com/v1',
  apiKey: '',
  enabled: true,
}

export function AiModelFormDialog({
  open,
  model,
  onOpenChange,
}: {
  open: boolean
  model: AiModel | null
  onOpenChange: (open: boolean) => void
}) {
  const create = useCreateAiModel()
  const update = useUpdateAiModel()
  const form = useForm<AiModelFormValues>({
    resolver: zodResolver(
      model ? updateAiModelFormSchema : createAiModelFormSchema
    ),
    defaultValues: defaults,
  })

  useEffect(() => {
    form.reset(
      model
        ? {
            provider: 'deepseek',
            name: model.name,
            modelName: model.modelName,
            baseUrl: model.baseUrl,
            apiKey: '',
            enabled: model.enabled,
          }
        : defaults
    )
  }, [form, model, open])

  const pending = create.isPending || update.isPending
  const submit = form.handleSubmit((values) => {
    const options = {
      onSuccess: () => {
        notify.success(model ? '模型配置已更新' : '模型配置已添加')
        onOpenChange(false)
      },
      onError: (error: Error) => notify.error(error, '保存模型配置失败'),
    }
    if (model) {
      update.mutate({ id: model.id, input: values }, options)
    } else {
      create.mutate(values, options)
    }
  })

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className='sm:max-w-lg'>
        <DialogHeader>
          <DialogTitle>{model ? '编辑模型配置' : '添加模型配置'}</DialogTitle>
          <DialogDescription>
            当前仅支持 DeepSeek。API Key 会加密保存且不会再次回显。
          </DialogDescription>
        </DialogHeader>
        <Form {...form}>
          <form className='flex flex-col gap-4' onSubmit={submit}>
            <FormItem>
              <FormLabel>供应商</FormLabel>
              <FormControl>
                <Input value='DeepSeek' disabled />
              </FormControl>
            </FormItem>
            <ModelTextField
              form={form}
              name='name'
              label='配置名称'
              placeholder='生产 DeepSeek'
            />
            <ModelTextField
              form={form}
              name='modelName'
              label='模型标识'
              placeholder='deepseek-chat'
            />
            <ModelTextField
              form={form}
              name='baseUrl'
              label='API 地址'
              placeholder='https://api.deepseek.com/v1'
            />
            <FormField
              control={form.control}
              name='apiKey'
              render={({ field }) => (
                <FormItem>
                  <FormLabel>API Key</FormLabel>
                  <FormControl>
                    <div className='relative'>
                      <KeyRound className='pointer-events-none absolute top-1/2 left-3 size-4 -translate-y-1/2 text-muted-foreground' />
                      <Input
                        {...field}
                        className='pl-9'
                        type='password'
                        autoComplete='new-password'
                        placeholder={model ? '留空以保留当前密钥' : 'sk-...'}
                      />
                    </div>
                  </FormControl>
                  {model && (
                    <FormDescription>
                      留空不会覆盖已保存的 API Key。
                    </FormDescription>
                  )}
                  <FormMessage />
                </FormItem>
              )}
            />
            <FormField
              control={form.control}
              name='enabled'
              render={({ field }) => (
                <FormItem className='flex items-center justify-between rounded-md border p-3'>
                  <div className='flex flex-col gap-1'>
                    <FormLabel>启用模型</FormLabel>
                    <FormDescription>禁用后不能设为默认模型。</FormDescription>
                  </div>
                  <FormControl>
                    <Switch
                      checked={field.value}
                      onCheckedChange={field.onChange}
                    />
                  </FormControl>
                </FormItem>
              )}
            />
            <DialogFooter>
              <Button
                type='button'
                variant='outline'
                onClick={() => onOpenChange(false)}
              >
                取消
              </Button>
              <Button type='submit' disabled={pending}>
                {pending ? '保存中…' : '保存配置'}
              </Button>
            </DialogFooter>
          </form>
        </Form>
      </DialogContent>
    </Dialog>
  )
}

type FormApi = ReturnType<typeof useForm<AiModelFormValues>>
function ModelTextField({
  form,
  name,
  label,
  placeholder,
}: {
  form: FormApi
  name: 'name' | 'modelName' | 'baseUrl'
  label: string
  placeholder: string
}) {
  return (
    <FormField
      control={form.control}
      name={name}
      render={({ field }) => (
        <FormItem>
          <FormLabel>{label}</FormLabel>
          <FormControl>
            <Input {...field} placeholder={placeholder} />
          </FormControl>
          <FormMessage />
        </FormItem>
      )}
    />
  )
}
