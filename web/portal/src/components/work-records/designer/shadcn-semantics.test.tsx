import { describe, expect, it, vi } from 'vitest'
import { render } from 'vitest-browser-react'
import { FormCanvas } from './form-canvas'
import { FormPreview } from './form-preview'
import { SchemaDiffPanel } from './schema-diff-panel'
import type { DesignerField } from './types'

const lockedBooleanField: DesignerField = {
  id: 'field-1',
  fieldName: '是否完成',
  fieldCode: 'completed',
  fieldType: 'boolean',
  required: true,
  optionSource: 'static',
  dictCode: '',
  listVisible: true,
  filterable: true,
  exportable: true,
  statistical: false,
  sortOrder: 0,
  enabled: true,
  locked: true,
  referenced: false,
}

describe('work record designer shadcn semantics', () => {
  it('uses a named selection button and Badge components on the form canvas', async () => {
    const onSelect = vi.fn()
    const onDuplicate = vi.fn()
    const screen = await render(
      <FormCanvas
        fields={[lockedBooleanField]}
        selectedFieldId=''
        onSelect={onSelect}
        onMove={vi.fn()}
        onDuplicate={onDuplicate}
        onRemoveOrDisable={vi.fn()}
      />
    )

    await screen.getByRole('button', { name: '选择字段 是否完成' }).click()

    expect(onSelect).toHaveBeenCalledWith('field-1')
    await screen.getByRole('button', { name: '复制字段 是否完成' }).click()
    expect(onDuplicate).toHaveBeenCalledWith('field-1')
    await expect
      .element(screen.getByText('编码锁定'))
      .toHaveAttribute('data-slot', 'badge')
  })

  it('uses an Alert for schema validation failures', async () => {
    const screen = await render(
      <SchemaDiffPanel
        diff={[]}
        validationErrors={['字段编码不能为空']}
        publishValidation={null}
      />
    )

    await expect.element(screen.getByRole('alert')).toBeVisible()
    await expect.element(screen.getByText('本地校验失败')).toBeVisible()
  })

  it('avoids nested labels and uses the destructive token for required fields', async () => {
    const screen = await render(<FormPreview fields={[lockedBooleanField]} />)

    expect(document.querySelector('label label')).toBeNull()
    await expect
      .element(screen.getByText('*', { exact: true }))
      .toHaveClass('text-destructive')
  })
})
