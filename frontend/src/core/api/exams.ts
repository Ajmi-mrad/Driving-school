import type { Exam, ExamOutcome, ExamStatus, ExamType } from '../types'
import { qs, request } from './client'

export interface ExamInput {
  type: ExamType
  clientId: string
  monitorId?: string | null
  vehicleId?: string | null
  scheduledAt: string
  location?: string | null
}

export interface ExamFilter {
  status?: ExamStatus
  type?: ExamType
  from?: string
  to?: string
  monitorId?: string
  clientId?: string
}

export const examsApi = {
  list(filter: ExamFilter = {}): Promise<Exam[]> {
    return request<Exam[]>(`/exams${qs(filter)}`)
  },

  get(id: string): Promise<Exam> {
    return request<Exam>(`/exams/${id}`)
  },

  /** Schedule an exam (staff only). */
  create(input: ExamInput): Promise<Exam> {
    return request<Exam>('/exams', { method: 'POST', body: input })
  },

  /** Record a terminal result (PASSED / FAILED / NO_SHOW) with an optional note. */
  recordResult(id: string, outcome: ExamOutcome, note?: string): Promise<Exam> {
    return request<Exam>(`/exams/${id}/result`, {
      method: 'PATCH',
      body: { outcome, note: note ?? null },
    })
  },

  reschedule(id: string, scheduledAt: string): Promise<Exam> {
    return request<Exam>(`/exams/${id}/reschedule`, {
      method: 'PATCH',
      body: { scheduledAt },
    })
  },

  cancel(id: string): Promise<Exam> {
    return request<Exam>(`/exams/${id}/cancel`, { method: 'PATCH' })
  },
}
