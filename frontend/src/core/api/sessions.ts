import type { Session, SessionStatus, SessionType } from '../types'
import { ApiError, qs, request } from './client'

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

/** Status → the PATCH action endpoint that produces it. */
const ACTION_BY_STATUS: Partial<Record<SessionStatus, string>> = {
  CONFIRMED: 'confirm',
  REFUSED: 'refuse',
  CANCELLED: 'cancel',
  COMPLETED: 'complete',
}

export const sessionsApi = {
  list(filter: SessionFilter = {}): Promise<Session[]> {
    return request<Session[]>(`/sessions${qs(filter)}`)
  },

  get(id: string): Promise<Session> {
    return request<Session>(`/sessions/${id}`)
  },

  create(input: SessionInput): Promise<Session> {
    return request<Session>('/sessions', { method: 'POST', body: input })
  },

  transition(id: string, status: SessionStatus, comment?: string): Promise<Session> {
    const action = ACTION_BY_STATUS[status]
    if (!action) throw new ApiError(400, `Transition non supportée: ${status}`)
    return request<Session>(`/sessions/${id}/${action}`, {
      method: 'PATCH',
      body: comment ? { comment } : undefined,
    })
  },

  /** Confirme une demande, avec une note facultative pour l'élève. */
  confirm: (id: string, comment?: string) => sessionsApi.transition(id, 'CONFIRMED', comment),
  /** Refuse une demande, avec une note facultative (ex. créneaux proposés). */
  refuse: (id: string, comment?: string) => sessionsApi.transition(id, 'REFUSED', comment),
  cancel: (id: string) => sessionsApi.transition(id, 'CANCELLED'),
  complete: (id: string) => sessionsApi.transition(id, 'COMPLETED'),

  reschedule(id: string, startTime: string, endTime: string): Promise<Session> {
    return request<Session>(`/sessions/${id}/reschedule`, {
      method: 'PATCH',
      body: { startTime, endTime },
    })
  },
}