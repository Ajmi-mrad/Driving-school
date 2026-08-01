import { NavLink } from 'react-router-dom'
import { useTranslation } from 'react-i18next'
import { Car } from 'lucide-react'
import { cn } from '@/lib/utils'
import { NAV_ITEMS } from '@/config/nav'
import { useAuth } from '@/core/auth/AuthContext'

export function Brand() {
  const { t } = useTranslation()
  return (
    <div className="flex h-14 items-center gap-2 px-4">
      <div className="flex size-8 items-center justify-center rounded-lg bg-primary text-primary-foreground">
        <Car className="size-5" />
      </div>
      <div className="leading-tight">
        <p className="text-sm font-semibold">{t('common.appName')}</p>
        <p className="text-xs text-muted-foreground">{t('common.appSubtitle')}</p>
      </div>
    </div>
  )
}

export function SidebarNav({ onNavigate }: { onNavigate?: () => void }) {
  const { t } = useTranslation()
  const { roles } = useAuth()
  const items = NAV_ITEMS.filter((item) => item.visible(roles))

  return (
    <nav className="flex flex-col gap-1 px-2 py-2">
      {items.map(({ to, labelKey, icon: Icon }) => (
        <NavLink
          key={to}
          to={to}
          end={to === '/'}
          onClick={onNavigate}
          className={({ isActive }) =>
            cn(
              'flex items-center gap-3 rounded-md px-3 py-2 text-sm font-medium transition-colors',
              isActive
                ? 'bg-sidebar-primary text-sidebar-primary-foreground'
                : 'text-sidebar-foreground/80 hover:bg-sidebar-accent hover:text-sidebar-accent-foreground',
            )
          }
        >
          <Icon className="size-4 shrink-0" />
          {t(labelKey)}
        </NavLink>
      ))}
    </nav>
  )
}

export function DesktopSidebar() {
  return (
    <aside className="hidden w-64 shrink-0 border-r bg-sidebar md:flex md:flex-col">
      <Brand />
      <div className="border-t" />
      <SidebarNav />
    </aside>
  )
}