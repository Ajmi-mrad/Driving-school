import { useTranslation } from 'react-i18next'
import { CheckCheck, MessageSquare, Wallet } from 'lucide-react'
import { PageHeader } from '@/components/common/PageHeader'
import { Button } from '@/components/ui/button'
import { Skeleton } from '@/components/ui/skeleton'
import { cn } from '@/lib/utils'
import { useAuth } from '@/core/auth/AuthContext'
import { useAsync } from '@/core/hooks/useAsync'
import { useRevalidateOnFocus } from '@/hooks/useRevalidateOnFocus'
import { notificationsApi } from '@/core/api'
import type { Notification } from '@/core/types'
import { formatRelativeShort } from '@/core/format'

const ICON = {
  NEW_MESSAGE: MessageSquare,
  PAYMENT_DUE: Wallet,
} as const

export function NotificationsPage() {
  const { t } = useTranslation()
  const { user } = useAuth()

  const { data, loading, reload, refresh } = useAsync(
    () => (user ? notificationsApi.list(user.id, 0, 50) : Promise.resolve(null)),
    [user?.id],
  )
  useRevalidateOnFocus(refresh)
  const rows = data?.content ?? []
  const hasUnread = rows.some((n) => !n.readAt)

  const markRead = async (n: Notification) => {
    if (n.readAt) return
    await notificationsApi.markRead(n.id)
    reload()
  }

  const markAll = async () => {
    if (!user) return
    await notificationsApi.markAllRead(user.id)
    reload()
  }

  return (
    <div className="space-y-6">
      <PageHeader
        title={t('nav.notifications')}
        description={t('notifications.subtitle')}
        actions={
          hasUnread && (
            <Button variant="outline" onClick={markAll}>
              <CheckCheck className="size-4" />
              {t('notifications.markAllRead')}
            </Button>
          )
        }
      />

      {loading ? (
        <div className="space-y-2">
          {Array.from({ length: 4 }).map((_, i) => (
            <Skeleton key={i} className="h-16 rounded-xl" />
          ))}
        </div>
      ) : rows.length === 0 ? (
        <p className="py-16 text-center text-muted-foreground">
          {t('notifications.empty')}
        </p>
      ) : (
        <ul className="space-y-2">
          {rows.map((n) => {
            const Icon = ICON[n.type]
            return (
              <li key={n.id}>
                <button
                  type="button"
                  onClick={() => markRead(n)}
                  className={cn(
                    'flex w-full items-start gap-3 rounded-xl border p-4 text-start transition-colors hover:bg-accent',
                    !n.readAt && 'border-primary/30 bg-primary/5',
                  )}
                >
                  <div className="flex size-9 shrink-0 items-center justify-center rounded-lg bg-primary/10 text-primary">
                    <Icon className="size-4" />
                  </div>
                  <div className="min-w-0 flex-1">
                    <div className="flex items-center justify-between gap-2">
                      <p className="font-medium">{n.title}</p>
                      <span className="shrink-0 text-xs text-muted-foreground">
                        {formatRelativeShort(n.createdAt)}
                      </span>
                    </div>
                    <p className="text-sm text-muted-foreground">{n.body}</p>
                  </div>
                  {!n.readAt && (
                    <span className="mt-1 size-2 shrink-0 rounded-full bg-primary" />
                  )}
                </button>
              </li>
            )
          })}
        </ul>
      )}
    </div>
  )
}
