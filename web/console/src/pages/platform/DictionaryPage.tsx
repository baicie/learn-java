import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useMemo, useState } from 'react'

import { Badge } from '../../components/ui/badge'
import { Button } from '../../components/ui/button'
import { Card, CardContent, CardHeader, CardTitle } from '../../components/ui/card'
import { Input } from '../../components/ui/input'
import { Label } from '../../components/ui/label'
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from '../../components/ui/table'
import {
  createDictItem,
  createDictType,
  listDictItems,
  listDictionaries,
} from '../../features/platform-dictionary/api'

export function DictionaryPage() {
  const queryClient = useQueryClient()
  const typesQuery = useQuery({
    queryKey: ['dictionaries'],
    queryFn: listDictionaries,
  })
  const [selectedCode, setSelectedCode] = useState<string | null>(null)
  const activeCode = selectedCode ?? typesQuery.data?.[0]?.dictCode ?? null

  const itemsQuery = useQuery({
    queryKey: ['dict-items', activeCode],
    queryFn: () => listDictItems(activeCode as string),
    enabled: Boolean(activeCode),
  })

  const createType = useMutation({
    mutationFn: createDictType,
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['dictionaries'] }),
  })
  const createItem = useMutation({
    mutationFn: (payload: { dictCode: string; label: string; value: string }) =>
      createDictItem(payload.dictCode, {
        itemLabel: payload.label,
        itemValue: payload.value,
      }),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['dict-items'] }),
  })

  const [typeForm, setTypeForm] = useState({ code: '', name: '', description: '' })
  const [itemForm, setItemForm] = useState({ label: '', value: '' })

  const activeType = useMemo(
    () => typesQuery.data?.find((t) => t.dictCode === activeCode) ?? null,
    [typesQuery.data, activeCode],
  )

  return (
    <section className="space-y-6 p-6">
      <div>
        <h1 className="text-2xl font-semibold">字典管理</h1>
        <p className="text-sm text-muted-foreground">维护工作记录使用的通用枚举与私有选项来源。</p>
      </div>

      <div className="grid gap-4 md:grid-cols-[280px_1fr]">
        <Card>
          <CardHeader>
            <CardTitle>字典类型</CardTitle>
          </CardHeader>
          <CardContent className="space-y-3">
            <ul className="divide-y rounded-md border bg-card">
              {(typesQuery.data ?? []).map((type) => (
                <li
                  key={type.id}
                  className={`cursor-pointer p-3 text-sm ${
                    type.dictCode === activeCode ? 'bg-muted' : ''
                  }`}
                  onClick={() => setSelectedCode(type.dictCode)}
                >
                  <div className="flex items-center justify-between">
                    <span className="font-medium">{type.dictName}</span>
                    <Badge variant={type.enabled ? 'secondary' : 'outline'}>{type.dictCode}</Badge>
                  </div>
                  {type.description && (
                    <p className="mt-1 text-xs text-muted-foreground">{type.description}</p>
                  )}
                </li>
              ))}
              {typesQuery.data?.length === 0 && (
                <li className="p-3 text-sm text-muted-foreground">暂无字典类型</li>
              )}
            </ul>

            <form
              className="space-y-2 border-t pt-3"
              onSubmit={(e) => {
                e.preventDefault()
                createType.mutate({
                  dictCode: typeForm.code,
                  dictName: typeForm.name,
                  description: typeForm.description || undefined,
                  enabled: true,
                })
                setTypeForm({ code: '', name: '', description: '' })
              }}
            >
              <div className="space-y-1">
                <Label htmlFor="dict-code">编码</Label>
                <Input
                  id="dict-code"
                  value={typeForm.code}
                  onChange={(e) => setTypeForm((s) => ({ ...s, code: e.target.value }))}
                  placeholder="例如 record_status"
                  required
                />
              </div>
              <div className="space-y-1">
                <Label htmlFor="dict-name">名称</Label>
                <Input
                  id="dict-name"
                  value={typeForm.name}
                  onChange={(e) => setTypeForm((s) => ({ ...s, name: e.target.value }))}
                  placeholder="例如 工作记录状态"
                  required
                />
              </div>
              <div className="space-y-1">
                <Label htmlFor="dict-desc">描述</Label>
                <Input
                  id="dict-desc"
                  value={typeForm.description}
                  onChange={(e) => setTypeForm((s) => ({ ...s, description: e.target.value }))}
                />
              </div>
              <Button type="submit" disabled={createType.isPending}>
                {createType.isPending ? '提交中…' : '新增字典类型'}
              </Button>
            </form>
          </CardContent>
        </Card>

        <Card>
          <CardHeader>
            <CardTitle>{activeType ? `${activeType.dictName} 的字典项` : '字典项'}</CardTitle>
          </CardHeader>
          <CardContent className="space-y-4">
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>标签</TableHead>
                  <TableHead>值</TableHead>
                  <TableHead>排序</TableHead>
                  <TableHead>状态</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {(itemsQuery.data ?? []).map((item) => (
                  <TableRow key={item.id}>
                    <TableCell>{item.itemLabel}</TableCell>
                    <TableCell className="font-mono text-xs">{item.itemValue}</TableCell>
                    <TableCell>{item.sortOrder}</TableCell>
                    <TableCell>
                      <Badge variant={item.enabled ? 'secondary' : 'outline'}>
                        {item.enabled ? '启用' : '停用'}
                      </Badge>
                    </TableCell>
                  </TableRow>
                ))}
                {itemsQuery.data?.length === 0 && (
                  <TableRow>
                    <TableCell colSpan={4} className="text-center text-muted-foreground">
                      暂无字典项
                    </TableCell>
                  </TableRow>
                )}
              </TableBody>
            </Table>

            {activeCode && (
              <form
                className="grid gap-2 border-t pt-3 md:grid-cols-[1fr_1fr_auto]"
                onSubmit={(e) => {
                  e.preventDefault()
                  createItem.mutate({
                    dictCode: activeCode,
                    label: itemForm.label,
                    value: itemForm.value,
                  })
                  setItemForm({ label: '', value: '' })
                }}
              >
                <Input
                  value={itemForm.label}
                  onChange={(e) => setItemForm((s) => ({ ...s, label: e.target.value }))}
                  placeholder="标签（如 草稿）"
                  required
                />
                <Input
                  value={itemForm.value}
                  onChange={(e) => setItemForm((s) => ({ ...s, value: e.target.value }))}
                  placeholder="值（如 draft）"
                  required
                />
                <Button type="submit" disabled={createItem.isPending}>
                  {createItem.isPending ? '提交中…' : '新增字典项'}
                </Button>
              </form>
            )}
          </CardContent>
        </Card>
      </div>
    </section>
  )
}
