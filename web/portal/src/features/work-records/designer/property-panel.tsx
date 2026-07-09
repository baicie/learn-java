import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import {
  type DictTypeOption,
  type DesignerField,
  WORK_RECORD_FIELD_TYPES,
  type WorkRecordFieldType,
} from './types'

type PropertyPanelProps = {
  field?: DesignerField
  dictTypes: DictTypeOption[]
  onChange: (fieldId: string, patch: Partial<DesignerField>) => void
}

export function PropertyPanel({
  field,
  dictTypes,
  onChange,
}: PropertyPanelProps) {
  if (!field) {
    return (
      <Card className='h-full'>
        <CardHeader>
          <CardTitle>属性面板</CardTitle>
        </CardHeader>
        <CardContent className='text-sm text-muted-foreground'>
          请选择一个字段
        </CardContent>
      </Card>
    )
  }

  return (
    <Card className='h-full'>
      <CardHeader>
        <CardTitle>属性面板</CardTitle>
      </CardHeader>
      <CardContent className='grid gap-4 text-sm'>
        <FieldInput
          label='字段名称'
          value={field.fieldName}
          onChange={(value) => onChange(field.id, { fieldName: value })}
        />

        <FieldInput
          label='字段编码'
          value={field.fieldCode}
          disabled={field.locked}
          help={
            field.locked
              ? '该字段已发布，字段编码不可修改'
              : '规则：^[a-zA-Z][a-zA-Z0-9_]{0,63}$'
          }
          onChange={(value) => onChange(field.id, { fieldCode: value })}
        />

        <label className='grid gap-1'>
          <span className='text-xs text-muted-foreground'>字段类型</span>
          <select
            className='rounded-md border bg-background px-3 py-2'
            value={field.fieldType}
            disabled={field.locked}
            onChange={(event) =>
              onChange(field.id, {
                fieldType: event.target.value as WorkRecordFieldType,
              })
            }
          >
            {WORK_RECORD_FIELD_TYPES.map((type) => (
              <option key={type} value={type}>
                {type}
              </option>
            ))}
          </select>
          {field.locked ? (
            <span className='text-xs text-muted-foreground'>
              已发布字段类型不可修改
            </span>
          ) : null}
        </label>

        <label className='flex items-center gap-2'>
          <input
            type='checkbox'
            checked={field.required}
            onChange={(event) =>
              onChange(field.id, { required: event.target.checked })
            }
          />
          必填
        </label>

        <label className='grid gap-1'>
          <span className='text-xs text-muted-foreground'>选项来源</span>
          <select
            className='rounded-md border bg-background px-3 py-2'
            value={field.optionSource}
            onChange={(event) =>
              onChange(field.id, {
                optionSource: event.target.value === 'dict' ? 'dict' : 'static',
              })
            }
          >
            <option value='static'>静态</option>
            <option value='dict'>平台字典</option>
          </select>
        </label>

        {field.optionSource === 'dict' ? (
          <label className='grid gap-1'>
            <span className='text-xs text-muted-foreground'>绑定字典</span>
            <select
              className='rounded-md border bg-background px-3 py-2'
              value={field.dictCode}
              onChange={(event) =>
                onChange(field.id, { dictCode: event.target.value })
              }
            >
              <option value=''>请选择字典</option>
              {dictTypes
                .filter((item) => item.enabled)
                .map((dict) => (
                  <option key={dict.id} value={dict.dictCode}>
                    {dict.dictName} / {dict.dictCode}
                  </option>
                ))}
            </select>
          </label>
        ) : null}

        <div className='grid gap-2 rounded-md border p-3'>
          <span className='text-xs font-medium text-muted-foreground'>
            使用场景
          </span>
          <Flag
            label='列表展示'
            checked={field.listVisible}
            onChange={(value) => onChange(field.id, { listVisible: value })}
          />
          <Flag
            label='允许筛选'
            checked={field.filterable}
            onChange={(value) => onChange(field.id, { filterable: value })}
          />
          <Flag
            label='允许导出'
            checked={field.exportable}
            onChange={(value) => onChange(field.id, { exportable: value })}
          />
          <Flag
            label='参与统计'
            checked={field.statistical}
            onChange={(value) => onChange(field.id, { statistical: value })}
          />
        </div>
      </CardContent>
    </Card>
  )
}

function FieldInput({
  label,
  value,
  disabled,
  help,
  onChange,
}: {
  label: string
  value: string
  disabled?: boolean
  help?: string
  onChange: (value: string) => void
}) {
  return (
    <label className='grid gap-1'>
      <span className='text-xs text-muted-foreground'>{label}</span>
      <input
        className='rounded-md border bg-background px-3 py-2'
        value={value}
        disabled={disabled}
        onChange={(event) => onChange(event.target.value)}
      />
      {help ? (
        <span className='text-xs text-muted-foreground'>{help}</span>
      ) : null}
    </label>
  )
}

function Flag({
  label,
  checked,
  onChange,
}: {
  label: string
  checked: boolean
  onChange: (value: boolean) => void
}) {
  return (
    <label className='flex items-center gap-2'>
      <input
        type='checkbox'
        checked={checked}
        onChange={(event) => onChange(event.target.checked)}
      />
      {label}
    </label>
  )
}
