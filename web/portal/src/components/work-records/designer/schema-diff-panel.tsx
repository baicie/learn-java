import { AlertTriangle, CheckCircle2 } from 'lucide-react'
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
          <div className='rounded-md border border-red-200 bg-red-50 p-3 text-red-700'>
            <div className='mb-1 flex items-center gap-2 font-medium'>
              <AlertTriangle className='size-4' />
              本地校验失败
            </div>
            <ul className='list-disc pl-5'>
              {validationErrors.map((item) => (
                <li key={item}>{item}</li>
              ))}
            </ul>
          </div>
        ) : (
          <div className='flex items-center gap-2 text-muted-foreground'>
            <CheckCircle2 className='size-4' />
            本地字段校验通过
          </div>
        )}

        {publishValidation ? (
          <div
            className={`rounded-md border p-3 ${
              publishValidation.valid
                ? 'border-green-200 bg-green-50 text-green-800'
                : 'border-red-200 bg-red-50 text-red-700'
            }`}
          >
            <div className='font-medium'>
              发布校验：{publishValidation.valid ? '通过' : '失败'}
            </div>
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
          </div>
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
