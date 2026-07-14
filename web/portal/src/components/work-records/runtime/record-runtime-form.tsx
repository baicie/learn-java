import { useTranslation } from 'react-i18next'
import { useAuthStore } from '@/stores/auth-store'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Input } from '@/components/ui/input'
import {
  Select,
  SelectContent,
  SelectGroup,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select'
import {
  FormErrorSummary,
  FormFieldShell,
} from '@/components/form/form-field-shell'
import { DynamicFieldControl } from './dynamic-field-control'
import { setCustomValue, statusLabel } from './schema'
import {
  TemplateSwitchSelect,
  type TemplateOption,
} from './template-switch-select'
import type {
  RuntimeDictOptions,
  WorkRecordField,
  WorkRecordRuntimeFormValue,
  WorkRecordStatus,
  WorkRecordTemplate,
} from './types'

type RecordRuntimeFormProps = {
  mode: 'create' | 'edit'
  templates: WorkRecordTemplate[]
  fields: WorkRecordField[]
  dictOptions: RuntimeDictOptions
  value: WorkRecordRuntimeFormValue
  errors: Record<string, string>
  dirty: boolean
  submitting?: boolean
  onTemplateChange?: (templateId: string) => void
  onChange: (value: WorkRecordRuntimeFormValue) => void
  onSaveDraft: () => void
  onSubmitDone: () => void
  onCancel: () => void
}

export function RecordRuntimeForm({
  mode,
  templates,
  fields,
  dictOptions,
  value,
  errors,
  dirty,
  submitting,
  onTemplateChange,
  onChange,
  onSaveDraft,
  onSubmitDone,
  onCancel,
}: RecordRuntimeFormProps) {
  const { t } = useTranslation()
  const principal = useAuthStore((state) => state.auth.principal)
  const authUser = principal

  const templateOptions: TemplateOption[] = templates.map((template) => ({
    id: template.id,
    name: template.name,
    disabled: !template.enabled || !template.currentVersionId,
  }))

  const enabledFields = fields
    .filter((field) => field.enabled)
    .sort((a, b) => a.sortOrder - b.sortOrder)

  const changeStatus = (status: WorkRecordStatus) => {
    onChange({ ...value, status })
  }

  const errorEntries = Object.entries(errors)

  return (
    <main className='grid gap-4 p-6 xl:grid-cols-[minmax(0,1fr)_320px]'>
      <section className='grid gap-4'>
        {errorEntries.length ? <FormErrorSummary errors={errors} /> : null}

        <Card>
          <CardHeader>
            <CardTitle>
              {mode === 'create'
                ? t('workRecords.new.title')
                : t('workRecords.edit.title')}
            </CardTitle>
          </CardHeader>
          <CardContent className='grid gap-4'>
            <FormFieldShell
              id='title'
              label={t('workRecords.field.title')}
              required
              error={errors.title}
            >
              {(controlProps) => (
                <Input
                  {...controlProps}
                  value={value.title}
                  onChange={(event) =>
                    onChange({ ...value, title: event.target.value })
                  }
                />
              )}
            </FormFieldShell>

            <FormFieldShell
              id='templateId'
              label={t('workRecords.field.template')}
              required
              error={errors.templateId}
            >
              {(controlProps) => (
                <TemplateSwitchSelect
                  id={controlProps.id}
                  value={value.templateId}
                  templates={templateOptions}
                  dirty={dirty}
                  disabled={mode === 'edit'}
                  onChange={(templateId) => onTemplateChange?.(templateId)}
                />
              )}
            </FormFieldShell>

            <div className='grid gap-4 md:grid-cols-3'>
              <FormFieldShell id='status' label={t('workRecords.field.status')}>
                {(controlProps) => (
                  <Select
                    value={value.status}
                    onValueChange={(next) =>
                      changeStatus(next as WorkRecordStatus)
                    }
                  >
                    <SelectTrigger {...controlProps} className='w-full'>
                      <SelectValue />
                    </SelectTrigger>
                    <SelectContent>
                      <SelectGroup>
                        <SelectItem value='draft'>
                          {t('workRecords.status.draft')}
                        </SelectItem>
                        <SelectItem value='processing'>
                          {t('workRecords.status.processing')}
                        </SelectItem>
                        <SelectItem value='done'>
                          {t('workRecords.status.done')}
                        </SelectItem>
                        <SelectItem value='archived'>
                          {t('workRecords.status.archived')}
                        </SelectItem>
                      </SelectGroup>
                    </SelectContent>
                  </Select>
                )}
              </FormFieldShell>

              <FormFieldShell id='ownerId' label={t('workRecords.field.owner')}>
                {(controlProps) => (
                  <Input
                    {...controlProps}
                    placeholder={t('workRecords.form.ownerPlaceholder')}
                    value={value.ownerId}
                    onChange={(event) =>
                      onChange({ ...value, ownerId: event.target.value })
                    }
                  />
                )}
              </FormFieldShell>

              <FormFieldShell
                id='recordTime'
                label={t('workRecords.field.recordTime')}
                required
                error={errors.recordTime}
              >
                {(controlProps) => (
                  <Input
                    {...controlProps}
                    type='datetime-local'
                    value={value.recordTime}
                    onChange={(event) =>
                      onChange({ ...value, recordTime: event.target.value })
                    }
                  />
                )}
              </FormFieldShell>
            </div>
          </CardContent>
        </Card>

        <Card>
          <CardHeader>
            <CardTitle>{t('workRecords.form.recordContent')}</CardTitle>
          </CardHeader>
          <CardContent className='grid gap-4'>
            {enabledFields.length === 0 ? (
              <div className='rounded-md border border-dashed p-6 text-center text-sm text-muted-foreground'>
                {t('workRecords.designer.emptyHint')}
              </div>
            ) : null}

            {enabledFields.map((field) => {
              const errorKey = `custom.${field.fieldCode}`
              const controlId = `custom-${field.fieldCode}`
              return (
                <FormFieldShell
                  key={field.id}
                  id={controlId}
                  label={field.fieldName}
                  required={field.required}
                  error={errors[errorKey]}
                >
                  {(controlProps) => (
                    <DynamicFieldControl
                      field={field}
                      value={value.customData[field.fieldCode]}
                      dictOptions={dictOptions}
                      controlProps={controlProps}
                      onChange={(fieldValue) =>
                        onChange(
                          setCustomValue(value, field.fieldCode, fieldValue)
                        )
                      }
                    />
                  )}
                </FormFieldShell>
              )
            })}
          </CardContent>
        </Card>
      </section>

      <aside className='grid content-start gap-4'>
        <Card>
          <CardHeader>
            <CardTitle>{t('workRecords.form.submit')}</CardTitle>
          </CardHeader>
          <CardContent className='grid gap-3 text-sm'>
            <div>
              {t('workRecords.field.status')}：{statusLabel(value.status)}
            </div>
            <div>
              {t('workRecords.field.template')}：
              {value.templateVersionId || '-'}
            </div>
            <div>
              {t('workRecords.field.creator')}：
              {authUser?.displayName ?? authUser?.username ?? '-'}
            </div>

            {errorEntries.length ? (
              <div className='rounded-md border border-destructive/30 bg-destructive/5 p-3 text-destructive'>
                {t('workRecords.form.validationFailed')}
              </div>
            ) : (
              <div className='rounded-md border bg-muted/40 p-3 text-muted-foreground'>
                {t('workRecords.designer.toolbar.dirty')}：{dirty ? '✓' : '—'}
              </div>
            )}

            <Button
              type='button'
              variant='outline'
              disabled={submitting}
              onClick={onSaveDraft}
            >
              {submitting
                ? t('workRecords.form.saving')
                : t('workRecords.form.saveDraft')}
            </Button>

            <Button type='button' disabled={submitting} onClick={onSubmitDone}>
              {submitting
                ? t('workRecords.form.submitting')
                : t('workRecords.form.submit')}
            </Button>

            <Button type='button' variant='ghost' onClick={onCancel}>
              {t('common.back')}
            </Button>
          </CardContent>
        </Card>
      </aside>
    </main>
  )
}
