/**
 * Authentication context. React-only (no DOM) so it can be reused by the
 * future React Native app.
 *
 * MOCK behaviour: `login`/`loginAs` resolve a seeded user from the mock db
 * and mint a fake token. To go live, swap the body of `resolveUser` and the
 * login methods for a Keycloak OIDC (PKCE) flow that redirects to
 * `${keycloak}/realms/auto-ecole/...` and then calls GET /api/me. The
 * context shape ({ user, roles, token, login, logout }) stays the same.
 */
import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useState,
  type ReactNode,
} from 'react'
import type { Role, User } from '../types'
import { db, IDS } from '../mock/db'
import { memoryStorage, type AuthStorage } from './storage'

const STORAGE_KEY = 'ae.session.userId'

/** Representative seed user for each role (quick login / dev role switcher). */
const ROLE_SAMPLE_USER: Record<Role, string> = {
  OWNER: IDS.owner,
  SECRETARY: IDS.secretary,
  MONITOR: IDS.monitor1,
  CLIENT: IDS.client1,
}

export type AuthStatus = 'loading' | 'authenticated' | 'unauthenticated'

export interface AuthContextValue {
  user: User | null
  roles: Role[]
  token: string | null
  status: AuthStatus
  login: (userId: string) => void
  loginAs: (role: Role) => void
  logout: () => void
}

const AuthContext = createContext<AuthContextValue | null>(null)

function resolveUser(userId: string): User | null {
  return db.users.find((u) => u.id === userId && u.active) ?? null
}

export function AuthProvider({
  children,
  storage = memoryStorage(),
}: {
  children: ReactNode
  storage?: AuthStorage
}) {
  const [user, setUser] = useState<User | null>(null)
  const [status, setStatus] = useState<AuthStatus>('loading')

  // Restore a persisted session on mount.
  useEffect(() => {
    const savedId = storage.get(STORAGE_KEY)
    const restored = savedId ? resolveUser(savedId) : null
    setUser(restored)
    setStatus(restored ? 'authenticated' : 'unauthenticated')
    // storage is stable for the app lifetime.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  const login = useCallback(
    (userId: string) => {
      const next = resolveUser(userId)
      if (!next) return
      storage.set(STORAGE_KEY, next.id)
      setUser(next)
      setStatus('authenticated')
    },
    [storage],
  )

  const loginAs = useCallback(
    (role: Role) => login(ROLE_SAMPLE_USER[role]),
    [login],
  )

  const logout = useCallback(() => {
    storage.remove(STORAGE_KEY)
    setUser(null)
    setStatus('unauthenticated')
  }, [storage])

  const value = useMemo<AuthContextValue>(
    () => ({
      user,
      roles: user?.roles ?? [],
      // Mock bearer token; a real impl stores the Keycloak access token.
      token: user ? `mock-token-${user.id}` : null,
      status,
      login,
      loginAs,
      logout,
    }),
    [user, status, login, loginAs, logout],
  )

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}

export function useAuth(): AuthContextValue {
  const ctx = useContext(AuthContext)
  if (!ctx) throw new Error('useAuth must be used within an <AuthProvider>')
  return ctx
}