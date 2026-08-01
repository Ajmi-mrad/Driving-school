import { useNavigate } from 'react-router-dom'
import { useTranslation } from 'react-i18next'
import { Bell, MessageSquare, Wallet } from 'lucide-react'
import { Button } from '@/components/ui/button'
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuLabel,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from '@/components/ui/dropdown-menu'
import { cn } from '@/lib/utils'
import { useAuth } from '@/core/auth/AuthContext'
import { useAsync } from '@/core/hooks/useAsync'
import { notificationsApi } from '@/core/api'
import { formatRelativeShort } from '@/core/format'

const ICON = { NEW_MESSAGE: MessageSquare, PAYMENT_DUE: Wallet } as const

export function NotificationsBell() {
  const { t } = useTranslation()
  const { user } = useAuth()
  const navigate = useNavigate()

  const { data, reload } = useAsync(
    () => (user ? notificationsApi.list(user.id, 0, 6) : Promise.resolve(null)),
    [user?.id],
  )
  const rows = data?.content ?? []
  const unread = rows.filter((n) => !n.readAt).length

  const open = async (id: string) => {
    await notificationsApi.markRead(id)
    reload()
    navigate('/notifications')
  }

  return (
    <DropdownMenu>
      <DropdownMenuTrigger asChild>
        <Button variant="ghost" size="icon" className="relative" aria-label={t('nav.notifications')}>
          <Bell className="size-5" />
          {unread > 0 && (
            <span className="absolute -end-1 -top-1 flex size-4 items-center justify-center rounded-full bg-destructive text-[10px] font-semibold text-destructive-foreground">
              {unread > 9 ? '9+' : unread}
            </span>
          )}
        </Button>
      </DropdownMenuTrigger>
      <DropdownMenuContent align="end" className="w-80">
        <DropdownMenuLabel>{t('notifications.recent')}</DropdownMenuLabel>
        <DropdownMenuSeparator />
        {rows.length === 0 ? (
          <p className="px-2 py-6 text-center text-sm text-muted-foreground">
            {t('notifications.empty')}
          </p>
        ) : (
          rows.map((n) => {
            const Icon = ICON[n.type]
            return (
              <DropdownMenuItem
                key={n.id}
                onClick={() => open(n.id)}
                className="items-start gap-2"
              >
                <Icon className="mt-0.5 size-4 shrink-0 text-primary" />
                <div className="min-w-0 flex-1">
                  <p className={cn('truncate text-sm', !n.readAt && 'font-medium')}>
                    {n.title}
                  </p>
                  <p className="truncate text-xs text-muted-foreground">{n.body}</p>
                </div>
                <span className="shrink-0 text-[10px] text-muted-foreground">
                  {formatRelativeShort(n.createdAt)}
                </span>
              </DropdownMenuItem>
            )
          })
        )}
        <DropdownMenuSeparator />
        <DropdownMenuItem
          onClick={() => navigate('/notifications')}
          className="justify-center text-sm font-medium text-primary"
        >
          {t('notifications.viewAll')}
        </DropdownMenuItem>
      </DropdownMenuContent>
    </DropdownMenu>
  )
}
