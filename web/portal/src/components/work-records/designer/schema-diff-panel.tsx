import { AlertTriangle, CheckCircle2 } from 'lucide-react'
import { Alert, AlertDescription, AlertTitle } from '@/components/ui/alert'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import type { SchemaDiffItem, TemplatePublishValidationResult } from './types'

type SchemaDiffPanelProps = {
  diff: SchemaDiffItem[]
  validationErrors: string[]
  publishValidation: TemplatePublishValidationResult | null
}

export function SchemaDiffPanel({
  diff,
  validationErrors,
  publishValidation,
}: SchemaDiffPanelProps) {
  return (
    <Card>
      <CardHeader>
        <CardTitle>Schema diff 与发布提示</CardTitle>
      </CardHeader>
      <CardContent className='grid gap-3 text-sm'>
        {validationErrors.length ? (
          <Alert variant='destructive'>
            <AlertTriangle />
            <AlertTitle>本地校验失败</AlertTitle>
            <AlertDescription>
              <ul className='list-disc pl-5'>
                {validationErrors.map((item) => (
                  <li key={item}>{item}</li>
                ))}
              </ul>
            </AlertDescription>
          </Alert>
        ) : (
          <Alert>
            <CheckCircle2 />
            <AlertTitle>本地字段校验通过</AlertTitle>
          </Alert>
        )}

        {publishValidation ? (
          <Alert variant={publishValidation.valid ? 'default' : 'destructive'}>
            {publishValidation.valid ? <CheckCircle2 /> : <AlertTriangle />}
            <AlertTitle>
              发布校验：{publishValidation.valid ? '通过' : '失败'}
            </AlertTitle>
            <AlertDescription>
              <div>字段数：{publishValidation.fieldCount}</div>
              <div>
                当前版本引用记录数：
                {publishValidation.referencedRecordCount}
              </div>
              {publishValidation.errors.length ? (
                <ul className='mt-2 list-disc pl-5'>
                  {publishValidation.errors.map((item) => (
                    <li key={item}>{item}</li>
                  ))}
                </ul>
              ) : null}
              {publishValidation.warnings.length ? (
                <ul className='mt-2 list-disc pl-5'>
                  {publishValidation.warnings.map((item) => (
                    <li key={item}>{item}</li>
                  ))}
                </ul>
              ) : null}
            </AlertDescription>
          </Alert>
        ) : null}

        <div className='grid gap-2'>
          <div className='font-medium'>字段变化</div>
          {diff.length === 0 ? (
            <div className='text-muted-foreground'>暂无变化</div>
          ) : (
            <ul className='list-disc pl-5'>
              {diff.map((item) => (
                <li key={`${item.type}-${item.fieldCode}-${item.message}`}>
                  {item.message}
                </li>
              ))}
            </ul>
          )}
        </div>
      </CardContent>
    </Card>
  )
}
