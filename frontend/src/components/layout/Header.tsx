import { Link, useLocation } from 'react-router-dom'
import { useTranslation } from 'react-i18next'
import { MessageSquare } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { Separator } from '@/components/ui/separator'
import { SidebarTrigger } from '@/components/ui/sidebar'
import { LanguageSwitcher } from './LanguageSwitcher'
import { ModeToggle } from './ModeToggle'
import { NotificationsBell } from '@/components/notifications/NotificationsBell'
import { NAV_ITEMS } from '@/config/nav'
import { useAuth } from '@/core/auth/AuthContext'
import { useAsync } from '@/core/hooks/useAsync'
import { conversationsApi } from '@/core/api'
import { permissions } from '@/core/auth/roles'

function CountBadge({ count }: { count: number }) {
  if (count <= 0) return null
  return (
    <span className="absolute -end-1 -top-1 flex size-4 items-center justify-center rounded-full bg-destructive text-[10px] font-semibold text-destructive-foreground">
      {count > 9 ? '9+' : count}
    </span>
  )
}

function usePageTitle(): string {
  const { pathname } = useLocation()
  // Pick the most specific matching nav item (longest `to`).
  const match = [...NAV_ITEMS]
    .filter((i) => (i.to === '/' ? pathname === '/' : pathname.startsWith(i.to)))
    .sort((a, b) => b.to.length - a.to.length)[0]
  return match?.labelKey ?? 'nav.dashboard'
}

export function Header() {
  const { t } = useTranslation()
  const { user, roles } = useAuth()
  const titleKey = usePageTitle()
  const { data: msgCount } = useAsync(
    () => (user ? conversationsApi.unreadCount(user.id) : Promise.resolve(0)),
    [user?.id],
  )

  if (!user) return null

  return (
    <header className="sticky top-0 z-30 flex h-14 shrink-0 items-center gap-2 border-b bg-background/95 px-3 backdrop-blur supports-[backdrop-filter]:bg-background/60 sm:px-4">
      <SidebarTrigger className="-ms-1" />
      <Separator orientation="vertical" className="me-1 h-5" />
      <span className="text-sm font-semibold sm:text-base">{t(titleKey)}</span>

      <div className="flex-1" />

      <LanguageSwitcher />
      <ModeToggle />

      {permissions.chat(roles) && (
        <Button asChild variant="ghost" size="icon" className="relative">
          <Link to="/messages" aria-label={t('nav.messages')}>
            <MessageSquare className="size-5" />
            <CountBadge count={msgCount ?? 0} />
          </Link>
        </Button>
      )}

      <NotificationsBell />
    </header>
  )
}