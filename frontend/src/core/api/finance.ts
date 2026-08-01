import type {
  Enrollment,
  Forfait,
  Invoice,
  Payment,
  PaymentMethod,
} from '../types'
import { db } from '../mock/db'
import { ApiError, clone, delay, uid } from './client'

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
    const rows = db.forfaits.filter((f) => !activeOnly || f.active)
    return delay(clone(rows))
  },
  get(id: string): Promise<Forfait> {
    const row = db.forfaits.find((f) => f.id === id)
    if (!row) throw new ApiError(404, 'Forfait introuvable')
    return delay(clone(row))
  },
  create(input: ForfaitInput): Promise<Forfait> {
    const forfait: Forfait = { id: uid('f'), ...input }
    db.forfaits.push(forfait)
    return delay(clone(forfait))
  },
  update(id: string, input: Partial<ForfaitInput>): Promise<Forfait> {
    const row = db.forfaits.find((f) => f.id === id)
    if (!row) throw new ApiError(404, 'Forfait introuvable')
    Object.assign(row, input)
    return delay(clone(row))
  },
}

// -- Enrollments ------------------------------------------------------------
export const enrollmentsApi = {
  list(clientId?: string): Promise<Enrollment[]> {
    const rows = db.enrollments.filter((e) => !clientId || e.clientId === clientId)
    return delay(clone(rows))
  },
  get(id: string): Promise<Enrollment> {
    const row = db.enrollments.find((e) => e.id === id)
    if (!row) throw new ApiError(404, 'Inscription introuvable')
    return delay(clone(row))
  },
  create(clientId: string, forfaitId: string): Promise<Enrollment> {
    const forfait = db.forfaits.find((f) => f.id === forfaitId)
    if (!forfait) throw new ApiError(404, 'Forfait introuvable')
    const enrollment: Enrollment = {
      id: uid('e'),
      clientId,
      forfaitId,
      status: 'ACTIVE',
      totalPrice: forfait.price,
      amountPaid: 0,
      outstanding: forfait.price,
      remainingDrivingHours: forfait.drivingHours,
      remainingCodeSessions: forfait.codeSessions,
      enrolledAt: new Date().toISOString(),
    }
    db.enrollments.push(enrollment)
    // Issue an invoice, mirroring the backend flow.
    db.invoices.push({
      id: uid('i'),
      enrollmentId: enrollment.id,
      clientId,
      number: `FAC-${new Date().getFullYear()}-${String(db.invoices.length + 1).padStart(4, '0')}`,
      type: 'INVOICE',
      amount: forfait.price,
      issuedAt: enrollment.enrolledAt,
    })
    return delay(clone(enrollment))
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
    const rows = db.payments
      .filter((p) => !clientId || p.clientId === clientId)
      .sort((a, b) => b.paidAt.localeCompare(a.paidAt))
    return delay(clone(rows))
  },
  create(input: PaymentInput): Promise<Payment> {
    const enrollment = db.enrollments.find((e) => e.id === input.enrollmentId)
    if (!enrollment) throw new ApiError(404, 'Inscription introuvable')
    const payment: Payment = {
      id: uid('p'),
      enrollmentId: input.enrollmentId,
      clientId: enrollment.clientId,
      amount: input.amount,
      method: input.method,
      reference: input.reference ?? null,
      paidAt: new Date().toISOString(),
    }
    db.payments.push(payment)
    enrollment.amountPaid += input.amount
    enrollment.outstanding = Math.max(0, enrollment.totalPrice - enrollment.amountPaid)
    // Issue a receipt.
    db.invoices.push({
      id: uid('i'),
      enrollmentId: enrollment.id,
      clientId: enrollment.clientId,
      number: `REC-${new Date().getFullYear()}-${String(db.invoices.length + 1).padStart(4, '0')}`,
      type: 'RECEIPT',
      amount: input.amount,
      issuedAt: payment.paidAt,
    })
    return delay(clone(payment))
  },
  remind(enrollmentId: string): Promise<void> {
    const enrollment = db.enrollments.find((e) => e.id === enrollmentId)
    if (!enrollment) throw new ApiError(404, 'Inscription introuvable')
    return delay(undefined)
  },
}

// -- Invoices ---------------------------------------------------------------
export const invoicesApi = {
  list(clientId?: string): Promise<Invoice[]> {
    const rows = db.invoices
      .filter((i) => !clientId || i.clientId === clientId)
      .sort((a, b) => b.issuedAt.localeCompare(a.issuedAt))
    return delay(clone(rows))
  },
}