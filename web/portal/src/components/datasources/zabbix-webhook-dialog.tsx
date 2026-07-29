import { CircleHelp, Copy, Download, TriangleAlert } from 'lucide-react'
import { apiClient } from '@/lib/api-client'
import type { Datasource } from '@/lib/datasources/datasource'
import {
  buildZabbixWebhookUrl,
  isLoopbackWebhookUrl,
} from '@/lib/datasources/zabbix-webhook'
import { useDownloadZabbixWebhookTemplate } from '@/hooks/datasources/use-datasources'
import { Alert, AlertDescription, AlertTitle } from '@/components/ui/alert'
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
  Tooltip,
  TooltipContent,
  TooltipTrigger,
} from '@/components/ui/tooltip'
import { notify } from '@/components/feedback/app-toaster'

export function ZabbixWebhookDialog({
  datasource,
  open,
  onOpenChange,
}: {
  datasource: Datasource
  open: boolean
  onOpenChange: (open: boolean) => void
}) {
  const templateDownload = useDownloadZabbixWebhookTemplate()
  const webhookUrl = buildZabbixWebhookUrl(
    datasource.id,
    apiClient.defaults.baseURL,
    window.location.origin
  )

  const copyUrl = async () => {
    try {
      await navigator.clipboard.writeText(webhookUrl)
      notify.success('Webhook 地址已复制')
    } catch (error) {
      notify.error(error, '复制失败，请手动复制')
    }
  }

  const downloadTemplate = () => {
    templateDownload.mutate(datasource.id, {
      onError: (error) => notify.error(error, '获取 Webhook Token 失败'),
    })
  }

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className='max-h-[90vh] overflow-y-auto sm:max-w-2xl'>
        <DialogHeader>
          <DialogTitle>Zabbix 实时告警接入</DialogTitle>
          <DialogDescription>
            为“{datasource.name}”配置 Media Type 和告警 Action。
          </DialogDescription>
        </DialogHeader>

        <div className='grid gap-4'>
          <div className='grid gap-2'>
            <div className='flex items-center gap-1'>
              <Label htmlFor='zabbix-webhook-url'>Webhook 地址</Label>
              <Tooltip>
                <TooltipTrigger asChild>
                  <Button
                    type='button'
                    variant='ghost'
                    size='icon'
                    className='size-6'
                    aria-label='Webhook 配置说明'
                  >
                    <CircleHelp />
                  </Button>
                </TooltipTrigger>
                <TooltipContent className='max-w-72'>
                  此地址由 AegisOps API 地址和当前数据源 ID 生成，需确保 Zabbix
                  Server 可以访问。
                </TooltipContent>
              </Tooltip>
            </div>
            <div className='flex gap-2'>
              <Input
                id='zabbix-webhook-url'
                readOnly
                value={webhookUrl}
                className='font-mono text-xs'
              />
              <Button
                type='button'
                variant='outline'
                size='icon'
                aria-label='复制 Webhook 地址'
                onClick={() => void copyUrl()}
              >
                <Copy />
              </Button>
            </div>
          </div>

          {isLoopbackWebhookUrl(webhookUrl) ? (
            <Alert>
              <TriangleAlert />
              <AlertTitle>当前地址仅本机可达</AlertTitle>
              <AlertDescription>
                请将 VITE_API_BASE_URL 配置为 Zabbix Server 可访问的 AegisOps
                API 地址后再复制或下载模板。
              </AlertDescription>
            </Alert>
          ) : null}

          <div className='grid gap-2 text-sm'>
            <h3 className='font-medium'>配置步骤</h3>
            <ol className='grid list-decimal gap-1 pl-5 text-muted-foreground'>
              <li>下载配置模板。</li>
              <li>在 Zabbix 的 Alerts → Media types 页面点击 Import。</li>
              <li>导入后确认 AegisOps Webhook 已启用。</li>
              <li>
                为通知用户添加该 Media，并在 Action 中配置问题和恢复操作。
              </li>
            </ol>
          </div>
        </div>

        <DialogFooter>
          <Button
            type='button'
            variant='outline'
            disabled={templateDownload.isPending}
            onClick={downloadTemplate}
          >
            <Download />
            {templateDownload.isPending ? '正在生成模板…' : '下载配置模板'}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}
