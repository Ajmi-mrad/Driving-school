/**
 * Role & permission helpers. Pure functions, framework-agnostic — reusable
 * by the future React Native app. Mirror the backend @PreAuthorize rules.
 */
import type { Role } from '../types'

export function hasRole(roles: Role[], role: Role): boolean {
  return roles.includes(role)
}

export function hasAnyRole(roles: Role[], ...allowed: Role[]): boolean {
  return roles.some((r) => allowed.includes(r))
}

/** OWNER or SECRETARY. */
export function isStaff(roles: Role[]): boolean {
  return hasAnyRole(roles, 'OWNER', 'SECRETARY')
}

export function isOwner(roles: Role[]): boolean {
  return hasRole(roles, 'OWNER')
}

export const permissions = {
  manageUsers: isStaff,
  manageVehicles: isStaff,
  writeForfaits: isStaff,
  manageFinance: isStaff,
  editBookingSettings: isOwner,
  /** Confirm / refuse / complete sessions. */
  moderateSessions: (roles: Role[]) => hasAnyRole(roles, 'OWNER', 'SECRETARY', 'MONITOR'),
  /** Book / cancel / reschedule own sessions. */
  bookSessions: (roles: Role[]) => hasAnyRole(roles, 'OWNER', 'SECRETARY', 'CLIENT'),
  /** Messaging is monitor <-> client only. */
  chat: (roles: Role[]) => hasAnyRole(roles, 'MONITOR', 'CLIENT'),
} as const