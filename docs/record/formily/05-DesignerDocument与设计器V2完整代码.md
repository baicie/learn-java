# DesignerDocument、编译器与设计器 v2 完整代码

## 1. 为什么替换扁平 DesignerField

当前 `DesignerField[]` 只能表达顺序字段，无法表达：

```text
Section
Grid
Divider
多列
嵌套布局
节点级设计状态
```

DesignerDocument v2：

```ts
export type DesignerDocument = {
  version: 2
  templateId: string
  nodes: DesignerNode[]
  settings: {
    pageWidth: 'sm' | 'md' | 'lg' | 'full'
    labelPlacement: 'top' | 'left'
    labelWidth?: number
  }
}

export type DesignerNode =
  | DesignerFieldNode
  | DesignerSectionNode
  | DesignerGridNode
  | DesignerDividerNode

export type DesignerNodeBase = {
  id: string
  kind: string
}

export type DesignerFieldNode = DesignerNodeBase & {
  kind: 'field'
  fieldCode: string
  fieldName: string
  fieldType: WorkRecordFieldType
  description?: string
  required: boolean
  enabled: boolean
  locked: boolean
  referenced: boolean
  componentProps: Record<string, unknown>
  validators: FieldValidatorRule[]
  dataSource:
    | { type: 'static'; options: Array<{ label: string; value: string }> }
    | { type: 'dict'; dictCode: string }
    | { type: 'user' }
  capability: {
    listVisible: boolean
    filterable: boolean
    exportable: boolean
    statistical: boolean
  }
}

export type DesignerSectionNode = DesignerNodeBase & {
  kind: 'section'
  title: string
  description?: string
  collapsible: boolean
  children: DesignerNode[]
}

export type DesignerGridNode = DesignerNodeBase & {
  kind: 'grid'
  columns: 2 | 3 | 4
  children: DesignerNode[]
}

export type DesignerDividerNode = DesignerNodeBase & {
  kind: 'divider'
  title?: string
}
```

## 2. Zod Schema

```ts
const baseNodeSchema = z.object({
  id: z.string().min(1).max(64),
  kind: z.string(),
})

export const designerFieldNodeSchema = baseNodeSchema.extend({
  kind: z.literal('field'),
  fieldCode: z
    .string()
    .regex(/^[a-zA-Z][a-zA-Z0-9_]{0,63}$/),
  fieldName: z.string().min(1).max(128),
  fieldType: z.enum(WORK_RECORD_FIELD_TYPES),
  description: z.string().max(500).optional(),
  required: z.boolean(),
  enabled: z.boolean(),
  locked: z.boolean(),
  referenced: z.boolean(),
  componentProps: z.record(z.string(), z.unknown()),
  validators: z.array(fieldValidatorSchema),
  dataSource: fieldDataSourceSchema,
  capability: fieldCapabilitySchema,
})

export const designerDocumentSchema: z.ZodType<DesignerDocument> = z.lazy(() =>
  z.object({
    version: z.literal(2),
    templateId: z.string(),
    nodes: z.array(designerNodeSchema).max(500),
    settings: designerSettingsSchema,
  })
)
```

## 3. 编译器

```ts
import type { ISchema } from '@formily/json-schema'

export function compileDesignerDocument(
  document: DesignerDocument
): ISchema {
  validateDesignerDocument(document)

  return {
    type: 'object',
    'x-work-record-schema-version': 2,
    properties: compileNodes(document.nodes),
  }
}

function compileNodes(nodes: DesignerNode[]): Record<string, ISchema> {
  const properties: Record<string, ISchema> = {}

  nodes.forEach((node, index) => {
    if (node.kind === 'field') {
      if (!node.enabled) return
      properties[node.fieldCode] = compileField(node, index)
      return
    }

    properties[`__layout_${node.id}`] = compileLayout(node)
  })

  return properties
}

function compileField(node: DesignerFieldNode, index: number): ISchema {
  return {
    type: jsonType(node.fieldType),
    title: node.fieldName,
    description: node.description,
    'x-decorator': 'FormItem',
    'x-component': componentName(node),
    'x-component-props': {
      ...node.componentProps,
      ...dataSourceProps(node.dataSource),
    },
    'x-validator': compileValidators(node),
    'x-index': index,
    'x-work-record': {
      fieldCode: node.fieldCode,
      fieldType: node.fieldType,
      optionSource:
        node.dataSource.type === 'dict' ? 'dict' : 'static',
      dictCode:
        node.dataSource.type === 'dict'
          ? node.dataSource.dictCode
          : undefined,
      ...node.capability,
    },
  }
}

function compileLayout(node: Exclude<DesignerNode, DesignerFieldNode>): ISchema {
  if (node.kind === 'divider') {
    return {
      type: 'void',
      'x-component': 'Divider',
      'x-component-props': { title: node.title },
    }
  }

  if (node.kind === 'section') {
    return {
      type: 'void',
      'x-component': 'Section',
      'x-component-props': {
        title: node.title,
        description: node.description,
        collapsible: node.collapsible,
      },
      properties: compileNodes(node.children),
    }
  }

  return {
    type: 'void',
    'x-component': 'Grid',
    'x-component-props': { columns: node.columns },
    properties: compileNodes(node.children),
  }
}
```

