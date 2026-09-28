import type { AuditEvent } from '../types'
import { qs, request } from './client'

export interface AuditFilters {
  action?: string
  entityType?: string
  limit?: number
}

/**
 * Owner-only audit trail. Finance events live in finance-service (`/audit`) and
 * account events in auth-service (`/users/audit`); each service owns its own log
 * in its own DB, so we fetch both feeds in parallel and merge them by time.
 */
export const auditApi = {
  async list(filters: AuditFilters = {}): Promise<AuditEvent[]> {
    const query = qs(filters as Record<string, string | number>)
    const [finance, users] = await Promise.all([
      request<AuditEvent[]>(`/audit${query}`),
      request<AuditEvent[]>(`/users/audit${query}`),
    ])
    return [...finance, ...users].sort(
      (a, b) => new Date(b.occurredAt).getTime() - new Date(a.occurredAt).getTime(),
    )
  },
}