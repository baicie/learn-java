import { useMemo, useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Plus } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import {
  createDictItem,
  createDictType,
  disableDictItem,
  disableDictType,
  listDictItems,
  listDictTypes,
  updateDictItem,
} from './api'

export function DictionariesPage() {
  const queryClient = useQueryClient()
  const [selectedCode, setSelectedCode] = useState<string>('')

  const types = useQuery({
    queryKey: ['platform-dictionaries'],
    queryFn: () => listDictTypes(true),
  })

  const selectedType = useMemo(() => {
    if (!types.data?.length) return undefined
    if (!selectedCode) return types.data[0]
    return (
      types.data.find((item) => item.dictCode === selectedCode) ?? types.data[0]
    )
  }, [selectedCode, types.data])

  const items = useQuery({
    queryKey: ['platform-dictionary-items', selectedType?.dictCode],
    queryFn: () => listDictItems(selectedType!.dictCode, true),
    enabled: Boolean(selectedType?.dictCode),
  })

  const refresh = async () => {
    await queryClient.invalidateQueries({ queryKey: ['platform-dictionaries'] })
    await queryClient.invalidateQueries({
      queryKey: ['platform-dictionary-items'],
    })
  }

  const createTypeMutation = useMutation({
    mutationFn: () =>
      createDictType({
        dictCode: `dict_${Date.now()}`,
        dictName: '新字典',
        enabled: true,
        sortOrder: 0,
      }),
    onSuccess: refresh,
  })

  const toggleTypeMutation = useMutation({
    mutationFn: (dictCode: string) => disableDictType(dictCode),
    onSuccess: refresh,
  })

  const createItemMutation = useMutation({
    mutationFn: (dictCode: string) =>
      createDictItem(dictCode, {
        itemLabel: '新选项',
        itemValue: `item_${Date.now()}`,
        enabled: true,
        sortOrder: 0,
        extraJson: '{}',
      }),
    onSuccess: refresh,
  })

  const toggleItemMutation = useMutation({
    mutationFn: ({ dictCode, itemId }: { dictCode: string; itemId: string }) =>
      disableDictItem(dictCode, itemId),
    onSuccess: refresh,
  })

  return (
    <main className='grid gap-4 p-6'>
      <div>
        <h1 className='text-2xl font-semibold'>字典管理</h1>
        <p className='text-sm text-muted-foreground'>
          维护平台字典类型和字典项。禁用不会删除历史含义。
        </p>
      </div>

      <div className='grid gap-4 lg:grid-cols-[320px_1fr]'>
        <Card>
          <CardHeader className='flex flex-row items-center justify-between'>
            <CardTitle>字典类型</CardTitle>
            <Button size='sm' onClick={() => createTypeMutation.mutate()}>
              <Plus className='mr-1 size-4' />
              新增
            </Button>
          </CardHeader>
          <CardContent className='grid gap-2'>
            {types.data?.map((item) => (
              <button
                key={item.id}
                type='button'
                className={`rounded-md border px-3 py-2 text-left text-sm ${
                  selectedType?.dictCode === item.dictCode ? 'bg-muted' : ''
                }`}
                onClick={() => setSelectedCode(item.dictCode)}
              >
                <div className='font-medium'>{item.dictName}</div>
                <div className='text-xs text-muted-foreground'>
                  {item.dictCode}
                </div>
                <div className='text-xs'>{item.enabled ? '启用' : '禁用'}</div>
              </button>
            ))}
          </CardContent>
        </Card>

        <Card>
          <CardHeader className='flex flex-row items-center justify-between'>
            <CardTitle>{selectedType?.dictName ?? '字典项'}</CardTitle>
            <div className='flex gap-2'>
              {selectedType && !selectedType.systemBuiltin ? (
                <Button
                  size='sm'
                  variant='outline'
                  onClick={() =>
                    toggleTypeMutation.mutate(selectedType.dictCode)
                  }
                >
                  禁用字典
                </Button>
              ) : null}
              {selectedType ? (
                <Button
                  size='sm'
                  onClick={() =>
                    createItemMutation.mutate(selectedType.dictCode)
                  }
                >
                  <Plus className='mr-1 size-4' />
                  新增选项
                </Button>
              ) : null}
            </div>
          </CardHeader>
          <CardContent>
            <div className='overflow-x-auto rounded-md border'>
              <table className='w-full text-sm'>
                <thead>
                  <tr className='border-b bg-muted/40'>
                    <th className='p-2 text-left'>标签</th>
                    <th className='p-2 text-left'>值</th>
                    <th className='p-2 text-left'>状态</th>
                    <th className='p-2 text-left'>排序</th>
                    <th className='p-2 text-left'>操作</th>
                  </tr>
                </thead>
                <tbody>
                  {items.data?.map((item) => (
                    <tr key={item.id} className='border-b'>
                      <td className='p-2'>{item.itemLabel}</td>
                      <td className='p-2'>{item.itemValue}</td>
                      <td className='p-2'>{item.enabled ? '启用' : '禁用'}</td>
                      <td className='p-2'>{item.sortOrder}</td>
                      <td className='p-2'>
                        {item.enabled ? (
                          <Button
                            size='sm'
                            variant='outline'
                            onClick={() =>
                              selectedType &&
                              toggleItemMutation.mutate({
                                dictCode: selectedType.dictCode,
                                itemId: item.id,
                              })
                            }
                          >
                            禁用
                          </Button>
                        ) : (
                          <Button
                            size='sm'
                            variant='outline'
                            onClick={() =>
                              selectedType &&
                              updateDictItem(selectedType.dictCode, item.id, {
                                enabled: true,
                              }).then(refresh)
                            }
                          >
                            启用
                          </Button>
                        )}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </CardContent>
        </Card>
      </div>
    </main>
  )
}
