import { ArrowLeft, Eye, Rocket, Save, ShieldCheck } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { Card, CardContent } from '@/components/ui/card'
import { FieldLibrary } from './field-library'
import { FormCanvas } from './form-canvas'
import { FormPreview } from './form-preview'
import { PropertyPanel } from './property-panel'
import { SchemaDiffPanel } from './schema-diff-panel'
import { useWorkRecordDesigner } from './use-work-record-designer'

export function WorkRecordDesignerPage() {
  const designer = useWorkRecordDesigner()

  if (designer.loading) {
    return <main className='p-6 text-sm text-muted-foreground'>加载中...</main>
  }

  if (!designer.templates.length) {
    return (
      <main className='p-6'>
        <Card>
          <CardContent className='p-6 text-sm text-muted-foreground'>
            暂无模板。请先在模板管理中创建模板。
          </CardContent>
        </Card>
      </main>
    )
  }

  return (
    <main className='grid gap-4 p-6'>
      <div className='flex flex-wrap items-center justify-between gap-3'>
        <div>
          <h1 className='text-2xl font-semibold'>工作记录表单设计器</h1>
          <p className='text-sm text-muted-foreground'>
            字段库 / 画布 / 属性面板 / 实时预览 / 发布校验
          </p>
        </div>

        <div className='flex flex-wrap items-center gap-2'>
          <Button
            type='button'
            variant='outline'
            onClick={() => history.back()}
          >
            <ArrowLeft className='mr-2 size-4' />
            返回
          </Button>

          <select
            className='rounded-md border bg-background px-3 py-2 text-sm'
            value={designer.selectedTemplateId}
            onChange={(event) => designer.loadTemplate(event.target.value)}
          >
            {designer.templates.map((template) => (
              <option key={template.id} value={template.id}>
                {template.name} / {template.status}
              </option>
            ))}
          </select>

          <Button
            type='button'
            variant='outline'
            onClick={() => designer.setPreviewOpen(!designer.previewOpen)}
          >
            <Eye className='mr-2 size-4' />
            {designer.previewOpen ? '关闭预览' : '打开预览'}
          </Button>

          <Button
            type='button'
            variant='outline'
            disabled={designer.saving}
            onClick={() => designer.saveDraft()}
          >
            <Save className='mr-2 size-4' />
            保存草稿
          </Button>

          <Button
            type='button'
            variant='outline'
            disabled={designer.validating}
            onClick={() => designer.validatePublish()}
          >
            <ShieldCheck className='mr-2 size-4' />
            发布校验
          </Button>

          <Button
            type='button'
            disabled={designer.publishing}
            onClick={() => designer.publish()}
          >
            <Rocket className='mr-2 size-4' />
            发布
          </Button>
        </div>
      </div>

      {designer.saveError || designer.publishError ? (
        <div className='rounded-md border border-red-200 bg-red-50 p-3 text-sm text-red-700'>
          {(designer.saveError as Error | null)?.message ??
            (designer.publishError as Error | null)?.message}
        </div>
      ) : null}

      <div className='grid gap-4 xl:grid-cols-[260px_minmax(0,1fr)_340px]'>
        <FieldLibrary onAdd={designer.addField} />

        <FormCanvas
          fields={designer.fields}
          selectedFieldId={designer.selectedField?.id ?? ''}
          onSelect={designer.setSelectedFieldId}
          onMove={designer.moveField}
          onDuplicate={designer.duplicateField}
          onRemoveOrDisable={designer.removeOrDisableField}
        />

        <PropertyPanel
          field={designer.selectedField}
          dictTypes={designer.dictTypes}
          onChange={designer.updateField}
        />
      </div>

      <div className='grid gap-4 xl:grid-cols-[minmax(0,1fr)_420px]'>
        <SchemaDiffPanel
          diff={designer.schemaDiff}
          validationErrors={designer.validationErrors}
          publishValidation={designer.publishValidation}
        />

        {designer.previewOpen ? <FormPreview fields={designer.fields} /> : null}
      </div>

      <details className='rounded-md border p-3'>
        <summary className='cursor-pointer text-sm font-medium'>
          查看生成的 schema
        </summary>
        <pre className='mt-3 overflow-auto rounded bg-muted p-3 text-xs'>
          {designer.schemaJson}
        </pre>
      </details>
    </main>
  )
}
