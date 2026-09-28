/**
 * Dev-only data administration API (owner-only, gated server-side by
 * `app.dev-tools.enabled`). Seeds realistic demo data across every service,
 * wipes back to a fresh database, or exports each service's data as JSON.
 *
 * Only the frontend can orchestrate seeding: auth-service is the sole minter
 * of Keycloak subs and has no outbound service clients, so we seed in
 * dependency order (auth -> vehicle -> finance/booking/communication) and
 * thread the returned `seedKey -> sub` / `vehicleKey -> id` maps into the
 * downstream seed request bodies.
 */
import { request } from './client'

/** Fixed password for every seeded demo account (dev only). Matches auth-service DevDataService. */
export const SEED_PASSWORD = 'Passw0rd!'

/** Seeded demo usernames, shown to the tester so they can log in as each role. */
export const SEED_ACCOUNTS: { username: string; role: string }[] = [
  { username: 'directeur', role: 'OWNER' },
  { username: 'secretaire', role: 'SECRETARY' },
  { username: 'moniteur.paul', role: 'MONITOR' },
  { username: 'moniteur.nadia', role: 'MONITOR' },
  { username: 'lucas.m', role: 'CLIENT' },
  { username: 'emma.d', role: 'CLIENT' },
  { username: 'hugo.b', role: 'CLIENT' },
  { username: 'chloe.p', role: 'CLIENT' },
]

export interface SeedResultDto {
  created: number
  skipped: number
}

interface SeedUsersResponse {
  users: Record<string, string>
  result: SeedResultDto
}

interface SeedVehiclesResponse {
  vehicles: Record<string, string>
  result: SeedResultDto
}

export interface SeedSummary {
  created: number
  skipped: number
}

export interface ExportBundle {
  auth: unknown
  vehicle: unknown
  finance: unknown
  booking: unknown
  communication: unknown
}

export const adminApi = {
  /** Seed all services in dependency order, threading the id maps between them. */
  async seedAll(): Promise<SeedSummary> {
    const auth = await request<SeedUsersResponse>('/admin/auth/seed', { method: 'POST' })
    const vehicle = await request<SeedVehiclesResponse>('/admin/vehicle/seed', { method: 'POST' })
    const finance = await request<SeedResultDto>('/admin/finance/seed', {
      method: 'POST',
      body: { users: auth.users },
    })
    const booking = await request<SeedResultDto>('/admin/booking/seed', {
      method: 'POST',
      body: { users: auth.users, vehicles: vehicle.vehicles },
    })
    const communication = await request<SeedResultDto>('/admin/communication/seed', {
      method: 'POST',
      body: { users: auth.users },
    })
    const parts = [auth.result, vehicle.result, finance, booking, communication]
    return {
      created: parts.reduce((n, p) => n + p.created, 0),
      skipped: parts.reduce((n, p) => n + p.skipped, 0),
    }
  },

  /** Wipe every service back to a fresh database (reverse dependency order). */
  async resetAll(): Promise<void> {
    await request('/admin/communication/reset', { method: 'POST' })
    await request('/admin/booking/reset', { method: 'POST' })
    await request('/admin/finance/reset', { method: 'POST' })
    await request('/admin/vehicle/reset', { method: 'POST' })
    await request('/admin/auth/reset', { method: 'POST' })
  },

  /** Export every service's data into a single JSON bundle. */
  async exportAll(): Promise<ExportBundle> {
    const [auth, vehicle, finance, booking, communication] = await Promise.all([
      request<unknown>('/admin/auth/export'),
      request<unknown>('/admin/vehicle/export'),
      request<unknown>('/admin/finance/export'),
      request<unknown>('/admin/booking/export'),
      request<unknown>('/admin/communication/export'),
    ])
    return { auth, vehicle, finance, booking, communication }
  },
}
