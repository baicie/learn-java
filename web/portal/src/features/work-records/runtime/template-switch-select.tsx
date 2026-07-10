import { useI18n } from '@/i18n/provider'
import { useConfirm } from '@/components/feedback/confirm-provider'

export type TemplateOption = {
  id: string
  name: string
  disabled?: boolean
}

export function TemplateSwitchSelect({
  id = 'templateId',
  value,
  templates,
  dirty,
  disabled = false,
  onChange,
}: {
  id?: string
  value: string
  templates: TemplateOption[]
  dirty: boolean
  disabled?: boolean
  onChange: (templateId: string) => void
}) {
  const confirm = useConfirm()
  const { t } = useI18n()

  const changeTemplate = async (nextTemplateId: string) => {
    if (nextTemplateId === value || !nextTemplateId) {
      return
    }

    if (!dirty) {
      onChange(nextTemplateId)
      return
    }

    const accepted = await confirm({
      title: t('template.switch.title'),
      description: t('template.switch.description'),
      confirmText: t('template.switch.confirm'),
      variant: 'warning',
    })

    if (accepted) {
      onChange(nextTemplateId)
    }
  }

  return (
    <select
      id={id}
      className='h-10 w-full rounded-md border bg-background px-3 text-sm disabled:cursor-not-allowed disabled:opacity-50'
      value={value}
      disabled={disabled}
      onChange={(event) => {
        void changeTemplate(event.target.value)
      }}
    >
      <option value=''>请选择模板</option>

      {templates.map((template) => (
        <option
          key={template.id}
          value={template.id}
          disabled={template.disabled}
        >
          {template.name}
        </option>
      ))}
    </select>
  )
}