## 4. 文档校验

```ts
export function validateDesignerDocument(document: DesignerDocument) {
  designerDocumentSchema.parse(document)

  const nodeIds = new Set<string>()
  const fieldCodes = new Set<string>()

  walkNodes(document.nodes, (node) => {
    if (!nodeIds.add(node.id)) {
      throw new DesignerValidationError(`重复节点 ID：${node.id}`)
    }

    if (node.kind === 'field') {
      if (!fieldCodes.add(node.fieldCode)) {
        throw new DesignerValidationError(
          `重复字段编码：${node.fieldCode}`
        )
      }

      validateFieldDataSource(node)
    }
  })
}
```

树结构由 JSON 自身表示，不接受 parentId 回指，因此天然避免循环；移动操作仍需阻止把布局节点移入自己的后代。

## 5. Zustand Store

```ts
import { create } from 'zustand'

type Snapshot = DesignerDocument

type DesignerStore = {
  document: DesignerDocument
  selectedNodeId: string | null
  history: Snapshot[]
  future: Snapshot[]
  dirty: boolean

  selectNode(id: string | null): void
  addNode(parentId: string | null, index: number, node: DesignerNode): void
  moveNode(id: string, parentId: string | null, index: number): void
  updateNode(id: string, patch: Partial<DesignerNode>): void
  duplicateNode(id: string): void
  removeOrDisableNode(id: string): void
  undo(): void
  redo(): void
  markSaved(): void
  reset(document: DesignerDocument): void
}

export const useDesignerStore = create<DesignerStore>((set, get) => ({
  document: emptyDesignerDocument(),
  selectedNodeId: null,
  history: [],
  future: [],
  dirty: false,

  selectNode: (id) => set({ selectedNodeId: id }),

  addNode: (parentId, index, node) => {
    commit(set, get, (document) =>
      insertNode(document, parentId, index, node)
    )
    set({ selectedNodeId: node.id })
  },

  moveNode: (id, parentId, index) => {
    const document = get().document

    if (isDescendant(document, id, parentId)) {
      throw new Error('layout node cannot be moved into its descendant')
    }

    commit(set, get, (current) =>
      moveDesignerNode(current, id, parentId, index)
    )
  },

  updateNode: (id, patch) => {
    commit(set, get, (document) =>
      updateDesignerNode(document, id, patch)
    )
  },

  duplicateNode: (id) => {
    const copy = cloneDesignerNode(requireNode(get().document, id))
    commit(set, get, (document) =>
      insertAfterNode(document, id, copy)
    )
    set({ selectedNodeId: copy.id })
  },

  removeOrDisableNode: (id) => {
    const node = requireNode(get().document, id)

    commit(set, get, (document) =>
      node.kind === 'field' && (node.locked || node.referenced)
        ? updateDesignerNode(document, id, { enabled: false })
        : removeDesignerNode(document, id)
    )
  },

  undo: () => {
    const { history, document, future } = get()
    const previous = history.at(-1)
    if (!previous) return

    set({
      document: structuredClone(previous),
      history: history.slice(0, -1),
      future: [structuredClone(document), ...future].slice(0, 50),
      dirty: true,
    })
  },

  redo: () => {
    const { history, document, future } = get()
    const next = future[0]
    if (!next) return

    set({
      document: structuredClone(next),
      history: [...history, structuredClone(document)].slice(-50),
      future: future.slice(1),
      dirty: true,
    })
  },

  markSaved: () => set({ dirty: false }),

  reset: (document) =>
    set({
      document: structuredClone(document),
      selectedNodeId: null,
      history: [],
      future: [],
      dirty: false,
    }),
}))

function commit(
  set: Parameters<typeof useDesignerStore.setState>[0],
  get: typeof useDesignerStore.getState,
  updater: (document: DesignerDocument) => DesignerDocument
) {
  const state = get()
  const previous = structuredClone(state.document)
  const next = updater(structuredClone(state.document))

  set({
    document: next,
    history: [...state.history, previous].slice(-50),
    future: [],
    dirty: true,
  })
}
```

