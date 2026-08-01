import type { Conversation, Message, Notification, Page } from '../types'
import { db, onlineUserIds } from '../mock/db'
import { ApiError, clone, delay, uid } from './client'

function paginate<T>(rows: T[], page: number, size: number): Page<T> {
  const start = page * size
  return {
    content: rows.slice(start, start + size),
    page,
    size,
    totalElements: rows.length,
    totalPages: Math.max(1, Math.ceil(rows.length / size)),
  }
}

export const conversationsApi = {
  /** Inbox for the current user (monitor or client). */
  list(userId: string): Promise<Conversation[]> {
    const rows = db.conversations
      .filter((c) => c.monitorId === userId || c.clientId === userId)
      .sort((a, b) => b.lastMessageAt.localeCompare(a.lastMessageAt))
    return delay(clone(rows))
  },

  getOrCreate(userId: string, counterpartId: string): Promise<Conversation> {
    let row = db.conversations.find(
      (c) =>
        (c.monitorId === userId && c.clientId === counterpartId) ||
        (c.clientId === userId && c.monitorId === counterpartId),
    )
    if (!row) {
      row = {
        id: uid('c'),
        monitorId: userId,
        clientId: counterpartId,
        lastMessageAt: new Date().toISOString(),
        lastMessagePreview: '',
        unreadCount: 0,
      }
      db.conversations.push(row)
    }
    return delay(clone(row))
  },

  unreadCount(userId: string): Promise<number> {
    const count = db.conversations
      .filter((c) => c.monitorId === userId || c.clientId === userId)
      .reduce((sum, c) => sum + c.unreadCount, 0)
    return delay(count)
  },

  presence(ids: string[]): Promise<string[]> {
    return delay(ids.filter((id) => onlineUserIds.has(id)))
  },

  messages(conversationId: string, page = 0, size = 30): Promise<Page<Message>> {
    const rows = db.messages
      .filter((m) => m.conversationId === conversationId)
      .sort((a, b) => a.sentAt.localeCompare(b.sentAt))
    return delay(clone(paginate(rows, page, size)))
  },

  send(conversationId: string, senderId: string, content: string): Promise<Message> {
    const conv = db.conversations.find((c) => c.id === conversationId)
    if (!conv) throw new ApiError(404, 'Conversation introuvable')
    const message: Message = {
      id: uid('msg'),
      conversationId,
      senderId,
      content,
      sentAt: new Date().toISOString(),
      readAt: null,
    }
    db.messages.push(message)
    conv.lastMessageAt = message.sentAt
    conv.lastMessagePreview = content
    return delay(clone(message), 150)
  },

  markRead(conversationId: string): Promise<void> {
    const conv = db.conversations.find((c) => c.id === conversationId)
    if (conv) conv.unreadCount = 0
    return delay(undefined)
  },
}

export const notificationsApi = {
  list(userId: string, page = 0, size = 20): Promise<Page<Notification>> {
    const rows = db.notifications
      .filter((n) => n.recipientId === userId)
      .sort((a, b) => b.createdAt.localeCompare(a.createdAt))
    return delay(clone(paginate(rows, page, size)))
  },

  unreadCount(userId: string): Promise<number> {
    const count = db.notifications.filter(
      (n) => n.recipientId === userId && !n.readAt,
    ).length
    return delay(count)
  },

  markRead(id: string): Promise<void> {
    const row = db.notifications.find((n) => n.id === id)
    if (row && !row.readAt) row.readAt = new Date().toISOString()
    return delay(undefined)
  },

  markAllRead(userId: string): Promise<void> {
    db.notifications
      .filter((n) => n.recipientId === userId && !n.readAt)
      .forEach((n) => (n.readAt = new Date().toISOString()))
    return delay(undefined)
  },
}