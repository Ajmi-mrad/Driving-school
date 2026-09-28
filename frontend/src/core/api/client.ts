/**
 * HTTP infrastructure for the real backend.
 *
 * Framework-agnostic (no DOM). Every api module below issues `fetch` calls
 * against the Spring Cloud Gateway and attaches the bearer token via
 * `authHeaders`. The auth token is held module-side and refreshed by the
 * AuthProvider through `setAuthToken`, so the api function signatures stay
 * free of a token argument and the UI / hooks do not change.
 *
 *   Base URL (through the gateway): http://localhost:8222/api
 *   Auth: every request sends `Authorization: Bearer <keycloak access token>`
 */
import type { Page } from '../types'

export const API_BASE_URL =
  (import.meta.env?.VITE_API_BASE_URL as string | undefined) ??
  'http://localhost:8222/api'

// -- Auth token ------------------------------------------------------------
let authToken: string | null = null

/** Set by the AuthProvider whenever the access token changes. */
export function setAuthToken(token: string | null): void {
  authToken = token
}

/** Build the auth headers a request sends. */
export function authHeaders(token: string | null): Record<string, string> {
  return {
    'Content-Type': 'application/json',
    ...(token ? { Authorization: `Bearer ${token}` } : {}),
  }
}

/** Generate an id (used for optimistic client-side rows). */
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

// -- Query strings ---------------------------------------------------------
export type QueryValue = string | number | boolean | null | undefined

/**
 * Serialize a params object into a `?a=1&b=2` string (skips empty values).
 * Accepts `object` so interface-typed filter args pass without an explicit
 * index signature.
 */
export function qs(params?: Record<string, QueryValue> | object): string {
  if (!params) return ''
  const search = new URLSearchParams()
  for (const [key, value] of Object.entries(params as Record<string, QueryValue>)) {
    if (value !== null && value !== undefined && value !== '') {
      search.append(key, String(value))
    }
  }
  const str = search.toString()
  return str ? `?${str}` : ''
}

// -- Core request ----------------------------------------------------------
interface RequestOptions {
  method?: 'GET' | 'POST' | 'PUT' | 'PATCH' | 'DELETE'
  body?: unknown
}

/**
 * Issue a request against the gateway and parse the JSON response.
 * Throws an {@link ApiError} on any non-2xx status. Empty bodies
 * (204/202 or no content) resolve to `undefined`.
 */
export async function request<T>(path: string, options: RequestOptions = {}): Promise<T> {
  const { method = 'GET', body } = options
  let res: Response
  try {
    res = await fetch(`${API_BASE_URL}${path}`, {
      method,
      headers: authHeaders(authToken),
      body: body === undefined ? undefined : JSON.stringify(body),
    })
  } catch {
    throw new ApiError(0, 'Impossible de joindre le serveur')
  }

  if (!res.ok) {
    throw new ApiError(res.status, await extractError(res))
  }

  // No content (204/202) or empty body.
  if (res.status === 204 || res.status === 202) return undefined as T
  const text = await res.text()
  return (text ? JSON.parse(text) : undefined) as T
}

/**
 * Issue a request and return the raw response body as a {@link Blob} (for file
 * downloads: PDF, ZIP). Sends the bearer token but no JSON `Content-Type`.
 * Throws an {@link ApiError} on any non-2xx status.
 */
export async function requestBlob(path: string): Promise<Blob> {
  let res: Response
  try {
    res = await fetch(`${API_BASE_URL}${path}`, {
      method: 'GET',
      headers: authToken ? { Authorization: `Bearer ${authToken}` } : {},
    })
  } catch {
    throw new ApiError(0, 'Impossible de joindre le serveur')
  }
  if (!res.ok) {
    throw new ApiError(res.status, await extractError(res))
  }
  return res.blob()
}

/** Best-effort human-readable message from an error response. */
async function extractError(res: Response): Promise<string> {
  try {
    const text = await res.text()
    if (!text) return res.statusText || `Erreur ${res.status}`
    try {
      const json = JSON.parse(text)
      return json.message ?? json.error ?? json.detail ?? text
    } catch {
      return text
    }
  } catch {
    return res.statusText || `Erreur ${res.status}`
  }
}

// -- Spring Page adapter ---------------------------------------------------
/** Raw Spring Data `Page` JSON (uses `number` for the page index). */
export interface SpringPage<T> {
  content: T[]
  number: number
  size: number
  totalElements: number
  totalPages: number
}

/** Map a Spring `Page` (optionally mapping each row) to the frontend {@link Page}. */
export function mapPage<S, T>(raw: SpringPage<S>, mapRow: (row: S) => T): Page<T> {
  return {
    content: raw.content.map(mapRow),
    page: raw.number,
    size: raw.size,
    totalElements: raw.totalElements,
    totalPages: raw.totalPages,
  }
}