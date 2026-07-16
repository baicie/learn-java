import { useState } from 'react'
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
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { notify } from '@/components/feedback/app-toaster'

export function DatasourceFormDialog({
  open,
  onOpenChange,
}: {
  open: boolean
  onOpenChange: (open: boolean) => void
}) {
  const create = useCreateDatasource()
  const [name, setName] = useState('')
  const [endpoint, setEndpoint] = useState('')
  const [username, setUsername] = useState('')
  const [password, setPassword] = useState('')
  const [apiToken, setApiToken] = useState('')

  const submit = () => {
    if (
      !name.trim() ||
      !endpoint.trim() ||
      (!apiToken.trim() && (!username.trim() || !password))
    ) {
      notify.error('请填写名称、Endpoint 和一种认证方式')
      return
    }
    create.mutate(
      {
        type: 'zabbix',
        name: name.trim(),
        zabbix: {
          endpoint: endpoint.trim(),
          username: username.trim() || undefined,
          password: password || undefined,
          apiToken: apiToken.trim() || undefined,
        },
      },
      {
        onSuccess: () => {
          notify.success('Zabbix 数据源已添加')
          onOpenChange(false)
        },
        onError: (error) => notify.error(error, '添加数据源失败'),
      }
    )
  }

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className='max-h-[90vh] overflow-y-auto sm:max-w-xl'>
        <DialogHeader>
          <DialogTitle>添加 Zabbix 数据源</DialogTitle>
          <DialogDescription>
            配置 Zabbix JSON-RPC 地址和认证信息。敏感字段保存后不会回显。
          </DialogDescription>
        </DialogHeader>
        <div className='grid gap-4 py-2'>
          <Field
            label='名称'
            value={name}
            onChange={setName}
            placeholder='生产 Zabbix'
          />
          <Field
            label='Endpoint'
            value={endpoint}
            onChange={setEndpoint}
            placeholder='https://zabbix.example/api_jsonrpc.php'
          />
          <div className='grid gap-4 sm:grid-cols-2'>
            <Field label='用户名' value={username} onChange={setUsername} />
            <Field
              label='密码'
              value={password}
              onChange={setPassword}
              type='password'
            />
          </div>
          <Field
            label='API Token（可替代用户名密码）'
            value={apiToken}
            onChange={setApiToken}
            type='password'
          />
        </div>
        <DialogFooter>
          <Button variant='outline' onClick={() => onOpenChange(false)}>
            取消
          </Button>
          <Button onClick={submit} disabled={create.isPending}>
            {create.isPending ? '保存中…' : '保存数据源'}
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
  placeholder,
  type = 'text',
}: {
  label: string
  value: string
  onChange: (value: string) => void
  placeholder?: string
  type?: string
}) {
  return (
    <div className='grid gap-2'>
      <Label>{label}</Label>
      <Input
        type={type}
        value={value}
        placeholder={placeholder}
        onChange={(event) => onChange(event.target.value)}
      />
    </div>
  )
}
