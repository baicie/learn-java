import { describe, expect, it } from 'vitest'

import { buildMenuTree, resolveMenuIcon } from '../lib/menu'

import type { PlatformMenuItem } from '../api/client'

const baseItem: Omit<PlatformMenuItem, 'id' | 'parentId' | 'title' | 'path' | 'sortOrder'> = {
  moduleId: 'platform',
  icon: null,
  permissionCode: null,
  enabled: true,
  createdAt: '2026-07-02T00:00:00Z',
}

describe('buildMenuTree', () => {
  it('builds flat list when no parentId is set', () => {
    const tree = buildMenuTree({
      nodes: [
        { ...baseItem, id: 'a', parentId: null, title: 'A', path: '/a', sortOrder: 10 },
        { ...baseItem, id: 'b', parentId: null, title: 'B', path: '/b', sortOrder: 20 },
      ],
    })
    expect(tree).toHaveLength(2)
    expect(tree.map((n) => n.id)).toEqual(['a', 'b'])
    expect(tree.every((n) => n.children.length === 0)).toBe(true)
  })

  it('nests children under their parent and preserves sort order', () => {
    const tree = buildMenuTree({
      nodes: [
        {
          ...baseItem,
          id: 'child-1',
          parentId: 'parent',
          title: 'Child1',
          path: '/c1',
          sortOrder: 11,
        },
        { ...baseItem, id: 'parent', parentId: null, title: 'Parent', path: '/p', sortOrder: 10 },
        {
          ...baseItem,
          id: 'child-2',
          parentId: 'parent',
          title: 'Child2',
          path: '/c2',
          sortOrder: 12,
        },
        { ...baseItem, id: 'other', parentId: null, title: 'Other', path: '/o', sortOrder: 20 },
      ],
    })
    expect(tree.map((n) => n.id)).toEqual(['parent', 'other'])
    expect(tree[0].children.map((n) => n.id)).toEqual(['child-1', 'child-2'])
    expect(tree[1].children).toEqual([])
  })

  it('treats unknown parentId as a root to avoid losing nodes', () => {
    const tree = buildMenuTree({
      nodes: [
        {
          ...baseItem,
          id: 'orphan',
          parentId: 'missing',
          title: 'Orphan',
          path: '/orphan',
          sortOrder: 5,
        },
      ],
    })
    expect(tree).toHaveLength(1)
    expect(tree[0].id).toBe('orphan')
  })
})

describe('resolveMenuIcon', () => {
  it('returns null when name is empty', () => {
    expect(resolveMenuIcon(null)).toBeNull()
    expect(resolveMenuIcon(undefined)).toBeNull()
  })

  it('returns null for unknown icon names', () => {
    expect(resolveMenuIcon('unknown-icon')).toBeNull()
  })

  it('resolves a known icon to a component', () => {
    const Icon = resolveMenuIcon('layout-dashboard')
    expect(Icon).not.toBeNull()
    expect(['function', 'object']).toContain(typeof Icon)
  })
})
