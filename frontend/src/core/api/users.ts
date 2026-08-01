import type { Role, User } from '../types'
import { db } from '../mock/db'
import { ApiError, clone, delay, uid } from './client'

export interface UserInput {
  username: string
  email: string
  firstName: string
  lastName: string
  phones: string[]
  roles: Role[]
  permitNumber?: string | null
  notificationsEnabled: boolean
}

export const usersApi = {
  list(role?: Role): Promise<User[]> {
    const rows = db.users.filter((u) => !role || u.roles.includes(role))
    return delay(clone(rows))
  },

  get(id: string): Promise<User> {
    const row = db.users.find((u) => u.id === id)
    if (!row) throw new ApiError(404, 'Utilisateur introuvable')
    return delay(clone(row))
  },

  create(input: UserInput): Promise<User> {
    const user: User = { id: uid('u'), active: true, ...input }
    db.users.push(user)
    return delay(clone(user))
  },

  update(id: string, input: Partial<UserInput>): Promise<User> {
    const row = db.users.find((u) => u.id === id)
    if (!row) throw new ApiError(404, 'Utilisateur introuvable')
    Object.assign(row, input)
    return delay(clone(row))
  },

  deactivate(id: string): Promise<void> {
    const row = db.users.find((u) => u.id === id)
    if (!row) throw new ApiError(404, 'Utilisateur introuvable')
    row.active = false
    return delay(undefined)
  },

  resetPassword(id: string): Promise<void> {
    const row = db.users.find((u) => u.id === id)
    if (!row) throw new ApiError(404, 'Utilisateur introuvable')
    return delay(undefined)
  },
}