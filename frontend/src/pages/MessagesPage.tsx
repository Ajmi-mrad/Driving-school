import { useEffect, useMemo, useRef, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { ArrowLeft, Send } from 'lucide-react'
import { PageHeader } from '@/components/common/PageHeader'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Avatar, AvatarFallback } from '@/components/ui/avatar'
import { Skeleton } from '@/components/ui/skeleton'
import { cn } from '@/lib/utils'
import { useAuth } from '@/core/auth/AuthContext'
import { useAsync } from '@/core/hooks/useAsync'
import { conversationsApi, usersApi } from '@/core/api'
import type { Conversation, User } from '@/core/types'
import { fullName, initials, formatTime, formatRelativeShort } from '@/core/format'

export function MessagesPage() {
  const { t } = useTranslation()
  const { user } = useAuth()
  const [selectedId, setSelectedId] = useState<string | null>(null)
  const [draft, setDraft] = useState('')
  const endRef = useRef<HTMLDivElement>(null)

  const { data: conversations, reload: reloadConvs } = useAsync(
    () => (user ? conversationsApi.list(user.id) : Promise.resolve([])),
    [user?.id],
  )
  const { data: users } = useAsync(() => usersApi.list(), [])

  const userMap = useMemo(
    () => new Map((users ?? []).map((u) => [u.id, u])),
    [users],
  )

  const counterpartId = (c: Conversation) =>
    c.monitorId === user?.id ? c.clientId : c.monitorId

  const { data: presence } = useAsync(
    () =>
      conversations && conversations.length
        ? conversationsApi.presence(conversations.map(counterpartId))
        : Promise.resolve([]),
    [conversations],
  )
  const onlineSet = useMemo(() => new Set(presence ?? []), [presence])

  const { data: page, loading: loadingMsgs, reload: reloadMsgs } = useAsync(
    () =>
      selectedId
        ? conversationsApi.messages(selectedId, 0, 50)
        : Promise.resolve(null),
    [selectedId],
  )
  const messages = page?.content ?? []

  // Mark read when opening a conversation.
  useEffect(() => {
    if (selectedId) {
      void conversationsApi.markRead(selectedId).then(reloadConvs)
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [selectedId])

  useEffect(() => {
    endRef.current?.scrollIntoView({ block: 'end' })
  }, [messages.length])

  const selected = conversations?.find((c) => c.id === selectedId) ?? null

  const send = async () => {
    if (!selectedId || !user || !draft.trim()) return
    await conversationsApi.send(selectedId, user.id, draft.trim())
    setDraft('')
    reloadMsgs()
    reloadConvs()
  }

  const partner = (c: Conversation): User | undefined => userMap.get(counterpartId(c))

  return (
    <div className="space-y-6">
      <PageHeader title={t('nav.messages')} description={t('messages.subtitle')} />

      <div className="grid h-[calc(100svh-16rem)] grid-cols-1 overflow-hidden rounded-xl border bg-card md:grid-cols-[20rem_1fr]">
        {/* Conversation list */}
        <div
          className={cn(
            'flex flex-col border-e',
            selectedId && 'hidden md:flex',
          )}
        >
          {(conversations ?? []).length === 0 ? (
            <p className="p-6 text-center text-sm text-muted-foreground">
              {t('messages.empty')}
            </p>
          ) : (
            <ul className="divide-y overflow-y-auto">
              {(conversations ?? []).map((c) => {
                const p = partner(c)
                const online = onlineSet.has(counterpartId(c))
                return (
                  <li key={c.id}>
                    <button
                      type="button"
                      onClick={() => setSelectedId(c.id)}
                      className={cn(
                        'flex w-full items-center gap-3 p-3 text-start hover:bg-accent',
                        selectedId === c.id && 'bg-accent',
                      )}
                    >
                      <div className="relative">
                        <Avatar className="size-9">
                          <AvatarFallback className="bg-primary/10 text-xs text-primary">
                            {p ? initials(p) : '?'}
                          </AvatarFallback>
                        </Avatar>
                        {online && (
                          <span className="absolute -end-0.5 -bottom-0.5 size-3 rounded-full border-2 border-card bg-success" />
                        )}
                      </div>
                      <div className="min-w-0 flex-1">
                        <div className="flex items-center justify-between gap-2">
                          <span className="truncate font-medium">
                            {p ? fullName(p) : '—'}
                          </span>
                          <span className="shrink-0 text-xs text-muted-foreground">
                            {formatRelativeShort(c.lastMessageAt)}
                          </span>
                        </div>
                        <p className="truncate text-sm text-muted-foreground">
                          {c.lastMessagePreview}
                        </p>
                      </div>
                      {c.unreadCount > 0 && (
                        <span className="flex size-5 shrink-0 items-center justify-center rounded-full bg-primary text-[10px] font-semibold text-primary-foreground">
                          {c.unreadCount}
                        </span>
                      )}
                    </button>
                  </li>
                )
              })}
            </ul>
          )}
        </div>

        {/* Thread */}
        <div className={cn('flex flex-col', !selectedId && 'hidden md:flex')}>
          {!selected ? (
            <div className="flex flex-1 items-center justify-center p-6 text-sm text-muted-foreground">
              {t('messages.selectConversation')}
            </div>
          ) : (
            <>
              <div className="flex items-center gap-3 border-b p-3">
                <Button
                  variant="ghost"
                  size="icon"
                  className="md:hidden"
                  onClick={() => setSelectedId(null)}
                >
                  <ArrowLeft className="size-4 rtl:rotate-180" />
                </Button>
                <Avatar className="size-8">
                  <AvatarFallback className="bg-primary/10 text-xs text-primary">
                    {partner(selected) ? initials(partner(selected)!) : '?'}
                  </AvatarFallback>
                </Avatar>
                <div>
                  <p className="font-medium leading-tight">
                    {partner(selected) ? fullName(partner(selected)!) : '—'}
                  </p>
                  <p className="text-xs text-muted-foreground">
                    {onlineSet.has(counterpartId(selected))
                      ? t('messages.online')
                      : t('messages.offline')}
                  </p>
                </div>
              </div>

              <div className="flex-1 space-y-2 overflow-y-auto p-4">
                {loadingMsgs ? (
                  <Skeleton className="h-16 w-2/3" />
                ) : messages.length === 0 ? (
                  <p className="py-8 text-center text-sm text-muted-foreground">
                    {t('messages.noMessages')}
                  </p>
                ) : (
                  messages.map((m) => {
                    const mine = m.senderId === user?.id
                    return (
                      <div
                        key={m.id}
                        className={cn('flex', mine ? 'justify-end' : 'justify-start')}
                      >
                        <div
                          className={cn(
                            'max-w-[75%] rounded-2xl px-3 py-2 text-sm',
                            mine
                              ? 'bg-primary text-primary-foreground'
                              : 'bg-muted',
                          )}
                        >
                          <p>{m.content}</p>
                          <p
                            className={cn(
                              'mt-0.5 text-[10px]',
                              mine ? 'text-primary-foreground/70' : 'text-muted-foreground',
                            )}
                          >
                            {formatTime(m.sentAt)}
                          </p>
                        </div>
                      </div>
                    )
                  })
                )}
                <div ref={endRef} />
              </div>

              <form
                className="flex items-center gap-2 border-t p-3"
                onSubmit={(e) => {
                  e.preventDefault()
                  void send()
                }}
              >
                <Input
                  value={draft}
                  onChange={(e) => setDraft(e.target.value)}
                  placeholder={t('messages.placeholder')}
                />
                <Button type="submit" size="icon" disabled={!draft.trim()}>
                  <Send className="size-4 rtl:rotate-180" />
                </Button>
              </form>
            </>
          )}
        </div>
      </div>
    </div>
  )
}