## 6. DnD Context

```tsx
import {
  DndContext,
  DragOverlay,
  PointerSensor,
  KeyboardSensor,
  useSensor,
  useSensors,
} from '@dnd-kit/core'
import {
  sortableKeyboardCoordinates,
} from '@dnd-kit/sortable'

export function DesignerDndProvider({ children }: PropsWithChildren) {
  const sensors = useSensors(
    useSensor(PointerSensor, {
      activationConstraint: { distance: 6 },
    }),
    useSensor(KeyboardSensor, {
      coordinateGetter: sortableKeyboardCoordinates,
    })
  )

  return (
    <DndContext
      sensors={sensors}
      collisionDetection={closestCenter}
      onDragEnd={handleDesignerDragEnd}
    >
      {children}
      <DragOverlay>{/* 当前拖拽节点缩略图 */}</DragOverlay>
    </DndContext>
  )
}
```

拖拽 payload：

```ts
type DesignerDragData =
  | { type: 'library'; fieldType: WorkRecordFieldType }
  | { type: 'canvas-node'; nodeId: string }

type DesignerDropData = {
  parentId: string | null
  index: number
}
```

## 7. 页面布局

```tsx
export function WorkRecordDesignerV2Page() {
  const document = useDesignerStore((s) => s.document)
  const dirty = useDesignerStore((s) => s.dirty)
  const compiledSchema = useMemo(
    () => compileDesignerDocument(document),
    [document]
  )

  useUnsavedChangesGuard(dirty)

  return (
    <div className='flex h-dvh min-h-0 flex-col bg-muted/30'>
      <DesignerToolbar />

      <DesignerDndProvider>
        <div className='grid min-h-0 flex-1 grid-cols-[280px_minmax(0,1fr)_340px]'>
          <FieldLibraryPanel />

          <DesignerWorkspace>
            <DesignerPageSurface>
              <DesignerCanvas schema={compiledSchema} />
            </DesignerPageSurface>
          </DesignerWorkspace>

          <PropertyPanel />
        </div>
      </DesignerDndProvider>
    </div>
  )
}
```

Workspace：

```tsx
export function DesignerWorkspace({ children }: PropsWithChildren) {
  return (
    <ScrollArea className='min-w-0 bg-muted/40'>
      <div className='mx-auto min-h-full p-8 xl:p-12'>
        {children}
      </div>
    </ScrollArea>
  )
}

export function DesignerPageSurface({ children }: PropsWithChildren) {
  const width = useDesignerStore((s) => s.document.settings.pageWidth)

  return (
    <div
      className={cn(
        'mx-auto min-h-[720px] rounded-xl border bg-background p-8 shadow-sm',
        width === 'sm' && 'max-w-xl',
        width === 'md' && 'max-w-3xl',
        width === 'lg' && 'max-w-5xl',
        width === 'full' && 'max-w-none'
      )}
    >
      {children}
    </div>
  )
}
```

## 8. DesignerCanvasNode

