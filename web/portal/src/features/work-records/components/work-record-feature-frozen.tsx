import { AlertTriangle } from 'lucide-react'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'

type WorkRecordSurface = 'list' | 'new' | 'edit' | 'detail' | 'designer'

type WorkRecordFeatureFrozenProps = {
  surface: WorkRecordSurface
}

const surfaceLabels: Record<WorkRecordSurface, string> = {
  list: '记录列表',
  new: '新建记录',
  edit: '编辑记录',
  detail: '记录详情',
  designer: '表单设计',
}

export function WorkRecordFeatureFrozen({
  surface,
}: WorkRecordFeatureFrozenProps) {
  return (
    <main className='container grid gap-4 p-6'>
      <Card>
        <CardHeader>
          <CardTitle className='flex items-center gap-2'>
            <AlertTriangle className='size-5 text-muted-foreground' />
            工作记录模块重做中
          </CardTitle>
        </CardHeader>
        <CardContent className='grid gap-3 text-sm text-muted-foreground'>
          <p>
            当前页面：
            <span className='font-medium'>{surfaceLabels[surface]}</span>
          </p>
          <p>
            当前实现已在 Phase 0 冻结。Phase 1
            只保留路由和占位页，避免继续调用不可用的 record 实现。
          </p>
          <p>
            后续将按 enterprise roadmap
            重建模板、Schema、运行态、列表、导出、权限和审计。
          </p>
        </CardContent>
      </Card>
    </main>
  )
}
