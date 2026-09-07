/**
 * Authentication context. React-only (no DOM) so it can be reused by the
 * future React Native app: all identity-provider mechanics live behind the
 * injected {@link AuthClient} port (see `src/lib/keycloak-auth.ts` on web).
 *
 * The context shape ({ user, roles, token, status, signIn, logout }) is
 * stable across platforms; only the injected adapter changes.
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
import { setAuthToken } from '../api/client'
import type { AuthClient, AuthSession } from './oidc'

export type AuthStatus = 'loading' | 'authenticated' | 'unauthenticated'

export interface AuthContextValue {
  user: User | null
  roles: Role[]
  token: string | null
  status: AuthStatus
  /** Redirect to the identity provider to sign in. */
  signIn: () => void
  /** Finish the redirect callback (used by the /auth/callback route). */
  completeSignin: () => Promise<void>
  logout: () => void
}

const AuthContext = createContext<AuthContextValue | null>(null)

export function AuthProvider({
  children,
  authClient,
}: {
  children: ReactNode
  authClient: AuthClient
}) {
  const [user, setUser] = useState<User | null>(null)
  const [token, setToken] = useState<string | null>(null)
  const [status, setStatus] = useState<AuthStatus>('loading')

  const applySession = useCallback((session: AuthSession | null) => {
    setUser(session?.user ?? null)
    setToken(session?.token ?? null)
    // Keep the api layer's bearer token in sync.
    setAuthToken(session?.token ?? null)
    setStatus(session ? 'authenticated' : 'unauthenticated')
  }, [])

  // Restore a persisted session and subscribe to token changes.
  useEffect(() => {
    let active = true
    authClient
      .getSession()
      .then((session) => active && applySession(session))
      .catch(() => active && applySession(null))
    const unsubscribe = authClient.subscribe((session) => applySession(session))
    return () => {
      active = false
      unsubscribe()
    }
  }, [authClient, applySession])

  const signIn = useCallback(() => {
    void authClient.signinRedirect()
  }, [authClient])

  const completeSignin = useCallback(async () => {
    applySession(await authClient.completeSignin())
  }, [authClient, applySession])

  const logout = useCallback(() => {
    void authClient.signout().finally(() => applySession(null))
  }, [authClient, applySession])

  const value = useMemo<AuthContextValue>(
    () => ({
      user,
      roles: user?.roles ?? [],
      token,
      status,
      signIn,
      completeSignin,
      logout,
    }),
    [user, token, status, signIn, completeSignin, logout],
  )

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}

export function useAuth(): AuthContextValue {
  const ctx = useContext(AuthContext)
  if (!ctx) throw new Error('useAuth must be used within an <AuthProvider>')
  return ctx
}