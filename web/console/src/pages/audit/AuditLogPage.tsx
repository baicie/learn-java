import { FileTextIcon } from 'lucide-react'

import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'

export function AuditLogPage() {
  return (
    <div className="flex flex-col gap-6">
      <header>
        <h1 className="text-2xl font-semibold">审计日志</h1>
        <p className="text-sm text-muted-foreground">Phase 1 建立入口，后续展示关键操作记录。</p>
      </header>

      <Card>
        <CardHeader className="flex flex-row items-center gap-3">
          <FileTextIcon className="text-muted-foreground" />
          <CardTitle className="text-base">操作记录</CardTitle>
        </CardHeader>
        <CardContent>
          <p className="text-sm text-muted-foreground">
            审计日志记录平台内的所有关键操作，包括登录、数据源配置、故障处理、自动化执行等。 Phase 1
            仅保留入口，后续接入完整日志查询与过滤功能。
          </p>
        </CardContent>
      </Card>
    </div>
  )
}
