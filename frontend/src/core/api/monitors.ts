import type { AvailabilityRule, FreeSlot, TimeOff } from '../types'
import { qs, request } from './client'

export interface TimeOffInput {
  startTime: string // ISO
  endTime: string // ISO
  reason?: string | null
}

export interface FreeSlotFilter {
  from: string // ISO
  to: string // ISO
  slotMinutes?: number
}

/**
 * Monitor availability: recurring weekly working hours, one-off absences, and
 * computed bookable slots (working hours − booked sessions − time-off). Staff
 * manage any monitor; a monitor manages their own (enforced server-side).
 */
export const monitorsApi = {
  getAvailability(monitorId: string): Promise<AvailabilityRule[]> {
    return request<AvailabilityRule[]>(`/monitors/${monitorId}/availability`)
  },

  /** Replace the full set of weekly working hours (idempotent PUT). */
  replaceAvailability(monitorId: string, rules: AvailabilityRule[]): Promise<AvailabilityRule[]> {
    return request<AvailabilityRule[]>(`/monitors/${monitorId}/availability`, {
      method: 'PUT',
      body: { rules },
    })
  },

  listTimeOff(monitorId: string): Promise<TimeOff[]> {
    return request<TimeOff[]>(`/monitors/${monitorId}/time-off`)
  },

  createTimeOff(monitorId: string, input: TimeOffInput): Promise<TimeOff> {
    return request<TimeOff>(`/monitors/${monitorId}/time-off`, { method: 'POST', body: input })
  },

  deleteTimeOff(monitorId: string, timeOffId: string): Promise<void> {
    return request<void>(`/monitors/${monitorId}/time-off/${timeOffId}`, { method: 'DELETE' })
  },

  /** Computed bookable slots over [from, to). Consumed by booking and the AI scheduler. */
  freeSlots(monitorId: string, filter: FreeSlotFilter): Promise<FreeSlot[]> {
    return request<FreeSlot[]>(`/monitors/${monitorId}/free-slots${qs(filter)}`)
  },
}
