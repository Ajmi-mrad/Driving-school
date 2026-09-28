import type { Role, User } from '../types'
import { qs, request } from './client'

export interface UserInput {
  username: string
  email: string
  firstName: string
  lastName: string
  phones: string[]
  roles: Role[]
  permitNumber?: string | null
  notificationsEnabled: boolean
  /** Required on create (min 8 chars); ignored on update. */
  password?: string
  /** Update only: activate (true) or deactivate (false) the account. */
  active?: boolean
}

/**
 * Raw auth-service user shape. It exposes both the internal DB id (`id`) and the
 * Keycloak `sub` (`keycloakId`). The frontend's canonical {@link User.id} is the
 * Keycloak id (consistent with the JWT-derived current user and how booking/
 * finance reference users), so we swap them here and keep the DB id as
 * `internalId` for the user-management endpoints that key on the internal id.
 */
interface RawUser extends Omit<User, 'id' | 'internalId'> {
  id: string
  keycloakId?: string | null
}

function mapUser(raw: RawUser): User {
  const { id, keycloakId, ...rest } = raw
  return { ...rest, id: keycloakId ?? id, internalId: id }
}

/** Slim messaging-contact shape; `id` is already the Keycloak `sub`. */
interface Contact {
  id: string
  firstName: string
  lastName: string
  roles: Role[]
}

function contactToUser(c: Contact): User {
  return {
    id: c.id,
    username: '',
    email: '',
    firstName: c.firstName ?? '',
    lastName: c.lastName ?? '',
    phones: [],
    roles: c.roles,
    permitNumber: null,
    active: true,
    notificationsEnabled: true,
  }
}

export const usersApi = {
  async list(role?: Role): Promise<User[]> {
    const raw = await request<RawUser[]>(`/users${qs({ role })}`)
    return raw.map(mapUser)
  },

  /**
   * Messaging contacts for the current participant (a monitor's students, a
   * student's instructors). Minimal shape (name + Keycloak id) — accessible to
   * MONITOR/CLIENT, unlike {@link usersApi.list} which is staff-only.
   */
  async contacts(): Promise<User[]> {
    const raw = await request<Contact[]>('/users/contacts')
    return raw.map(contactToUser)
  },

  async get(id: string): Promise<User> {
    return mapUser(await request<RawUser>(`/users/${id}`))
  },

  async create(input: UserInput): Promise<User> {
    return mapUser(await request<RawUser>('/users', { method: 'POST', body: input }))
  },

  async update(id: string, input: Partial<UserInput>): Promise<User> {
    return mapUser(await request<RawUser>(`/users/${id}`, { method: 'PUT', body: input }))
  },

  deactivate(id: string): Promise<void> {
    return request<void>(`/users/${id}`, { method: 'DELETE' })
  },

  resetPassword(id: string): Promise<void> {
    return request<void>(`/users/${id}/reset-password`, { method: 'POST' })
  },
}