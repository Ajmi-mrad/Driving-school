import type {
  EnrollmentStatus,
  ExamStatus,
  SessionStatus,
  VehicleStatus,
} from '@/core/types'

type Tone = 'neutral' | 'success' | 'warning' | 'danger' | 'info'

export const VEHICLE_STATUS_TONE: Record<VehicleStatus, Tone> = {
  AVAILABLE: 'success',
  IN_USE: 'info',
  MAINTENANCE: 'warning',
  OUT_OF_SERVICE: 'danger',
}

export const SESSION_STATUS_TONE: Record<SessionStatus, Tone> = {
  PENDING: 'warning',
  CONFIRMED: 'info',
  REFUSED: 'danger',
  CANCELLED: 'neutral',
  COMPLETED: 'success',
}

export const ENROLLMENT_STATUS_TONE: Record<EnrollmentStatus, Tone> = {
  ACTIVE: 'info',
  COMPLETED: 'success',
  CANCELLED: 'neutral',
}

export const EXAM_STATUS_TONE: Record<ExamStatus, Tone> = {
  SCHEDULED: 'info',
  PASSED: 'success',
  FAILED: 'danger',
  NO_SHOW: 'warning',
  CANCELLED: 'neutral',
}
