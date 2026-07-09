import { describe, expect, it, vi } from 'vitest'
import { render } from 'vitest-browser-react'
import { RecordReadonlyView } from './record-readonly-view'
import type { WorkRecord, WorkRecordField } from './types'

describe('RecordReadonlyView', () => {
  it('renders readonly record and disabled field badge', async () => {
    const screen = await render(
      <RecordReadonlyView
        record={record()}
        fields={[
          {
            ...field('priority', 'select'),
            fieldName: '优先级',
            optionSource: 'dict',
            dictCode: 'record_priority',
            enabled: false,
          },
        ]}
        dictOptions={{
          record_priority: [
            {
              id: 'p1',
              itemLabel: 'P1',
              itemValue: 'P1',
              color: null,
              enabled: false,
            },
          ],
        }}
        customData={{ priority: 'P1' }}
        onBack={vi.fn()}
        onEdit={vi.fn()}
      />
    )

    await expect.element(screen.getByText('日报')).toBeVisible()
    await expect.element(screen.getByText('已禁用字段')).toBeVisible()
    await expect.element(screen.getByText('P1（已禁用）')).toBeVisible()
  })
})

function record(): WorkRecord {
  return {
    id: 'r1',
    tenantId: 't1',
    templateId: 'tpl1',
    templateVersionId: 'v1',
    title: '日报',
    status: 'done',
    ownerId: 'u1',
    creatorId: 'u1',
    recordTime: '2026-01-01T00:00:00Z',
    builtinDataJson: '{}',
    customDataJson: '{"priority":"P1"}',
    rowVersion: 1,
    createdAt: '2026-01-01T00:00:00Z',
    updatedAt: '2026-01-01T00:00:00Z',
    deletedAt: null,
  }
}

function field(
  code: string,
  fieldType: WorkRecordField['fieldType']
): WorkRecordField {
  return {
    id: `f-${code}`,
    tenantId: 't1',
    templateId: 'tpl1',
    templateVersionId: 'v1',
    fieldName: code,
    fieldCode: code,
    fieldType,
    required: false,
    defaultValue: null,
    optionSource: 'static',
    dictCode: null,
    optionsJson: '[]',
    schemaPath: `.properties.${code}`,
    listVisible: true,
    filterable: true,
    exportable: true,
    statistical: false,
    sortOrder: 0,
    enabled: true,
    createdAt: '2026-01-01T00:00:00Z',
    updatedAt: '2026-01-01T00:00:00Z',
  }
}
