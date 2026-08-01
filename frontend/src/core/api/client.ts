/**
 * Mock API infrastructure.
 *
 * Framework-agnostic (no DOM). Every api module below simulates network
 * latency over the in-memory `db`. To go live, replace the module bodies
 * with `fetch` calls against API_BASE_URL and attach the bearer token via
 * `authHeaders(token)` — the function signatures stay identical, so the UI
 * and hooks do not change.
 *
 *   Real base URL (through the Spring Cloud Gateway): http://localhost:8222/api
 *   Auth: every request sends `Authorization: Bearer <keycloak access token>`
 */

export const API_BASE_URL =
  (import.meta.env?.VITE_API_BASE_URL as string | undefined) ??
  'http://localhost:8222/api'

/** Build the auth headers a real request would send. */
export function authHeaders(token: string | null): Record<string, string> {
  return {
    'Content-Type': 'application/json',
    ...(token ? { Authorization: `Bearer ${token}` } : {}),
  }
}

/** Simulate network latency. */
export function delay<T>(value: T, ms = 350): Promise<T> {
  return new Promise((resolve) => setTimeout(() => resolve(value), ms))
}

/** Deep clone so callers never mutate the mock store by reference. */
export function clone<T>(value: T): T {
  return structuredClone(value)
}

/** Generate a mock id. */
export function uid(prefix = 'id'): string {
  return `${prefix}-${Math.random().toString(36).slice(2, 10)}`
}

export class ApiError extends Error {
  status: number

  constructor(status: number, message: string) {
    super(message)
    this.name = 'ApiError'
    this.status = status
  }
}