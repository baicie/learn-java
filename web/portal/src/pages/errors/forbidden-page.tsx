import { Link } from '@tanstack/react-router'

export function ForbiddenPage() {
  return (
    <main className='grid min-h-[60vh] place-items-center p-6'>
      <div className='text-center'>
        <div className='text-6xl font-semibold'>403</div>

        <h1 className='mt-4 text-xl font-medium'>无权访问</h1>

        <p className='mt-2 text-sm text-muted-foreground'>
          当前账号没有访问该页面所需的权限。
        </p>

        <Link className='mt-4 inline-block text-primary underline' to='/'>
          返回首页
        </Link>
      </div>
    </main>
  )
}
