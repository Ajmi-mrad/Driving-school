/**
 * Domain types for the Auto-École platform.
 *
 * Framework-agnostic (no DOM / no React). Safe to copy into the future
 * React Native app. Mirrors the backend JPA entities exposed through the
 * API gateway at http://localhost:8222/api.
 *
 * NOTE: the project's tsconfig uses `erasableSyntaxOnly`, so we model
 * backend enums as string-literal unions + `as const` value arrays rather
 * than TypeScript `enum`s.
 */

// ---------------------------------------------------------------------------
// Roles
// ---------------------------------------------------------------------------
export const ROLES = ['OWNER', 'SECRETARY', 'MONITOR', 'CLIENT'] as const
export type Role = (typeof ROLES)[number]

// ---------------------------------------------------------------------------
// Users (auth-service)
// ---------------------------------------------------------------------------
export interface User {
  id: string
  username: string
  email: string
  firstName: string
  lastName: string
  phones: string[]
  roles: Role[]
  permitNumber?: string | null
  active: boolean
  notificationsEnabled: boolean
}

// ---------------------------------------------------------------------------
// Vehicles (vehicle-service)
// ---------------------------------------------------------------------------
export const GEARBOX_TYPES = ['MANUAL', 'AUTOMATIC'] as const
export type GearboxType = (typeof GEARBOX_TYPES)[number]

export const FUEL_TYPES = ['GASOLINE', 'DIESEL', 'ELECTRIC', 'HYBRID'] as const
export type FuelType = (typeof FUEL_TYPES)[number]

export const VEHICLE_STATUSES = [
  'AVAILABLE',
  'IN_USE',
  'MAINTENANCE',
  'OUT_OF_SERVICE',
] as const
export type VehicleStatus = (typeof VEHICLE_STATUSES)[number]

export interface Vehicle {
  id: string
  brand: string
  model: string
  registrationNumber: string
  gearboxType: GearboxType
  fuelType: FuelType
  status: VehicleStatus
  manufactureYear: number
  mileage: number
  insuranceExpiry: string // ISO date
  technicalInspectionExpiry: string // ISO date
}

export const MAINTENANCE_TYPES = [
  'REVISION',
  'REPAIR',
  'TECHNICAL_INSPECTION',
  'INSURANCE_RENEWAL',
  'OTHER',
] as const
export type MaintenanceType = (typeof MAINTENANCE_TYPES)[number]

export interface MaintenanceRecord {
  id: string
  vehicleId: string
  type: MaintenanceType
  description: string
  cost: number
  date: string // ISO date
  mileage: number
}

// ---------------------------------------------------------------------------
// Sessions (booking-service)
// ---------------------------------------------------------------------------
export const SESSION_TYPES = ['DRIVING', 'CODE'] as const
export type SessionType = (typeof SESSION_TYPES)[number]

export const SESSION_STATUSES = [
  'PENDING',
  'CONFIRMED',
  'REFUSED',
  'CANCELLED',
  'COMPLETED',
] as const
export type SessionStatus = (typeof SESSION_STATUSES)[number]

export interface Session {
  id: string
  type: SessionType
  clientId: string
  monitorId?: string | null
  vehicleId?: string | null
  startTime: string // ISO datetime
  endTime: string // ISO datetime
  status: SessionStatus
  notes?: string | null
}

export interface BookingSettings {
  autoValidationEnabled: boolean
  cancellationNoticeHours: number
}

// ---------------------------------------------------------------------------
// Finance (finance-service)
// ---------------------------------------------------------------------------
export interface Forfait {
  id: string
  name: string
  description: string
  drivingHours: number
  codeSessions: number
  price: number
  active: boolean
}

export const ENROLLMENT_STATUSES = ['ACTIVE', 'COMPLETED', 'CANCELLED'] as const
export type EnrollmentStatus = (typeof ENROLLMENT_STATUSES)[number]

export interface Enrollment {
  id: string
  clientId: string
  forfaitId: string
  status: EnrollmentStatus
  totalPrice: number
  amountPaid: number
  outstanding: number
  remainingDrivingHours: number
  remainingCodeSessions: number
  enrolledAt: string // ISO datetime
}

export const PAYMENT_METHODS = ['CASH', 'CHECK', 'TRANSFER', 'CARD'] as const
export type PaymentMethod = (typeof PAYMENT_METHODS)[number]

export interface Payment {
  id: string
  enrollmentId: string
  clientId: string
  amount: number
  method: PaymentMethod
  reference?: string | null
  paidAt: string // ISO datetime
}

export const INVOICE_TYPES = ['INVOICE', 'RECEIPT'] as const
export type InvoiceType = (typeof INVOICE_TYPES)[number]

export interface Invoice {
  id: string
  enrollmentId: string
  clientId: string
  number: string
  type: InvoiceType
  amount: number
  issuedAt: string // ISO datetime
}

// ---------------------------------------------------------------------------
// Communication (communication-service)
// ---------------------------------------------------------------------------
export interface Conversation {
  id: string
  monitorId: string
  clientId: string
  lastMessageAt: string // ISO datetime
  lastMessagePreview: string
  unreadCount: number
}

export interface Message {
  id: string
  conversationId: string
  senderId: string
  content: string
  sentAt: string // ISO datetime
  readAt?: string | null
}

export const NOTIFICATION_TYPES = ['NEW_MESSAGE', 'PAYMENT_DUE'] as const
export type NotificationType = (typeof NOTIFICATION_TYPES)[number]

export interface Notification {
  id: string
  recipientId: string
  type: NotificationType
  title: string
  body: string
  amount?: number | null
  referenceId?: string | null
  readAt?: string | null
  createdAt: string // ISO datetime
}

// ---------------------------------------------------------------------------
// Shared API shapes
// ---------------------------------------------------------------------------
export interface Page<T> {
  content: T[]
  page: number
  size: number
  totalElements: number
  totalPages: number
}