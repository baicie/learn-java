import { useQuery } from '@tanstack/react-query'
import { NavLink } from 'react-router-dom'

import { listPlatformMenus } from '../api/client'

export function AppLayout({ children }: { children: React.ReactNode }) {
  const menus = useQuery({ queryKey: ['platform', 'menus'], queryFn: listPlatformMenus })

  return (
    <div className="min-h-screen bg-background text-foreground">
      <aside className="fixed inset-y-0 left-0 w-60 border-r bg-card p-4">
        <div className="mb-6 text-lg font-semibold">AegisOps</div>
        <nav className="space-y-1">
          {(menus.data ?? []).map((item) => (
            <NavLink
              key={item.id}
              to={item.path}
              className={({ isActive }) =>
                `block rounded-md px-3 py-2 text-sm ${isActive ? 'bg-muted font-medium' : 'hover:bg-muted'}`
              }
            >
              {item.title}
            </NavLink>
          ))}
        </nav>
      </aside>
      <main className="ml-60 min-h-screen p-6">{children}</main>
    </div>
  )
}
