import type {
  Enrollment,
  EnrollmentStatus,
  Forfait,
  Invoice,
  Payment,
  PaymentMethod,
} from '../types'
import { qs, request, requestBlob } from './client'

export interface EmailResult {
  sent: number
  skipped: number
}

// -- Forfaits ---------------------------------------------------------------
export interface ForfaitInput {
  name: string
  description: string
  drivingHours: number
  codeSessions: number
  price: number
  active: boolean
}

export const forfaitsApi = {
  list(activeOnly = false): Promise<Forfait[]> {
    return request<Forfait[]>(`/forfaits${qs({ activeOnly })}`)
  },
  get(id: string): Promise<Forfait> {
    return request<Forfait>(`/forfaits/${id}`)
  },
  create(input: ForfaitInput): Promise<Forfait> {
    return request<Forfait>('/forfaits', { method: 'POST', body: input })
  },
  update(id: string, input: Partial<ForfaitInput>): Promise<Forfait> {
    return request<Forfait>(`/forfaits/${id}`, { method: 'PUT', body: input })
  },
  remove(id: string): Promise<void> {
    return request<void>(`/forfaits/${id}`, { method: 'DELETE' })
  },
}

// -- Enrollments ------------------------------------------------------------
export const enrollmentsApi = {
  list(clientId?: string): Promise<Enrollment[]> {
    return request<Enrollment[]>(`/enrollments${qs({ clientId })}`)
  },
  get(id: string): Promise<Enrollment> {
    return request<Enrollment>(`/enrollments/${id}`)
  },
  create(clientId: string, forfaitId: string): Promise<Enrollment> {
    return request<Enrollment>('/enrollments', {
      method: 'POST',
      body: { clientId, forfaitId },
    })
  },
  update(id: string, input: { forfaitId: string; status: EnrollmentStatus }): Promise<Enrollment> {
    return request<Enrollment>(`/enrollments/${id}`, { method: 'PUT', body: input })
  },
  remove(id: string): Promise<void> {
    return request<void>(`/enrollments/${id}`, { method: 'DELETE' })
  },
}

// -- Payments ---------------------------------------------------------------
export interface PaymentInput {
  enrollmentId: string
  amount: number
  method: PaymentMethod
  reference?: string | null
}

export const paymentsApi = {
  list(clientId?: string): Promise<Payment[]> {
    return request<Payment[]>(`/payments${qs({ clientId })}`)
  },
  create(input: PaymentInput): Promise<Payment> {
    return request<Payment>('/payments', { method: 'POST', body: input })
  },
  update(id: string, input: Pick<PaymentInput, 'amount' | 'method' | 'reference'>): Promise<Payment> {
    return request<Payment>(`/payments/${id}`, { method: 'PUT', body: input })
  },
  remind(enrollmentId: string): Promise<void> {
    return request<void>(`/payments/${enrollmentId}/remind`, {
      method: 'POST',
      body: {},
    })
  },
  remove(id: string): Promise<void> {
    return request<void>(`/payments/${id}`, { method: 'DELETE' })
  },
}

// -- Invoices ---------------------------------------------------------------
export const invoicesApi = {
  list(clientId?: string): Promise<Invoice[]> {
    return request<Invoice[]>(`/invoices${qs({ clientId })}`)
  },

  /** PDF of a single document (staff, or the owning client). */
  pdf(id: string): Promise<Blob> {
    return requestBlob(`/invoices/${id}/pdf`)
  },

  /** ZIP archive of several documents' PDFs (staff only). */
  zip(ids: string[]): Promise<Blob> {
    return requestBlob(`/invoices/pdf${qs({ ids: ids.join(',') })}`)
  },

  /** Email documents to their clients, grouped per client (staff only). */
  email(ids: string[]): Promise<EmailResult> {
    return request<EmailResult>('/invoices/email', { method: 'POST', body: { ids } })
  },

  /** Void a single document (auditable soft delete, staff only). */
  remove(id: string): Promise<void> {
    return request<void>(`/invoices/${id}`, { method: 'DELETE' })
  },

  /** Void several documents at once (staff only). */
  removeMany(ids: string[]): Promise<void> {
    return request<void>(`/invoices${qs({ ids: ids.join(',') })}`, { method: 'DELETE' })
  },
}