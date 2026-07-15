import { useTranslation } from 'react-i18next'
import {
  Select,
  SelectContent,
  SelectGroup,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select'
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
  hasDynamicValues,
  disabled = false,
  onChange,
}: {
  id?: string
  value: string
  templates: TemplateOption[]
  hasDynamicValues: boolean
  disabled?: boolean
  onChange: (templateId: string) => void
}) {
  const confirm = useConfirm()
  const { t } = useTranslation()

  const changeTemplate = async (nextTemplateId: string) => {
    if (nextTemplateId === value || !nextTemplateId) {
      return
    }

    if (!hasDynamicValues) {
      onChange(nextTemplateId)
      return
    }

    const accepted = await confirm({
      title: t('workRecords.templateSwitch.title'),
      description: t('workRecords.templateSwitch.description'),
      confirmText: t('workRecords.templateSwitch.confirm'),
      variant: 'warning',
    })

    if (accepted) {
      onChange(nextTemplateId)
    }
  }

  return (
    <Select
      value={value || 'none'}
      disabled={disabled}
      onValueChange={(next) => void changeTemplate(next === 'none' ? '' : next)}
    >
      <SelectTrigger id={id} className='w-full'>
        <SelectValue />
      </SelectTrigger>
      <SelectContent>
        <SelectGroup>
          <SelectItem value='none'>
            {t('workRecords.form.selectTemplate')}
          </SelectItem>
          {templates.map((template) => (
            <SelectItem
              key={template.id}
              value={template.id}
              disabled={template.disabled}
            >
              {template.name}
            </SelectItem>
          ))}
        </SelectGroup>
      </SelectContent>
    </Select>
  )
}
