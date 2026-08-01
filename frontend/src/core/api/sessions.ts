import type { Session, SessionStatus, SessionType } from '../types'
import { db } from '../mock/db'
import { ApiError, clone, delay, uid } from './client'

export interface SessionInput {
  type: SessionType
  clientId: string
  monitorId?: string | null
  vehicleId?: string | null
  startTime: string
  endTime: string
  notes?: string | null
}

export interface SessionFilter {
  status?: SessionStatus
  type?: SessionType
  from?: string
  to?: string
  monitorId?: string
  clientId?: string
}

function find(id: string): Session {
  const row = db.sessions.find((s) => s.id === id)
  if (!row) throw new ApiError(404, 'Séance introuvable')
  return row
}

export const sessionsApi = {
  list(filter: SessionFilter = {}): Promise<Session[]> {
    const rows = db.sessions
      .filter(
        (s) =>
          (!filter.status || s.status === filter.status) &&
          (!filter.type || s.type === filter.type) &&
          (!filter.monitorId || s.monitorId === filter.monitorId) &&
          (!filter.clientId || s.clientId === filter.clientId) &&
          (!filter.from || s.startTime >= filter.from) &&
          (!filter.to || s.startTime <= filter.to),
      )
      .sort((a, b) => a.startTime.localeCompare(b.startTime))
    return delay(clone(rows))
  },

  get(id: string): Promise<Session> {
    return delay(clone(find(id)))
  },

  create(input: SessionInput): Promise<Session> {
    const auto = db.bookingSettings.autoValidationEnabled
    const session: Session = {
      id: uid('s'),
      status: auto ? 'CONFIRMED' : 'PENDING',
      monitorId: input.monitorId ?? null,
      vehicleId: input.vehicleId ?? null,
      notes: input.notes ?? null,
      type: input.type,
      clientId: input.clientId,
      startTime: input.startTime,
      endTime: input.endTime,
    }
    db.sessions.push(session)
    return delay(clone(session))
  },

  transition(id: string, status: SessionStatus): Promise<Session> {
    const row = find(id)
    row.status = status
    return delay(clone(row))
  },

  confirm: (id: string) => sessionsApi.transition(id, 'CONFIRMED'),
  refuse: (id: string) => sessionsApi.transition(id, 'REFUSED'),
  cancel: (id: string) => sessionsApi.transition(id, 'CANCELLED'),
  complete: (id: string) => sessionsApi.transition(id, 'COMPLETED'),

  reschedule(id: string, startTime: string, endTime: string): Promise<Session> {
    const row = find(id)
    row.startTime = startTime
    row.endTime = endTime
    row.status = 'PENDING'
    return delay(clone(row))
  },
}