import { Link } from 'react-router-dom'
import { useTranslation } from 'react-i18next'
import { LogOut, Menu, MessageSquare } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { Avatar, AvatarFallback } from '@/components/ui/avatar'
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuLabel,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from '@/components/ui/dropdown-menu'
import { Sheet, SheetContent, SheetTitle, SheetTrigger } from '@/components/ui/sheet'
import { Brand, SidebarNav } from './Sidebar'
import { LanguageSwitcher } from './LanguageSwitcher'
import { NotificationsBell } from '@/components/notifications/NotificationsBell'
import { useAuth } from '@/core/auth/AuthContext'
import { useAsync } from '@/core/hooks/useAsync'
import { conversationsApi } from '@/core/api'
import { permissions } from '@/core/auth/roles'
import { fullName, initials } from '@/core/format'

function CountBadge({ count }: { count: number }) {
  if (count <= 0) return null
  return (
    <span className="absolute -right-1 -top-1 flex size-4 items-center justify-center rounded-full bg-destructive text-[10px] font-semibold text-destructive-foreground">
      {count > 9 ? '9+' : count}
    </span>
  )
}

export function Header() {
  const { t } = useTranslation()
  const { user, roles, logout } = useAuth()
  const { data: msgCount } = useAsync(
    () => (user ? conversationsApi.unreadCount(user.id) : Promise.resolve(0)),
    [user?.id],
  )

  if (!user) return null

  return (
    <header className="sticky top-0 z-30 flex h-14 items-center gap-2 border-b bg-background px-3 sm:px-4">
      {/* Mobile nav */}
      <Sheet>
        <SheetTrigger asChild>
          <Button variant="ghost" size="icon" className="md:hidden">
            <Menu className="size-5" />
          </Button>
        </SheetTrigger>
        <SheetContent side="left" className="w-64 p-0">
          <SheetTitle className="sr-only">{t('nav.navigation')}</SheetTitle>
          <Brand />
          <div className="border-t" />
          <SidebarNav />
        </SheetContent>
      </Sheet>

      <div className="flex-1" />

      <LanguageSwitcher />

      {permissions.chat(roles) && (
        <Button asChild variant="ghost" size="icon" className="relative">
          <Link to="/messages" aria-label={t('nav.messages')}>
            <MessageSquare className="size-5" />
            <CountBadge count={msgCount ?? 0} />
          </Link>
        </Button>
      )}

      <NotificationsBell />

      <DropdownMenu>
        <DropdownMenuTrigger asChild>
          <Button variant="ghost" className="gap-2 px-2">
            <Avatar className="size-7">
              <AvatarFallback className="bg-primary/10 text-xs text-primary">
                {initials(user)}
              </AvatarFallback>
            </Avatar>
            <span className="hidden text-sm font-medium sm:inline">
              {fullName(user)}
            </span>
          </Button>
        </DropdownMenuTrigger>
        <DropdownMenuContent align="end" className="w-56">
          <DropdownMenuLabel>
            <p className="font-medium">{fullName(user)}</p>
            <p className="text-xs font-normal text-muted-foreground">{user.email}</p>
            <p className="mt-1 text-xs font-normal text-primary">
              {roles.map((r) => t(`enums.role.${r}`)).join(', ')}
            </p>
          </DropdownMenuLabel>
          <DropdownMenuSeparator />
          <DropdownMenuItem onClick={logout} className="text-destructive">
            <LogOut className="size-4" />
            {t('auth.logout')}
          </DropdownMenuItem>
        </DropdownMenuContent>
      </DropdownMenu>
    </header>
  )
}