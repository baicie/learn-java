import { Badge } from '@/components/ui/badge'
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from '@/components/ui/table'
import { type DictItem } from '../data/schema'

type DictionaryItemTableProps = {
  items: DictItem[]
}

export function DictionaryItemTable({ items }: DictionaryItemTableProps) {
  return (
    <div className='overflow-hidden rounded-md border'>
      <Table>
        <TableHeader>
          <TableRow>
            <TableHead>名称</TableHead>
            <TableHead>值</TableHead>
            <TableHead>状态</TableHead>
            <TableHead>排序</TableHead>
          </TableRow>
        </TableHeader>
        <TableBody>
          {items.length ? (
            items.map((item) => (
              <TableRow key={item.id}>
                <TableCell className='font-medium'>{item.itemLabel}</TableCell>
                <TableCell className='font-mono'>{item.itemValue}</TableCell>
                <TableCell>
                  <Badge variant='outline'>
                    {item.enabled ? '启用' : '禁用'}
                  </Badge>
                </TableCell>
                <TableCell>{item.sortOrder}</TableCell>
              </TableRow>
            ))
          ) : (
            <TableRow>
              <TableCell colSpan={4} className='h-24 text-center'>
                暂无字典项
              </TableCell>
            </TableRow>
          )}
        </TableBody>
      </Table>
    </div>
  )
}
