import { useNavigate } from '@tanstack/react-router'
import { ArrowLeft, Eye, Rocket, Save, ShieldCheck } from 'lucide-react'
import { useTranslation } from 'react-i18next'
import { useUnsavedChangesGuard } from '@/hooks/use-unsaved-changes-guard'
import { useWorkRecordDesigner } from '@/hooks/work-records/use-work-record-designer'
import { Button } from '@/components/ui/button'
import { Card, CardContent } from '@/components/ui/card'
import { notify } from '@/components/feedback/app-toaster'
import {
  EmptyState,
  ErrorState,
  PageLoadingState,
} from '@/components/feedback/async-state'
import { useConfirm } from '@/components/feedback/confirm-provider'
import {
  DetailPageLayout,
  DetailSection,
} from '@/components/layout/detail-page-layout'
import { fieldDeleteConfirmOptions } from './field-delete-risk'
import { FieldLibrary } from './field-library'
import { FormCanvas } from './form-canvas'
import { FormPreview } from './form-preview'
import { PropertyPanel } from './property-panel'
import { SchemaDiffPanel } from './schema-diff-panel'

export function WorkRecordDesignerPage({ templateId }: { templateId: string }) {
  const designer = useWorkRecordDesigner(templateId)
  const confirm = useConfirm()
  const { t } = useTranslation()
  const navigate = useNavigate()

  useUnsavedChangesGuard(
    designer.dirty && !designer.saving && !designer.publishing
  )

  const handleBack = () => {
    void navigate({ to: '/work-records/templates' })
  }

  const removeField = async (fieldId: string) => {
    const field = designer.fields.find((item) => item.id === fieldId)
    if (!field) return

    const accepted = await confirm(
      fieldDeleteConfirmOptions({
        fieldName: field.fieldName,
        fieldCode: field.fieldCode,
        published: field.locked || field.referenced,
        required: field.required,
        exportable: field.exportable,
        t,
      })
    )

    if (accepted) {
      designer.removeOrDisableField(fieldId)
    }
  }

  const saveDraft = async () => {
    try {
      await designer.saveDraft()
      notify.success(t('workRecords.designer.saveDraftSuccess'))
    } catch (error) {
      notify.error(error, t('workRecords.designer.saveDraftFailed'))
    }
  }

  const publishTemplate = async () => {
    await designer.publish()
    notify.success(t('workRecords.designer.publishSuccess'))
  }

  if (designer.queryError) {
    return (
      <main className='p-4 md:p-6'>
        <ErrorState
          error={designer.queryError as Error}
          onRetry={() => window.location.reload()}
        />
      </main>
    )
  }

  if (designer.loading && designer.fields.length === 0) {
    return <PageLoadingState />
  }

  if (!designer.selectedTemplate) {
    return (
      <main className='p-4 md:p-6'>
        <EmptyState
          title={t('workRecords.designer.noTemplates')}
          description={t('workRecords.designer.noTemplatesHint')}
        />
      </main>
    )
  }

  return (
    <DetailPageLayout
      title={t('workRecords.designer.title')}
      description={t('workRecords.designer.subtitle')}
      meta={
        <div className='flex flex-wrap gap-2 text-xs text-muted-foreground'>
          <span>
            {t('workRecords.designer.toolbar.dirty')}：
            {designer.dirty ? '✓' : '—'}
          </span>
          <span>·</span>
          <span>
            {t('workRecords.designer.toolbar.fields')}：{designer.fields.length}
          </span>
        </div>
      }
      actions={
        <>
          <Button type='button' variant='outline' onClick={handleBack}>
            <ArrowLeft className='mr-2 size-4' />
            {t('common.back')}
          </Button>

          <Button
            type='button'
            variant='outline'
            onClick={() => designer.setPreviewOpen(!designer.previewOpen)}
          >
            <Eye className='mr-2 size-4' />
            {designer.previewOpen
              ? t('workRecords.designer.toolbar.closePreview')
              : t('workRecords.designer.toolbar.openPreview')}
          </Button>

          <Button
            type='button'
            variant='outline'
            disabled={designer.saving || !designer.dirty}
            onClick={() => void saveDraft()}
          >
            <Save className='mr-2 size-4' />
            {t('workRecords.designer.toolbar.saveDraft')}
          </Button>

          <Button
            type='button'
            variant='outline'
            disabled={designer.validating}
            onClick={() => designer.validatePublish()}
          >
            <ShieldCheck className='mr-2 size-4' />
            {t('workRecords.designer.toolbar.validatePublish')}
          </Button>

          <Button
            type='button'
            disabled={designer.publishing}
            onClick={() => void publishTemplate()}
          >
            <Rocket className='mr-2 size-4' />
            {t('workRecords.designer.toolbar.publish')}
          </Button>
        </>
      }
    >
      <DetailSection
        title={t('workRecords.designer.section.canvas')}
        description={t('workRecords.designer.section.canvasDescription')}
      >
        <div className='grid gap-4 xl:grid-cols-[260px_minmax(0,1fr)_340px]'>
          <FieldLibrary onAdd={designer.addField} />

          <FormCanvas
            fields={designer.fields}
            selectedFieldId={designer.selectedField?.id ?? ''}
            onSelect={designer.setSelectedFieldId}
            onMove={designer.moveField}
            onDuplicate={designer.duplicateField}
            onRemoveOrDisable={(fieldId) => void removeField(fieldId)}
          />

          <PropertyPanel
            field={designer.selectedField}
            dictTypes={designer.dictTypes}
            onChange={designer.updateField}
          />
        </div>
      </DetailSection>

      <DetailSection
        title={t('workRecords.designer.section.diff')}
        description={t('workRecords.designer.section.diffDescription')}
      >
        <div className='grid gap-4 xl:grid-cols-[minmax(0,1fr)_420px]'>
          <SchemaDiffPanel
            diff={designer.schemaDiff}
            validationErrors={designer.validationErrors}
            publishValidation={designer.publishValidation}
          />

          {designer.previewOpen ? (
            <Card>
              <CardContent className='p-4'>
                <FormPreview fields={designer.fields} />
              </CardContent>
            </Card>
          ) : null}
        </div>
      </DetailSection>

      <details className='rounded-md border p-3'>
        <summary className='cursor-pointer text-sm font-medium'>
          {t('workRecords.designer.viewGeneratedSchema')}
        </summary>
        <pre className='mt-3 overflow-auto rounded bg-muted p-3 text-xs'>
          {designer.schemaJson}
        </pre>
      </details>
    </DetailPageLayout>
  )
}
