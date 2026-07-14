import { useMemo, useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Download, Pencil, Plus, Upload } from 'lucide-react'
import {
  createDictItem,
  createDictType,
  disableDictItem,
  disableDictType,
  listDictItems,
  listDictTypes,
  updateDictItem,
  updateDictType,
  type DictItem,
  type DictType,
} from '@/api/dictionaries'
import {
  parseDictionaryRows,
  serializeDictionaryRows,
  type DictionaryTransferFormat,
} from '@/lib/dictionaries/dictionary-transfer'
import { cn } from '@/lib/utils'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select'
import { Switch } from '@/components/ui/switch'
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from '@/components/ui/table'
import { Textarea } from '@/components/ui/textarea'
import { notify } from '@/components/feedback/app-toaster'

type TypeEditor = { mode: 'create' | 'edit'; value?: DictType }
type ItemEditor = { mode: 'create' | 'edit'; value?: DictItem }

export function DictionariesPage() {
  const queryClient = useQueryClient()
  const [selectedCode, setSelectedCode] = useState('')
  const [typeEditor, setTypeEditor] = useState<TypeEditor | null>(null)
  const [itemEditor, setItemEditor] = useState<ItemEditor | null>(null)
  const [transferMode, setTransferMode] = useState<'import' | 'export' | null>(
    null
  )
  const [format, setFormat] = useState<DictionaryTransferFormat>('table')
  const [file, setFile] = useState<File | null>(null)
  const [typeForm, setTypeForm] = useState({
    code: '',
    name: '',
    description: '',
    sortOrder: 0,
    enabled: true,
  })
  const [itemForm, setItemForm] = useState({
    label: '',
    value: '',
    description: '',
    sortOrder: 0,
    enabled: true,
  })

  const types = useQuery({
    queryKey: ['platform-dictionaries'],
    queryFn: () => listDictTypes(true),
  })
  const selectedType = useMemo(() => {
    if (!types.data?.length) return undefined
    return (
      types.data.find((item) => item.dictCode === selectedCode) ?? types.data[0]
    )
  }, [selectedCode, types.data])
  const items = useQuery({
    queryKey: ['platform-dictionary-items', selectedType?.dictCode],
    queryFn: () => listDictItems(selectedType!.dictCode, true),
    enabled: Boolean(selectedType),
  })

  const refresh = async () => {
    await queryClient.invalidateQueries({ queryKey: ['platform-dictionaries'] })
    await queryClient.invalidateQueries({
      queryKey: ['platform-dictionary-items'],
    })
  }

  const saveType = useMutation({
    mutationFn: async () => {
      if (typeEditor?.mode === 'edit' && typeEditor.value) {
        return updateDictType(typeEditor.value.dictCode, {
          dictName: typeForm.name.trim(),
          description: typeForm.description.trim(),
          sortOrder: typeForm.sortOrder,
          enabled: typeForm.enabled,
        })
      }
      return createDictType({
        dictCode: typeForm.code.trim(),
        dictName: typeForm.name.trim(),
        description: typeForm.description.trim(),
        sortOrder: typeForm.sortOrder,
        enabled: typeForm.enabled,
      })
    },
    onSuccess: async (result) => {
      setSelectedCode(result.dictCode)
      setTypeEditor(null)
      notify.success(typeEditor?.mode === 'edit' ? '字典已更新' : '字典已创建')
      await refresh()
    },
    onError: (error) => notify.error(error, '保存字典失败'),
  })

  const saveItem = useMutation({
    mutationFn: async () => {
      if (!selectedType) throw new Error('请先选择字典')
      if (itemEditor?.mode === 'edit' && itemEditor.value) {
        return updateDictItem(selectedType.dictCode, itemEditor.value.id, {
          itemLabel: itemForm.label.trim(),
          description: itemForm.description.trim(),
          sortOrder: itemForm.sortOrder,
          enabled: itemForm.enabled,
        })
      }
      return createDictItem(selectedType.dictCode, {
        itemLabel: itemForm.label.trim(),
        itemValue: itemForm.value.trim(),
        description: itemForm.description.trim(),
        sortOrder: itemForm.sortOrder,
        enabled: itemForm.enabled,
        extraJson: '{}',
      })
    },
    onSuccess: async () => {
      setItemEditor(null)
      notify.success(
        itemEditor?.mode === 'edit' ? '字典项已更新' : '字典项已创建'
      )
      await refresh()
    },
    onError: (error) => notify.error(error, '保存字典项失败'),
  })

  const importItems = useMutation({
    mutationFn: async () => {
      if (!selectedType || !file) throw new Error('请选择导入文件')
      const rows = parseDictionaryRows(await file.text(), format)
      const currentItems = items.data ?? []
      for (const row of rows) {
        const current = currentItems.find(
          (item) => item.itemValue === row.itemValue
        )
        if (current) {
          await updateDictItem(selectedType.dictCode, current.id, {
            itemLabel: row.itemLabel,
            description: row.description,
            sortOrder: row.sortOrder,
            enabled: row.enabled,
          })
        } else {
          await createDictItem(selectedType.dictCode, {
            ...row,
            extraJson: '{}',
          })
        }
      }
      return rows.length
    },
    onSuccess: async (count) => {
      setTransferMode(null)
      setFile(null)
      notify.success(`已导入 ${count} 个字典项`)
      await refresh()
    },
    onError: (error) => notify.error(error, '导入字典失败'),
  })

  const openTypeEditor = (mode: TypeEditor['mode'], value?: DictType) => {
    setTypeForm({
      code: value?.dictCode ?? '',
      name: value?.dictName ?? '',
      description: value?.description ?? '',
      sortOrder: value?.sortOrder ?? 0,
      enabled: value?.enabled ?? true,
    })
    setTypeEditor({ mode, value })
  }
  const openItemEditor = (mode: ItemEditor['mode'], value?: DictItem) => {
    setItemForm({
      label: value?.itemLabel ?? '',
      value: value?.itemValue ?? '',
      description: value?.description ?? '',
      sortOrder: value?.sortOrder ?? 0,
      enabled: value?.enabled ?? true,
    })
    setItemEditor({ mode, value })
  }

  const exportItems = () => {
    if (!selectedType) return
    const content = serializeDictionaryRows(
      (items.data ?? []).map((item) => ({
        itemLabel: item.itemLabel,
        itemValue: item.itemValue,
        description: item.description ?? '',
        sortOrder: item.sortOrder,
        enabled: item.enabled,
      })),
      format
    )
    const blob = new Blob([`\uFEFF${content}`], {
      type:
        format === 'csv'
          ? 'text/csv;charset=utf-8'
          : 'text/tab-separated-values;charset=utf-8',
    })
    const url = URL.createObjectURL(blob)
    const anchor = document.createElement('a')
    anchor.href = url
    anchor.download = `${selectedType.dictCode}.${format === 'csv' ? 'csv' : 'tsv'}`
    anchor.click()
    URL.revokeObjectURL(url)
    setTransferMode(null)
    notify.success('字典已导出')
  }

  return (
    <main className='grid gap-6 p-4 md:p-6'>
      <div>
        <h1 className='text-2xl font-semibold'>字典管理</h1>
        <p className='text-sm text-muted-foreground'>
          维护字典名称、字段和批量数据；禁用不会删除历史含义。
        </p>
      </div>

      <div className='grid gap-4 lg:grid-cols-[320px_1fr]'>
        <Card>
          <CardHeader className='flex flex-row items-center justify-between'>
            <CardTitle>字典类型</CardTitle>
            <Button size='sm' onClick={() => openTypeEditor('create')}>
              <Plus data-icon='inline-start' />
              新增
            </Button>
          </CardHeader>
          <CardContent className='grid gap-2'>
            {types.data?.map((item) => (
              <div
                key={item.id}
                className={cn(
                  'flex items-center gap-2 rounded-md border p-2',
                  selectedType?.dictCode === item.dictCode &&
                    'border-primary bg-muted/50'
                )}
              >
                <Button
                  type='button'
                  variant='ghost'
                  className='h-auto min-w-0 flex-1 justify-start px-1 py-0 text-left'
                  onClick={() => setSelectedCode(item.dictCode)}
                >
                  <span className='grid min-w-0 gap-0.5'>
                    <span className='truncate font-medium'>
                      {item.dictName}
                    </span>
                    <span className='truncate text-xs text-muted-foreground'>
                      {item.dictCode} · {item.enabled ? '启用' : '禁用'}
                    </span>
                  </span>
                </Button>
                <Button
                  size='icon'
                  variant='ghost'
                  aria-label={`编辑 ${item.dictName}`}
                  onClick={() => openTypeEditor('edit', item)}
                >
                  <Pencil />
                </Button>
              </div>
            ))}
          </CardContent>
        </Card>

        <Card>
          <CardHeader className='flex flex-col gap-3 xl:flex-row xl:items-center xl:justify-between'>
            <div>
              <CardTitle>{selectedType?.dictName ?? '字典项'}</CardTitle>
              {selectedType ? (
                <p className='mt-1 text-sm text-muted-foreground'>
                  {selectedType.dictCode}
                </p>
              ) : null}
            </div>
            {selectedType ? (
              <div className='flex flex-wrap gap-2'>
                <Button
                  size='sm'
                  variant='outline'
                  onClick={() => setTransferMode('import')}
                >
                  <Upload data-icon='inline-start' />
                  导入
                </Button>
                <Button
                  size='sm'
                  variant='outline'
                  onClick={() => setTransferMode('export')}
                >
                  <Download data-icon='inline-start' />
                  导出
                </Button>
                {!selectedType.systemBuiltin && selectedType.enabled ? (
                  <Button
                    size='sm'
                    variant='outline'
                    onClick={() =>
                      disableDictType(selectedType.dictCode).then(refresh)
                    }
                  >
                    禁用字典
                  </Button>
                ) : null}
                <Button size='sm' onClick={() => openItemEditor('create')}>
                  <Plus data-icon='inline-start' />
                  新增字段
                </Button>
              </div>
            ) : null}
          </CardHeader>
          <CardContent>
            <div className='overflow-x-auto rounded-md border'>
              <Table>
                <TableHeader>
                  <TableRow className='bg-muted/40'>
                    <TableHead>字段名称</TableHead>
                    <TableHead>字段值</TableHead>
                    <TableHead>说明</TableHead>
                    <TableHead>状态</TableHead>
                    <TableHead>排序</TableHead>
                    <TableHead className='text-right'>操作</TableHead>
                  </TableRow>
                </TableHeader>
                <TableBody>
                  {items.data?.map((item) => (
                    <TableRow key={item.id}>
                      <TableCell className='font-medium'>
                        {item.itemLabel}
                      </TableCell>
                      <TableCell>{item.itemValue}</TableCell>
                      <TableCell>{item.description || '—'}</TableCell>
                      <TableCell>{item.enabled ? '启用' : '禁用'}</TableCell>
                      <TableCell>{item.sortOrder}</TableCell>
                      <TableCell>
                        <div className='flex justify-end gap-1'>
                          <Button
                            size='sm'
                            variant='outline'
                            onClick={() => openItemEditor('edit', item)}
                          >
                            <Pencil data-icon='inline-start' />
                            编辑
                          </Button>
                          <Button
                            size='sm'
                            variant='outline'
                            onClick={() =>
                              selectedType &&
                              (item.enabled
                                ? disableDictItem(
                                    selectedType.dictCode,
                                    item.id
                                  ).then(refresh)
                                : updateDictItem(
                                    selectedType.dictCode,
                                    item.id,
                                    { enabled: true }
                                  ).then(refresh))
                            }
                          >
                            {item.enabled ? '禁用' : '启用'}
                          </Button>
                        </div>
                      </TableCell>
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
            </div>
          </CardContent>
        </Card>
      </div>

      <Dialog
        open={Boolean(typeEditor)}
        onOpenChange={(open) => !open && setTypeEditor(null)}
      >
        <DialogContent>
          <DialogHeader>
            <DialogTitle>
              {typeEditor?.mode === 'edit' ? '编辑字典' : '新增字典'}
            </DialogTitle>
            <DialogDescription>
              字典编码创建后不可修改，名称和说明可随时编辑。
            </DialogDescription>
          </DialogHeader>
          <div className='grid gap-4'>
            <Field label='字典名称'>
              <Input
                value={typeForm.name}
                onChange={(e) =>
                  setTypeForm({ ...typeForm, name: e.target.value })
                }
              />
            </Field>
            <Field label='字典编码'>
              <Input
                value={typeForm.code}
                disabled={typeEditor?.mode === 'edit'}
                onChange={(e) =>
                  setTypeForm({ ...typeForm, code: e.target.value })
                }
              />
            </Field>
            <Field label='说明'>
              <Textarea
                value={typeForm.description}
                onChange={(e) =>
                  setTypeForm({ ...typeForm, description: e.target.value })
                }
              />
            </Field>
            <Field label='排序'>
              <Input
                type='number'
                value={typeForm.sortOrder}
                onChange={(e) =>
                  setTypeForm({
                    ...typeForm,
                    sortOrder: Number(e.target.value),
                  })
                }
              />
            </Field>
            <ToggleField
              label='启用'
              checked={typeForm.enabled}
              onCheckedChange={(enabled) =>
                setTypeForm({ ...typeForm, enabled })
              }
            />
          </div>
          <DialogFooter>
            <Button variant='outline' onClick={() => setTypeEditor(null)}>
              取消
            </Button>
            <Button
              disabled={
                !typeForm.name.trim() ||
                !typeForm.code.trim() ||
                saveType.isPending
              }
              onClick={() => saveType.mutate()}
            >
              保存
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

      <Dialog
        open={Boolean(itemEditor)}
        onOpenChange={(open) => !open && setItemEditor(null)}
      >
        <DialogContent>
          <DialogHeader>
            <DialogTitle>
              {itemEditor?.mode === 'edit' ? '编辑字典字段' : '新增字典字段'}
            </DialogTitle>
            <DialogDescription>
              字段值创建后不可修改，字段名称用于界面显示。
            </DialogDescription>
          </DialogHeader>
          <div className='grid gap-4'>
            <Field label='字段名称'>
              <Input
                value={itemForm.label}
                onChange={(e) =>
                  setItemForm({ ...itemForm, label: e.target.value })
                }
              />
            </Field>
            <Field label='字段值'>
              <Input
                value={itemForm.value}
                disabled={itemEditor?.mode === 'edit'}
                onChange={(e) =>
                  setItemForm({ ...itemForm, value: e.target.value })
                }
              />
            </Field>
            <Field label='说明'>
              <Textarea
                value={itemForm.description}
                onChange={(e) =>
                  setItemForm({ ...itemForm, description: e.target.value })
                }
              />
            </Field>
            <Field label='排序'>
              <Input
                type='number'
                value={itemForm.sortOrder}
                onChange={(e) =>
                  setItemForm({
                    ...itemForm,
                    sortOrder: Number(e.target.value),
                  })
                }
              />
            </Field>
            <ToggleField
              label='启用'
              checked={itemForm.enabled}
              onCheckedChange={(enabled) =>
                setItemForm({ ...itemForm, enabled })
              }
            />
          </div>
          <DialogFooter>
            <Button variant='outline' onClick={() => setItemEditor(null)}>
              取消
            </Button>
            <Button
              disabled={
                !itemForm.label.trim() ||
                !itemForm.value.trim() ||
                saveItem.isPending
              }
              onClick={() => saveItem.mutate()}
            >
              保存
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

      <Dialog
        open={Boolean(transferMode)}
        onOpenChange={(open) => !open && setTransferMode(null)}
      >
        <DialogContent>
          <DialogHeader>
            <DialogTitle>
              {transferMode === 'import' ? '导入字典表' : '导出字典表'}
            </DialogTitle>
            <DialogDescription>
              选择 CSV 或可直接用表格软件打开的 TSV
              格式。相同字段值会更新现有字段。
            </DialogDescription>
          </DialogHeader>
          <div className='grid gap-4'>
            <Field label='文件格式'>
              <Select
                value={format}
                onValueChange={(value) =>
                  setFormat(value as DictionaryTransferFormat)
                }
              >
                <SelectTrigger>
                  <SelectValue />
                </SelectTrigger>
                <SelectContent>
                  <SelectItem value='table'>表格（TSV）</SelectItem>
                  <SelectItem value='csv'>CSV</SelectItem>
                </SelectContent>
              </Select>
            </Field>
            {transferMode === 'import' ? (
              <Field label='选择文件'>
                <Input
                  type='file'
                  accept={
                    format === 'csv'
                      ? '.csv,text/csv'
                      : '.tsv,text/tab-separated-values'
                  }
                  onChange={(e) => setFile(e.target.files?.[0] ?? null)}
                />
              </Field>
            ) : null}
          </div>
          <DialogFooter>
            <Button variant='outline' onClick={() => setTransferMode(null)}>
              取消
            </Button>
            <Button
              disabled={
                transferMode === 'import' && (!file || importItems.isPending)
              }
              onClick={() =>
                transferMode === 'import' ? importItems.mutate() : exportItems()
              }
            >
              {transferMode === 'import' ? '开始导入' : '下载文件'}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </main>
  )
}

function Field({
  label,
  children,
}: {
  label: string
  children: React.ReactNode
}) {
  return (
    <div className='grid gap-2'>
      <Label>{label}</Label>
      {children}
    </div>
  )
}

function ToggleField({
  label,
  checked,
  onCheckedChange,
}: {
  label: string
  checked: boolean
  onCheckedChange: (checked: boolean) => void
}) {
  return (
    <div className='flex items-center justify-between rounded-md border p-3'>
      <Label>{label}</Label>
      <Switch checked={checked} onCheckedChange={onCheckedChange} />
    </div>
  )
}
