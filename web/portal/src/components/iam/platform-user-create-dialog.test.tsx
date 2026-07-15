import { setLanguage } from '@/i18n'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { render } from 'vitest-browser-react'
import { userEvent } from 'vitest/browser'
import { PlatformUserCreateDialog } from './platform-user-create-dialog'

const mocks = vi.hoisted(() => ({
  mutateAsync: vi.fn(),
}))

vi.mock('@/hooks/iam/use-platform-users', () => ({
  useCreatePlatformUser: () => ({
    mutateAsync: mocks.mutateAsync,
    isPending: false,
  }),
}))

vi.mock('@/hooks/iam/use-platform-roles', () => ({
  usePlatformRoles: () => ({ data: [] }),
}))

describe('PlatformUserCreateDialog', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    setLanguage('zh-CN')
  })

  afterEach(() => setLanguage('zh-CN'))

  it('在请求发出前展示必填校验并阻止创建', async () => {
    const screen = await render(
      <PlatformUserCreateDialog open onOpenChange={vi.fn()} />
    )

    await userEvent.click(screen.getByRole('button', { name: '创建' }))

    await expect
      .element(screen.getByText('请输入用户名（3-64 个字符）'))
      .toBeVisible()
    await expect.element(screen.getByText('请输入显示名')).toBeVisible()
    await expect
      .element(screen.getByText('请输入初始密码（8-128 个字符）'))
      .toBeVisible()
    expect(mocks.mutateAsync).not.toHaveBeenCalled()
  })

  it('邮箱留空时允许创建，并提交清理过的字段', async () => {
    mocks.mutateAsync.mockResolvedValueOnce({})
    const onOpenChange = vi.fn()
    const screen = await render(
      <PlatformUserCreateDialog open onOpenChange={onOpenChange} />
    )

    await userEvent.fill(screen.getByLabelText('用户名'), ' test-user ')
    await userEvent.fill(screen.getByLabelText('显示名'), ' 测试用户 ')
    await userEvent.fill(screen.getByLabelText('初始密码'), 'changeMe-9!')
    await userEvent.click(screen.getByRole('button', { name: '创建' }))

    expect(mocks.mutateAsync).toHaveBeenCalledOnce()
    expect(mocks.mutateAsync).toHaveBeenCalledWith({
      username: 'test-user',
      displayName: '测试用户',
      email: null,
      initialPassword: 'changeMe-9!',
      status: 'active',
      roleCodes: [],
    })
    expect(onOpenChange).toHaveBeenCalledWith(false)
  })

  it('邮箱有值但格式错误时在前端拦截', async () => {
    const screen = await render(
      <PlatformUserCreateDialog open onOpenChange={vi.fn()} />
    )

    await userEvent.fill(screen.getByLabelText('用户名'), 'test-user')
    await userEvent.fill(screen.getByLabelText('显示名'), '测试用户')
    await userEvent.fill(screen.getByLabelText('邮箱'), 'invalid-email')
    await userEvent.fill(screen.getByLabelText('初始密码'), 'changeMe-9!')
    await userEvent.click(screen.getByRole('button', { name: '创建' }))

    await expect.element(screen.getByText('请输入有效的邮箱地址')).toBeVisible()
    expect(mocks.mutateAsync).not.toHaveBeenCalled()
  })
})
