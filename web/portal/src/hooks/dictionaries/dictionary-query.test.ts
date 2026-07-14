import { QueryClient } from '@tanstack/react-query'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import {
  dictionaryItemsQueryOptions,
  dictionaryKeys,
  dictionaryLabel,
} from './dictionary-query'

vi.mock('@/api/dictionaries', () => ({
  listDictItems: vi.fn(async () => [
    {
      itemValue: 'P1',
      itemLabel: '高',
      enabled: true,
    },
  ]),
}))

describe('dictionary query', () => {
  let client: QueryClient

  beforeEach(() => {
    client = new QueryClient({
      defaultOptions: {
        queries: {
          retry: false,
        },
      },
    })
  })

  it('uses stable dictionary cache key', () => {
    expect(dictionaryKeys.items('priority', true)).toEqual([
      'platform-dictionaries',
      'items',
      'priority',
      true,
    ])
  })

  it('reuses fresh query cache', async () => {
    const options = dictionaryItemsQueryOptions('priority', true)

    const first = await client.fetchQuery(options)
    const second = await client.fetchQuery(options)

    expect(second).toBe(first)
  })

  it('returns raw value when label is missing', () => {
    expect(
      dictionaryLabel(
        {
          priority: [
            {
              value: 'P1',
              label: '高',
              enabled: true,
            },
          ],
        },
        'priority',
        'P2'
      )
    ).toBe('P2')
  })

  it('shares cache across pages via platform-dictionaries namespace', async () => {
    // 第一次 fetch 写入 cache，模拟列表页请求字典。
    await client.fetchQuery(dictionaryItemsQueryOptions('priority', true))

    // 详情 / 新建页通过相同 key 拉取，得到引用相等的缓存数据。
    const second = client.getQueryData(dictionaryKeys.items('priority', true))

    expect(second).toBeDefined()
  })
})
