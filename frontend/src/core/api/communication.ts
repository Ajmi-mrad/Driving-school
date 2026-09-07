import type { Conversation, Message, Notification, Page } from '../types'
import { mapPage, qs, request, type SpringPage } from './client'

// -- Backend shapes that differ from the frontend types --------------------
interface UnreadCountResponse {
  count: number
}

interface PresenceResponse {
  online: string[]
}

interface ConversationResponse {
  id: string
  monitorId: string
  clientId: string
  lastMessageAt: string
  lastMessagePreview: string
}

interface MessageResponse {
  id: string
  conversationId: string
  senderId: string
  type: string
  content: string
  sentAt: string
  readAt: string | null
}

function toConversation(res: ConversationResponse): Conversation {
  // Per-conversation unread is not exposed by the list endpoint.
  return { ...res, unreadCount: 0 }
}

function toMessage(res: MessageResponse): Message {
  return {
    id: res.id,
    conversationId: res.conversationId,
    senderId: res.senderId,
    content: res.content,
    sentAt: res.sentAt,
    readAt: res.readAt,
  }
}

export const conversationsApi = {
  /** Inbox for the current user (derived from the JWT server-side). */
  async list(_userId: string): Promise<Conversation[]> {
    const rows = await request<ConversationResponse[]>('/conversations')
    return rows.map(toConversation)
  },

  async getOrCreate(_userId: string, counterpartId: string): Promise<Conversation> {
    const res = await request<ConversationResponse>('/conversations', {
      method: 'POST',
      body: { counterpartId },
    })
    return toConversation(res)
  },

  async unreadCount(_userId: string): Promise<number> {
    const res = await request<UnreadCountResponse>('/conversations/unread-count')
    return res.count
  },

  async presence(ids: string[]): Promise<string[]> {
    if (ids.length === 0) return []
    const res = await request<PresenceResponse>(
      `/conversations/presence${qs({ ids: ids.join(',') })}`,
    )
    return res.online
  },

  async messages(conversationId: string, page = 0, size = 30): Promise<Page<Message>> {
    const raw = await request<SpringPage<MessageResponse>>(
      `/conversations/${conversationId}/messages${qs({ page, size })}`,
    )
    return mapPage(raw, toMessage)
  },

  /** Send a message. The server derives the sender from the JWT; `_senderId` is ignored. */
  async send(conversationId: string, _senderId: string, content: string): Promise<Message> {
    const res = await request<MessageResponse>(`/conversations/${conversationId}/messages`, {
      method: 'POST',
      body: { content },
    })
    return toMessage(res)
  },

  markRead(conversationId: string): Promise<void> {
    return request<void>(`/conversations/${conversationId}/read`, { method: 'PATCH' })
  },
}

export const notificationsApi = {
  async list(userId: string, page = 0, size = 20): Promise<Page<Notification>> {
    const raw = await request<SpringPage<NotificationRow>>(
      `/notifications${qs({ page, size })}`,
    )
    return mapPage(raw, (row) => toNotification(row, userId))
  },

  async unreadCount(_userId: string): Promise<number> {
    const res = await request<UnreadCountResponse>('/notifications/unread-count')
    return res.count
  },

  markRead(id: string): Promise<void> {
    return request<void>(`/notifications/${id}/read`, { method: 'PATCH' })
  },

  markAllRead(_userId: string): Promise<void> {
    return request<void>('/notifications/read-all', { method: 'PATCH' })
  },
}

/** Backend NotificationResponse; `recipientId` is implicit (the current user). */
interface NotificationRow {
  id: string
  type: Notification['type']
  title: string
  body: string
  referenceId: string | null
  amount: number | null
  readAt: string | null
  createdAt: string
}

function toNotification(row: NotificationRow, recipientId: string): Notification {
  return { ...row, recipientId }
}