```tsx
export function DesignerCanvasNode({
  node,
  children,
}: PropsWithChildren<{ node: DesignerNode }>) {
  const selectedId = useDesignerStore((s) => s.selectedNodeId)
  const select = useDesignerStore((s) => s.selectNode)
  const duplicate = useDesignerStore((s) => s.duplicateNode)
  const remove = useDesignerStore((s) => s.removeOrDisableNode)

  const selected = selectedId === node.id
  const sortable = useSortable({ id: node.id, data: { type: 'canvas-node' } })

  return (
    <div
      ref={sortable.setNodeRef}
      style={CSS.Transform.toString(sortable.transform)}
      className={cn(
        'group relative rounded-md border border-transparent p-2',
        selected && 'border-primary ring-1 ring-primary/30',
        sortable.isDragging && 'opacity-40'
      )}
      onClick={(event) => {
        event.stopPropagation()
        select(node.id)
      }}
    >
      <div
        className={cn(
          'absolute -top-3 right-2 z-10 hidden items-center gap-1 rounded-md border bg-background p-1 shadow-sm',
          selected && 'flex'
        )}
      >
        <Button
          size='icon-xs'
          variant='ghost'
          {...sortable.attributes}
          {...sortable.listeners}
          aria-label='拖动字段'
        >
          <GripVertical />
        </Button>
        <Button
          size='icon-xs'
          variant='ghost'
          onClick={() => duplicate(node.id)}
          aria-label='复制字段'
        >
          <Copy />
        </Button>
        <Button
          size='icon-xs'
          variant='ghost'
          onClick={() => remove(node.id)}
          aria-label={isHistorical(node) ? '禁用字段' : '删除字段'}
        >
          {isHistorical(node) ? <EyeOff /> : <Trash2 />}
        </Button>
      </div>

      {children}
    </div>
  )
}
```

## 9. 空画布与 Drop Zone

```tsx
export function EmptyCanvasDropZone() {
  const droppable = useDroppable({
    id: 'root-empty-drop',
    data: { parentId: null, index: 0 },
  })

  return (
    <div
      ref={droppable.setNodeRef}
      className={cn(
        'grid min-h-80 place-items-center rounded-xl border-2 border-dashed',
        droppable.isOver && 'border-primary bg-primary/5'
      )}
    >
      <div className='text-center'>
        <PanelTop className='mx-auto size-10 text-muted-foreground' />
        <p className='mt-3 font-medium'>拖入字段开始设计表单</p>
        <p className='mt-1 text-sm text-muted-foreground'>
          支持字段、分组、两列布局和分割线
        </p>
      </div>
    </div>
  )
}
```

## 10. 属性面板锁定

```tsx
<Input
  value={field.fieldCode}
  readOnly={field.locked}
  aria-describedby={field.locked ? 'field-code-locked-help' : undefined}
/>

{field.locked ? (
  <p id='field-code-locked-help' className='text-xs text-amber-700'>
    字段已经发布，编码用于查询、导出和 API 契约，不能修改。
  </p>
) : null}
```

类型锁定同理。有历史引用时，删除按钮显示“禁用字段”。

## 11. 保存

```ts
export function buildTemplateDraftPayload(document: DesignerDocument) {
  const schema = compileDesignerDocument(document)

  return {
    draftDesignerJson: JSON.stringify(document),
    draftSchemaJson: JSON.stringify(schema),
  }
}
```

服务端必须重新解析 DesignerDocument 并验证编译结果，不能只信客户端。

## 12. Store 单元测试

```ts
describe('designer store', () => {
  beforeEach(() => {
    useDesignerStore.getState().reset(emptyDesignerDocument())
  })

  it('adds field and supports undo/redo', () => {
    const field = createFieldNode('summary')

    useDesignerStore.getState().addNode(null, 0, field)
    expect(findField('summary')).toBeDefined()

    useDesignerStore.getState().undo()
    expect(findField('summary')).toBeUndefined()

    useDesignerStore.getState().redo()
    expect(findField('summary')).toBeDefined()
  })

  it('disables referenced field instead of deleting', () => {
    const field = {
      ...createFieldNode('priority'),
      locked: true,
      referenced: true,
    }

    useDesignerStore.getState().reset({
      ...emptyDesignerDocument(),
      nodes: [field],
    })

    useDesignerStore.getState().removeOrDisableNode(field.id)

    expect(requireField('priority').enabled).toBe(false)
  })

  it('rejects moving layout into descendant', () => {
    const document = nestedGridDocument()
    useDesignerStore.getState().reset(document)

    expect(() =>
      useDesignerStore.getState().moveNode(
        document.nodes[0].id,
        nestedChildId(document),
        0
      )
    ).toThrow(/descendant/)
  })
})
```

## 13. Browser 测试

必须覆盖：

```text
字段库拖入文本字段
拖动排序
拖入 Grid
修改标题立即更新真实控件 label
发布字段编码只读
删除历史字段出现风险确认
撤销/重做
页面宽度切换
预览与画布字段一致
```
